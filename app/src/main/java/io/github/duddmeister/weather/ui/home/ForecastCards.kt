package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.domain.Scales
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.isDaylightAt
import io.github.duddmeister.weather.domain.localDate
import io.github.duddmeister.weather.domain.upcomingDays
import io.github.duddmeister.weather.domain.upcomingHours
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.DailyPoint
import io.github.duddmeister.weather.model.HourlyPoint
import io.github.duddmeister.weather.model.UnitPrefs
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.components.CardLabel
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.Motion
import io.github.duddmeister.weather.ui.components.SegmentedControl
import io.github.duddmeister.weather.ui.components.WeatherIcon
import io.github.duddmeister.weather.ui.components.rememberAppearProgress
import io.github.duddmeister.weather.ui.components.rememberHaptics
import io.github.duddmeister.weather.ui.components.temperatureColor
import io.github.duddmeister.weather.ui.theme.SkyText
import io.github.duddmeister.weather.ui.theme.TabularNumbers
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class HourlyMode(val label: String) { Temperature("Temp"), Precipitation("Rain"), Wind("Wind") }

private val HourColumn = 58.dp
private val ChartHeight = 96.dp
private val RainBlue = Color(0xFF6CC7FF)

/** One column of the hourly forecast. */
private class HourCell(
    val time: Long,
    val label: String,
    /** "Now" and the first hour of each day stand out. */
    val emphasised: Boolean,
    val startsDay: Boolean,
    val weatherCode: Int?,
    val isDay: Boolean,
    val point: HourlyPoint,
    val temperature: Double?,
    /** Everything about the hour in a sentence, for screen readers. */
    val description: String,
    val title: String,
)

/** What the chart plots in one mode: a height (0..1, NaN where unknown) and a label for each hour. */
private class Series(
    val heights: FloatArray,
    val labels: List<String>,
    val bars: Boolean,
    val colours: List<Color>?,
)

/** The chart on its way from one [Series] to another. */
private class ChartMorph(val from: Series, val to: Series, val progress: Animatable<Float, AnimationVector1D>)

@Composable
fun HourlyCard(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, settings: AppSettings, use24h: Boolean) {
    var mode by rememberSaveable { mutableStateOf(HourlyMode.Temperature) }
    var selectedTime by rememberSaveable { mutableStateOf<Long?>(null) }
    val zone = snapshot.zoneId()
    val units = settings.units
    val text = remember(zone, use24h) { TimeText(zone, use24h) }
    // The first column is "now": it follows what the top of the page says, the rest change with the hour.
    val nowShown = Triple(Units.roundedTemperature(now.temperature, units.temperature), now.weatherCode, now.isDay)
    val cells = remember(snapshot, nowMillis / 300_000, nowShown, units, use24h) { hourCells(snapshot, now, nowMillis, units, text) }
    if (cells.isEmpty()) return
    val series = remember(cells, mode, units) { seriesFor(mode, cells, units) }
    val haptics = rememberHaptics()

    // Switching between temperature, rain and wind morphs the chart from one to the other.
    var morph by remember { mutableStateOf(ChartMorph(series, series, Animatable(1f))) }
    LaunchedEffect(series) {
        if (series !== morph.to) {
            // One write, so no frame ever sees the new chart with the old progress.
            val next = ChartMorph(from = morph.to, to = series, progress = Animatable(0f))
            morph = next
            next.progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    val appear by rememberAppearProgress(durationMillis = 1000)

    val selectedIndex = cells.indexOfFirst { it.time == selectedTime }
    // Where the highlight was last, so it has somewhere to fade out from.
    val lastSelection = remember { intArrayOf(0) }
    if (selectedIndex >= 0) lastSelection[0] = selectedIndex
    val lastSelected = lastSelection[0]
    val highlightAt by animateFloatAsState(lastSelected.toFloat(), Motion.snappy(), label = "hour")
    val highlight by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, tween(Motion.FADE_MILLIS), label = "hourHighlight")

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
        CardLabel(Icons.Rounded.Schedule, "Hourly", Modifier.padding(horizontal = 16.dp)) {
            SegmentedControl(
                options = HourlyMode.entries,
                selected = mode,
                label = { it.label },
                onSelect = { mode = it },
                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.08f),
                horizontalPadding = 10.dp,
                verticalPadding = 5.dp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Box {
                Column(
                    Modifier
                        .padding(vertical = 4.dp)
                        .drawBehind {
                            val column = HourColumn.toPx()
                            // A hairline where each new day begins.
                            cells.forEachIndexed { i, cell ->
                                if (cell.startsDay && i > 0) {
                                    drawLine(
                                        Color.White.copy(alpha = 0.16f),
                                        Offset(column * i, 0f),
                                        Offset(column * i, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                                    )
                                }
                            }
                            if (highlight > 0f) {
                                drawRoundRect(
                                    Color.White,
                                    topLeft = Offset(column * highlightAt + 3.dp.toPx(), -2.dp.toPx()),
                                    size = Size(column - 6.dp.toPx(), size.height + 4.dp.toPx()),
                                    cornerRadius = CornerRadius(18.dp.toPx()),
                                    alpha = 0.13f * highlight,
                                )
                            }
                        }
                        .clearAndSetSemantics { },
                ) {
                    Row {
                        cells.forEach { cell ->
                            Text(
                                cell.label,
                                modifier = Modifier.width(HourColumn),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (cell.emphasised) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (cell.emphasised) Color.White else SkyText.Secondary,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        cells.forEach { cell ->
                            Box(Modifier.width(HourColumn), contentAlignment = Alignment.Center) {
                                WeatherIcon(cell.weatherCode, cell.isDay, 30.dp, described = false)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HourlyChart(
                        from = morph.from,
                        to = morph.to,
                        morph = { morph.progress.value },
                        appear = { appear },
                        modifier = Modifier.width(HourColumn * cells.size).height(ChartHeight),
                    )
                    Spacer(Modifier.height(4.dp))
                    Crossfade(mode, animationSpec = tween(Motion.FADE_MILLIS), label = "hourFooter") { footer ->
                        Row {
                            cells.forEach { cell -> HourFooter(footer, cell.point, units) }
                        }
                    }
                }
                // Each hour is one target for touch and for screen readers.
                Row(Modifier.matchParentSize()) {
                    cells.forEachIndexed { index, cell ->
                        val isSelected = index == selectedIndex
                        Box(
                            Modifier
                                .width(HourColumn)
                                .fillMaxHeight()
                                .semantics {
                                    contentDescription = cell.description
                                    selected = isSelected
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = if (isSelected) "Hide details" else "Show details",
                                    role = Role.Button,
                                ) {
                                    haptics.tick()
                                    selectedTime = if (isSelected) null else cell.time
                                },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = selectedIndex >= 0,
            enter = expandVertically(Motion.glide()) + fadeIn(tween(Motion.FADE_MILLIS, delayMillis = 80)),
            exit = shrinkVertically(Motion.glide()) + fadeOut(tween(120)),
        ) {
            cells.getOrNull(lastSelected)?.let { HourDetails(it, units) }
        }
        AnimatedVisibility(visible = mode == HourlyMode.Wind && selectedIndex < 0) {
            Text(
                "Wind speed in ${units.wind.label}",
                style = MaterialTheme.typography.labelSmall,
                color = SkyText.Tertiary,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
    }
}

private fun hourCells(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, units: UnitPrefs, text: TimeText): List<HourCell> {
    val zone = snapshot.zoneId()
    return snapshot.upcomingHours(nowMillis, 48).mapIndexed { index, hour ->
        val local = Instant.ofEpochSecond(hour.time).atZone(zone)
        val isNow = index == 0
        val startsDay = local.hour == 0
        val code = if (isNow) now.weatherCode else hour.weatherCode
        val isDay = if (isNow) now.isDay else snapshot.isDaylightAt(hour.time) ?: hour.isDay ?: true
        val temperature = if (isNow) now.temperature else hour.temperature
        val time = if (isNow) "Now" else "${text.weekdayLong(local.toLocalDate())} ${text.hour(hour.time)}"
        val condition = WeatherCondition.fromCode(code).label(isDay)
        HourCell(
            time = hour.time,
            label = when {
                isNow -> "Now"
                startsDay -> text.weekdayShort(local.toLocalDate())
                else -> text.hour(hour.time)
            },
            emphasised = isNow || startsDay,
            startsDay = startsDay,
            weatherCode = code,
            isDay = isDay,
            point = hour,
            temperature = temperature,
            description = listOfNotNull(
                time,
                condition,
                temperature?.let { Units.formatTemperature(it, units.temperature) },
                hour.precipitationProbability?.let { "${it.roundToInt()}% chance of rain" },
                hour.windSpeed?.let { "wind ${Units.formatWind(it, units.wind)}" },
            ).joinToString(", "),
            title = if (isNow) "Now" else "${text.weekdayShort(local.toLocalDate())} ${text.hour(hour.time)}",
        )
    }
}

private fun seriesFor(mode: HourlyMode, cells: List<HourCell>, units: UnitPrefs): Series = when (mode) {
    HourlyMode.Temperature -> {
        val values = cells.map { it.temperature }
        val known = values.filterNotNull()
        val min = known.minOrNull() ?: 0.0
        val range = ((known.maxOrNull() ?: 1.0) - min).coerceAtLeast(1.0)
        Series(
            // Leaves the coldest hour a little above the floor of the chart.
            heights = FloatArray(values.size) { i -> values[i]?.let { 0.1f + 0.9f * ((it - min) / range).toFloat() } ?: Float.NaN },
            labels = values.map { Units.formatTemperature(it, units.temperature) },
            bars = false,
            colours = values.map { temperatureColor(it ?: 10.0) },
        )
    }
    HourlyMode.Precipitation -> {
        val values = cells.map { it.point.precipitationProbability }
        Series(
            heights = FloatArray(values.size) { i -> ((values[i] ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
            labels = values.map { value -> value?.takeIf { it >= 0.5 }?.let { "${it.roundToInt()}%" } ?: "" },
            bars = true,
            colours = null,
        )
    }
    HourlyMode.Wind -> {
        val values = cells.map { it.point.windSpeed }
        // From calm, so a breeze reads as a breeze, with headroom so a steady wind isn't pinned to the top.
        val top = ((values.filterNotNull().maxOrNull() ?: 0.0) * 1.15).coerceAtLeast(20.0)
        Series(
            heights = FloatArray(values.size) { i -> values[i]?.let { (it / top).toFloat().coerceIn(0f, 1f) } ?: Float.NaN },
            labels = values.map { Units.formatWind(it, units.wind, withUnit = false) },
            bars = false,
            colours = null,
        )
    }
}

@Composable
private fun HourFooter(mode: HourlyMode, hour: HourlyPoint, units: UnitPrefs) {
    Box(Modifier.width(HourColumn).height(18.dp), contentAlignment = Alignment.Center) {
        when (mode) {
            HourlyMode.Temperature -> {
                val chance = hour.precipitationProbability?.roundToInt() ?: 0
                if (chance >= 20) Text("$chance%", style = MaterialTheme.typography.labelSmall, color = SkyText.Rain)
            }
            HourlyMode.Precipitation -> {
                val amount = hour.precipitation ?: 0.0
                if (amount >= 0.05) {
                    Text(Units.formatPrecipitation(amount, units.precipitation), style = MaterialTheme.typography.labelSmall, color = SkyText.Rain, maxLines = 1)
                }
            }
            HourlyMode.Wind -> hour.windDirection?.let { direction ->
                Icon(
                    Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = SkyText.Secondary,
                    modifier = Modifier.size(14.dp).rotate((direction + 180).toFloat()),
                )
            }
        }
    }
}

/**
 * A smooth line (or bars) with a value above each hour. While [morph] runs 0 → 1 every point glides
 * from where [from] had it to where [to] puts it, and one style of drawing fades into the other.
 */
@Composable
private fun HourlyChart(from: Series, to: Series, morph: () -> Float, appear: () -> Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontFeatureSettings = TabularNumbers)
    val density = LocalDensity.current
    val fromLabels = remember(from, labelStyle, density) { from.labels.map { measurer.measure(it, labelStyle) } }
    val toLabels = remember(to, labelStyle, density) { to.labels.map { measurer.measure(it, labelStyle) } }
    Spacer(
        modifier.drawWithCache {
            val count = to.heights.size
            val column = size.width / count.coerceAtLeast(1)
            val top = 26.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val line = Path()
            val fill = Path()
            val ys = FloatArray(count)
            val lineStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            val fillBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = size.height)
            val barBrush = Brush.verticalGradient(listOf(RainBlue, RainBlue.copy(alpha = 0.55f)), startY = top, endY = bottom)

            fun y(series: Series, i: Int, grow: Float): Float {
                val h = series.heights.getOrElse(i) { Float.NaN }
                return if (h.isNaN()) Float.NaN else bottom - h * (bottom - top) * grow
            }

            onDrawBehind {
                if (count == 0) return@onDrawBehind
                val grow = appear().coerceIn(0f, 1f)
                val p = morph().coerceIn(0f, 1f)
                for (i in 0 until count) {
                    val a = y(from, i, grow)
                    val b = y(to, i, grow)
                    ys[i] = when {
                        b.isNaN() -> Float.NaN
                        a.isNaN() -> b
                        else -> a + (b - a) * p
                    }
                }
                val barsAlpha = (if (to.bars) p else 0f) + (if (from.bars) 1f - p else 0f)
                val lineAlpha = 1f - barsAlpha

                if (barsAlpha > 0.01f) {
                    for (i in 0 until count) {
                        if (ys[i].isNaN()) continue
                        val height = (bottom - ys[i]).coerceAtLeast(2.dp.toPx())
                        val strength = ((bottom - ys[i]) / (bottom - top)).coerceIn(0f, 1f)
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(column * i + column * 0.29f, bottom - height),
                            size = Size(column * 0.42f, height),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            alpha = barsAlpha * (0.3f + 0.7f * strength),
                        )
                    }
                }
                if (lineAlpha > 0.01f) {
                    line.rewind()
                    fill.rewind()
                    var previous = Offset.Unspecified
                    var first = Offset.Unspecified
                    for (i in 0 until count) {
                        if (ys[i].isNaN()) {
                            previous = Offset.Unspecified
                            continue
                        }
                        val point = Offset(column * (i + 0.5f), ys[i])
                        if (previous == Offset.Unspecified) {
                            line.moveTo(point.x, point.y)
                            if (first == Offset.Unspecified) {
                                first = point
                                // The wash under the line runs out to both edges of the chart.
                                fill.moveTo(0f, point.y)
                            }
                            fill.lineTo(point.x, point.y)
                        } else {
                            val mid = (point.x - previous.x) / 2f
                            line.cubicTo(previous.x + mid, previous.y, point.x - mid, point.y, point.x, point.y)
                            fill.cubicTo(previous.x + mid, previous.y, point.x - mid, point.y, point.x, point.y)
                        }
                        previous = point
                    }
                    if (first != Offset.Unspecified && previous != Offset.Unspecified) {
                        fill.lineTo(size.width, previous.y)
                        fill.lineTo(size.width, size.height)
                        fill.lineTo(0f, size.height)
                        fill.close()
                        drawPath(fill, fillBrush, alpha = lineAlpha)
                        val colours = List(count) { i ->
                            lerp(from.colours?.getOrNull(i) ?: Color.White, to.colours?.getOrNull(i) ?: Color.White, p)
                        }
                        val stroke = if (count >= 2) {
                            Brush.horizontalGradient(colours, startX = column * 0.5f, endX = size.width - column * 0.5f)
                        } else {
                            Brush.linearGradient(listOf(Color.White, Color.White))
                        }
                        drawPath(line, stroke, alpha = lineAlpha, style = lineStroke)
                    }
                    for (i in 0 until count) {
                        if (ys[i].isNaN()) continue
                        val centre = Offset(column * (i + 0.5f), ys[i])
                        if (i == 0) drawCircle(Color.White, radius = 7.dp.toPx(), center = centre, alpha = 0.25f * lineAlpha)
                        drawCircle(Color.White, radius = (if (i == 0) 4f else 2.6f).dp.toPx(), center = centre, alpha = lineAlpha)
                    }
                }

                // Values fade out with the old chart and in with the new one, once it has grown.
                val settled = ((grow - 0.5f) * 2f).coerceIn(0f, 1f)
                fun labels(layouts: List<TextLayoutResult>, series: Series, alpha: Float) {
                    if (alpha <= 0.01f) return
                    for (i in 0 until count) {
                        val layout = layouts.getOrNull(i) ?: continue
                        if (ys[i].isNaN() || layout.size.width == 0) continue
                        drawText(
                            layout,
                            color = if (series.bars) SkyText.Rain else Color.White,
                            topLeft = Offset(column * (i + 0.5f) - layout.size.width / 2f, ys[i] - layout.size.height - 5.dp.toPx()),
                            alpha = alpha,
                        )
                    }
                }
                if (from !== to) labels(fromLabels, from, settled * (1f - p * 2f).coerceIn(0f, 1f))
                labels(toLabels, to, settled * (if (from === to) 1f else (p * 2f - 1f).coerceIn(0f, 1f)))
            }
        },
    )
}

/** Everything about the hour that was tapped. */
@Composable
private fun HourDetails(cell: HourCell, units: UnitPrefs) {
    val hour = cell.point
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cell.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(
                "  ·  " + WeatherCondition.fromCode(cell.weatherCode).label(cell.isDay),
                style = MaterialTheme.typography.titleSmall,
                color = SkyText.Secondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "Feels like" to Units.formatTemperature(hour.apparentTemperature, units.temperature),
                "Chance of rain" to Units.formatPercent(hour.precipitationProbability),
                "Rainfall" to when {
                    (hour.snowfall ?: 0.0) >= 0.1 -> Units.formatSnowfall(hour.snowfall, units.precipitation) + " snow"
                    else -> Units.formatPrecipitation(hour.precipitation, units.precipitation)
                },
                "Wind" to (Units.formatWind(hour.windSpeed, units.wind) + " " + Units.compass(hour.windDirection)).trim(),
                "Humidity" to Units.formatPercent(hour.humidity),
                "UV index" to (hour.uvIndex?.let { "${it.roundToInt()} · ${Scales.uv(it).label}" } ?: "--"),
            ),
            columns = 3,
        )
    }
}

/** Small labelled values in a grid. */
@Composable
internal fun StatGrid(stats: List<Pair<String, String>>, columns: Int) {
    Column(Modifier.fillMaxWidth()) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                row.forEach { (name, value) ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(name, style = MaterialTheme.typography.labelSmall, color = SkyText.Tertiary, maxLines = 1)
                        Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun DailyCard(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, settings: AppSettings, use24h: Boolean) {
    val days = remember(snapshot, nowMillis / 3_600_000) { snapshot.upcomingDays(nowMillis) }
    if (days.isEmpty()) return
    val text = remember(snapshot.timezone, use24h) { TimeText(snapshot.zoneId(), use24h) }
    val today = snapshot.localDate(nowMillis)
    val weekMin = days.mapNotNull { it.temperatureMin }.minOrNull() ?: 0.0
    val weekMax = days.mapNotNull { it.temperatureMax }.maxOrNull() ?: (weekMin + 1.0)
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp)) {
        CardLabel(Icons.Rounded.DateRange, "${days.size}-day forecast", Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(6.dp))
        days.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.08f))
            val date = remember(day.date) { LocalDate.parse(day.date) }
            DayRow(
                index = index,
                day = day,
                label = text.dayLabel(date, today),
                dateLabel = text.shortDate(date),
                fullName = if (date == today) "Today" else text.weekdayLong(date),
                currentTemperature = if (index == 0) now.temperature else null,
                weekMin = weekMin,
                weekMax = weekMax,
                expanded = expanded == day.date,
                hours = if (expanded == day.date) remember(snapshot, day.date) { snapshot.hoursOf(day, days.getOrNull(index + 1)) } else emptyList(),
                settings = settings,
                text = text,
                onClick = {
                    haptics.tick()
                    expanded = if (expanded == day.date) null else day.date
                },
            )
        }
    }
}

/** The hourly forecast for one day: from its midnight up to and including the next one. */
private fun WeatherSnapshot.hoursOf(day: DailyPoint, next: DailyPoint?): List<HourlyPoint> {
    val end = next?.time ?: (day.time + 24 * 3600)
    return hourly.filter { it.time in day.time..end }
}

@Composable
private fun DayRow(
    index: Int,
    day: DailyPoint,
    label: String,
    dateLabel: String,
    fullName: String,
    currentTemperature: Double?,
    weekMin: Double,
    weekMax: Double,
    expanded: Boolean,
    hours: List<HourlyPoint>,
    settings: AppSettings,
    text: TimeText,
    onClick: () -> Unit,
) {
    val units = settings.units
    val grow by rememberAppearProgress(durationMillis = 750, extraDelayMillis = index * 40)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.snappy(), label = "chevron")
    val chance = day.precipitationProbabilityMax?.roundToInt() ?: 0
    val numbers = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNumbers)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Hide details" else "Show details", role = Role.Button, onClick = onClick)
            .semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(
                        fullName,
                        WeatherCondition.fromCode(day.weatherCode).label(true),
                        "high ${Units.formatTemperature(day.temperatureMax, units.temperature)}",
                        "low ${Units.formatTemperature(day.temperatureMin, units.temperature)}",
                        "$chance% chance of rain".takeIf { chance >= 20 },
                    ).joinToString(", ")
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(58.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = SkyText.Tertiary, maxLines = 1)
            }
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherIcon(day.weatherCode, isDay = true, size = 28.dp, described = false)
                if (chance >= 20) Text("$chance%", style = MaterialTheme.typography.labelSmall, color = SkyText.Rain)
            }
            Text(
                Units.formatTemperature(day.temperatureMin, units.temperature),
                style = numbers,
                color = SkyText.Tertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp),
            )
            Spacer(Modifier.width(10.dp))
            RangeBar(day.temperatureMin, day.temperatureMax, weekMin, weekMax, currentTemperature, { grow }, Modifier.weight(1f).height(6.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                Units.formatTemperature(day.temperatureMax, units.temperature),
                style = numbers,
                color = Color.White,
                textAlign = TextAlign.End,
                modifier = Modifier.width(38.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = SkyText.Faint,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.glide()) + fadeIn(tween(Motion.FADE_MILLIS, delayMillis = 80)),
            exit = shrinkVertically(Motion.glide()) + fadeOut(tween(120)),
        ) {
            DayDetails(day, hours, settings, text)
        }
    }
}

@Composable
private fun RangeBar(low: Double?, high: Double?, weekMin: Double, weekMax: Double, current: Double?, grow: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = radius)
        if (low == null || high == null) return@Canvas
        val span = (weekMax - weekMin).coerceAtLeast(1.0)
        val fullStart = ((low - weekMin) / span).toFloat() * size.width
        val fullEnd = ((high - weekMin) / span).toFloat() * size.width
        val middle = (fullStart + fullEnd) / 2f
        val g = grow()
        val start = middle - (middle - fullStart) * g
        val end = middle + (fullEnd - middle) * g
        val width = (end - start).coerceAtLeast(size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(temperatureColor(low), temperatureColor(high)), startX = start, endX = start + width),
            topLeft = Offset(start, 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
        if (current != null && g > 0.6f) {
            val x = (((current - weekMin) / span).toFloat() * size.width).coerceIn(size.height / 2f, size.width - size.height / 2f)
            val pop = ((g - 0.6f) / 0.4f).coerceIn(0f, 1f)
            drawCircle(Color(0xFF1B2440), radius = size.height * 0.95f * pop, center = Offset(x, size.height / 2f))
            drawCircle(Color.White, radius = size.height * 0.62f * pop, center = Offset(x, size.height / 2f))
        }
    }
}

@Composable
private fun DayDetails(day: DailyPoint, hours: List<HourlyPoint>, settings: AppSettings, text: TimeText) {
    val units = settings.units
    val stats = buildList {
        val precipitation = day.precipitationSum ?: 0.0
        val snow = day.snowfallSum ?: 0.0
        add(
            "Precipitation" to when {
                snow >= 0.5 -> Units.formatSnowfall(snow, units.precipitation) + " snow"
                precipitation > 0.0 -> Units.formatPrecipitation(precipitation, units.precipitation)
                else -> "None"
            } + (day.precipitationProbabilityMax?.let { " · ${it.roundToInt()}%" } ?: ""),
        )
        add("Wind" to Units.formatWind(day.windSpeedMax, units.wind) + " " + Units.compass(day.windDirectionDominant) +
            (day.windGustsMax?.let { " · gusts ${Units.formatWind(it, units.wind, withUnit = false)}" } ?: ""))
        day.uvIndexMax?.let { add("UV index" to "${it.roundToInt()} · ${Scales.uv(it).label}") }
        if (day.sunrise != null && day.sunset != null) {
            add("Sun" to "${text.time(day.sunrise)} – ${text.time(day.sunset)}")
        }
        day.apparentMax?.let { add("Feels like" to "${Units.formatTemperature(day.apparentMin, units.temperature)} to ${Units.formatTemperature(it, units.temperature)}") }
        day.daylightDuration?.let { add("Daylight" to TimeText.hoursMinutes(it)) }
    }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp)) {
        if (hours.count { it.temperature != null } >= 6) {
            DayChart(day, hours, text, Modifier.fillMaxWidth().height(96.dp))
            Spacer(Modifier.height(8.dp))
        }
        StatGrid(stats, columns = 2)
    }
}

/**
 * The day at a glance: its temperature curve in the colours of the range bar, the chance of rain
 * as bars along the bottom, and the night shaded at either end.
 */
@Composable
private fun DayChart(day: DailyPoint, hours: List<HourlyPoint>, text: TimeText, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = SkyText.Tertiary)
    val density = LocalDensity.current
    val start = day.time
    val end = hours.last().time.coerceAtLeast(start + 3600)
    val ticks = remember(day.date, axisStyle, density, text) {
        listOf(6, 12, 18).map { h -> (start + h * 3600L).let { it to measurer.measure(text.hour(it), axisStyle) } }
    }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(700, delayMillis = 120, easing = FastOutSlowInEasing)) }
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                val axis = 18.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - axis - 4.dp.toPx()
                fun x(time: Long) = ((time - start).toFloat() / (end - start)) * size.width
                val known = hours.filter { it.temperature != null }
                val min = known.minOf { it.temperature!! }
                val range = (known.maxOf { it.temperature!! } - min).coerceAtLeast(1.0)
                val points = known.map { Offset(x(it.time), bottom - (0.12f + 0.88f * ((it.temperature!! - min) / range).toFloat()) * (bottom - top)) }
                val line = Path().apply {
                    points.forEachIndexed { i, point ->
                        if (i == 0) {
                            moveTo(point.x, point.y)
                        } else {
                            val before = points[i - 1]
                            val mid = (point.x - before.x) / 2f
                            cubicTo(before.x + mid, before.y, point.x - mid, point.y, point.x, point.y)
                        }
                    }
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, bottom)
                    lineTo(points.first().x, bottom)
                    close()
                }
                val stroke = Brush.horizontalGradient(known.map { temperatureColor(it.temperature!!) }, startX = points.first().x, endX = points.last().x.coerceAtLeast(points.first().x + 1f))
                val fillBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), startY = top, endY = bottom)
                val night = Color.Black.copy(alpha = 0.14f)
                val corner = CornerRadius(10.dp.toPx())
                val slot = size.width / (hours.size.coerceAtLeast(2))
                onDrawBehind {
                    val shown = draw.value
                    // Night at either end of the day.
                    val sunrise = day.sunrise
                    val sunset = day.sunset
                    if (sunrise != null && sunset != null && sunrise in start..end && sunset in start..end) {
                        drawRoundRect(night, topLeft = Offset(0f, 0f), size = Size(x(sunrise), bottom + 2.dp.toPx()), cornerRadius = corner)
                        drawRoundRect(night, topLeft = Offset(x(sunset), 0f), size = Size(size.width - x(sunset), bottom + 2.dp.toPx()), cornerRadius = corner)
                    }
                    hours.forEach { hour ->
                        val chance = ((hour.precipitationProbability ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                        if (chance >= 0.1f) {
                            val height = (bottom - top) * 0.5f * chance * shown
                            drawRoundRect(
                                RainBlue,
                                topLeft = Offset(x(hour.time) - slot * 0.3f, bottom - height),
                                size = Size(slot * 0.6f, height),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                alpha = 0.22f + 0.4f * chance,
                            )
                        }
                    }
                    // The curve draws itself from midnight to midnight.
                    clipRect(right = size.width * shown) {
                        drawPath(fill, fillBrush)
                        drawPath(line, stroke, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, bottom + 2.dp.toPx()), Offset(size.width, bottom + 2.dp.toPx()), strokeWidth = 1.dp.toPx())
                    ticks.forEach { (time, layout) ->
                        if (time in start..end) {
                            drawLine(Color.White.copy(alpha = 0.14f), Offset(x(time), bottom - 3.dp.toPx()), Offset(x(time), bottom + 2.dp.toPx()), strokeWidth = 1.dp.toPx())
                            drawText(layout, topLeft = Offset((x(time) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), size.height - axis + 3.dp.toPx()))
                        }
                    }
                }
            },
    )
}
