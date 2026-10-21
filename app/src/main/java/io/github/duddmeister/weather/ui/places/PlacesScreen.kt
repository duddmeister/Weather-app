package io.github.duddmeister.weather.ui.places

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
import io.github.duddmeister.weather.ui.components.WeatherIcon
import io.github.duddmeister.weather.ui.home.rememberUse24h
import io.github.duddmeister.weather.ui.theme.SkyText

@Composable
fun PlacesScreen(
    state: MainUiState,
    searchState: SearchState,
    nowMillis: Long,
    startWithSearch: Boolean,
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
    val use24h = rememberUse24h(state.settings)
    val currentPage = state.pages.firstOrNull { it.location.isCurrent }
    val savedPages = state.pages.filterNot { it.location.isCurrent }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("Places", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
            if (savedPages.isNotEmpty() && query.isBlank()) {
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Edit", color = Color.White) }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search for a city or town") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onSearch("")
                    }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color.Black.copy(alpha = 0.18f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.14f),
                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                cursorColor = Color.White,
                focusedPlaceholderColor = SkyText.Tertiary,
                unfocusedPlaceholderColor = SkyText.Tertiary,
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = SkyText.Secondary,
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = SkyText.Secondary,
            ),
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
                    CurrentLocationCard(state, currentPage, nowMillis, use24h, onSelect, onUseMyLocation)
                }
                if (savedPages.isEmpty() && startWithSearch) {
                    item(key = "hint") {
                        Text(
                            "Search above to add cities. Swipe between them on the main screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyText.Secondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
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
private fun SearchResults(state: SearchState, padding: PaddingValues, onPick: (GeocodingResult) -> Unit) {
    when (state) {
        SearchState.Idle, SearchState.Searching -> Box(Modifier.fillMaxWidth().padding(padding), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        is SearchState.Failed -> Text(state.message, color = SkyText.Warning, modifier = Modifier.padding(padding))
        is SearchState.Results -> if (state.results.isEmpty()) {
            Text("No places found.", color = SkyText.Secondary, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results, key = { it.id }) { result ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onPick(result) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(flagEmoji(result.countryCode), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(result.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                val subtitle = listOfNotNull(result.admin1, result.country).filter { it != result.name }.distinct().joinToString(", ")
                                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
                            }
                            Icon(Icons.Rounded.Add, contentDescription = "Add ${result.name}", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentLocationCard(
    state: MainUiState,
    page: LocationPage?,
    nowMillis: Long,
    use24h: Boolean,
    onSelect: (String) -> Unit,
    onUseMyLocation: (Boolean) -> Unit,
) {
    val enabled = state.settings.useCurrentLocation && state.hasLocationPermission
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = page?.let { { onSelect(it.location.id) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                WeatherIcon(now.weatherCode, now.isDay, 32.dp)
                Spacer(Modifier.width(6.dp))
                Text(Units.formatTemperature(now.temperature, state.settings.units.temperature), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = enabled,
                onCheckedChange = onUseMyLocation,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4F8EF7), checkedThumbColor = Color.White),
            )
        }
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val snapshot = page.snapshot
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = if (editing) null else onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val flag = flagEmoji(page.location.countryCode)
                    if (flag.isNotEmpty()) {
                        Text(flag)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(page.location.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val localTime = snapshot?.let { TimeText(it.zoneId(), use24h).timeMillis(nowMillis) }
                val now = snapshot?.conditionsAt(nowMillis)
                val details = listOfNotNull(localTime, now?.condition?.label(now.isDay)).joinToString(" · ")
                Text(details.ifBlank { page.location.subtitle }, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary, maxLines = 1)
            }
            if (editing) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = if (canMoveUp) Color.White else SkyText.Tertiary)
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = if (canMoveDown) Color.White else SkyText.Tertiary)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Color(0xFFFF8A80)) }
            } else if (snapshot != null) {
                val now = snapshot.conditionsAt(nowMillis)
                val today = snapshot.today(nowMillis)
                WeatherIcon(now.weatherCode, now.isDay, 34.dp)
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Units.formatTemperature(now.temperature, settings.units.temperature), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    if (today != null) {
                        Text(
                            "${Units.formatTemperature(today.temperatureMax, settings.units.temperature)} / ${Units.formatTemperature(today.temperatureMin, settings.units.temperature)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SkyText.Secondary,
                        )
                    }
                }
            } else if (page.refreshing) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}
