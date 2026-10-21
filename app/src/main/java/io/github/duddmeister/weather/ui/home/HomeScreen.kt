package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.ui.LocationPage
import io.github.duddmeister.weather.ui.MainUiState
import io.github.duddmeister.weather.ui.components.LocalSkyPalette
import io.github.duddmeister.weather.ui.theme.SkyText
import io.github.duddmeister.weather.update.UpdateState

@Composable
fun HomeScreen(
    state: MainUiState,
    pagerState: PagerState,
    listStateFor: (String) -> LazyListState,
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
                    WeatherPage(
                        page = page,
                        settings = state.settings,
                        nowMillis = nowMillis,
                        listState = listStateFor(page.location.id),
                        onRefresh = { onRefresh(page) },
                    )
                }
                val currentPage = state.pages.getOrNull(pagerState.currentPage)
                val listState = currentPage?.let { listStateFor(it.location.id) }
                val scrolled by remember(listState) {
                    derivedStateOf { listState != null && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420) }
                }
                TopBar(
                    pages = state.pages,
                    currentIndex = pagerState.currentPage,
                    compactTitle = currentPage?.takeIf { scrolled }?.let { compactTitle(it, state, nowMillis) },
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
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White) }
            }
        }
    }
}

private fun compactTitle(page: LocationPage, state: MainUiState, nowMillis: Long): String {
    val temperature = page.snapshot?.conditionsAt(nowMillis)?.temperature
    return if (temperature == null) page.location.name else "${page.location.name}  ${Units.formatTemperature(temperature, state.settings.units.temperature)}"
}

@Composable
private fun TopBar(
    pages: List<LocationPage>,
    currentIndex: Int,
    compactTitle: String?,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalSkyPalette.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(palette.top.copy(alpha = 0.92f), palette.top.copy(alpha = 0.55f)))),
        ) {
            Column {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenPlaces) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = "Places", tint = Color.White)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Crossfade(targetState = compactTitle, label = "topBarTitle") { title ->
                            if (title != null) {
                                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                PageDots(pages, currentIndex)
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Brush.verticalGradient(listOf(palette.top.copy(alpha = 0.55f), Color.Transparent))),
        )
    }
}

@Composable
private fun PageDots(pages: List<LocationPage>, currentIndex: Int) {
    if (pages.size <= 1) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        pages.forEachIndexed { index, page ->
            val selected = index == currentIndex
            val tint = if (selected) Color.White else Color.White.copy(alpha = 0.4f)
            if (page.location.isCurrent) {
                Icon(Icons.Rounded.NearMe, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
            } else {
                Box(Modifier.size(if (selected) 8.dp else 6.dp).clip(CircleShape).background(tint))
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
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1E3A66).copy(alpha = 0.92f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = Color(0xFFFFD27A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label.orEmpty(), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
private fun Onboarding(onUseMyLocation: () -> Unit, onSearchCity: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Nimbus", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "Live conditions, rain alerts and forecasts that stay fresh, on your home screen too.",
            style = MaterialTheme.typography.bodyLarge,
            color = SkyText.Secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onUseMyLocation,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F3E7A)),
        ) {
            Icon(Icons.Rounded.NearMe, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Use my location", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSearchCity,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Search for a city", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Your location is only used to fetch the weather. It's never stored anywhere but this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = SkyText.Tertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Locating() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp))
        Text("Finding your location…", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
