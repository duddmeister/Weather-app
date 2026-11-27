package io.github.duddmeister.weather.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.ui.components.RevealState
import io.github.duddmeister.weather.ui.components.newRevealState
import io.github.duddmeister.weather.ui.home.HomeScreen
import io.github.duddmeister.weather.ui.home.blend
import io.github.duddmeister.weather.ui.home.scrolledPixels
import io.github.duddmeister.weather.ui.places.PlacesScreen
import io.github.duddmeister.weather.ui.settings.SettingsScreen
import io.github.duddmeister.weather.ui.sky.LocalSkyBackdrop
import io.github.duddmeister.weather.ui.sky.SkyBackground
import io.github.duddmeister.weather.ui.sky.SkyLook
import io.github.duddmeister.weather.ui.sky.SkyScenes
import io.github.duddmeister.weather.ui.sky.lerp
import io.github.duddmeister.weather.ui.sky.look
import io.github.duddmeister.weather.ui.sky.rememberSkyBackdrop
import io.github.duddmeister.weather.ui.update.InstallPermissionDialog
import io.github.duddmeister.weather.ui.update.UpdateSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class Screen { Home, Places, Settings }

/** Something the app was opened for: a widget tap (location) or an update notification. */
data class LaunchRequest(
    val locationId: String? = null,
    val showUpdate: Boolean = false,
    val installUpdate: Boolean = false,
    /** Opened from a launcher shortcut. */
    val screen: Screen? = null,
)

@Composable
fun NimbusRoot(vm: MainViewModel, launchRequest: LaunchRequest?, onLaunchRequestHandled: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val needsInstallPermission by vm.needsInstallPermission.collectAsStateWithLifecycle()
    val metOffice by vm.metOfficeState.collectAsStateWithLifecycle()

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
        request.screen?.let { target ->
            placesFromOnboarding = target == Screen.Places
            screen = target
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

    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    NimbusStage(
        pages = state.pages,
        pagerState = pagerState,
        listStateFor = listStateFor,
        nowMillis = nowMillis,
        screen = screen,
        animateSky = state.settings.animatedBackground && !reduceMotion,
        onBack = { screen = Screen.Home },
    ) { target, revealStateFor ->
        when (target) {
            Screen.Home -> HomeScreen(
                state = state,
                pagerState = pagerState,
                listStateFor = listStateFor,
                revealStateFor = revealStateFor,
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
            Screen.Places -> PlacesScreen(
                state = state,
                searchState = searchState,
                nowMillis = nowMillis,
                focusSearch = placesFromOnboarding,
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
            Screen.Settings -> SettingsScreen(
                settings = state.settings,
                updateState = updateState,
                updatesEnabled = vm.updatesEnabled,
                releasesUrl = vm.releasesPageUrl,
                hasBackgroundLocation = state.permissions.backgroundLocation,
                unrestrictedBattery = state.permissions.unrestrictedBattery,
                metOffice = metOffice,
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
                onMetOfficeKey = vm::setMetOfficeKey,
            )
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

/**
 * The sky, and on top of it whichever screen is showing. The sky is one for the whole app: it
 * blends towards the next place as you swipe to it, rises a little as the forecast scrolls, and
 * calms down behind Places and Settings. Screens arrive from the side and leave by shrinking away;
 * a back swipe drags that transition with the finger.
 *
 * [content] also gets where to keep each place's entrance animations, so they play once rather
 * than every time the forecast comes back from another screen.
 */
@Composable
fun NimbusStage(
    pages: List<LocationPage>,
    pagerState: PagerState,
    listStateFor: (String) -> LazyListState,
    nowMillis: Long,
    screen: Screen,
    animateSky: Boolean,
    onBack: () -> Unit,
    content: @Composable (Screen, (String) -> RevealState) -> Unit,
) {
    val calm = screen != Screen.Home
    val looks = remember(pages, nowMillis, calm) {
        pages.map { page ->
            val snapshot = page.snapshot
            SkyScenes.forConditions(snapshot, snapshot?.conditionsAt(nowMillis), nowMillis).look(calm)
        }
    }
    val fallback = remember(calm) { SkyScenes.Default.look(calm) }
    val currentLooks by rememberUpdatedState(looks)
    val currentFallback by rememberUpdatedState(fallback)
    val currentPages by rememberUpdatedState(pages)
    val target: () -> SkyLook = remember(pagerState) {
        {
            val page = pagerState.currentPage
            val offset = pagerState.currentPageOffsetFraction
            val here = currentLooks.getOrNull(page) ?: currentFallback
            val there = currentLooks.getOrNull(if (offset > 0f) page + 1 else page - 1)
            if (there == null || abs(offset) < 0.001f) here else lerp(here, there, abs(offset))
        }
    }
    val maxLift = with(LocalDensity.current) { 480.dp.toPx() }
    val lift: () -> Float = remember(pagerState, maxLift) {
        {
            pagerState.blend { index ->
                currentPages.getOrNull(index)?.let { listStateFor(it.location.id).scrolledPixels().coerceAtMost(maxLift) }
            }
        }
    }

    // Each place keeps a record of which cards have made their entrance, so coming back from another
    // screen doesn't replay them.
    val reveals = remember { mutableMapOf<String, RevealState>() }
    val revealStateFor: (String) -> RevealState = remember { { id -> reveals.getOrPut(id) { newRevealState() } } }
    val backdrop = rememberSkyBackdrop()
    val screens = rememberSaveableStateHolder()

    // Navigation that can be scrubbed: a back swipe seeks towards Home, letting go finishes the
    // move and cancelling runs it back.
    val navigation = remember { SeekableTransitionState(screen) }
    var swipingBack by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = screen != Screen.Home) { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
                swipingBack = true
            }
            swipingBack = false
            onBack()
        } catch (e: CancellationException) {
            swipingBack = false
            throw e
        }
    }
    LaunchedEffect(screen, swipingBack) {
        if (!swipingBack) navigation.animateTo(screen)
    }
    LaunchedEffect(swipingBack, backProgress) {
        // The swipe only previews the way back; the rest plays out on release.
        if (swipingBack) navigation.seekTo(backProgress.coerceIn(0f, 1f) * BACK_PREVIEW, targetState = Screen.Home)
    }

    CompositionLocalProvider(LocalSkyBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1530))) {
            SkyBackground(target = target, backdrop = backdrop, animate = animateSky, calm = calm, lift = lift)
            rememberTransition(navigation, label = "screen").AnimatedContent(
                transitionSpec = { screenTransition(forward = targetState != Screen.Home) },
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                // What a screen remembers (which hour is open, how far a chart has scrolled) survives a
                // visit to another screen.
                screens.SaveableStateProvider(shown) { content(shown, revealStateFor) }
            }
        }
    }
}

/** How far through the transition a full back swipe takes it. */
private const val BACK_PREVIEW = 0.4f

/** Going deeper, the new screen slides in over the old one as it falls back; going back reverses it. */
private fun screenTransition(forward: Boolean): ContentTransform {
    val move = tween<Float>(durationMillis = 380, easing = FastOutSlowInEasing)
    return if (forward) {
        (fadeIn(tween(240, delayMillis = 60, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 7 }) togetherWith
            (fadeOut(tween(160)) + scaleOut(move, targetScale = 0.94f))
    } else {
        (fadeIn(tween(260, delayMillis = 40, easing = LinearOutSlowInEasing)) + scaleIn(move, initialScale = 0.94f)) togetherWith
            (fadeOut(tween(200)) + slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 7 })
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
