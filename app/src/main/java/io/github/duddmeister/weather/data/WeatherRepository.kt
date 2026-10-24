package io.github.duddmeister.weather.data

import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.core.HttpException
import io.github.duddmeister.weather.data.local.WeatherCache
import io.github.duddmeister.weather.data.remote.MeteoAlarmClient
import io.github.duddmeister.weather.data.remote.NwsClient
import io.github.duddmeister.weather.data.remote.OpenMeteoClient
import io.github.duddmeister.weather.data.remote.toAirQuality
import io.github.duddmeister.weather.data.remote.toSnapshot
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed interface RefreshOutcome {
    data class Updated(val snapshot: WeatherSnapshot) : RefreshOutcome
    data class Fresh(val snapshot: WeatherSnapshot) : RefreshOutcome
    data class Failed(val error: Throwable, val cached: WeatherSnapshot?) : RefreshOutcome
}

/**
 * Single source of truth for weather data. Cached snapshots load from disk at startup so the UI
 * and widgets render immediately; refreshes are de-duplicated per location.
 */
class WeatherRepository(
    private val openMeteo: OpenMeteoClient,
    private val nws: NwsClient,
    private val meteoAlarm: MeteoAlarmClient,
    private val cache: WeatherCache,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Whether the current connection is metered (mobile data), to go easy on large downloads. */
    private val isMetered: () -> Boolean = { false },
    private val language: () -> String = { Locale.getDefault().language },
) {
    private val _snapshots = MutableStateFlow<Map<String, WeatherSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, WeatherSnapshot>> = _snapshots.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    init {
        scope.launch {
            try {
                val cached = cache.loadAll()
                _snapshots.update { current -> cached + current }
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    fun snapshot(locationId: String): WeatherSnapshot? = _snapshots.value[locationId]

    /**
     * Fetches fresh data for [location] unless the cached copy is younger than [maxAgeMillis]
     * (always fetches when [force]).
     */
    suspend fun refresh(location: SavedLocation, force: Boolean = false, maxAgeMillis: Long = DEFAULT_MAX_AGE): RefreshOutcome {
        awaitLoaded()
        val lock = locks.getOrPut(location.id) { Mutex() }
        return lock.withLock {
            val existing = _snapshots.value[location.id]
            if (!force && existing != null && existing.isFor(location) && clock() - existing.fetchedAt < maxAgeMillis) {
                return@withLock RefreshOutcome.Fresh(existing)
            }
            _refreshing.update { it + location.id }
            try {
                val snapshot = fetch(location, existing, requested = force)
                cache.save(snapshot)
                _snapshots.update { it + (location.id to snapshot) }
                _errors.update { it - location.id }
                AppLog.i(TAG, "Updated ${location.name}: ${snapshot.current.temperature}°C, ${snapshot.hourly.size} hours")
                RefreshOutcome.Updated(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Refresh failed for ${location.name}", e)
                _errors.update { it + (location.id to userMessage(e)) }
                RefreshOutcome.Failed(e, existing)
            } finally {
                _refreshing.update { it - location.id }
            }
        }
    }

    /** [requested]: someone asked for this refresh, so warnings may be checked sooner. */
    private suspend fun fetch(location: SavedLocation, previous: WeatherSnapshot?, requested: Boolean): WeatherSnapshot = coroutineScope {
        val now = clock()
        val reusable = previous?.takeIf { it.isFor(location) && now - it.fetchedAt < 3 * 3600_000L }
        val forecast = async { openMeteo.forecast(location.latitude, location.longitude) }
        val air = async {
            optional("air quality") { openMeteo.airQuality(location.latitude, location.longitude).toAirQuality() }
        }
        val source = when {
            location.isUnitedStates -> SOURCE_NWS
            MeteoAlarmClient.covers(location.countryCode) -> SOURCE_METEOALARM
            else -> null
        }
        val alerts = async {
            when (source) {
                SOURCE_NWS -> optional("alerts") { nws.activeAlerts(location.latitude, location.longitude, now / 1000) }
                SOURCE_METEOALARM -> if (warningsDue(reusable, now, requested)) {
                    optional("warnings") {
                        meteoAlarm.warningsAt(location.countryCode, location.latitude, location.longitude, now / 1000, language())
                    }
                } else {
                    null
                }
                else -> null
            }
        }
        val base = forecast.await().toSnapshot(location, now)
        val fetchedAlerts = alerts.await()
        base.copy(
            airQuality = air.await() ?: reusable?.airQuality,
            alerts = fetchedAlerts ?: reusable?.alerts.orEmpty().filter { (it.endTime ?: Long.MAX_VALUE) > now / 1000 },
            alertsSupported = source != null,
            alertsSource = source,
            alertsFetchedAt = if (fetchedAlerts != null) now else reusable?.alertsFetchedAt,
        )
    }

    /**
     * MeteoAlarm serves a whole country's warnings uncompressed and without caching headers (the UK's
     * is about 300 KB), so it's checked at most every half hour on Wi-Fi and every two hours on mobile
     * data, or after ten minutes when someone asks for a refresh. Warnings are mostly issued hours or
     * days ahead.
     */
    private fun warningsDue(previous: WeatherSnapshot?, now: Long, requested: Boolean): Boolean {
        val last = previous?.takeIf { it.alertsSource == SOURCE_METEOALARM }?.alertsFetchedAt ?: return true
        val interval = when {
            requested -> 10 * 60_000L
            isMetered() -> 2 * 3600_000L
            else -> 30 * 60_000L
        }
        return now - last >= interval
    }

    private suspend fun <T> optional(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w(TAG, "Couldn't load $what", e)
        null
    }

    suspend fun remove(locationId: String) {
        _snapshots.update { it - locationId }
        _errors.update { it - locationId }
        cache.delete(locationId)
    }

    private fun WeatherSnapshot.isFor(location: SavedLocation): Boolean =
        location.distanceKmTo(latitude, longitude) < 2.0

    companion object {
        private const val TAG = "WeatherRepository"
        const val SOURCE_NWS = "National Weather Service"
        const val SOURCE_METEOALARM = "MeteoAlarm"
        const val DEFAULT_MAX_AGE = 10 * 60 * 1000L

        fun userMessage(e: Throwable): String = when (e) {
            is UnknownHostException -> "No internet connection"
            is SocketTimeoutException -> "The weather service took too long to respond"
            is HttpException -> "The weather service returned an error (${e.code})"
            else -> "Couldn't update the weather"
        }
    }
}
