package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.domain.PressureTrend
import io.github.duddmeister.weather.domain.Scales
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.pressureTrend
import io.github.duddmeister.weather.domain.upcomingHours
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.HourlyPoint
import io.github.duddmeister.weather.model.UnitPrefs
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.components.temperatureColor
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlin.math.roundToInt

/** A detail that opens into a chart of the next 24 hours. */
enum class Metric { Uv, Wind, Rainfall, FeelsLike, Humidity, Visibility, Pressure, Clouds }

private val SheetColor = Color(0xFF111B36)
private val RainBlue = Color(0xFF6CC7FF)

/** One line (or set of bars) on the chart; values are in the forecast's own metric units. */
private class Line(val values: List<Double?>, val name: String, val colourOf: (Double) -> Color)

/** Everything the sheet shows about a [Metric]. */
private class MetricDetails(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val level: String?,
    val levelColour: Color,
    val summary: String,
    val times: List<Long>,
    val main: Line,
    /** A second, dashed line for comparison (gusts against wind, the thermometer against how it feels). */
    val other: Line?,
    val bars: Boolean,
    /** The bottom and top of the scale, where the data shouldn't decide them. */
    val floor: Double?,
    val ceiling: Double?,
    val format: (Double) -> String,
    val stats: List<Pair<String, String>>,
    val about: String,
)

@Composable
fun MetricSheet(
    metric: Metric,
    snapshot: WeatherSnapshot,
    now: NowConditions,
    nowMillis: Long,
    settings: AppSettings,
    use24h: Boolean,
    onDismiss: () -> Unit,
) {
    val text = remember(snapshot.timezone, use24h) { TimeText(snapshot.zoneId(), use24h) }
    val details = remember(metric, snapshot, nowMillis / 300_000, settings.units, text) {
        metricDetails(metric, snapshot, now, nowMillis, settings.units, text)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = SheetColor,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    Icon(details.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(details.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("The next 24 hours", style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(details.value, style = MaterialTheme.typography.displaySmall, color = Color.White)
                if (details.level != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(details.level, style = MaterialTheme.typography.titleMedium, color = details.levelColour, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            if (details.summary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(details.summary, style = MaterialTheme.typography.bodyMedium, color = SkyText.Secondary)
            }
            Spacer(Modifier.height(18.dp))
            if (details.main.values.count { it != null } >= 4) {
                MetricChart(details, text, Modifier.fillMaxWidth().height(190.dp))
                if (details.other != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(details.main.name, dashed = false)
                        Spacer(Modifier.width(18.dp))
                        Legend(details.other.name, dashed = true)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            StatGrid(details.stats, columns = 2)
            Spacer(Modifier.height(12.dp))
            Text(details.about, style = MaterialTheme.typography.bodySmall, color = SkyText.Tertiary)
        }
    }
}

@Composable
private fun Legend(name: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier.width(18.dp).height(3.dp).drawWithCache {
                val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null
                onDrawBehind {
                    drawLine(
                        Color.White.copy(alpha = if (dashed) 0.6f else 1f),
                        Offset(0f, size.height / 2f),
                        Offset(size.width, size.height / 2f),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round,
                        pathEffect = effect,
                    )
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = SkyText.Secondary)
    }
}

/** The chart: a scale on the left, hours along the bottom, and the line drawing itself in from now. */
@Composable
private fun MetricChart(details: MetricDetails, text: TimeText, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = SkyText.Tertiary)
    val peakStyle = MaterialTheme.typography.labelLarge.copy(color = Color.White)
    val density = LocalDensity.current
    val known = (details.main.values + details.other?.values.orEmpty()).filterNotNull()
    val low = details.floor ?: known.min()
    val high = (details.ceiling ?: known.max()).coerceAtLeast(low)
    // A little air above and below, unless the scale has fixed ends. A value that never changes
    // still gets a band to sit in, so its line runs through the middle rather than along an edge.
    val pad = maxOf((high - low) * 0.12, 0.5)
    val bottomValue = if (details.floor != null) low else low - pad
    val topValue = if (details.ceiling != null) high.coerceAtLeast(bottomValue + pad) else high + pad
    val scale = remember(details, axisStyle, density) {
        listOf(topValue, (topValue + bottomValue) / 2, bottomValue).map { it to measurer.measure(details.format(it), axisStyle) }
    }
    val hours = remember(details, axisStyle, density, text) {
        details.times.mapIndexedNotNull { i, time ->
            when {
                i == 0 -> i to measurer.measure("Now", axisStyle)
                i % 6 == 0 -> i to measurer.measure(text.hour(time), axisStyle)
                else -> null
            }
        }
    }
    val peakIndex = details.main.values.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index
    val peak = remember(details, peakStyle, density) {
        peakIndex?.let { measurer.measure(details.format(details.main.values[it]!!), peakStyle) }
    }
    val draw = remember(details) { Animatable(0f) }
    LaunchedEffect(details) { draw.animateTo(1f, tween(900, delayMillis = 250, easing = FastOutSlowInEasing)) }
    val description = "Chart of ${details.title.lowercase()} over the next 24 hours. ${details.stats.joinToString(". ") { "${it.first} ${it.second}" }}"

    Spacer(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .drawWithCache {
                val gutter = scale.maxOf { it.second.size.width } + 10.dp.toPx()
                val axis = 22.dp.toPx()
                val top = 22.dp.toPx()
                val bottom = size.height - axis
                val left = gutter
                val right = size.width - 6.dp.toPx()
                val count = details.times.size
                fun x(i: Int) = left + (right - left) * (if (count <= 1) 0f else i / (count - 1f))
                fun y(value: Double) = bottom - ((value - bottomValue) / (topValue - bottomValue)).toFloat().coerceIn(0f, 1f) * (bottom - top)

                fun path(values: List<Double?>): Path = Path().apply {
                    var previous = Offset.Unspecified
                    values.forEachIndexed { i, value ->
                        if (value == null) {
                            previous = Offset.Unspecified
                            return@forEachIndexed
                        }
                        val point = Offset(x(i), y(value))
                        if (previous == Offset.Unspecified) {
                            moveTo(point.x, point.y)
                        } else {
                            val mid = (point.x - previous.x) / 2f
                            cubicTo(previous.x + mid, previous.y, point.x - mid, point.y, point.x, point.y)
                        }
                        previous = point
                    }
                }

                val main = path(details.main.values)
                val other = details.other?.let { path(it.values) }
                val firstIndex = details.main.values.indexOfFirst { it != null }
                val lastIndex = details.main.values.indexOfLast { it != null }
                val fill = Path().apply {
                    if (firstIndex >= 0) {
                        addPath(main)
                        lineTo(x(lastIndex), bottom)
                        lineTo(x(firstIndex), bottom)
                        close()
                    }
                }
                val colours = details.main.values.map { value -> details.main.colourOf(value ?: low) }
                val stroke = if (colours.size >= 2) Brush.horizontalGradient(colours, startX = left, endX = right) else Brush.linearGradient(listOf(Color.White, Color.White))
                val wash = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), startY = top, endY = bottom)
                val dashes = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                val grid = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
                val slot = (right - left) / count.coerceAtLeast(1)

                onDrawBehind {
                    val shown = draw.value
                    scale.forEach { (value, label) ->
                        val lineY = y(value)
                        drawLine(Color.White.copy(alpha = 0.14f), Offset(left, lineY), Offset(right, lineY), strokeWidth = 1.dp.toPx(), pathEffect = grid)
                        drawText(label, topLeft = Offset(0f, (lineY - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
                    }
                    hours.forEach { (i, label) ->
                        drawText(label, topLeft = Offset((x(i) - label.size.width / 2f).coerceIn(left - 4.dp.toPx(), size.width - label.size.width), bottom + 6.dp.toPx()))
                    }
                    if (details.bars) {
                        details.main.values.forEachIndexed { i, value ->
                            val height = if (value == null || value <= 0.0) 2.dp.toPx() else (bottom - y(value)).coerceAtLeast(3.dp.toPx())
                            val rise = (shown * 1.5f - 0.5f * i / count).coerceIn(0f, 1f)
                            drawRoundRect(
                                details.main.colourOf(value ?: 0.0),
                                topLeft = Offset(x(i) - slot * 0.3f, bottom - height * rise),
                                size = Size(slot * 0.6f, height * rise),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                                alpha = if (value != null && value > 0.0) 0.95f else 0.25f,
                            )
                        }
                    } else {
                        clipRect(right = left + (right - left) * shown + 4.dp.toPx()) {
                            drawPath(fill, wash)
                            if (other != null) {
                                drawPath(other, Color.White.copy(alpha = 0.55f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes))
                            }
                            drawPath(main, stroke, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    // The moment it peaks, once the line has reached it.
                    if (peakIndex != null && peak != null) {
                        val value = details.main.values[peakIndex]!!
                        val reached = ((shown * (count - 1) - peakIndex) / 2f + 1f).coerceIn(0f, 1f)
                        val point = Offset(x(peakIndex), y(value))
                        if (!details.bars) {
                            drawCircle(SheetColor, radius = 6.dp.toPx() * reached, center = point)
                            drawCircle(Color.White, radius = 4.dp.toPx() * reached, center = point)
                        }
                        drawText(
                            peak,
                            topLeft = Offset((point.x - peak.size.width / 2f).coerceIn(left, size.width - peak.size.width), (point.y - peak.size.height - 8.dp.toPx()).coerceAtLeast(0f)),
                            alpha = reached,
                        )
                    }
                }
            },
    )
}

private fun metricDetails(
    metric: Metric,
    snapshot: WeatherSnapshot,
    now: NowConditions,
    nowMillis: Long,
    units: UnitPrefs,
    text: TimeText,
): MetricDetails {
    val hours = snapshot.upcomingHours(nowMillis, 25)
    val times = hours.map { it.time }
    val white: (Double) -> Color = { Color.White }

    /** The forecast for each hour, starting from what it is right now. */
    fun series(nowValue: Double?, of: (HourlyPoint) -> Double?) = hours.mapIndexed { i, hour -> if (i == 0) nowValue ?: of(hour) else of(hour) }

    fun at(index: Int) = times.getOrNull(index)?.let { text.hour(it) } ?: "--"
    fun highest(values: List<Double?>) = values.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }
    fun lowest(values: List<Double?>) = values.withIndex().filter { it.value != null }.minByOrNull { it.value!! }
    fun whenIs(entry: IndexedValue<Double?>?) = entry?.let { if (it.index == 0) "now" else "at ${at(it.index)}" } ?: ""

    return when (metric) {
        Metric.Uv -> {
            val values = series(now.uvIndex) { it.uvIndex }
            val level = Scales.uv(now.uvIndex ?: 0.0)
            val peak = highest(values)
            // The first stretch of the day that's strong enough to burn.
            val riskyFrom = values.indexOfFirst { (it ?: 0.0) >= 3 }
            val riskyTo = if (riskyFrom < 0) -1 else (riskyFrom until values.size).takeWhile { (values[it] ?: 0.0) >= 3 }.last()
            MetricDetails(
                icon = Icons.Rounded.WbSunny,
                title = "UV index",
                value = (now.uvIndex ?: 0.0).roundToInt().toString(),
                level = level.label,
                levelColour = Color(level.color),
                summary = level.advice + ".",
                times = times,
                main = Line(values, "UV index") { Color(Scales.uv(it).color) },
                other = null,
                bars = false,
                floor = 0.0,
                ceiling = maxOf(8.0, (peak?.value ?: 0.0) + 1),
                format = { it.roundToInt().toString() },
                stats = listOf(
                    "Peak" to (peak?.value?.let { "${it.roundToInt()} · ${Scales.uv(it).label} ${whenIs(peak)}" } ?: "--"),
                    "Sun protection" to when {
                        riskyFrom < 0 -> "Not needed"
                        riskyFrom == 0 -> "Until ${at(riskyTo)}"
                        else -> "${at(riskyFrom)} – ${at(riskyTo)}"
                    },
                ),
                about = "The UV index is the strength of the sun's burning rays. From 3 up, skin can burn; cover up, wear sunscreen and look for shade around midday.",
            )
        }
        Metric.Wind -> {
            val speed = series(now.windSpeed) { it.windSpeed }
            val gusts = series(now.windGusts) { it.windGusts }
            val strongest = highest(speed)
            val gustiest = highest(gusts)
            MetricDetails(
                icon = Icons.Rounded.Air,
                title = "Wind",
                value = Units.formatWind(now.windSpeed, units.wind),
                level = Units.compass(now.windDirection).takeIf { it.isNotBlank() }?.let { "from the $it" },
                levelColour = SkyText.Secondary,
                summary = now.windSpeed?.let { Units.beaufortDescription(it) + " (force ${Units.beaufort(it)})." } ?: "",
                times = times,
                main = Line(speed, "Wind speed", white),
                other = Line(gusts, "Gusts", white),
                bars = false,
                floor = 0.0,
                ceiling = null,
                format = { Units.formatWind(it, units.wind, withUnit = false) },
                stats = listOf(
                    "Strongest" to (strongest?.value?.let { "${Units.formatWind(it, units.wind)} ${whenIs(strongest)}" } ?: "--"),
                    "Gusts up to" to (gustiest?.value?.let { "${Units.formatWind(it, units.wind)} ${whenIs(gustiest)}" } ?: "--"),
                ),
                about = "Wind speed is the average over ten minutes, ten metres above the ground. Gusts are the brief bursts on top of it.",
            )
        }
        Metric.Rainfall -> {
            val amounts = hours.map { it.precipitation }
            val total = amounts.drop(1).sumOf { it ?: 0.0 }
            val wettest = highest(amounts)?.takeIf { (it.value ?: 0.0) > 0.0 }
            val likeliest = highest(hours.map { it.precipitationProbability })
            MetricDetails(
                icon = Icons.Rounded.Umbrella,
                title = "Rainfall",
                value = Units.formatPrecipitation(total, units.precipitation),
                level = "expected",
                levelColour = SkyText.Secondary,
                summary = if (total < 0.1) "Dry for the next 24 hours." else "Rain or melted snow, hour by hour.",
                times = times,
                main = Line(amounts, "Rainfall") { RainBlue },
                other = null,
                bars = true,
                floor = 0.0,
                ceiling = maxOf(2.0, (amounts.filterNotNull().maxOrNull() ?: 0.0) * 1.25),
                format = { Units.formatPrecipitation(it, units.precipitation).substringBefore(' ') },
                stats = listOf(
                    "Wettest hour" to (wettest?.let { "${Units.formatPrecipitation(it.value, units.precipitation)} ${whenIs(wettest)}" } ?: "None"),
                    "Chance peaks at" to (likeliest?.value?.let { "${it.roundToInt()}% ${whenIs(likeliest)}" } ?: "--"),
                ),
                about = "Amounts are the depth of water that falls in each hour, in ${units.precipitation.label}. Snow counts as the water it melts to.",
            )
        }
        Metric.FeelsLike -> {
            val feels = series(now.apparentTemperature) { it.apparentTemperature }
            val actual = series(now.temperature) { it.temperature }
            val warmest = highest(feels)
            val coldest = lowest(feels)
            val delta = now.apparentTemperature?.let { Units.temperatureDelta(it - now.temperature, units.temperature) } ?: 0
            MetricDetails(
                icon = Icons.Rounded.Thermostat,
                title = "Feels like",
                value = Units.formatTemperature(now.apparentTemperature, units.temperature),
                level = "actual ${Units.formatTemperature(now.temperature, units.temperature)}",
                levelColour = SkyText.Secondary,
                summary = when {
                    delta <= -1 -> "${-delta}° colder than the thermometer says."
                    delta >= 1 -> "$delta° warmer than the thermometer says."
                    else -> "About what the thermometer says."
                },
                times = times,
                main = Line(feels, "Feels like") { temperatureColor(it) },
                other = Line(actual, "Actual", white),
                bars = false,
                floor = null,
                ceiling = null,
                format = { Units.formatTemperature(it, units.temperature) },
                stats = listOf(
                    "Warmest" to (warmest?.value?.let { "${Units.formatTemperature(it, units.temperature)} ${whenIs(warmest)}" } ?: "--"),
                    "Coldest" to (coldest?.value?.let { "${Units.formatTemperature(it, units.temperature)} ${whenIs(coldest)}" } ?: "--"),
                ),
                about = "How warm it feels to a person in the shade: wind strips heat away and makes it feel colder, humidity holds it in and makes it feel warmer.",
            )
        }
        Metric.Humidity -> {
            val values = series(now.humidity) { it.humidity }
            val dampest = highest(values)
            val driest = lowest(values)
            MetricDetails(
                icon = Icons.Rounded.WaterDrop,
                title = "Humidity",
                value = Units.formatPercent(now.humidity),
                level = now.humidity?.let { Scales.humidityDescription(it, now.dewPoint) },
                levelColour = SkyText.Secondary,
                summary = now.dewPoint?.let { "The dew point is ${Units.formatTemperature(it, units.temperature)}." } ?: "",
                times = times,
                main = Line(values, "Humidity") { RainBlue },
                other = null,
                bars = false,
                floor = 0.0,
                ceiling = 100.0,
                format = { "${it.roundToInt()}%" },
                stats = listOf(
                    "Most humid" to (dampest?.value?.let { "${it.roundToInt()}% ${whenIs(dampest)}" } ?: "--"),
                    "Driest" to (driest?.value?.let { "${it.roundToInt()}% ${whenIs(driest)}" } ?: "--"),
                ),
                about = "Relative humidity is how much moisture the air holds compared with the most it could at this temperature. The dew point says how muggy it feels: above 16° is humid, above 21° oppressive.",
            )
        }
        Metric.Visibility -> {
            val values = series(now.visibility) { it.visibility }
            val worst = lowest(values)
            val best = highest(values)
            MetricDetails(
                icon = Icons.Rounded.Visibility,
                title = "Visibility",
                value = Units.formatDistance(now.visibility, units.distance),
                level = null,
                levelColour = SkyText.Secondary,
                summary = now.visibility?.let { Scales.visibilityDescription(it) + "." } ?: "",
                times = times,
                main = Line(values, "Visibility", white),
                other = null,
                bars = false,
                floor = 0.0,
                ceiling = null,
                format = { Units.formatDistance(it, units.distance) },
                stats = listOf(
                    "Poorest" to (worst?.value?.let { "${Units.formatDistance(it, units.distance)} ${whenIs(worst)}" } ?: "--"),
                    "Clearest" to (best?.value?.let { "${Units.formatDistance(it, units.distance)} ${whenIs(best)}" } ?: "--"),
                ),
                about = "How far you can see clearly. Fog, mist, heavy rain and snow all shorten it; under a kilometre counts as fog.",
            )
        }
        Metric.Pressure -> {
            val values = series(now.pressure) { it.pressure }
            val top = highest(values)
            val bottom = lowest(values)
            val trend = snapshot.pressureTrend(nowMillis)
            MetricDetails(
                icon = Icons.Rounded.Compress,
                title = "Pressure",
                value = Units.formatPressure(now.pressure, units.pressure),
                level = trend?.label,
                levelColour = SkyText.Secondary,
                summary = when (trend) {
                    PressureTrend.Rising -> "Rising over the last three hours: usually a sign of settling weather."
                    PressureTrend.Falling -> "Falling over the last three hours: often a sign of rain or wind on the way."
                    PressureTrend.Steady -> "Steady over the last three hours."
                    null -> ""
                },
                times = times,
                main = Line(values, "Pressure", white),
                other = null,
                bars = false,
                floor = null,
                ceiling = null,
                format = { Units.formatPressure(it, units.pressure).substringBefore(' ') },
                stats = listOf(
                    "Highest" to (top?.value?.let { "${Units.formatPressure(it, units.pressure)} ${whenIs(top)}" } ?: "--"),
                    "Lowest" to (bottom?.value?.let { "${Units.formatPressure(it, units.pressure)} ${whenIs(bottom)}" } ?: "--"),
                ),
                about = "The weight of the air above, adjusted to sea level. Around 1013 hPa is average; highs bring calm, clear weather and lows bring cloud, wind and rain.",
            )
        }
        Metric.Clouds -> {
            val values = series(now.cloudCover) { it.cloudCover }
            val clearest = lowest(values)
            val cloudiest = highest(values)
            MetricDetails(
                icon = Icons.Rounded.Cloud,
                title = "Cloud cover",
                value = Units.formatPercent(now.cloudCover),
                level = now.cloudCover?.let { Scales.cloudDescription(it) },
                levelColour = SkyText.Secondary,
                summary = "",
                times = times,
                main = Line(values, "Cloud cover", white),
                other = null,
                bars = false,
                floor = 0.0,
                ceiling = 100.0,
                format = { "${it.roundToInt()}%" },
                stats = listOf(
                    "Clearest" to (clearest?.value?.let { "${it.roundToInt()}% ${whenIs(clearest)}" } ?: "--"),
                    "Cloudiest" to (cloudiest?.value?.let { "${it.roundToInt()}% ${whenIs(cloudiest)}" } ?: "--"),
                ),
                about = "The share of the sky covered by cloud at any height.",
            )
        }
    }
}
