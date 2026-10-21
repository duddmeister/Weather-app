package io.github.duddmeister.weather.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.ui.components.LocalSkyPalette
import io.github.duddmeister.weather.ui.home.HomeScreen
import io.github.duddmeister.weather.ui.places.PlacesScreen
import io.github.duddmeister.weather.ui.settings.SettingsScreen
import io.github.duddmeister.weather.ui.sky.SkyBackground
import io.github.duddmeister.weather.ui.sky.SkyScenes
import io.github.duddmeister.weather.ui.update.InstallPermissionDialog
import io.github.duddmeister.weather.ui.update.UpdateSheet
import kotlinx.coroutines.delay

enum class Screen { Home, Places, Settings }

/** Something the app was opened for: a widget tap (location) or an update notification. */
data class LaunchRequest(
    val locationId: String? = null,
    val showUpdate: Boolean = false,
    val installUpdate: Boolean = false,
)

@Composable
fun NimbusRoot(vm: MainViewModel, launchRequest: LaunchRequest?, onLaunchRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val needsInstallPermission by vm.needsInstallPermission.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var placesFromOnboarding by rememberSaveable { mutableStateOf(false) }
    var showUpdate by rememberSaveable { mutableStateOf(false) }
    var pendingLocationId by rememberSaveable { mutableStateOf<String?>(null) }

    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val pagerState = rememberPagerState(pageCount = { state.pages.size })
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val listStateFor: (String) -> LazyListState = { id -> listStates.getOrPut(id) { LazyListState() } }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    // While the app stays open, keep topping up anything that has gone stale.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(5 * 60_000L)
                vm.refreshStale()
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onLocationPermissionResult(result.values.any { it })
        requestNotificationsIfNeeded()
    }
    fun requestLocation() {
        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onBackgroundLocationResult(granted)
    }

    // Jump to a place once it exists (after a widget tap or adding a city).
    LaunchedEffect(launchRequest) {
        val request = launchRequest ?: return@LaunchedEffect
        if (request.locationId != null) {
            pendingLocationId = request.locationId
            screen = Screen.Home
        }
        if (request.showUpdate || request.installUpdate) {
            showUpdate = true
            if (request.installUpdate) vm.startUpdate()
        }
        onLaunchRequestHandled()
    }
    LaunchedEffect(pendingLocationId, state.pages, screen) {
        val id = pendingLocationId ?: return@LaunchedEffect
        if (screen != Screen.Home) return@LaunchedEffect
        val index = state.pages.indexOfFirst { it.location.id == id }
        if (index >= 0) {
            pagerState.scrollToPage(index)
            pendingLocationId = null
        }
    }

    val currentPage = state.pages.getOrNull(pagerState.currentPage)
    val scene = remember(currentPage?.snapshot, nowMillis) {
        val snapshot = currentPage?.snapshot
        SkyScenes.forConditions(snapshot, snapshot?.conditionsAt(nowMillis), nowMillis)
    }
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    CompositionLocalProvider(LocalSkyPalette provides scene.palette) {
        Box(Modifier.fillMaxSize().background(scene.palette.top)) {
            SkyBackground(scene, animate = state.settings.animatedBackground && !reduceMotion && screen == Screen.Home)
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "screen",
            ) { target ->
                when (target) {
                    Screen.Home -> HomeScreen(
                        state = state,
                        pagerState = pagerState,
                        listStateFor = listStateFor,
                        updateState = updateState,
                        nowMillis = nowMillis,
                        onRefresh = vm::refresh,
                        onOpenPlaces = {
                            placesFromOnboarding = false
                            screen = Screen.Places
                        },
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenUpdate = { showUpdate = true },
                        onUseMyLocation = ::requestLocation,
                        onSearchCity = {
                            placesFromOnboarding = true
                            screen = Screen.Places
                        },
                    )
                    Screen.Places -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                        PlacesScreen(
                            state = state,
                            searchState = searchState,
                            nowMillis = nowMillis,
                            startWithSearch = placesFromOnboarding,
                            onBack = { screen = Screen.Home },
                            onSearch = vm::search,
                            onAdd = { result ->
                                pendingLocationId = vm.addLocation(result)
                                screen = Screen.Home
                            },
                            onSelect = { id ->
                                pendingLocationId = id
                                screen = Screen.Home
                            },
                            onRemove = vm::removeLocation,
                            onMove = vm::moveLocation,
                            onUseMyLocation = { enabled ->
                                if (enabled && !state.hasLocationPermission) requestLocation() else vm.setUseCurrentLocation(enabled)
                            },
                        )
                    }
                    Screen.Settings -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                        SettingsScreen(
                            settings = state.settings,
                            updateState = updateState,
                            updatesEnabled = vm.updatesEnabled,
                            releasesUrl = vm.releasesPageUrl,
                            hasBackgroundLocation = state.permissions.backgroundLocation,
                            unrestrictedBattery = state.permissions.unrestrictedBattery,
                            onBack = { screen = Screen.Home },
                            onChange = vm::updateSettings,
                            onBackgroundLocation = { enabled ->
                                when {
                                    !enabled -> vm.updateSettings { it.copy(backgroundLocation = false) }
                                    !state.hasLocationPermission -> requestLocation()
                                    state.permissions.backgroundLocation -> vm.updateSettings { it.copy(backgroundLocation = true) }
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    else -> vm.updateSettings { it.copy(backgroundLocation = true) }
                                }
                            },
                            onAllowUnrestrictedBattery = {
                                runCatching { context.startActivity(vm.batteryExemptionIntent()) }
                            },
                            onNotificationToggle = { enabled, apply ->
                                vm.updateSettings(apply)
                                if (enabled) requestNotificationsIfNeeded()
                            },
                            onCheckForUpdates = vm::checkForUpdates,
                            onOpenUpdate = { showUpdate = true },
                        )
                    }
                }
            }
        }

        if (showUpdate) {
            UpdateSheet(
                state = updateState,
                onDismiss = { showUpdate = false },
                onUpdate = vm::startUpdate,
                onCheck = vm::checkForUpdates,
                onOpenReleases = { uri.openUri(vm.releasesPageUrl) },
            )
        }
        if (needsInstallPermission != null) {
            InstallPermissionDialog(
                onOpenSettings = {
                    runCatching { context.startActivity(vm.installPermissionIntent()) }
                },
                onDismiss = vm::dismissInstallPermission,
            )
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
