package io.github.duddmeister.weather.ui.home

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.data.WeatherRepository
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.domain.NowcastBuilder
import io.github.duddmeister.weather.domain.SummaryBuilder
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.domain.today
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.DailyPoint
import io.github.duddmeister.weather.model.TimeFormat
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.ui.LocationPage
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.PulsingDots
import io.github.duddmeister.weather.ui.components.Reveal
import io.github.duddmeister.weather.ui.components.rememberRevealState
import io.github.duddmeister.weather.ui.theme.SkyText
import java.time.ZoneId

/** Whether to show 24-hour times, honouring the app setting and then the system setting. */
@Composable
fun rememberUse24h(settings: AppSettings): Boolean {
    val context = LocalContext.current
    return when (settings.timeFormat) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.System -> remember(context) { DateFormat.is24HourFormat(context) }
    }
}

@Composable
fun WeatherPage(
    page: LocationPage,
    settings: AppSettings,
    nowMillis: Long,
    listState: LazyListState,
    onRefresh: () -> Unit,
) {
    val snapshot = page.snapshot
    val use24h = rememberUse24h(settings)
    val now = remember(snapshot, nowMillis) { snapshot?.conditionsAt(nowMillis) }
    val today = remember(snapshot, nowMillis) { snapshot?.today(nowMillis) }
    val nowcast = remember(snapshot, nowMillis) { snapshot?.let { NowcastBuilder.build(it, nowMillis) } }
    val summary = remember(snapshot, nowMillis, settings.units, use24h) {
        snapshot?.let { SummaryBuilder.build(it, nowMillis, settings.units, use24h) }.orEmpty()
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
    val reveal = rememberRevealState(page.location.id)

    ElasticRefreshBox(
        refreshing = page.refreshing,
        error = page.error,
        onRefresh = onRefresh,
        headerTop = topInset,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset, bottom = bottomInset, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                Reveal(reveal, "hero", 0, Modifier.animateItem()) {
                    Hero(
                        page = page,
                        now = now,
                        today = today,
                        settings = settings,
                        nowMillis = nowMillis,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).fillParentMaxHeight(0.42f),
                    )
                }
            }
            if (snapshot == null || now == null) {
                item(key = "empty") { Reveal(reveal, "empty", 1, Modifier.animateItem()) { EmptyCard(page, onRefresh) } }
                return@LazyColumn
            }
            var order = 1
            if (snapshot.alerts.isNotEmpty()) {
                val o = order++
                item(key = "alerts") { Reveal(reveal, "alerts", o, Modifier.animateItem()) { AlertsCard(snapshot, nowMillis, use24h) } }
            }
            if (nowcast != null && (nowcast.hasPrecipitation || now.condition.isPrecipitation)) {
                val o = order++
                item(key = "nowcast") { Reveal(reveal, "nowcast", o, Modifier.animateItem()) { NowcastCard(nowcast, snapshot, use24h) } }
            }
            if (summary.isNotBlank()) {
                val o = order++
                item(key = "summary") { Reveal(reveal, "summary", o, Modifier.animateItem()) { SummaryCard(summary) } }
            }
            val hourlyOrder = order++
            item(key = "hourly") { Reveal(reveal, "hourly", hourlyOrder, Modifier.animateItem()) { HourlyCard(snapshot, now, nowMillis, settings, use24h) } }
            val dailyOrder = order++
            item(key = "daily") { Reveal(reveal, "daily", dailyOrder, Modifier.animateItem()) { DailyCard(snapshot, now, nowMillis, settings, use24h) } }
            detailRows(snapshot, now, nowMillis, settings, use24h, page.location, reveal, order)
            item(key = "footer") { Footer(page, nowMillis, use24h) }
        }
    }
}

@Composable
private fun Hero(
    page: LocationPage,
    now: NowConditions?,
    today: DailyPoint?,
    settings: AppSettings,
    nowMillis: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = settings.units
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page.location.isCurrent) {
                Icon(Icons.Rounded.NearMe, contentDescription = "Current location", tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                page.location.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        FreshnessLine(page, nowMillis, onRefresh)
        AnimatedContent(
            targetState = Units.formatTemperature(now?.temperature, units.temperature),
            transitionSpec = {
                // Digits roll up when it gets warmer and down when it gets colder.
                val warmer = degrees(targetState) >= degrees(initialState)
                (slideInVertically(tween(450)) { h -> if (warmer) h / 2 else -h / 2 } + fadeIn(tween(450))) togetherWith
                    (slideOutVertically(tween(450)) { h -> if (warmer) -h / 2 else h / 2 } + fadeOut(tween(300))) using
                    SizeTransform(clip = false)
            },
            label = "temperature",
        ) { temperature ->
            Text(
                text = temperature,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
        if (now != null) {
            Text(now.condition.label(now.isDay), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(6.dp))
            if (today != null) {
                Text(
                    "H:${Units.formatTemperature(today.temperatureMax, units.temperature)}   L:${Units.formatTemperature(today.temperatureMin, units.temperature)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyText.Secondary,
                )
            }
            now.apparentTemperature?.let {
                Text(
                    "Feels like ${Units.formatTemperature(it, units.temperature)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkyText.Tertiary,
                )
            }
        }
    }
}

@Composable
private fun FreshnessLine(page: LocationPage, nowMillis: Long, onRefresh: () -> Unit) {
    val snapshot = page.snapshot
    val age = snapshot?.let { nowMillis - it.fetchedAt }
    val stale = age != null && age > 60 * 60_000L
    val text = when {
        page.refreshing -> "Updating"
        snapshot == null -> page.error ?: "Loading…"
        page.error != null && age != null && age > 15 * 60_000L -> "Couldn't update · ${TimeText.relativeAge(nowMillis, snapshot.fetchedAt)}"
        else -> "Updated ${TimeText.relativeAge(nowMillis, snapshot.fetchedAt)}"
    }
    val color = if (stale || (page.error != null && !page.refreshing)) SkyText.Warning else SkyText.Tertiary
    Row(
        modifier = Modifier.clickable(enabled = !page.refreshing, onClick = onRefresh).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!page.refreshing) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing), color = color)
        if (page.refreshing) {
            Spacer(Modifier.width(6.dp))
            PulsingDots(SkyText.Secondary, dotSize = 3.dp)
        }
    }
}

@Composable
private fun EmptyCard(page: LocationPage, onRefresh: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (page.refreshing) {
                PulsingDots(Color.White, dotSize = 8.dp)
                Spacer(Modifier.height(14.dp))
                Text("Getting the forecast…", color = Color.White, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(page.error ?: "No forecast yet", color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                TextButton(onClick = onRefresh) { Text("Try again", color = Color.White) }
            }
        }
    }
}

@Composable
private fun Footer(page: LocationPage, nowMillis: Long, use24h: Boolean) {
    val uri = LocalUriHandler.current
    val snapshot = page.snapshot ?: return
    val deviceZone = ZoneId.systemDefault()
    val localTimes = remember(snapshot.timezone) { runCatching { ZoneId.of(snapshot.timezone) }.getOrNull() }
    val differentZone = localTimes != null && localTimes.rules.getOffset(java.time.Instant.ofEpochMilli(nowMillis)) !=
        deviceZone.rules.getOffset(java.time.Instant.ofEpochMilli(nowMillis))
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Weather data by Open-Meteo.com",
            style = MaterialTheme.typography.labelMedium,
            color = SkyText.Secondary,
            modifier = Modifier.clickable { uri.openUri("https://open-meteo.com/") }.padding(4.dp),
        )
        val sources = buildList {
            if (snapshot.airQuality != null) add("Air quality: Copernicus CAMS")
            when (snapshot.alertsSource ?: WeatherRepository.SOURCE_NWS.takeIf { snapshot.alertsSupported }) {
                WeatherRepository.SOURCE_NWS -> add("Alerts: National Weather Service")
                WeatherRepository.SOURCE_METEOALARM -> add("Warnings: MeteoAlarm")
            }
        }
        if (sources.isNotEmpty()) {
            Text(sources.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = SkyText.Tertiary, textAlign = TextAlign.Center)
        }
        Text(
            "Fetched at " + TimeText(deviceZone, use24h).timeMillis(snapshot.fetchedAt) + if (differentZone) " · forecast times are local to ${page.location.name}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = SkyText.Tertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The number in a formatted temperature like "-3°", for deciding which way digits roll. */
private fun degrees(text: String): Int =
    Regex("[-−]?\\d+").find(text)?.value?.replace('−', '-')?.toIntOrNull() ?: 0
