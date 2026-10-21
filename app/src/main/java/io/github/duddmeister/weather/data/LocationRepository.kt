package io.github.duddmeister.weather.data

import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.data.local.SettingsStore
import io.github.duddmeister.weather.data.location.LocationProvider
import io.github.duddmeister.weather.data.location.PlaceResolver
import io.github.duddmeister.weather.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The ordered list of places to show: the device's location (when enabled) followed by saved places. */
class LocationRepository(
    private val settingsStore: SettingsStore,
    private val locationProvider: LocationProvider,
    private val places: PlaceResolver,
) {
    private val updateLock = Mutex()

    val savedLocations: Flow<List<SavedLocation>> = settingsStore.savedLocations

    val displayLocations: Flow<List<SavedLocation>> = combine(
        settingsStore.settings,
        settingsStore.currentLocation,
        settingsStore.savedLocations,
    ) { settings, current, saved ->
        buildList {
            if (settings.useCurrentLocation && current != null) add(current)
            addAll(saved)
        }
    }.distinctUntilChanged()

    suspend fun displayLocationsNow(): List<SavedLocation> = displayLocations.first()

    fun hasLocationPermission() = locationProvider.hasPermission()

    fun hasBackgroundLocationPermission() = locationProvider.hasBackgroundPermission()

    /**
     * Reads the device location and stores it as the "current location" entry, re-resolving the
     * place name only when the device has moved. Returns the stored entry (possibly unchanged).
     */
    suspend fun updateCurrentLocation(): SavedLocation? = updateLock.withLock {
        val stored = settingsStore.currentLocation.first()
        val fix = locationProvider.currentLocation() ?: return@withLock stored
        if (stored != null && stored.name != FALLBACK_NAME && stored.distanceKmTo(fix.latitude, fix.longitude) < 1.0) {
            return@withLock stored
        }
        val place = places.resolve(fix.latitude, fix.longitude)
        val updated = SavedLocation(
            id = SavedLocation.CURRENT_ID,
            name = place?.name ?: stored?.takeIf { it.distanceKmTo(fix.latitude, fix.longitude) < 5.0 }?.name ?: FALLBACK_NAME,
            region = place?.region,
            country = place?.country,
            countryCode = place?.countryCode ?: stored?.countryCode,
            latitude = fix.latitude,
            longitude = fix.longitude,
            isCurrent = true,
        )
        AppLog.i(TAG, "Current location: ${updated.name}")
        settingsStore.setCurrentLocation(updated)
        updated
    }

    suspend fun add(location: SavedLocation) = settingsStore.addLocation(location)

    suspend fun remove(id: String) = settingsStore.removeLocation(id)

    suspend fun move(id: String, delta: Int) = settingsStore.moveLocation(id, delta)

    companion object {
        private const val TAG = "LocationRepository"
        const val FALLBACK_NAME = "My location"
    }
}
