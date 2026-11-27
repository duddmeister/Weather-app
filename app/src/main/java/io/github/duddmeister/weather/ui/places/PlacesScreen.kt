package io.github.duddmeister.weather.ui.places

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.data.remote.GeocodingResult
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.domain.today
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.flagEmoji
import io.github.duddmeister.weather.ui.LocationPage
import io.github.duddmeister.weather.ui.MainUiState
import io.github.duddmeister.weather.ui.SearchState
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.GlassIconButton
import io.github.duddmeister.weather.ui.components.Motion
import io.github.duddmeister.weather.ui.components.PillButton
import io.github.duddmeister.weather.ui.components.PulsingDots
import io.github.duddmeister.weather.ui.components.WeatherIcon
import io.github.duddmeister.weather.ui.components.glass
import io.github.duddmeister.weather.ui.components.nimbusSwitchColors
import io.github.duddmeister.weather.ui.components.pressScale
import io.github.duddmeister.weather.ui.components.rememberHaptics
import io.github.duddmeister.weather.ui.home.rememberUse24h
import io.github.duddmeister.weather.ui.sky.SkyLook
import io.github.duddmeister.weather.ui.sky.SkyScenes
import io.github.duddmeister.weather.ui.sky.look
import io.github.duddmeister.weather.ui.sky.miniSky
import io.github.duddmeister.weather.ui.theme.SkyText

private val PlaceShape = RoundedCornerShape(24.dp)

@Composable
fun PlacesScreen(
    state: MainUiState,
    searchState: SearchState,
    nowMillis: Long,
    /** Put the cursor in the search field right away (onboarding, launcher shortcut). */
    focusSearch: Boolean,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onAdd: (GeocodingResult) -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onUseMyLocation: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) searchFocus.requestFocus()
    }
    val use24h = rememberUse24h(state.settings)
    val currentPage = state.pages.firstOrNull { it.location.isCurrent }
    val savedPages = state.pages.filterNot { it.location.isCurrent }

    // A comfortable column on wide screens.
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 640.dp).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(8.dp))
            Text(
                "Places",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (savedPages.isNotEmpty() && query.isBlank()) {
                PillButton(if (editing) "Done" else "Edit", { editing = !editing }, modifier = Modifier.padding(end = 8.dp), filled = editing)
            }
        }
        SearchField(
            query = query,
            onQuery = {
                query = it
                onSearch(it)
            },
            onDone = { focus.clearFocus() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(searchFocus),
        )
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        if (query.isNotBlank()) {
            SearchResults(searchState, PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom)) { result ->
                focus.clearFocus()
                query = ""
                onAdd(result)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "current") {
                    CurrentLocationCard(state, currentPage, nowMillis, Modifier.animateItem(), onSelect, onUseMyLocation)
                }
                if (savedPages.isEmpty()) {
                    item(key = "hint") {
                        Text(
                            "Search above to add cities. Swipe between them on the main screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyText.Secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).animateItem(),
                        )
                    }
                }
                items(savedPages, key = { it.location.id }) { page ->
                    val index = savedPages.indexOf(page)
                    PlaceCard(
                        page = page,
                        settings = state.settings,
                        nowMillis = nowMillis,
                        use24h = use24h,
                        editing = editing,
                        canMoveUp = index > 0,
                        canMoveDown = index < savedPages.lastIndex,
                        modifier = Modifier.animateItem(),
                        onClick = { onSelect(page.location.id) },
                        onRemove = { onRemove(page.location.id) },
                        onMove = { delta -> onMove(page.location.id, delta) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        decorationBox = { field ->
            Row(
                Modifier.glass(RoundedCornerShape(20.dp)).heightIn(min = 54.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = SkyText.Secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search for a city or town", style = MaterialTheme.typography.bodyLarge, color = SkyText.Tertiary, maxLines = 1)
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = SkyText.Secondary) }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        },
    )
}

@Composable
private fun SearchResults(state: SearchState, padding: PaddingValues, onPick: (GeocodingResult) -> Unit) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn(tween(Motion.FADE_MILLIS)) togetherWith fadeOut(tween(120)) },
        contentKey = { it::class },
        label = "search",
    ) { shown ->
        when (shown) {
            SearchState.Idle, SearchState.Searching -> Box(Modifier.fillMaxWidth().padding(padding), contentAlignment = Alignment.TopCenter) {
                PulsingDots(Color.White, Modifier.padding(top = 20.dp), dotSize = 7.dp)
            }
            is SearchState.Failed -> SearchMessage(Icons.Rounded.SearchOff, shown.message, SkyText.Warning, padding)
            is SearchState.Results -> if (shown.results.isEmpty()) {
                SearchMessage(Icons.Rounded.SearchOff, "No places found. Check the spelling, or try a bigger town nearby.", SkyText.Secondary, padding)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown.results, key = { it.id }) { result ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            shape = PlaceShape,
                            onClick = { onPick(result) },
                            onClickLabel = "Add ${result.name}",
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(flagEmoji(result.countryCode), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(result.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    val subtitle = listOfNotNull(result.admin1, result.country).filter { it != result.name }.distinct().joinToString(", ")
                                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
                                }
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(icon: ImageVector, message: String, colour: Color, padding: PaddingValues) {
    Column(Modifier.fillMaxWidth().padding(padding).padding(top = 24.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colour, textAlign = TextAlign.Center)
    }
}

/** The sky over a place right now, for the background of its card. */
@Composable
private fun rememberLook(page: LocationPage?, nowMillis: Long): SkyLook? {
    val snapshot = page?.snapshot ?: return null
    return remember(snapshot, nowMillis / 300_000) { SkyScenes.forConditions(snapshot, snapshot.conditionsAt(nowMillis), nowMillis).look() }
}

@Composable
private fun CurrentLocationCard(
    state: MainUiState,
    page: LocationPage?,
    nowMillis: Long,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onUseMyLocation: (Boolean) -> Unit,
) {
    val enabled = state.settings.useCurrentLocation && state.hasLocationPermission
    val haptics = rememberHaptics()
    val look = rememberLook(page.takeIf { enabled }, nowMillis)
    val interaction = remember { MutableInteractionSource() }
    val open = page?.takeIf { enabled }?.let { { onSelect(it.location.id) } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .then(if (look != null) Modifier.miniSky(look, PlaceShape) else Modifier.glass(PlaceShape))
            .then(
                if (open != null) {
                    Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = open)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 84.dp)
            .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NearMe, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("My location", style = MaterialTheme.typography.titleMedium, color = Color.White)
            val subtitle = when {
                !state.hasLocationPermission -> "Allow location access to see the weather where you are"
                !state.settings.useCurrentLocation -> "Off"
                page != null -> page.location.name
                else -> "Finding you…"
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary, maxLines = 2)
        }
        if (enabled && page?.snapshot != null) {
            val now = page.snapshot.conditionsAt(nowMillis)
            Text(Units.formatTemperature(now.temperature, state.settings.units.temperature), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.width(8.dp))
            WeatherIcon(now.weatherCode, now.isDay, 32.dp)
            Spacer(Modifier.width(10.dp))
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                haptics.toggle(it)
                onUseMyLocation(it)
            },
            colors = nimbusSwitchColors(),
        )
    }
}

@Composable
private fun PlaceCard(
    page: LocationPage,
    settings: AppSettings,
    nowMillis: Long,
    use24h: Boolean,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val snapshot = page.snapshot
    val look = rememberLook(page, nowMillis)
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .then(if (look != null) Modifier.miniSky(look, PlaceShape) else Modifier.glass(PlaceShape))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = !editing,
                onClickLabel = "Show the forecast",
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 84.dp)
            .padding(start = 18.dp, end = if (editing) 6.dp else 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val flag = flagEmoji(page.location.countryCode)
                if (flag.isNotEmpty()) {
                    Text(flag)
                    Spacer(Modifier.width(8.dp))
                }
                Text(page.location.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val localTime = snapshot?.let { TimeText(it.zoneId(), use24h).timeMillis(nowMillis) }
            val now = snapshot?.conditionsAt(nowMillis)
            val details = listOfNotNull(localTime, now?.condition?.label(now.isDay)).joinToString(" · ")
            Text(details.ifBlank { page.location.subtitle }, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary, maxLines = 1)
        }
        // Editing swaps the weather for the controls, in place.
        AnimatedContent(
            targetState = editing,
            transitionSpec = { fadeIn(tween(Motion.FADE_MILLIS)) togetherWith fadeOut(tween(120)) },
            contentAlignment = Alignment.CenterEnd,
            label = "placeTrailing",
        ) { edit ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    edit -> {
                        EditButton(Icons.Rounded.ArrowUpward, "Move ${page.location.name} up", enabled = canMoveUp) {
                            haptics.tick()
                            onMove(-1)
                        }
                        EditButton(Icons.Rounded.ArrowDownward, "Move ${page.location.name} down", enabled = canMoveDown) {
                            haptics.tick()
                            onMove(1)
                        }
                        EditButton(Icons.Rounded.DeleteOutline, "Remove ${page.location.name}", tint = SkyText.Negative, onClick = onRemove)
                    }
                    snapshot != null -> {
                        val now = snapshot.conditionsAt(nowMillis)
                        val today = snapshot.today(nowMillis)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Units.formatTemperature(now.temperature, settings.units.temperature), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                            if (today != null) {
                                Text(
                                    "${Units.formatTemperature(today.temperatureMax, settings.units.temperature)} / ${Units.formatTemperature(today.temperatureMin, settings.units.temperature)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyText.Secondary,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        WeatherIcon(now.weatherCode, now.isDay, 36.dp)
                    }
                    page.refreshing -> PulsingDots(Color.White)
                }
            }
        }
    }
}

@Composable
private fun EditButton(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = if (enabled) tint else SkyText.Faint, modifier = Modifier.size(20.dp))
        }
    }
}
