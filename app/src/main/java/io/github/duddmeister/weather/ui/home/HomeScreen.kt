package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.ui.LocationPage
import io.github.duddmeister.weather.ui.MainUiState
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.GlassIconButton
import io.github.duddmeister.weather.ui.components.Motion
import io.github.duddmeister.weather.ui.components.Reveal
import io.github.duddmeister.weather.ui.components.RevealState
import io.github.duddmeister.weather.ui.components.WeatherIcon
import io.github.duddmeister.weather.ui.components.WideButton
import io.github.duddmeister.weather.ui.components.glass
import io.github.duddmeister.weather.ui.components.pressScale
import io.github.duddmeister.weather.ui.components.rememberRevealState
import io.github.duddmeister.weather.ui.components.skyVeil
import io.github.duddmeister.weather.ui.theme.SkyText
import io.github.duddmeister.weather.update.UpdateState
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(
    state: MainUiState,
    pagerState: PagerState,
    listStateFor: (String) -> LazyListState,
    revealStateFor: (String) -> RevealState,
    updateState: UpdateState,
    nowMillis: Long,
    onRefresh: (LocationPage) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdate: () -> Unit,
    onUseMyLocation: () -> Unit,
    onSearchCity: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            !state.ready -> Unit
            state.needsSetup -> Onboarding(onUseMyLocation, onSearchCity)
            state.pages.isEmpty() -> Locating()
            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { index -> state.pages.getOrNull(index)?.location?.id ?: index },
                ) { index ->
                    val page = state.pages.getOrNull(index) ?: return@HorizontalPager
                    Box(
                        Modifier.graphicsLayer {
                            // Places recede slightly as they're swiped away and come forward as they arrive.
                            val away = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            alpha = 1f - 0.5f * away
                            val scale = 1f - 0.07f * away
                            scaleX = scale
                            scaleY = scale
                        },
                    ) {
                        WeatherPage(
                            page = page,
                            settings = state.settings,
                            nowMillis = nowMillis,
                            listState = listStateFor(page.location.id),
                            reveal = revealStateFor(page.location.id),
                            onRefresh = { onRefresh(page) },
                        )
                    }
                }
                val pages by rememberUpdatedState(state.pages)
                // How far the forecast has scrolled, blended between places during a swipe.
                val scrolled: () -> Float = remember(pagerState) {
                    { pagerState.blend { index -> pages.getOrNull(index)?.let { listStateFor(it.location.id).scrolledPixels() } } }
                }
                val currentPage = state.pages.getOrNull(pagerState.currentPage)
                TopBar(
                    pages = state.pages,
                    currentIndex = pagerState.currentPage,
                    title = currentPage?.let { compactTitle(it, state, nowMillis) },
                    scrolled = scrolled,
                    onOpenPlaces = onOpenPlaces,
                    onOpenSettings = onOpenSettings,
                )
                UpdatePill(
                    updateState = updateState,
                    onClick = onOpenUpdate,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                )
            }
        }
        if (!state.ready || state.needsSetup || state.pages.isEmpty()) {
            // Settings stay reachable before any place is set up.
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }
    }
}

/** Pixels a forecast list has scrolled, as far as anything on screen cares about. */
internal const val SCROLL_CAP = 4000f

internal fun LazyListState.scrolledPixels(): Float =
    if (firstVisibleItemIndex > 0) SCROLL_CAP else firstVisibleItemScrollOffset.toFloat().coerceAtMost(SCROLL_CAP)

/** A per-page value, blended with the neighbouring page's while a swipe is under way. */
internal inline fun PagerState.blend(valueOf: (Int) -> Float?): Float {
    val offset = currentPageOffsetFraction
    val here = valueOf(currentPage) ?: 0f
    val there = valueOf(if (offset > 0f) currentPage + 1 else currentPage - 1) ?: here
    return here + (there - here) * offset.absoluteValue
}

private class CompactTitle(val name: String, val temperature: String?, val weatherCode: Int?, val isDay: Boolean)

private fun compactTitle(page: LocationPage, state: MainUiState, nowMillis: Long): CompactTitle {
    val now = page.snapshot?.conditionsAt(nowMillis)
    return CompactTitle(
        name = page.location.name,
        temperature = now?.let { Units.formatTemperature(it.temperature, state.settings.units.temperature) },
        weatherCode = now?.weatherCode,
        isDay = now?.isDay ?: true,
    )
}

/**
 * Two glass buttons and, between them, the page dots. As the forecast scrolls, the sky closes over
 * the top of the screen so cards dissolve beneath the bar, and the dots give way to the place and
 * its temperature.
 */
@Composable
private fun TopBar(
    pages: List<LocationPage>,
    currentIndex: Int,
    title: CompactTitle?,
    scrolled: () -> Float,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val veilStart = with(density) { 24.dp.toPx() }
    val veilRange = with(density) { 72.dp.toPx() }
    val titleStart = with(density) { 150.dp.toPx() }
    val titleRange = with(density) { 56.dp.toPx() }
    val titleRise = with(density) { 10.dp.toPx() }
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.matchParentSize().skyVeil(fade = 30.dp) { (scrolled() - veilStart) / veilRange })
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Rounded.Place, "Places", onOpenPlaces)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.graphicsLayer { alpha = 1f - ((scrolled() - titleStart + titleRange) / titleRange).coerceIn(0f, 1f) }) {
                        PageDots(pages, currentIndex)
                    }
                    if (title != null) {
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    val shown = ((scrolled() - titleStart) / titleRange).coerceIn(0f, 1f)
                                    alpha = shown
                                    translationY = (1f - shown) * titleRise
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (title.weatherCode != null) {
                                WeatherIcon(title.weatherCode, title.isDay, 22.dp, described = false)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                title.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (title.temperature != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(title.temperature, style = MaterialTheme.typography.titleMedium, color = SkyText.Secondary, maxLines = 1)
                            }
                        }
                    }
                }
                GlassIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
            // Room for the veil's faded edge.
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun PageDots(pages: List<LocationPage>, currentIndex: Int) {
    if (pages.size <= 1) return
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Place ${currentIndex + 1} of ${pages.size}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEachIndexed { index, page ->
            val selected = index == currentIndex
            val emphasis by animateFloatAsState(if (selected) 1f else 0f, Motion.snappy(), label = "dot")
            if (page.location.isCurrent) {
                Icon(
                    Icons.Rounded.NearMe,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp).graphicsLayer {
                        alpha = 0.4f + 0.6f * emphasis
                        val scale = 1f + 0.25f * emphasis
                        scaleX = scale
                        scaleY = scale
                    },
                )
            } else {
                // The selected place stretches into a pill.
                Box(
                    Modifier
                        .width(6.dp + 12.dp * emphasis.coerceIn(0f, 1.2f))
                        .height(6.dp)
                        .graphicsLayer { alpha = 0.4f + 0.6f * emphasis.coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun UpdatePill(updateState: UpdateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (updateState) {
        is UpdateState.Available -> "Update available · ${updateState.release.version}"
        is UpdateState.Downloading -> "Downloading update… ${updateState.progress?.let { "${(it * 100).toInt()}%" }.orEmpty()}"
        is UpdateState.Installing -> "Installing update…"
        else -> null
    }
    AnimatedVisibility(
        visible = label != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(Motion.settle()) { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .pressScale(interaction, pressedScale = 0.95f)
                .glass(CircleShape, tint = Color(0xFF0B1E42).copy(alpha = 0.55f))
                .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = SkyText.Warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label.orEmpty(), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun Onboarding(onUseMyLocation: () -> Unit, onSearchCity: () -> Unit) {
    val reveal = rememberRevealState("onboarding")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Reveal(reveal, "mark", 0) { FloatingMark() }
        Spacer(Modifier.height(20.dp))
        Reveal(reveal, "title", 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nimbus", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Medium, fontSize = 46.sp), color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Weather that's always current, here and on your home screen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SkyText.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Reveal(reveal, "features", 2) {
            GlassCard(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                Feature(Icons.Rounded.Umbrella, "Rain in the next two hours", "With a heads-up before it starts.")
                Feature(Icons.Rounded.Widgets, "Widgets that stay fresh", "And say when they last updated.")
                Feature(Icons.Rounded.Lock, "Private", "No accounts, no ads, no tracking.")
            }
        }
        Spacer(Modifier.height(28.dp))
        Reveal(reveal, "location", 3) {
            WideButton("Use my location", onUseMyLocation, icon = Icons.Rounded.NearMe)
        }
        Spacer(Modifier.height(12.dp))
        Reveal(reveal, "search", 4) {
            WideButton("Search for a city", onSearchCity, icon = Icons.Rounded.Search, primary = false)
        }
        Spacer(Modifier.height(20.dp))
        Reveal(reveal, "note", 5) {
            Text(
                "Your location is only used to fetch the weather. It's never stored anywhere but this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = SkyText.Tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The app's weather mark on a disc of glass, bobbing gently. */
@Composable
private fun FloatingMark() {
    val bob by rememberInfiniteTransition(label = "mark").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "bob",
    )
    val travel = with(LocalDensity.current) { 5.dp.toPx() }
    Box(
        Modifier
            .graphicsLayer { translationY = bob * travel }
            .size(112.dp)
            .glass(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        WeatherIcon(2, isDay = true, size = 76.dp, described = false)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
        }
    }
}

/** Rings spreading from a pin while the phone works out where it is. */
@Composable
private fun Locating() {
    val ping by rememberInfiniteTransition(label = "locating").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ping",
    )
    Column(
        modifier = Modifier.fillMaxSize().semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val centre = Offset(size.width / 2f, size.height / 2f)
                repeat(3) { ring ->
                    val phase = (ping + ring / 3f) % 1f
                    val radius = 26.dp.toPx() + (size.minDimension / 2f - 28.dp.toPx()) * phase
                    drawCircle(Color.White, radius = radius, center = centre, alpha = 0.34f * (1f - phase) * (1f - phase), style = Stroke(1.5.dp.toPx()))
                }
            }
            Box(Modifier.size(52.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.NearMe, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Finding your location", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
