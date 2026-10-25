package io.github.duddmeister.weather.data

import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.core.HttpException
import io.github.duddmeister.weather.data.remote.MetOfficeClient
import io.github.duddmeister.weather.model.MetOfficeData
import io.github.duddmeister.weather.model.SavedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What's kept about the user's Met Office key: the key itself and how much of today's allowance is used. */
data class MetOfficeAccount(
    val apiKey: String? = null,
    /** The Met Office turned this key down; nothing is fetched with it until it's replaced. */
    val keyRejected: Boolean = false,
    /** The UTC day (days since the epoch) that [calls] counts. */
    val callDay: Long = 0,
    val calls: Int = 0,
    /** The UTC day on which the Met Office said the day's calls were used up. */
    val limitDay: Long? = null,
    /** Epoch millis of the last successful call. */
    val lastSuccessAt: Long? = null,
) {
    fun callsOn(day: Long): Int = if (callDay == day) calls else 0
}

/** Where the account is kept; in the app, a store that backups leave out. */
interface MetOfficeAccountStore {
    suspend fun load(): MetOfficeAccount
    suspend fun save(account: MetOfficeAccount)
}

enum class MetOfficeStatus {
    NoKey,
    /** A new key is being tried out. */
    Checking,
    /** The key works, or hasn't been turned down yet. */
    Ready,
    /** The Met Office turned the key down (wrong key, or not one for site-specific forecasts). */
    Rejected,
    /** Today's calls are used up; the allowance resets at midnight UTC. */
    LimitReached,
    /** The last call failed for another reason, such as no connection. */
    Unavailable,
}

/** What Settings shows about the key. */
data class MetOfficeState(
    val status: MetOfficeStatus = MetOfficeStatus.NoKey,
    /** The key's last few characters, to tell keys apart without showing them. */
    val keyHint: String? = null,
    val callsToday: Int = 0,
    val lastSuccessAt: Long? = null,
)

/**
 * The Met Office's own forecasts for places in the UK and Ireland, fetched with the user's Weather
 * DataHub key. The free plan allows 360 calls a day, so a place's hourly forecast is fetched at most
 * every half hour and its daily one every three hours, and calls stop for the day short of the limit.
 * Anything that goes wrong just leaves the place on Open-Meteo's forecast.
 */
class MetOfficeForecasts(
    private val client: MetOfficeClient,
    private val store: MetOfficeAccountStore,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var account: MetOfficeAccount? = null
    private var checking = false
    private var unavailable = false

    private val _state = MutableStateFlow(MetOfficeState())
    val state: StateFlow<MetOfficeState> = _state.asStateFlow()

    init {
        scope.launch { mutex.withLock { loaded() } }
    }

    /**
     * Saves the key (null or blank removes it) and tries a new one out with a single call, so
     * Settings can say straight away whether it works.
     */
    suspend fun setKey(raw: String?) {
        val key = raw?.let(::cleanKey)
        mutex.withLock {
            val current = loaded()
            val same = key == current.apiKey
            save(
                MetOfficeAccount(
                    apiKey = key,
                    callDay = current.callDay,
                    calls = if (same) current.calls else 0,
                    lastSuccessAt = if (same) current.lastSuccessAt else null,
                ),
            )
            unavailable = false
            checking = key != null
            publish()
        }
        if (key == null) return
        try {
            call { client.daily(it, CHECK_LATITUDE, CHECK_LONGITUDE) }
        } finally {
            mutex.withLock {
                checking = false
                publish()
            }
        }
    }

    /**
     * The Met Office forecast for [location], with whichever parts are due fetched afresh, or null
     * when there's no key, the place isn't covered or nothing usable is at hand. [previous] is what
     * was fetched for the place before; [requested] means someone asked for this refresh.
     */
    suspend fun forecastFor(location: SavedLocation, previous: MetOfficeData?, requested: Boolean): MetOfficeData? {
        if (!MetOfficeClient.covers(location.countryCode)) return null
        if (mutex.withLock { loaded().apiKey } == null) return null
        val now = clock()
        val kept = previous?.let { usable(it, now) } ?: MetOfficeData()
        val (hourly, daily) = coroutineScope {
            val hourly = if (hourlyDue(kept, now, requested)) async { call { client.hourly(it, location.latitude, location.longitude) } } else null
            val daily = if (dailyDue(kept, now, requested)) async { call { client.daily(it, location.latitude, location.longitude) } } else null
            hourly?.await()?.takeIf { it.isNotEmpty() } to daily?.await()?.takeIf { it.isNotEmpty() }
        }
        val data = MetOfficeData(
            hourly = hourly ?: kept.hourly,
            daily = daily ?: kept.daily,
            hourlyFetchedAt = if (hourly != null) now else kept.hourlyFetchedAt,
            dailyFetchedAt = if (daily != null) now else kept.dailyFetchedAt,
        )
        return data.takeIf { it.hourly.isNotEmpty() || it.daily.isNotEmpty() }
    }

    /** Makes one call if the key and today's allowance allow it; null if it wasn't made or failed. */
    private suspend fun <T> call(block: suspend (apiKey: String) -> T): T? {
        val key = mutex.withLock {
            val current = loaded()
            val today = utcDay(clock())
            val key = current.apiKey
            if (key == null || current.keyRejected || current.limitDay == today || current.callsOn(today) >= DAILY_CALL_LIMIT) {
                publish()
                return null
            }
            save(current.copy(callDay = today, calls = current.callsOn(today) + 1))
            key
        }
        val result = try {
            Result.success(block(key))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Met Office forecast failed", e)
            Result.failure(e)
        }
        mutex.withLock {
            val current = loaded()
            // The key was changed or removed while this call was out.
            if (current.apiKey != key) return null
            val error = result.exceptionOrNull()
            unavailable = false
            when {
                error == null -> save(current.copy(lastSuccessAt = clock()))
                error is HttpException && error.code in REJECTED_CODES -> save(current.copy(keyRejected = true))
                error is HttpException && error.code == 429 -> save(current.copy(limitDay = utcDay(clock())))
                else -> unavailable = true
            }
            publish()
        }
        return result.getOrNull()
    }

    /** The account, read once; if it can't be read, there's no key for now and the next use tries again. */
    private suspend fun loaded(): MetOfficeAccount = account ?: try {
        store.load().also {
            account = it
            publish()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w(TAG, "Couldn't read the Met Office key", e)
        MetOfficeAccount()
    }

    private suspend fun save(updated: MetOfficeAccount) {
        account = updated
        try {
            store.save(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Couldn't save the Met Office key", e)
        }
    }

    private fun publish() {
        val current = account ?: return
        val today = utcDay(clock())
        _state.value = MetOfficeState(
            status = when {
                current.apiKey == null -> MetOfficeStatus.NoKey
                checking -> MetOfficeStatus.Checking
                current.keyRejected -> MetOfficeStatus.Rejected
                current.limitDay == today || current.callsOn(today) >= DAILY_CALL_LIMIT -> MetOfficeStatus.LimitReached
                unavailable -> MetOfficeStatus.Unavailable
                else -> MetOfficeStatus.Ready
            },
            // Keys end in base64 padding, which every key shares.
            keyHint = current.apiKey?.trimEnd('=')?.takeLast(4),
            callsToday = current.callsOn(today),
            lastSuccessAt = current.lastSuccessAt,
        )
    }

    companion object {
        private const val TAG = "MetOffice"
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE

        /** The free plan allows 360 calls a day; this leaves room for slack, such as a second phone. */
        const val DAILY_CALL_LIMIT = 340

        /** The key itself was refused, or isn't subscribed to site-specific forecasts. */
        private val REJECTED_CODES = setOf(401, 403)

        /** A new key is tried out on London's forecast. */
        private const val CHECK_LATITUDE = 51.5072
        private const val CHECK_LONGITUDE = -0.1276

        fun utcDay(epochMillis: Long): Long = Math.floorDiv(epochMillis, 24 * HOUR)

        /** Keys are long tokens; copying one from a web page can pick up spaces, line breaks or quotes. */
        fun cleanKey(raw: String): String? = raw.filterNot { it.isWhitespace() }.trim('"', '\'').ifEmpty { null }

        fun hourlyDue(data: MetOfficeData, now: Long, requested: Boolean): Boolean =
            data.hourly.isEmpty() || now - data.hourlyFetchedAt >= if (requested) 10 * MINUTE else 30 * MINUTE

        fun dailyDue(data: MetOfficeData, now: Long, requested: Boolean): Boolean =
            data.daily.isEmpty() || now - data.dailyFetchedAt >= if (requested) HOUR else 3 * HOUR

        /**
         * Drops parts too old to trust, which happens when calls keep failing or the day's allowance
         * runs out: hourly forecasts after six hours, daily ones after twelve.
         */
        fun usable(data: MetOfficeData, now: Long): MetOfficeData = data.copy(
            hourly = data.hourly.takeIf { now - data.hourlyFetchedAt < 6 * HOUR }.orEmpty(),
            daily = data.daily.takeIf { now - data.dailyFetchedAt < 12 * HOUR }.orEmpty(),
        )
    }
}
