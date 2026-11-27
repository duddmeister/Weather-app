package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.domain.Astronomy
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.domain.PressureTrend
import io.github.duddmeister.weather.domain.Scales
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.precipitationLast24h
import io.github.duddmeister.weather.domain.precipitationNext24h
import io.github.duddmeister.weather.domain.pressureTrend
import io.github.duddmeister.weather.domain.today
import io.github.duddmeister.weather.domain.tomorrow
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.AirQuality
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.components.CardLabel
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.Reveal
import io.github.duddmeister.weather.ui.components.RevealState
import io.github.duddmeister.weather.ui.components.rememberAppearProgress
import io.github.duddmeister.weather.ui.components.temperatureColor
import io.github.duddmeister.weather.ui.sky.moonLitPath
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val TileHeight = 176.dp
private val RainBlue = Color(0xFF6CC7FF)

/** Adds the "details" tiles to the page, two per row. */
fun LazyListScope.detailRows(
    snapshot: WeatherSnapshot,
    now: NowConditions,
    nowMillis: Long,
    settings: AppSettings,
    use24h: Boolean,
    location: SavedLocation,
    reveal: RevealState,
    firstOrder: Int,
    onOpen: (Metric) -> Unit,
) {
    var order = firstOrder
    snapshot.airQuality?.let { air ->
        val european = Scales.useEuropean(settings.aqiScale, location.countryCode)
        val value = if (european) air.europeanAqi else air.usAqi
        if (value != null) {
            val o = order++
            item(key = "air") { Reveal(reveal, "air", o, Modifier.animateItem()) { AirQualityTile(air, value, european) } }
        }
    }
    val tiles = listOf(
        TileSpec("uv") { m -> UvTile(snapshot, now, nowMillis, use24h, m) { onOpen(Metric.Uv) } },
        TileSpec("sun") { m -> SunTile(snapshot, nowMillis, use24h, m) },
        TileSpec("wind") { m -> WindTile(now, settings, m) { onOpen(Metric.Wind) } },
        TileSpec("precipitation") { m -> PrecipitationTile(snapshot, nowMillis, settings, m) { onOpen(Metric.Rainfall) } },
        TileSpec("feels") { m -> FeelsLikeTile(now, settings, m) { onOpen(Metric.FeelsLike) } },
        TileSpec("humidity") { m -> HumidityTile(now, settings, m) { onOpen(Metric.Humidity) } },
        TileSpec("visibility") { m -> VisibilityTile(now, settings, m) { onOpen(Metric.Visibility) } },
        TileSpec("pressure") { m -> PressureTile(snapshot, now, nowMillis, settings, m) { onOpen(Metric.Pressure) } },
        TileSpec("moon") { m -> MoonTile(nowMillis, m) },
        TileSpec("clouds") { m -> CloudTile(now, m) { onOpen(Metric.Clouds) } },
    )
    tiles.chunked(2).forEach { row ->
        val key = "tiles-" + row.joinToString("-") { it.key }
        val o = order++
        item(key = key) {
            Reveal(reveal, key, o, Modifier.animateItem()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> tile.content(Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private class TileSpec(val key: String, val content: @Composable (Modifier) -> Unit)

/** A tile of one detail. With [onClick] it opens that detail's next 24 hours, and says so with a chevron. */
@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Larger system text gets a taller tile, so captions still fit.
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    GlassCard(modifier = modifier.height(TileHeight * fontScale), onClick = onClick, onClickLabel = "Show the next 24 hours") {
        CardLabel(icon, label) {
            if (onClick != null) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SkyText.Faint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun BigValue(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 1)
}

@Composable
private fun Subtitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary, modifier = modifier, maxLines = maxLines)
}

/** A coloured scale with a marker that slides to [fraction] when the tile comes in. */
@Composable
private fun ScaleBar(fraction: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val appear by rememberAppearProgress(durationMillis = 1100)
    val value by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "scale")
    Canvas(modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics { }) {
        drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(size.height / 2))
        val x = (value * appear * size.width).coerceIn(size.height / 2, size.width - size.height / 2)
        drawCircle(Color(0xFF1B2440), radius = size.height * 0.95f, center = Offset(x, size.height / 2))
        drawCircle(Color.White, radius = size.height * 0.62f, center = Offset(x, size.height / 2))
    }
}

private val UvColors = listOf(Color(0xFF66BB6A), Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFF4511E), Color(0xFFAB47BC))

@Composable
private fun UvTile(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, use24h: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val uv = now.uvIndex ?: 0.0
    val level = Scales.uv(uv)
    val caption = remember(snapshot, nowMillis / 600_000, use24h) {
        val text = TimeText(snapshot.zoneId(), use24h)
        val nowSec = nowMillis / 1000
        val sunset = snapshot.today(nowMillis)?.sunset
        val dayHours = snapshot.hourly.filter { it.time >= nowSec && (sunset == null || it.time <= sunset) }
        val protectUntil = if (uv >= 3) dayHours.takeWhile { (it.uvIndex ?: 0.0) >= 3 }.lastOrNull()?.time else null
        val peak = dayHours.maxByOrNull { it.uvIndex ?: 0.0 }
        when {
            protectUntil != null -> "Use sun protection until ${text.hour(protectUntil)}."
            peak != null && (peak.uvIndex ?: 0.0) >= 3 -> "Peaks at ${(peak.uvIndex ?: 0.0).roundToInt()} around ${text.hour(peak.time)}."
            else -> level.advice + "."
        }
    }
    Tile(Icons.Rounded.WbSunny, "UV index", modifier, onClick) {
        BigValue(uv.roundToInt().toString())
        Subtitle(level.label)
        Spacer(Modifier.height(10.dp))
        ScaleBar(level.fraction, UvColors)
        Spacer(Modifier.weight(1f))
        Caption(caption)
    }
}

@Composable
private fun SunTile(snapshot: WeatherSnapshot, nowMillis: Long, use24h: Boolean, modifier: Modifier) {
    val text = remember(snapshot.timezone, use24h) { TimeText(snapshot.zoneId(), use24h) }
    val today = snapshot.today(nowMillis)
    val tomorrow = snapshot.tomorrow(nowMillis)
    val nowSec = nowMillis / 1000
    val sunrise = today?.sunrise
    val sunset = today?.sunset
    val daytime = sunrise != null && sunset != null && nowSec in sunrise until sunset
    val label = if (daytime) "Sunset" else "Sunrise"
    val nextEvent = when {
        daytime -> sunset
        sunrise != null && nowSec < sunrise -> sunrise
        else -> tomorrow?.sunrise
    }
    Tile(Icons.Rounded.WbTwilight, label, modifier) {
        BigValue(nextEvent?.let { text.time(it) } ?: "--")
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (sunrise != null && sunset != null) {
                val appear by rememberAppearProgress(durationMillis = 1400)
                SunArc(Astronomy.sunProgress(nowSec, sunrise, sunset).toFloat()) { appear }
            }
        }
        Caption(
            when {
                daytime && sunrise != null -> "Sunrise ${text.time(sunrise)}"
                sunset != null && nowSec >= sunset -> "Sunset ${text.time(sunset)}"
                else -> today?.daylightDuration?.let { TimeText.hoursMinutes(it) + " of daylight" } ?: ""
            },
            maxLines = 1,
        )
    }
}

/** The sun's path over the horizon: travelled so far in gold, still to come as a dotted line. */
@Composable
private fun BoxScope.SunArc(progress: Float, appear: () -> Float) {
    Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
        val horizon = size.height * 0.80f
        val radiusX = size.width * 0.44f
        val radiusY = size.height * 0.64f
        val center = Offset(size.width / 2f, horizon)
        fun at(fraction: Float): Offset {
            val angle = PI * fraction
            return Offset(center.x - radiusX * cos(angle).toFloat(), center.y - radiusY * sin(angle).toFloat())
        }
        val arc = Path()
        val steps = 48
        for (i in 0..steps) {
            val point = at(i / steps.toFloat())
            if (i == 0) arc.moveTo(point.x, point.y) else arc.lineTo(point.x, point.y)
        }
        drawPath(
            arc,
            Color.White.copy(alpha = 0.34f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 6.dp.toPx()))),
        )
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.4f), Color.Transparent)),
            Offset(0f, horizon),
            Offset(size.width, horizon),
            strokeWidth = 1.dp.toPx(),
        )
        if (progress in 0f..1f) {
            val shown = progress * appear()
            val travelled = Path()
            PathMeasure().apply { setPath(arc, false) }.let { it.getSegment(0f, it.length * shown, travelled, true) }
            drawPath(
                travelled,
                Brush.horizontalGradient(listOf(Color(0x66FFD54F), Color(0xFFFFD54F)), startX = at(0f).x, endX = at(shown).x.coerceAtLeast(at(0f).x + 1f)),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            val sun = at(shown)
            drawCircle(Brush.radialGradient(listOf(Color(0x99FFE082), Color.Transparent), center = sun, radius = 18.dp.toPx()), radius = 18.dp.toPx(), center = sun)
            drawCircle(Color(0xFFFFD54F), radius = 5.5.dp.toPx(), center = sun)
        } else {
            // After dark, both ends of tomorrow's path wait on the horizon.
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 2.5.dp.toPx(), center = at(0f))
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 2.5.dp.toPx(), center = at(1f))
        }
    }
}

@Composable
private fun WindTile(now: NowConditions, settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val units = settings.units
    val speed = now.windSpeed
    Tile(Icons.Rounded.Air, "Wind", modifier, onClick) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BigValue(Units.formatWind(speed, units.wind, withUnit = false))
                Subtitle(units.wind.label)
                Text(Units.compass(now.windDirection), style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
            }
            Compass(now.windDirection, speed ?: 0.0, Modifier.size(74.dp))
        }
        Caption(
            listOfNotNull(
                now.windGusts?.let { "Gusts ${Units.formatWind(it, units.wind)}" },
                speed?.let { Units.beaufortDescription(it) },
            ).joinToString(" · "),
            maxLines = 2,
        )
    }
}

/** A compass whose arrow points where the wind is going, and trembles more the harder it blows. */
@Composable
private fun Compass(directionFrom: Double?, speedKmh: Double, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = SkyText.Tertiary)
    val swing by rememberAppearProgress(durationMillis = 1300)
    val sway by rememberInfiniteTransition(label = "wind").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val tremble = (speedKmh / 9.0).coerceIn(0.0, 6.0).toFloat()
    Canvas(modifier.clearAndSetSemantics { }) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.White.copy(alpha = 0.07f), radius = radius - 1.dp.toPx(), center = center)
        drawCircle(Color.White.copy(alpha = 0.22f), radius = radius - 1.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        for (i in 0 until 36) {
            rotate(i * 10f, center) {
                val long = i % 9 == 0
                drawLine(
                    Color.White.copy(alpha = if (long) 0.75f else 0.25f),
                    Offset(center.x, center.y - radius + 2.dp.toPx()),
                    Offset(center.x, center.y - radius + (if (long) 8 else 5).dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (letter, angle) ->
            val layout = measurer.measure(letter, style)
            val r = radius - 16.dp.toPx()
            val rad = Math.toRadians(angle.toDouble() - 90)
            val p = Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        if (directionFrom != null) {
            rotate((directionFrom.toFloat() + 180f) * swing + sway * tremble, center) {
                val tip = Offset(center.x, center.y - radius * 0.62f)
                val tail = Offset(center.x, center.y + radius * 0.5f)
                drawLine(
                    Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.45f)), startY = tip.y, endY = tail.y),
                    tail,
                    tip,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 4.dp.toPx())
                    lineTo(tip.x - 6.dp.toPx(), tip.y + 7.dp.toPx())
                    lineTo(tip.x + 6.dp.toPx(), tip.y + 7.dp.toPx())
                    close()
                }
                drawPath(head, Color.White)
                drawCircle(Color.White.copy(alpha = 0.6f), radius = 3.dp.toPx(), center = tail)
            }
        }
    }
}

@Composable
private fun PrecipitationTile(snapshot: WeatherSnapshot, nowMillis: Long, settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val units = settings.units
    val last = remember(snapshot, nowMillis / 600_000) { snapshot.precipitationLast24h(nowMillis) }
    val next = remember(snapshot, nowMillis / 600_000) { snapshot.precipitationNext24h(nowMillis) }
    val coming = remember(snapshot, nowMillis / 600_000) {
        val nowSec = nowMillis / 1000
        snapshot.hourly.filter { it.time > nowSec && it.time <= nowSec + 24 * 3600 }.map { (it.precipitation ?: 0.0).toFloat() }
    }
    Tile(Icons.Rounded.Umbrella, "Rainfall", modifier, onClick) {
        BigValue(Units.formatPrecipitation(last ?: 0.0, units.precipitation))
        Text("in the last 24h", style = MaterialTheme.typography.labelSmall, color = SkyText.Secondary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (coming.size >= 12) {
            val grow by rememberAppearProgress(durationMillis = 900)
            // The next 24 hours, an hour to a bar.
            Canvas(Modifier.fillMaxWidth().height(26.dp).clearAndSetSemantics { }) {
                val slot = size.width / coming.size
                val top = max(coming.max(), 2f)
                coming.forEachIndexed { i, mm ->
                    val rise = (grow * 1.5f - 0.5f * i / coming.size).coerceIn(0f, 1f)
                    val height = (if (mm > 0f) (size.height * min(mm / top, 1f)).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()) * rise
                    drawRoundRect(
                        RainBlue,
                        topLeft = Offset(i * slot + slot * 0.18f, size.height - height),
                        size = Size(slot * 0.64f, height),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                        alpha = if (mm > 0f) 0.95f else 0.25f,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Caption(
            when {
                next == null -> ""
                next >= 0.1 -> "${Units.formatPrecipitation(next, units.precipitation)} expected in the next 24h."
                else -> "None expected in the next 24h."
            },
            maxLines = 2,
        )
    }
}

@Composable
private fun FeelsLikeTile(now: NowConditions, settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val apparent = now.apparentTemperature
    val delta = apparent?.let { it - now.temperature } ?: 0.0
    val caption = when {
        apparent == null -> ""
        delta < -2 && (now.windSpeed ?: 0.0) >= 15 -> "Wind is making it feel colder."
        delta < -2 -> "Feels colder than the actual temperature."
        delta > 2 && (now.humidity ?: 0.0) >= 60 -> "Humidity is making it feel warmer."
        delta > 2 -> "Feels warmer than the actual temperature."
        else -> "Similar to the actual temperature."
    }
    Tile(Icons.Rounded.Thermostat, "Feels like", modifier, onClick) {
        BigValue(Units.formatTemperature(apparent, settings.units.temperature))
        if (apparent != null) {
            Subtitle("Actual ${Units.formatTemperature(now.temperature, settings.units.temperature)}")
            Spacer(Modifier.height(10.dp))
            FeelsBar(actual = now.temperature, feels = apparent)
        }
        Spacer(Modifier.weight(1f))
        Caption(caption)
    }
}

/** Where it feels like (the solid marker) against where the thermometer is (the ring). */
@Composable
private fun FeelsBar(actual: Double, feels: Double) {
    val appear by rememberAppearProgress(durationMillis = 1100)
    Canvas(Modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics { }) {
        // A window of temperatures around the two, wide enough that a degree of difference shows.
        val middle = (actual + feels) / 2.0
        val half = max(abs(actual - feels) / 2.0 + 3.0, 6.0)
        fun x(value: Double) = (((value - (middle - half)) / (2 * half)).toFloat() * size.width).coerceIn(size.height / 2, size.width - size.height / 2)
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(
            Brush.horizontalGradient(listOf(temperatureColor(middle - half), temperatureColor(middle), temperatureColor(middle + half))),
            cornerRadius = radius,
            alpha = 0.45f,
        )
        val from = x(actual)
        val to = from + (x(feels) - from) * appear
        drawLine(Color.White, Offset(from, size.height / 2), Offset(to, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round, alpha = 0.55f)
        drawCircle(Color(0xFF1B2440), radius = size.height * 0.9f, center = Offset(from, size.height / 2))
        drawCircle(Color.White, radius = size.height * 0.9f, center = Offset(from, size.height / 2), style = Stroke(1.5.dp.toPx()))
        drawCircle(Color(0xFF1B2440), radius = size.height * 0.95f, center = Offset(to, size.height / 2))
        drawCircle(Color.White, radius = size.height * 0.62f, center = Offset(to, size.height / 2))
    }
}

private val HumidityColors = listOf(Color(0xFFFFD27A), Color(0xFF8BE0A4), Color(0xFF5AB8FF), Color(0xFF4A7DFF))

@Composable
private fun HumidityTile(now: NowConditions, settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val humidity = now.humidity
    Tile(Icons.Rounded.WaterDrop, "Humidity", modifier, onClick) {
        BigValue(Units.formatPercent(humidity))
        humidity?.let {
            Subtitle(Scales.humidityDescription(it, now.dewPoint))
            Spacer(Modifier.height(10.dp))
            ScaleBar((it / 100.0).toFloat(), HumidityColors)
        }
        Spacer(Modifier.weight(1f))
        now.dewPoint?.let { Caption("The dew point is ${Units.formatTemperature(it, settings.units.temperature)} right now.") }
    }
}

@Composable
private fun VisibilityTile(now: NowConditions, settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val visibility = now.visibility
    Tile(Icons.Rounded.Visibility, "Visibility", modifier, onClick) {
        BigValue(Units.formatDistance(visibility, settings.units.distance))
        if (visibility != null) {
            Spacer(Modifier.height(12.dp))
            val grow by rememberAppearProgress(durationMillis = 900)
            // Like signal strength: more bars, further to see.
            val clear = when {
                visibility >= 30_000 -> 5
                visibility >= 10_000 -> 4
                visibility >= 4_000 -> 3
                visibility >= 1_000 -> 2
                visibility >= 200 -> 1
                else -> 0
            }
            Canvas(Modifier.size(width = 74.dp, height = 28.dp).clearAndSetSemantics { }) {
                val slot = size.width / 5f
                repeat(5) { i ->
                    val rise = (grow * 1.6f - 0.15f * i).coerceIn(0f, 1f)
                    val height = size.height * (0.36f + 0.16f * i) * rise
                    drawRoundRect(
                        Color.White,
                        topLeft = Offset(i * slot, size.height - height),
                        size = Size(slot * 0.62f, height),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        alpha = if (i < clear) 0.92f else 0.2f,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        visibility?.let { Caption(Scales.visibilityDescription(it) + ".") }
    }
}

@Composable
private fun PressureTile(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, settings: AppSettings, modifier: Modifier, onClick: () -> Unit) {
    val trend = remember(snapshot, nowMillis / 600_000) { snapshot.pressureTrend(nowMillis) }
    val pressure = now.pressure
    Tile(Icons.Rounded.Compress, "Pressure", modifier, onClick) {
        PressureGauge(
            hpa = pressure,
            reading = Units.formatPressure(pressure, settings.units.pressure).substringBefore(' '),
            unit = settings.units.pressure.label,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(4.dp))
        Caption(
            when (trend) {
                PressureTrend.Rising -> "↑ Rising over the last 3 hours."
                PressureTrend.Falling -> "↓ Falling over the last 3 hours."
                PressureTrend.Steady -> "Steady over the last 3 hours."
                null -> ""
            },
            maxLines = 2,
        )
    }
}

/** A 240° gauge from 960 to 1060 hPa that fits its box, with the reading in the middle. */
@Composable
private fun PressureGauge(hpa: Double?, reading: String, unit: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val readingStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White)
    val unitStyle = MaterialTheme.typography.labelSmall.copy(color = SkyText.Secondary)
    val appear by rememberAppearProgress(durationMillis = 1200)
    val fraction by animateFloatAsState(
        hpa?.let { ((it - 960.0) / 100.0).toFloat().coerceIn(0f, 1f) } ?: 0f,
        tween(700),
        label = "pressure",
    )
    Canvas(modifier.clearAndSetSemantics { }) {
        val stroke = 4.dp.toPx()
        // The arc reaches one radius above its centre and half a radius below it.
        val radius = (minOf(size.width / 2f, size.height / 1.5f) - stroke).coerceAtLeast(1f)
        val center = Offset(size.width / 2f, (size.height - radius * 1.5f) / 2f + radius)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        drawArc(Color.White.copy(alpha = 0.2f), startAngle = 150f, sweepAngle = 240f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (hpa != null) {
            val sweep = 240f * fraction * appear
            drawArc(
                Brush.sweepGradient(0f to Color.White, 0.25f to Color.White, 0.42f to Color.White.copy(alpha = 0.5f), 1f to Color.White, center = center),
                startAngle = 150f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // A bead where the reading sits.
            val angle = Math.toRadians((150f + sweep).toDouble())
            val bead = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
            drawCircle(Color(0xFF1B2440), radius = stroke * 1.5f, center = bead)
            drawCircle(Color.White, radius = stroke * 1.0f, center = bead)
        }
        val value = measurer.measure(reading, readingStyle)
        val label = measurer.measure(unit, unitStyle)
        val blockTop = center.y - (value.size.height + label.size.height) / 2f
        drawText(value, topLeft = Offset(center.x - value.size.width / 2f, blockTop))
        drawText(label, topLeft = Offset(center.x - label.size.width / 2f, blockTop + value.size.height))
    }
}

@Composable
private fun MoonTile(nowMillis: Long, modifier: Modifier) {
    val moon = remember(nowMillis / 3_600_000) { Astronomy.moon(nowMillis) }
    Tile(Icons.Rounded.DarkMode, "Moon", modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(moon.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("${(moon.illumination * 100).roundToInt()}% lit", style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
            }
            Canvas(Modifier.size(64.dp).clearAndSetSemantics { }) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 6.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color(0x33E8EEFF), Color.Transparent), center = center, radius = size.minDimension / 2f), radius = size.minDimension / 2f, center = center)
                drawCircle(Color.White.copy(alpha = 0.12f), radius = radius, center = center)
                translate(center.x, center.y) {
                    drawPath(moonLitPath(Offset.Zero, radius, moon.phase.toFloat()), Color(0xFFF4F1E6))
                }
            }
        }
        val days = if (moon.name == "Full moon") moon.daysUntilNew else moon.daysUntilFull
        val next = if (moon.name == "Full moon") "New moon" else "Full moon"
        Caption("$next in ${days.roundToInt().coerceAtLeast(1)} ${if (days.roundToInt() == 1) "day" else "days"}.", maxLines = 2)
    }
}

private val CloudColors = listOf(Color(0xFF7DBAF0), Color(0xFFB0BCC9), Color(0xFF6E7C8C))

@Composable
private fun CloudTile(now: NowConditions, modifier: Modifier, onClick: () -> Unit) {
    Tile(Icons.Rounded.Cloud, "Cloud cover", modifier, onClick) {
        BigValue(Units.formatPercent(now.cloudCover))
        now.cloudCover?.let {
            Subtitle(Scales.cloudDescription(it))
            Spacer(Modifier.height(10.dp))
            ScaleBar(it.toFloat() / 100f, CloudColors)
        }
        Spacer(Modifier.weight(1f))
    }
}

private val UsAqiColors = listOf(Color(0xFF66BB6A), Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFF4511E), Color(0xFFAB47BC), Color(0xFF8E2442))
private val EuAqiColors = listOf(Color(0xFF4FC3F7), Color(0xFF66BB6A), Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFF4511E), Color(0xFFAB47BC))

@Composable
private fun AirQualityTile(air: AirQuality, value: Double, european: Boolean) {
    val level = if (european) Scales.europeanAqi(value) else Scales.usAqi(value)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(Icons.Rounded.Masks, if (european) "Air quality · European AQI" else "Air quality · US AQI")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigValue(value.roundToInt().toString())
            Spacer(Modifier.size(12.dp))
            Text(level.label, style = MaterialTheme.typography.titleMedium, color = Color(level.color), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        ScaleBar(level.fraction, if (european) EuAqiColors else UsAqiColors)
        Spacer(Modifier.height(10.dp))
        Caption(level.advice + ".")
        val pollutants = listOfNotNull(
            air.pm25?.let { "PM2.5" to it },
            air.pm10?.let { "PM10" to it },
            air.ozone?.let { "O₃" to it },
            air.nitrogenDioxide?.let { "NO₂" to it },
        )
        if (pollutants.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                pollutants.forEach { (name, amount) ->
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.labelSmall, color = SkyText.Tertiary)
                        Text("${amount.roundToInt()}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                }
                Text("µg/m³", style = MaterialTheme.typography.labelSmall, color = SkyText.Tertiary, modifier = Modifier.align(Alignment.Bottom))
            }
        }
        val pollen = air.pollen.filterValues { it >= 1.0 }
        if (pollen.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Pollen: " + pollen.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${Scales.pollen(it.value).label.lowercase()}" },
                style = MaterialTheme.typography.bodySmall,
                color = SkyText.Secondary,
            )
        }
    }
}
