package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.components.CardLabel
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.WeatherIcon
import io.github.duddmeister.weather.ui.components.rememberAppearProgress
import io.github.duddmeister.weather.ui.components.temperatureColor
import io.github.duddmeister.weather.ui.theme.SkyText
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class HourlyMode(val label: String) { Temperature("Temp"), Precipitation("Rain"), Wind("Wind") }

private val HourColumn = 58.dp

@Composable
fun HourlyCard(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, settings: AppSettings, use24h: Boolean) {
    var mode by rememberSaveable { mutableStateOf(HourlyMode.Temperature) }
    val zone = snapshot.zoneId()
    val text = remember(zone, use24h) { TimeText(zone, use24h) }
    val hours = remember(snapshot, nowMillis / 300_000) { snapshot.upcomingHours(nowMillis, 48) }
    if (hours.isEmpty()) return
    val units = settings.units
    val appear by rememberAppearProgress(durationMillis = 1000)
    // Switching between temperature, rain and wind redraws the chart from the baseline.
    val modeSwitch = remember { Animatable(1f) }
    var shownMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        if (mode != shownMode) {
            shownMode = mode
            modeSwitch.snapTo(0f)
            modeSwitch.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) {
        CardLabel(Icons.Rounded.Schedule, "Hourly forecast", Modifier.padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HourlyMode.entries.forEach { option ->
                    ModeChip(option.label, option == mode) { mode = option }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Column {
                Row {
                    hours.forEachIndexed { index, hour ->
                        val local = Instant.ofEpochSecond(hour.time).atZone(zone)
                        val label = when {
                            index == 0 -> "Now"
                            local.hour == 0 -> text.weekdayShort(local.toLocalDate())
                            else -> text.hour(hour.time)
                        }
                        Text(
                            label,
                            modifier = Modifier.width(HourColumn),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (index == 0 || local.hour == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == 0 || local.hour == 0) Color.White else SkyText.Secondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    hours.forEachIndexed { index, hour ->
                        Box(Modifier.width(HourColumn), contentAlignment = Alignment.Center) {
                            if (index == 0) {
                                WeatherIcon(now.weatherCode, now.isDay, 30.dp)
                            } else {
                                WeatherIcon(hour.weatherCode, snapshot.isDaylightAt(hour.time) ?: hour.isDay ?: true, 30.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val values: List<Double?> = when (mode) {
                    HourlyMode.Temperature -> hours.mapIndexed { i, h -> if (i == 0) now.temperature else h.temperature }
                    HourlyMode.Precipitation -> hours.map { it.precipitationProbability }
                    HourlyMode.Wind -> hours.map { it.windSpeed }
                }
                val labels = when (mode) {
                    HourlyMode.Temperature -> values.map { Units.formatTemperature(it, units.temperature) }
                    HourlyMode.Precipitation -> values.map { v -> v?.let { "${it.roundToInt()}%" } ?: "" }
                    HourlyMode.Wind -> values.map { Units.formatWind(it, units.wind, withUnit = false) }
                }
                val colors = when (mode) {
                    HourlyMode.Temperature -> values.map { temperatureColor(it ?: 10.0) }
                    else -> emptyList()
                }
                HourlyChart(
                    values = values,
                    labels = labels,
                    lineColors = colors,
                    bars = mode == HourlyMode.Precipitation,
                    progress = { appear * modeSwitch.value },
                    modifier = Modifier.width(HourColumn * hours.size).height(92.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    hours.forEach { hour ->
                        Box(Modifier.width(HourColumn).height(18.dp), contentAlignment = Alignment.Center) {
                            when (mode) {
                                HourlyMode.Temperature -> {
                                    val chance = hour.precipitationProbability?.roundToInt() ?: 0
                                    if (chance >= 20) Text("$chance%", style = MaterialTheme.typography.labelMedium, color = SkyText.Rain)
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
                                        contentDescription = "Wind from ${Units.compass(direction)}",
                                        tint = SkyText.Secondary,
                                        modifier = Modifier.size(14.dp).rotate((direction + 180).toFloat()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (mode == HourlyMode.Wind) {
            Text(
                "Wind speed in ${units.wind.label}",
                style = MaterialTheme.typography.labelSmall,
                color = SkyText.Tertiary,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.24f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else SkyText.Tertiary)
    }
}

/** A smooth line (or bars) with a value label above each hour. */
@Composable
private fun HourlyChart(
    values: List<Double?>,
    labels: List<String>,
    lineColors: List<Color>,
    bars: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.Medium)
    Canvas(modifier) {
        val count = values.size
        if (count == 0) return@Canvas
        val grow = progress().coerceIn(0f, 1f)
        val labelAlpha = ((grow - 0.5f) * 2f).coerceIn(0f, 1f)
        val column = size.width / count
        val top = 24.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas

        if (bars) {
            values.forEachIndexed { i, value ->
                val fraction = ((value ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                val height = (bottom - top) * fraction * grow
                val x = column * i + column * 0.28f
                drawRoundRect(
                    color = Color(0xFF6CC7FF).copy(alpha = 0.35f + 0.6f * fraction),
                    topLeft = Offset(x, bottom - height.coerceAtLeast(2.dp.toPx())),
                    size = Size(column * 0.44f, height.coerceAtLeast(2.dp.toPx())),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                if (fraction > 0f) {
                    val layout = measurer.measure(labels[i], labelStyle.copy(color = SkyText.Rain))
                    drawText(layout, topLeft = Offset(column * (i + 0.5f) - layout.size.width / 2f, bottom - height - layout.size.height - 2.dp.toPx()), alpha = labelAlpha)
                }
            }
            return@Canvas
        }

        val min = known.min()
        val max = known.max()
        val range = (max - min).coerceAtLeast(1.0)
        // The line rises from the baseline as the card comes in.
        val points = values.mapIndexed { i, value ->
            value?.let { Offset(column * (i + 0.5f), bottom - ((it - min) / range).toFloat() * (bottom - top - 8.dp.toPx()) * grow) }
        }
        val line = Path()
        var previous: Offset? = null
        var first: Offset? = null
        var last: Offset? = null
        points.forEach { point ->
            if (point == null) {
                previous = null
                return@forEach
            }
            val p = previous
            if (p == null) {
                line.moveTo(point.x, point.y)
                if (first == null) first = point
            } else {
                val mid = (point.x - p.x) / 2f
                line.cubicTo(p.x + mid, p.y, point.x - mid, point.y, point.x, point.y)
            }
            previous = point
            last = point
        }
        val start = first
        val end = last
        if (start != null && end != null) {
            val fill = Path().apply {
                addPath(line)
                lineTo(end.x, size.height)
                lineTo(start.x, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), startY = top, endY = size.height))
            val stroke = if (lineColors.size >= 2) {
                Brush.horizontalGradient(lineColors, startX = start.x, endX = end.x)
            } else {
                Brush.linearGradient(listOf(Color.White, Color.White))
            }
            drawPath(line, stroke, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        points.forEachIndexed { i, point ->
            if (point == null) return@forEachIndexed
            drawCircle(Color.White, radius = 3.dp.toPx(), center = point)
            val layout = measurer.measure(labels[i], labelStyle)
            drawText(layout, topLeft = Offset(point.x - layout.size.width / 2f, point.y - layout.size.height - 4.dp.toPx()), alpha = labelAlpha)
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

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
        CardLabel(Icons.Rounded.DateRange, "${days.size}-day forecast", Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        days.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.08f))
            DayRow(
                index = index,
                day = day,
                label = text.dayLabel(LocalDate.parse(day.date), today),
                currentTemperature = if (index == 0) now.temperature else null,
                weekMin = weekMin,
                weekMax = weekMax,
                expanded = expanded == day.date,
                settings = settings,
                text = text,
                onClick = { expanded = if (expanded == day.date) null else day.date },
            )
        }
    }
}

@Composable
private fun DayRow(
    index: Int,
    day: DailyPoint,
    label: String,
    currentTemperature: Double?,
    weekMin: Double,
    weekMax: Double,
    expanded: Boolean,
    settings: AppSettings,
    text: TimeText,
    onClick: () -> Unit,
) {
    val units = settings.units
    val grow by rememberAppearProgress(durationMillis = 750, extraDelayMillis = index * 40)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.width(64.dp), maxLines = 1)
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WeatherIcon(day.weatherCode, isDay = true, size = 28.dp)
                val chance = day.precipitationProbabilityMax?.roundToInt() ?: 0
                if (chance >= 20) Text("$chance%", style = MaterialTheme.typography.labelSmall, color = SkyText.Rain)
            }
            Text(
                Units.formatTemperature(day.temperatureMin, units.temperature),
                style = MaterialTheme.typography.titleMedium,
                color = SkyText.Tertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            RangeBar(day.temperatureMin, day.temperatureMax, weekMin, weekMax, currentTemperature, { grow }, Modifier.weight(1f).height(6.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                Units.formatTemperature(day.temperatureMax, units.temperature),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp),
            )
        }
        if (expanded) DayDetails(day, settings, text)
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
private fun DayDetails(day: DailyPoint, settings: AppSettings, text: TimeText) {
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
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        stats.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { (name, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.labelSmall, color = SkyText.Tertiary)
                        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
