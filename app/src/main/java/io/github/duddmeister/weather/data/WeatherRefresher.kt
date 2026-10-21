package io.github.duddmeister.weather.data

import android.content.Context
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.data.local.SettingsStore
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.notify.WeatherNotifier
import io.github.duddmeister.weather.widget.WidgetUpdater
import io.github.duddmeister.weather.work.WorkScheduler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What the widgets need to know about the latest background refresh attempt. */
data class WidgetRefreshState(
    val refreshing: Boolean = false,
    /** Epoch millis of the last failed attempt (0 if none). Widgets show "offline" while it's newer than the data. */
    val lastFailureAt: Long = 0L,
)

/** Coordinates refreshes for the app, widget reload button and background work. */
class WeatherRefresher(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val locations: LocationRepository,
    private val repository: WeatherRepository,
    private val notifier: WeatherNotifier,
) {
    private val _widgetState = MutableStateFlow(WidgetRefreshState())
    val widgetState: StateFlow<WidgetRefreshState> = _widgetState.asStateFlow()

    private val backgroundLock = Mutex()

    /** App in the foreground: follow the device location, then refresh anything stale. */
    suspend fun refreshForeground(force: Boolean = false, first: String? = null) {
        val settings = settingsStore.currentSettings()
        if (settings.useCurrentLocation && locations.hasLocationPermission()) {
            locations.updateCurrentLocation()
        }
        val display = locations.displayLocationsNow()
        val ordered = display.sortedByDescending { it.id == first }
        refreshAll(ordered, force)
    }

    /** Refresh a single place, e.g. from pull-to-refresh. */
    suspend fun refreshOne(location: SavedLocation): RefreshOutcome {
        val target = if (location.isCurrent && locations.hasLocationPermission()) {
            locations.updateCurrentLocation() ?: location
        } else {
            location
        }
        return repository.refresh(target, force = true)
    }

    /**
     * Widget reload button: shows a spinner, refreshes what's on screen and re-renders. Bounded so it
     * fits within a broadcast; if it can't finish (offline), a refresh is queued for when the network returns.
     */
    suspend fun refreshFromWidget() {
        _widgetState.value = _widgetState.value.copy(refreshing = true)
        WidgetUpdater.updateAll(context)
        val ok = withTimeoutOrNull(WIDGET_TIMEOUT_MILLIS) { refreshBackgroundLocked(force = true) } ?: false
        _widgetState.value = WidgetRefreshState(
            refreshing = false,
            lastFailureAt = if (ok) 0L else System.currentTimeMillis(),
        )
        WidgetUpdater.updateAll(context)
        if (!ok) WorkScheduler.refreshWhenOnline(context)
    }

    /** Periodic/background refresh. Returns true when everything that was needed is up to date. */
    suspend fun refreshBackground(force: Boolean = false): Boolean {
        val ok = refreshBackgroundLocked(force)
        if (ok) {
            _widgetState.value = WidgetRefreshState()
        } else if (_widgetState.value.lastFailureAt == 0L) {
            _widgetState.value = WidgetRefreshState(lastFailureAt = System.currentTimeMillis())
        }
        // Re-render even when offline: widgets read "now" off the cached forecast timeline.
        WidgetUpdater.updateAll(context)
        return ok
    }

    private suspend fun refreshBackgroundLocked(force: Boolean): Boolean = backgroundLock.withLock {
        val settings = settingsStore.currentSettings()
        if (settings.useCurrentLocation && settings.backgroundLocation && locations.hasBackgroundLocationPermission()) {
            runCatching { withTimeoutOrNull(8_000) { locations.updateCurrentLocation() } }
                .onFailure { AppLog.w(TAG, "Background location failed", it) }
        }
        val display = locations.displayLocationsNow()
        if (display.isEmpty()) return@withLock true
        val primary = display.first()
        val widgetIds = WidgetUpdater.locationIdsInUse(context)
        val targets = display.filter { location ->
            location.id == primary.id || location.id in widgetIds
        }
        val outcomes = refreshAll(targets, force, maxAgeMillis = BACKGROUND_MAX_AGE)
        notifier.evaluate(primary, targets, repository.snapshots.value, settings)
        outcomes.none { it is RefreshOutcome.Failed }
    }

    private suspend fun refreshAll(
        targets: List<SavedLocation>,
        force: Boolean,
        maxAgeMillis: Long = WeatherRepository.DEFAULT_MAX_AGE,
    ): List<RefreshOutcome> = coroutineScope {
        targets.map { location -> async { repository.refresh(location, force, maxAgeMillis) } }.awaitAll()
    }

    private companion object {
        const val TAG = "WeatherRefresher"
        const val WIDGET_TIMEOUT_MILLIS = 25_000L
        /** Background runs are ~15 minutes apart; anything older than this gets refetched. */
        const val BACKGROUND_MAX_AGE = 5 * 60 * 1000L
    }
}
