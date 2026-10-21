package io.github.duddmeister.weather.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.duddmeister.weather.container
import io.github.duddmeister.weather.data.remote.GeocodingResult
import io.github.duddmeister.weather.data.remote.toSavedLocation
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.update.ReleaseInfo
import io.github.duddmeister.weather.update.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class LocationPage(
    val location: SavedLocation,
    val snapshot: WeatherSnapshot?,
    val refreshing: Boolean,
    val error: String?,
)

data class Permissions(
    val location: Boolean = false,
    val backgroundLocation: Boolean = false,
    /** Exempt from battery optimisation, so background refresh isn't deferred. */
    val unrestrictedBattery: Boolean = false,
)

data class MainUiState(
    val ready: Boolean = false,
    val pages: List<LocationPage> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val permissions: Permissions = Permissions(),
    val locating: Boolean = false,
) {
    val hasLocationPermission: Boolean get() = permissions.location

    /** Nothing to show yet and nothing on the way: ask for location or a city. */
    val needsSetup: Boolean get() = ready && pages.isEmpty() && !locating
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val results: List<GeocodingResult>) : SearchState
    data class Failed(val message: String) : SearchState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    private val permissions = MutableStateFlow(currentPermissions())
    private val locating = MutableStateFlow(false)
    private val loaded = flow {
        c.repository.awaitLoaded()
        emit(true)
    }.onStart { emit(false) }

    private val pages = combine(
        c.locations.displayLocations,
        c.repository.snapshots,
        c.repository.refreshing,
        c.repository.errors,
    ) { locations, snapshots, refreshing, errors ->
        locations.map { location ->
            LocationPage(location, snapshots[location.id], location.id in refreshing, errors[location.id])
        }
    }

    val uiState: StateFlow<MainUiState> = combine(loaded, pages, c.settingsStore.settings, permissions, locating) { ready, pages, settings, permissions, locating ->
        MainUiState(ready, pages, settings, permissions, locating)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val updateState: StateFlow<UpdateState> = c.updates.state
    val needsInstallPermission: StateFlow<ReleaseInfo?> = c.updates.needsInstallPermission

    private fun currentPermissions() = Permissions(
        location = c.locations.hasLocationPermission(),
        backgroundLocation = c.locations.hasBackgroundLocationPermission(),
        unrestrictedBattery = getApplication<Application>().getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) == true,
    )

    fun refreshPermissions() {
        permissions.value = currentPermissions()
    }

    val updatesEnabled: Boolean get() = c.updates.enabled
    val releasesPageUrl: String get() = c.updates.releasesPageUrl

    /** App came to the foreground: re-check permissions, follow location, refresh what's stale. */
    fun onResume() {
        refreshPermissions()
        if (needsInstallPermission.value != null && canInstallPackages()) startUpdate()
        viewModelScope.launch {
            val settings = c.settingsStore.currentSettings()
            val needsFix = settings.useCurrentLocation && permissions.value.location
            locating.value = needsFix && c.locations.displayLocationsNow().none { it.isCurrent }
            try {
                c.refresher.refreshForeground()
            } finally {
                locating.value = false
            }
        }
        viewModelScope.launch { c.updates.check() }
    }

    /** Periodic top-up while the app is open; only fetches places older than the refresh threshold. */
    fun refreshStale() {
        viewModelScope.launch { c.refresher.refreshForeground() }
    }

    fun refresh(page: LocationPage) {
        viewModelScope.launch { c.refresher.refreshOne(page.location) }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        refreshPermissions()
        viewModelScope.launch {
            c.settingsStore.updateSettings { it.copy(useCurrentLocation = granted, onboardingDone = true) }
            if (granted) {
                locating.value = true
                try {
                    c.refresher.refreshForeground(force = true, first = SavedLocation.CURRENT_ID)
                } finally {
                    locating.value = false
                }
            }
        }
    }

    fun onBackgroundLocationResult(granted: Boolean) {
        refreshPermissions()
        viewModelScope.launch { c.settingsStore.updateSettings { it.copy(backgroundLocation = granted) } }
    }

    fun setUseCurrentLocation(enabled: Boolean) {
        viewModelScope.launch {
            c.settingsStore.updateSettings { it.copy(useCurrentLocation = enabled) }
            if (enabled) c.refresher.refreshForeground(first = SavedLocation.CURRENT_ID)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { c.settingsStore.updateSettings(transform) }
    }

    // --- Places ---

    private val query = MutableStateFlow("")

    val searchState: StateFlow<SearchState> = query
        .debounce(300)
        .distinctUntilChanged()
        .mapLatest { text ->
            if (text.trim().length < 2) {
                SearchState.Idle
            } else {
                try {
                    SearchState.Results(c.openMeteo.search(text, Locale.getDefault().language))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SearchState.Failed("Search isn't available right now. Check your connection.")
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    fun search(text: String) {
        query.value = text
    }

    /** Adds a search result and returns its id so the UI can jump to it. */
    fun addLocation(result: GeocodingResult): String {
        val location = result.toSavedLocation()
        viewModelScope.launch {
            c.locations.add(location)
            c.settingsStore.updateSettings { it.copy(onboardingDone = true) }
            c.repository.refresh(location)
        }
        query.value = ""
        return location.id
    }

    fun removeLocation(id: String) {
        viewModelScope.launch {
            c.locations.remove(id)
            c.repository.remove(id)
        }
    }

    fun moveLocation(id: String, delta: Int) {
        viewModelScope.launch { c.locations.move(id, delta) }
    }

    // --- Updates ---

    fun checkForUpdates() {
        viewModelScope.launch { c.updates.check(force = true) }
    }

    fun startUpdate() = c.updates.startUpdate()

    fun dismissInstallPermission() = c.updates.cancelPermissionRequest()

    @SuppressLint("BatteryLife") // Opt-in from Settings, explained in the UI; this app isn't on Play.
    fun batteryExemptionIntent() = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${getApplication<Application>().packageName}".toUri(),
    )

    fun installPermissionIntent() = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${getApplication<Application>().packageName}".toUri(),
    )

    fun canInstallPackages(): Boolean = getApplication<Application>().packageManager.canRequestPackageInstalls()
}
