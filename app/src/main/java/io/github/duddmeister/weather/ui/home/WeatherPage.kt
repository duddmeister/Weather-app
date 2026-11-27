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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.duddmeister.weather.data.WeatherRepository
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.domain.NowcastBuilder
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.domain.today
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.DailyPoint
import io.github.duddmeister.weather.model.TimeFormat
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.ui.LocationPage
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.PillButton
import io.github.duddmeister.weather.ui.components.PulsingDots
import io.github.duddmeister.weather.ui.components.Reveal
import io.github.duddmeister.weather.ui.components.RevealState
import io.github.duddmeister.weather.ui.components.rememberShimmer
import io.github.duddmeister.weather.ui.theme.SkyText
import java.time.Instant
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
    reveal: RevealState,
    onRefresh: () -> Unit,
) {
    val snapshot = page.snapshot
    val use24h = rememberUse24h(settings)
    val now = remember(snapshot, nowMillis) { snapshot?.conditionsAt(nowMillis) }
    val today = remember(snapshot, nowMillis) { snapshot?.today(nowMillis) }
    val nowcast = remember(snapshot, nowMillis) { snapshot?.let { NowcastBuilder.build(it, nowMillis) } }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
    // The top two fifths of the screen, or more if large text needs it.
    val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) } }
    val heroHeight = maxOf(300.dp, window.height * 0.42f)
    val sideInset = contentSideInset(window.width)
    var openMetric by rememberSaveable { mutableStateOf<Metric?>(null) }

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
            contentPadding = PaddingValues(top = topInset, bottom = bottomInset, start = sideInset, end = sideInset),
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
                        use24h = use24h,
                        scrolled = { listState.scrolledPixels() },
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxWidth().heightIn(min = heroHeight),
                    )
                }
            }
            if (snapshot == null || now == null) {
                if (page.refreshing) {
                    item(key = "loading-hourly") { Reveal(reveal, "loading-hourly", 1, Modifier.animateItem()) { SkeletonCard(rows = 0) } }
                    item(key = "loading-daily") { Reveal(reveal, "loading-daily", 2, Modifier.animateItem()) { SkeletonCard(rows = 5) } }
                } else {
                    item(key = "empty") { Reveal(reveal, "empty", 1, Modifier.animateItem()) { EmptyCard(page, onRefresh) } }
                }
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
            val hourlyOrder = order++
            item(key = "hourly") { Reveal(reveal, "hourly", hourlyOrder, Modifier.animateItem()) { HourlyCard(snapshot, now, nowMillis, settings, use24h) } }
            val dailyOrder = order++
            item(key = "daily") { Reveal(reveal, "daily", dailyOrder, Modifier.animateItem()) { DailyCard(snapshot, now, nowMillis, settings, use24h) } }
            detailRows(snapshot, now, nowMillis, settings, use24h, page.location, reveal, order) { openMetric = it }
            item(key = "footer") { Footer(page, nowMillis, use24h) }
        }
    }
    val metric = openMetric
    if (metric != null && snapshot != null && now != null) {
        MetricSheet(metric, snapshot, now, nowMillis, settings, use24h, onDismiss = { openMetric = null })
    }
}

/** Side padding that keeps content to a comfortable column on tablets, unfolded phones and in landscape. */
fun contentSideInset(windowWidth: Dp): Dp = maxOf(16.dp, (windowWidth - 640.dp) / 2)

/** Soft shadow that keeps white text readable where the sky behind it is bright. */
private val SkyShadow = Shadow(color = Color.Black.copy(alpha = 0.22f), offset = Offset(0f, 2f), blurRadius = 18f)

private fun TextStyle.onSky() = copy(shadow = SkyShadow)

/**
 * The place, how fresh its data is, and the temperature writ large. As the page scrolls it hangs
 * back, shrinks a little and fades, so the forecast slides up over it rather than pushing it away.
 */
@Composable
private fun Hero(
    page: LocationPage,
    now: NowConditions?,
    today: DailyPoint?,
    settings: AppSettings,
    nowMillis: Long,
    use24h: Boolean,
    scrolled: () -> Float,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = settings.units
    Column(
        modifier = modifier
            .graphicsLayer {
                val offset = scrolled()
                // Gone by the time the forecast is half way up it, when the top bar takes over its name.
                val gone = (offset / (size.height * 0.5f)).coerceIn(0f, 1f)
                translationY = offset * 0.42f
                alpha = 1f - gone
                val scale = 1f - 0.1f * gone
                scaleX = scale
                scaleY = scale
            }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
            if (page.location.isCurrent) {
                Icon(Icons.Rounded.NearMe, contentDescription = "Current location", tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                page.location.name,
                style = MaterialTheme.typography.headlineSmall.onSky(),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FreshnessLine(page, nowMillis, use24h, onRefresh)
        if (now != null) {
            AnimatedContent(
                targetState = Units.formatTemperature(now.temperature, units.temperature),
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
                    style = MaterialTheme.typography.displayLarge.onSky(),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    // Shrinks to fit when large system text would push it off a narrow screen.
                    autoSize = TextAutoSize.StepBased(minFontSize = 64.sp, maxFontSize = 116.sp, stepSize = 4.sp),
                    // The degree sign hangs off to the right, so the digits look centred with this.
                    modifier = Modifier.padding(start = 22.dp),
                )
            }
            Text(now.condition.label(now.isDay), style = MaterialTheme.typography.titleLarge.onSky(), color = Color.White)
            Spacer(Modifier.height(8.dp))
            if (today != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "High ${Units.formatTemperature(today.temperatureMax, units.temperature)}, " +
                            "low ${Units.formatTemperature(today.temperatureMin, units.temperature)}"
                    },
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = SkyText.Secondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(Units.formatTemperature(today.temperatureMax, units.temperature), style = MaterialTheme.typography.titleMedium.onSky(), color = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = SkyText.Secondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(Units.formatTemperature(today.temperatureMin, units.temperature), style = MaterialTheme.typography.titleMedium.onSky(), color = SkyText.Secondary)
                }
            }
            now.apparentTemperature?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Feels like ${Units.formatTemperature(it, units.temperature)}",
                    style = MaterialTheme.typography.titleSmall.onSky(),
                    color = SkyText.Secondary,
                )
            }
        }
    }
}

/** "Updated 2 min ago", doubling as a refresh button; away from home it also gives the place's own time. */
@Composable
private fun FreshnessLine(page: LocationPage, nowMillis: Long, use24h: Boolean, onRefresh: () -> Unit) {
    val snapshot = page.snapshot
    val age = snapshot?.let { nowMillis - it.fetchedAt }
    val stale = age != null && age > 60 * 60_000L
    val text = when {
        page.refreshing -> "Updating"
        snapshot == null -> if (page.error != null) "Couldn't update" else "Loading"
        page.error != null && age != null && age > 15 * 60_000L -> "Couldn't update · ${TimeText.relativeAge(nowMillis, snapshot.fetchedAt)}"
        else -> "Updated ${TimeText.relativeAge(nowMillis, snapshot.fetchedAt)}"
    }
    val localTime = remember(snapshot?.timezone, nowMillis, use24h) {
        val zone = snapshot?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return@remember null
        val instant = Instant.ofEpochMilli(nowMillis)
        if (zone.rules.getOffset(instant) == ZoneId.systemDefault().rules.getOffset(instant)) null else TimeText(zone, use24h).timeMillis(nowMillis)
    }
    val color = if (stale || (page.error != null && !page.refreshing)) SkyText.Warning else SkyText.Secondary
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp).onSky()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (localTime != null) {
            Spacer(Modifier.height(2.dp))
            Text("Local time $localTime", style = style, color = SkyText.Secondary, maxLines = 1)
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = !page.refreshing, onClickLabel = "Refresh", onClick = onRefresh)
                .padding(horizontal = 12.dp, vertical = if (localTime != null) 5.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!page.refreshing) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (page.refreshing) {
                Spacer(Modifier.width(7.dp))
                PulsingDots(SkyText.Secondary, dotSize = 3.5.dp)
            }
        }
    }
}

@Composable
private fun EmptyCard(page: LocationPage, onRefresh: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("No forecast yet", color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                page.error ?: "Pull down or tap below to fetch it.",
                color = SkyText.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            PillButton("Try again", onRefresh, icon = Icons.Rounded.Refresh)
        }
    }
}

/** A card-shaped placeholder with a sheen passing over it, shown while the first forecast loads. */
@Composable
private fun SkeletonCard(rows: Int) {
    val sweep by rememberShimmer()
    GlassCard(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Loading the forecast" }) {
        Canvas(Modifier.fillMaxWidth().height(if (rows == 0) 150.dp else (28 + rows * 40).dp)) {
            val bar = 12.dp.toPx()
            val start = size.width * (sweep * 2.2f - 0.6f)
            val brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f)),
                start = Offset(start, 0f),
                end = Offset(start + size.width * 0.5f, size.height * 0.4f),
            )
            fun block(x: Float, y: Float, width: Float, height: Float = bar) =
                drawRoundRect(brush, topLeft = Offset(x, y), size = Size(width, height), cornerRadius = CornerRadius(height / 2f))
            // The heading.
            block(0f, 2.dp.toPx(), size.width * 0.34f, 10.dp.toPx())
            if (rows == 0) {
                // Hours: a time, an icon and a temperature in each column.
                val column = size.width / 6f
                repeat(6) { i ->
                    val x = column * i + column * 0.2f
                    block(x, 34.dp.toPx(), column * 0.6f)
                    drawCircle(brush, radius = 13.dp.toPx(), center = Offset(column * (i + 0.5f), 76.dp.toPx()))
                    block(x, 118.dp.toPx(), column * 0.6f)
                }
            } else {
                // Days: a name, an icon and a range bar in each row.
                repeat(rows) { i ->
                    val y = 32.dp.toPx() + i * 40.dp.toPx() + 6.dp.toPx()
                    block(0f, y, size.width * 0.16f)
                    drawCircle(brush, radius = 11.dp.toPx(), center = Offset(size.width * 0.27f, y + bar / 2f))
                    block(size.width * 0.38f, y + 3.dp.toPx(), size.width * 0.62f, 6.dp.toPx())
                }
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
    val differentZone = localTimes != null && localTimes.rules.getOffset(Instant.ofEpochMilli(nowMillis)) !=
        deviceZone.rules.getOffset(Instant.ofEpochMilli(nowMillis))
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Weather data by Open-Meteo.com",
            style = MaterialTheme.typography.labelLarge,
            color = SkyText.Secondary,
            modifier = Modifier.clip(CircleShape).clickable { uri.openUri("https://open-meteo.com/") }.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        val sources = buildList {
            if (snapshot.metOffice != null) add("Forecast: Met Office")
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
