package io.github.duddmeister.weather

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.duddmeister.weather.core.AppLog
import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.core.createHttpClient
import io.github.duddmeister.weather.data.LocationRepository
import io.github.duddmeister.weather.data.WeatherRefresher
import io.github.duddmeister.weather.data.WeatherRepository
import io.github.duddmeister.weather.data.local.SettingsStore
import io.github.duddmeister.weather.data.local.WeatherCache
import io.github.duddmeister.weather.data.location.LocationProvider
import io.github.duddmeister.weather.data.location.PlaceResolver
import io.github.duddmeister.weather.data.remote.GitHubReleasesClient
import io.github.duddmeister.weather.data.remote.MeteoAlarmClient
import io.github.duddmeister.weather.data.remote.NwsClient
import io.github.duddmeister.weather.data.remote.OpenMeteoClient
import io.github.duddmeister.weather.data.remote.ReverseGeocodeClient
import io.github.duddmeister.weather.model.UnitPrefs
import io.github.duddmeister.weather.notify.Notifications
import io.github.duddmeister.weather.notify.WeatherNotifier
import io.github.duddmeister.weather.update.ApkInstaller
import io.github.duddmeister.weather.update.UpdateManager
import io.github.duddmeister.weather.widget.WidgetUpdater
import io.github.duddmeister.weather.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class NimbusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.sink = AppLog.Sink { level, tag, message, throwable ->
            when (level) {
                'E' -> Log.e("Nimbus/$tag", message, throwable)
                'W' -> Log.w("Nimbus/$tag", message, throwable)
                'I' -> Log.i("Nimbus/$tag", message, throwable)
                else -> Log.d("Nimbus/$tag", message, throwable)
            }
        }
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.start()
    }
}

val Context.container: AppContainer get() = (applicationContext as NimbusApp).container

/** Manual dependency wiring; one instance per process. */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val userAgent =
        "Nimbus/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; +https://github.com/${BuildConfig.UPDATE_REPO})"
    private val httpClient = createHttpClient()
    private val http = HttpFetcher(httpClient, userAgent)

    val openMeteo = OpenMeteoClient(http)
    private val nws = NwsClient(http)

    val settingsStore = SettingsStore(app, UnitPrefs.defaultsFor(Locale.getDefault().country))
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    val repository = WeatherRepository(
        openMeteo = openMeteo,
        nws = nws,
        meteoAlarm = MeteoAlarmClient(http),
        cache = WeatherCache(app),
        scope = appScope,
        isMetered = { connectivity?.isActiveNetworkMetered ?: true },
    )
    val locations = LocationRepository(settingsStore, LocationProvider(app), PlaceResolver(app, ReverseGeocodeClient(http)))
    val refresher = WeatherRefresher(app, settingsStore, locations, repository, WeatherNotifier(app, settingsStore))
    val updates = UpdateManager(
        context = app,
        client = GitHubReleasesClient(http),
        settingsStore = settingsStore,
        installer = ApkInstaller(app, httpClient, userAgent),
        scope = appScope,
    )

    fun start() {
        publishShortcuts()
        // Keep the periodic refresh in step with the setting.
        appScope.launch {
            settingsStore.settings.map { it.refreshIntervalMinutes }.distinctUntilChanged().collect { minutes ->
                WorkScheduler.schedulePeriodicRefresh(app, minutes)
            }
        }
        WorkScheduler.scheduleUpdateChecks(app)

        // Re-render widgets whenever data, settings or places change (e.g. after an in-app refresh).
        appScope.launch {
            combine(repository.snapshots, settingsStore.settings, locations.displayLocations) { _, _, _ -> Unit }
                .drop(1)
                .debounce(750)
                .collect { WidgetUpdater.updateAll(app) }
        }
    }

    /** Long-press shortcuts on the launcher icon. Only published when missing (updates are rate limited). */
    private fun publishShortcuts() {
        runCatching {
            val existing = ShortcutManagerCompat.getDynamicShortcuts(app).map { it.id }.toSet()
            if (existing.containsAll(listOf(MainActivity.SHORTCUT_PLACES, MainActivity.SHORTCUT_SETTINGS))) return
            fun shortcut(id: String, action: String, short: Int, long: Int, icon: Int) =
                ShortcutInfoCompat.Builder(app, id)
                    .setShortLabel(app.getString(short))
                    .setLongLabel(app.getString(long))
                    .setIcon(IconCompat.createWithResource(app, icon))
                    .setIntent(Intent(action).setClass(app, MainActivity::class.java))
                    .build()
            ShortcutManagerCompat.setDynamicShortcuts(
                app,
                listOf(
                    shortcut(MainActivity.SHORTCUT_PLACES, MainActivity.ACTION_OPEN_PLACES, R.string.shortcut_places_short, R.string.shortcut_places_long, R.drawable.ic_shortcut_search),
                    shortcut(MainActivity.SHORTCUT_SETTINGS, MainActivity.ACTION_OPEN_SETTINGS, R.string.shortcut_settings_short, R.string.shortcut_settings_long, R.drawable.ic_shortcut_settings),
                ),
            )
        }.onFailure { AppLog.w("AppContainer", "Couldn't publish shortcuts", it) }
    }
}
