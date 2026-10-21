package io.github.duddmeister.weather

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
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
    val repository = WeatherRepository(openMeteo, nws, WeatherCache(app), appScope)
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
}
