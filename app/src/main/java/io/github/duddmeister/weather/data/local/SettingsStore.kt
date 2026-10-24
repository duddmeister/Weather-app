package io.github.duddmeister.weather.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.UnitPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException

private val Context.nimbusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nimbus")

/** Settings, saved locations and small bits of bookkeeping, persisted with DataStore. */
class SettingsStore(context: Context, private val defaultUnits: UnitPrefs) {
    private val store = context.applicationContext.nimbusDataStore

    private object Keys {
        val TEMPERATURE = stringPreferencesKey("temperature_unit")
        val WIND = stringPreferencesKey("wind_unit")
        val PRECIPITATION = stringPreferencesKey("precipitation_unit")
        val PRESSURE = stringPreferencesKey("pressure_unit")
        val DISTANCE = stringPreferencesKey("distance_unit")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val AQI_SCALE = stringPreferencesKey("aqi_scale")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_minutes")
        val USE_CURRENT_LOCATION = booleanPreferencesKey("use_current_location")
        val BACKGROUND_LOCATION = booleanPreferencesKey("background_location")
        val PRECIPITATION_ALERTS = booleanPreferencesKey("precipitation_alerts")
        val SEVERE_ALERTS = booleanPreferencesKey("severe_alerts")
        val UPDATE_NOTIFICATIONS = booleanPreferencesKey("update_notifications")
        val ANIMATED_BACKGROUND = booleanPreferencesKey("animated_background")
        val ANIMATED_WIDGETS = booleanPreferencesKey("animated_widgets")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        val SAVED_LOCATIONS = stringPreferencesKey("saved_locations")
        val CURRENT_LOCATION = stringPreferencesKey("current_location")

        val UPDATE_ETAG = stringPreferencesKey("update_etag")
        val UPDATE_RELEASE = stringPreferencesKey("update_release_json")
        val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check")
        val UPDATE_NOTIFIED_VERSION = stringPreferencesKey("update_notified_version")

        val NOTIFIED_ALERTS = stringSetPreferencesKey("notified_alert_ids")
        val PRECIP_NOTIFIED_START = longPreferencesKey("precip_notified_start")
        val PRECIP_NOTIFIED_AT = longPreferencesKey("precip_notified_at")
    }

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) {
            AppLog.w(TAG, "Settings unreadable, using defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    val settings: Flow<AppSettings> = data.map { it.toSettings() }.distinctUntilChanged()

    val savedLocations: Flow<List<SavedLocation>> = data.map { it.savedLocations() }.distinctUntilChanged()

    val currentLocation: Flow<SavedLocation?> = data.map { it.currentLocation() }.distinctUntilChanged()

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.writeSettings(transform(prefs.toSettings())) }
    }

    suspend fun addLocation(location: SavedLocation) = editLocations { list ->
        if (list.any { it.id == location.id }) list else list + location
    }

    suspend fun removeLocation(id: String) = editLocations { list -> list.filterNot { it.id == id } }

    suspend fun moveLocation(id: String, delta: Int) = editLocations { list ->
        val index = list.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in list.indices) list else list.toMutableList().apply { add(target, removeAt(index)) }
    }

    private suspend fun editLocations(transform: (List<SavedLocation>) -> List<SavedLocation>) {
        store.edit { prefs ->
            val updated = transform(prefs.savedLocations())
            prefs[Keys.SAVED_LOCATIONS] = AppJson.encodeToString(locationListSerializer, updated)
        }
    }

    suspend fun setCurrentLocation(location: SavedLocation?) {
        store.edit { prefs ->
            if (location == null) {
                prefs.remove(Keys.CURRENT_LOCATION)
            } else {
                prefs[Keys.CURRENT_LOCATION] = AppJson.encodeToString(SavedLocation.serializer(), location)
            }
        }
    }

    // --- Update checker bookkeeping ---

    data class UpdateCache(val etag: String?, val releaseJson: String?, val lastCheck: Long, val notifiedVersion: String?)

    suspend fun updateCache(): UpdateCache {
        val prefs = data.first()
        return UpdateCache(prefs[Keys.UPDATE_ETAG], prefs[Keys.UPDATE_RELEASE], prefs[Keys.UPDATE_LAST_CHECK] ?: 0L, prefs[Keys.UPDATE_NOTIFIED_VERSION])
    }

    suspend fun saveUpdateCheck(etag: String?, releaseJson: String?, checkedAt: Long) {
        store.edit { prefs ->
            if (etag != null) prefs[Keys.UPDATE_ETAG] = etag else prefs.remove(Keys.UPDATE_ETAG)
            if (releaseJson != null) prefs[Keys.UPDATE_RELEASE] = releaseJson else prefs.remove(Keys.UPDATE_RELEASE)
            prefs[Keys.UPDATE_LAST_CHECK] = checkedAt
        }
    }

    suspend fun setNotifiedUpdateVersion(version: String) {
        store.edit { it[Keys.UPDATE_NOTIFIED_VERSION] = version }
    }

    // --- Notification de-duplication ---

    suspend fun notifiedAlertIds(): Set<String> = data.first()[Keys.NOTIFIED_ALERTS].orEmpty()

    suspend fun setNotifiedAlertIds(ids: Set<String>) {
        store.edit { it[Keys.NOTIFIED_ALERTS] = ids }
    }

    suspend fun lastPrecipitationNotification(): Pair<Long, Long> {
        val prefs = data.first()
        return (prefs[Keys.PRECIP_NOTIFIED_START] ?: 0L) to (prefs[Keys.PRECIP_NOTIFIED_AT] ?: 0L)
    }

    suspend fun setLastPrecipitationNotification(eventStart: Long, notifiedAt: Long) {
        store.edit {
            it[Keys.PRECIP_NOTIFIED_START] = eventStart
            it[Keys.PRECIP_NOTIFIED_AT] = notifiedAt
        }
    }

    // --- Mapping ---

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings(units = defaultUnits)
        return AppSettings(
            units = UnitPrefs(
                temperature = enumOr(this[Keys.TEMPERATURE], defaultUnits.temperature),
                wind = enumOr(this[Keys.WIND], defaultUnits.wind),
                precipitation = enumOr(this[Keys.PRECIPITATION], defaultUnits.precipitation),
                pressure = enumOr(this[Keys.PRESSURE], defaultUnits.pressure),
                distance = enumOr(this[Keys.DISTANCE], defaultUnits.distance),
            ),
            timeFormat = enumOr(this[Keys.TIME_FORMAT], defaults.timeFormat),
            aqiScale = enumOr(this[Keys.AQI_SCALE], defaults.aqiScale),
            refreshIntervalMinutes = this[Keys.REFRESH_INTERVAL] ?: defaults.refreshIntervalMinutes,
            useCurrentLocation = this[Keys.USE_CURRENT_LOCATION] ?: defaults.useCurrentLocation,
            backgroundLocation = this[Keys.BACKGROUND_LOCATION] ?: defaults.backgroundLocation,
            precipitationAlerts = this[Keys.PRECIPITATION_ALERTS] ?: defaults.precipitationAlerts,
            severeAlerts = this[Keys.SEVERE_ALERTS] ?: defaults.severeAlerts,
            updateNotifications = this[Keys.UPDATE_NOTIFICATIONS] ?: defaults.updateNotifications,
            animatedBackground = this[Keys.ANIMATED_BACKGROUND] ?: defaults.animatedBackground,
            animatedWidgets = this[Keys.ANIMATED_WIDGETS] ?: defaults.animatedWidgets,
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
        )
    }

    private fun MutablePreferences.writeSettings(s: AppSettings) {
        this[Keys.TEMPERATURE] = s.units.temperature.name
        this[Keys.WIND] = s.units.wind.name
        this[Keys.PRECIPITATION] = s.units.precipitation.name
        this[Keys.PRESSURE] = s.units.pressure.name
        this[Keys.DISTANCE] = s.units.distance.name
        this[Keys.TIME_FORMAT] = s.timeFormat.name
        this[Keys.AQI_SCALE] = s.aqiScale.name
        this[Keys.REFRESH_INTERVAL] = s.refreshIntervalMinutes
        this[Keys.USE_CURRENT_LOCATION] = s.useCurrentLocation
        this[Keys.BACKGROUND_LOCATION] = s.backgroundLocation
        this[Keys.PRECIPITATION_ALERTS] = s.precipitationAlerts
        this[Keys.SEVERE_ALERTS] = s.severeAlerts
        this[Keys.UPDATE_NOTIFICATIONS] = s.updateNotifications
        this[Keys.ANIMATED_BACKGROUND] = s.animatedBackground
        this[Keys.ANIMATED_WIDGETS] = s.animatedWidgets
        this[Keys.ONBOARDING_DONE] = s.onboardingDone
    }

    private fun Preferences.savedLocations(): List<SavedLocation> = this[Keys.SAVED_LOCATIONS]?.let { raw ->
        runCatching { AppJson.decodeFromString(locationListSerializer, raw) }
            .onFailure { AppLog.w(TAG, "Saved locations unreadable", it) }
            .getOrNull()
    }.orEmpty()

    private fun Preferences.currentLocation(): SavedLocation? = this[Keys.CURRENT_LOCATION]?.let { raw ->
        runCatching { AppJson.decodeFromString(SavedLocation.serializer(), raw) }.getOrNull()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

    companion object {
        private const val TAG = "SettingsStore"
        private val locationListSerializer = ListSerializer(SavedLocation.serializer())
    }
}
