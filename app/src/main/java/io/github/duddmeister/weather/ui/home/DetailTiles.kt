package io.github.duddmeister.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.duddmeister.weather.ui.sky.moonLitPath
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val TileHeight = 172.dp

/** Adds the "details" tiles to the page, two per row. */
fun LazyListScope.detailRows(
    snapshot: WeatherSnapshot,
    now: NowConditions,
    nowMillis: Long,
    settings: AppSettings,
    use24h: Boolean,
    location: SavedLocation,
) {
    snapshot.airQuality?.let { air ->
        val european = Scales.useEuropean(settings.aqiScale, location.countryCode)
        val value = if (european) air.europeanAqi else air.usAqi
        if (value != null) item(key = "air") { AirQualityTile(air, value, european) }
    }
    val tiles = listOf(
        TileSpec("uv") { m -> UvTile(snapshot, now, nowMillis, use24h, m) },
        TileSpec("sun") { m -> SunTile(snapshot, nowMillis, use24h, m) },
        TileSpec("wind") { m -> WindTile(now, settings, m) },
        TileSpec("precipitation") { m -> PrecipitationTile(snapshot, nowMillis, settings, m) },
        TileSpec("feels") { m -> FeelsLikeTile(now, settings, m) },
        TileSpec("humidity") { m -> HumidityTile(now, settings, m) },
        TileSpec("visibility") { m -> VisibilityTile(now, settings, m) },
        TileSpec("pressure") { m -> PressureTile(snapshot, now, nowMillis, settings, m) },
        TileSpec("moon") { m -> MoonTile(nowMillis, m) },
        TileSpec("clouds") { m -> CloudTile(now, m) },
    )
    tiles.chunked(2).forEach { row ->
        item(key = "tiles-" + row.joinToString("-") { it.key }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> tile.content(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private class TileSpec(val key: String, val content: @Composable (Modifier) -> Unit)

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = modifier.height(TileHeight)) {
        CardLabel(icon, label)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun BigValue(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 1)
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary, modifier = modifier, maxLines = 3)
}

@Composable
private fun ScaleBar(fraction: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(size.height / 2))
        val x = (fraction.coerceIn(0f, 1f) * size.width).coerceIn(size.height / 2, size.width - size.height / 2)
        drawCircle(Color(0xFF1B2440), radius = size.height * 0.95f, center = Offset(x, size.height / 2))
        drawCircle(Color.White, radius = size.height * 0.62f, center = Offset(x, size.height / 2))
    }
}

private val UvColors = listOf(Color(0xFF66BB6A), Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFF4511E), Color(0xFFAB47BC))

@Composable
private fun UvTile(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, use24h: Boolean, modifier: Modifier) {
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
    Tile(Icons.Rounded.WbSunny, "UV index", modifier) {
        BigValue(uv.roundToInt().toString())
        Text(level.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
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
            if (sunrise != null && sunset != null) SunArc(Astronomy.sunProgress(nowSec, sunrise, sunset).toFloat())
        }
        Caption(
            when {
                daytime && sunrise != null -> "Sunrise ${text.time(sunrise)}"
                sunset != null && nowSec >= sunset -> "Sunset ${text.time(sunset)}"
                else -> today?.daylightDuration?.let { TimeText.hoursMinutes(it) + " of daylight" } ?: ""
            },
        )
    }
}

@Composable
private fun BoxScope.SunArc(progress: Float) {
    Canvas(Modifier.matchParentSize()) {
        val horizon = size.height * 0.78f
        val radiusX = size.width * 0.46f
        val radiusY = size.height * 0.62f
        val center = Offset(size.width / 2f, horizon)
        val arc = Path()
        val steps = 48
        for (i in 0..steps) {
            val angle = PI * i / steps
            val x = center.x - radiusX * cos(angle).toFloat()
            val y = center.y - radiusY * sin(angle).toFloat()
            if (i == 0) arc.moveTo(x, y) else arc.lineTo(x, y)
        }
        drawPath(arc, Color.White.copy(alpha = 0.35f), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 1.dp.toPx())
        if (progress in 0f..1f) {
            val angle = PI * progress
            val sun = Offset(center.x - radiusX * cos(angle).toFloat(), center.y - radiusY * sin(angle).toFloat())
            drawCircle(Brush.radialGradient(listOf(Color(0x80FFE082), Color.Transparent), center = sun, radius = 16.dp.toPx()), radius = 16.dp.toPx(), center = sun)
            drawCircle(Color(0xFFFFD54F), radius = 5.dp.toPx(), center = sun)
        }
    }
}

@Composable
private fun WindTile(now: NowConditions, settings: AppSettings, modifier: Modifier) {
    val units = settings.units
    val speed = now.windSpeed
    Tile(Icons.Rounded.Air, "Wind", modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BigValue(Units.formatWind(speed, units.wind, withUnit = false))
                Text(units.wind.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(Units.compass(now.windDirection), style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
            }
            Compass(now.windDirection, Modifier.size(72.dp))
        }
        Caption(
            listOfNotNull(
                now.windGusts?.let { "Gusts ${Units.formatWind(it, units.wind)}" },
                speed?.let { Units.beaufortDescription(it) },
            ).joinToString(" · "),
        )
    }
}

@Composable
private fun Compass(directionFrom: Double?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = SkyText.Tertiary)
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.White.copy(alpha = 0.2f), radius = radius - 1.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        for (i in 0 until 36) {
            rotate(i * 10f, center) {
                val long = i % 9 == 0
                drawLine(
                    Color.White.copy(alpha = if (long) 0.7f else 0.25f),
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
            // The arrow points where the wind is going.
            rotate(directionFrom.toFloat() + 180f, center) {
                val tip = Offset(center.x, center.y - radius * 0.62f)
                val tail = Offset(center.x, center.y + radius * 0.5f)
                drawLine(Color.White, tail, tip, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 4.dp.toPx())
                    lineTo(tip.x - 6.dp.toPx(), tip.y + 7.dp.toPx())
                    lineTo(tip.x + 6.dp.toPx(), tip.y + 7.dp.toPx())
                    close()
                }
                drawPath(head, Color.White)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = tail)
            }
        }
    }
}

@Composable
private fun PrecipitationTile(snapshot: WeatherSnapshot, nowMillis: Long, settings: AppSettings, modifier: Modifier) {
    val units = settings.units
    val last = remember(snapshot, nowMillis / 600_000) { snapshot.precipitationLast24h(nowMillis) }
    val next = remember(snapshot, nowMillis / 600_000) { snapshot.precipitationNext24h(nowMillis) }
    Tile(Icons.Rounded.Umbrella, "Precipitation", modifier) {
        BigValue(Units.formatPrecipitation(last ?: 0.0, units.precipitation))
        Text("in last 24h", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Spacer(Modifier.weight(1f))
        Caption(
            when {
                next == null -> ""
                next >= 0.1 -> "${Units.formatPrecipitation(next, units.precipitation)} expected in the next 24h."
                else -> "None expected in the next 24h."
            },
        )
    }
}

@Composable
private fun FeelsLikeTile(now: NowConditions, settings: AppSettings, modifier: Modifier) {
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
    Tile(Icons.Rounded.Thermostat, "Feels like", modifier) {
        BigValue(Units.formatTemperature(apparent, settings.units.temperature))
        Spacer(Modifier.weight(1f))
        Caption(caption)
    }
}

@Composable
private fun HumidityTile(now: NowConditions, settings: AppSettings, modifier: Modifier) {
    val humidity = now.humidity
    Tile(Icons.Rounded.WaterDrop, "Humidity", modifier) {
        BigValue(Units.formatPercent(humidity))
        humidity?.let { Text(Scales.humidityDescription(it, now.dewPoint), style = MaterialTheme.typography.titleSmall, color = Color.White) }
        Spacer(Modifier.weight(1f))
        now.dewPoint?.let { Caption("The dew point is ${Units.formatTemperature(it, settings.units.temperature)} right now.") }
    }
}

@Composable
private fun VisibilityTile(now: NowConditions, settings: AppSettings, modifier: Modifier) {
    Tile(Icons.Rounded.Visibility, "Visibility", modifier) {
        BigValue(Units.formatDistance(now.visibility, settings.units.distance))
        Spacer(Modifier.weight(1f))
        now.visibility?.let { Caption(Scales.visibilityDescription(it) + ".") }
    }
}

@Composable
private fun PressureTile(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long, settings: AppSettings, modifier: Modifier) {
    val trend = remember(snapshot, nowMillis / 600_000) { snapshot.pressureTrend(nowMillis) }
    val pressure = now.pressure
    Tile(Icons.Rounded.Compress, "Pressure", modifier) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Gauge(pressure, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(18.dp))
                Text(
                    Units.formatPressure(pressure, settings.units.pressure).substringBefore(' '),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(settings.units.pressure.label, style = MaterialTheme.typography.labelSmall, color = SkyText.Secondary)
            }
        }
        Caption(
            when (trend) {
                PressureTrend.Rising -> "↑ Rising over the last 3 hours."
                PressureTrend.Falling -> "↓ Falling over the last 3 hours."
                PressureTrend.Steady -> "Steady over the last 3 hours."
                null -> ""
            },
        )
    }
}

@Composable
private fun Gauge(hpa: Double?, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val diameter = minOf(size.width, size.height * 1.5f) - stroke
        val topLeft = Offset((size.width - diameter) / 2f, stroke / 2f + 4.dp.toPx())
        val arcSize = Size(diameter, diameter)
        drawArc(Color.White.copy(alpha = 0.2f), startAngle = 150f, sweepAngle = 240f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (hpa != null) {
            val fraction = ((hpa - 960.0) / 100.0).toFloat().coerceIn(0f, 1f)
            drawArc(Color.White, startAngle = 150f, sweepAngle = 240f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
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
            Canvas(Modifier.size(56.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.12f), radius = radius, center = center)
                drawPath(moonLitPath(center, radius, moon.phase.toFloat()), Color(0xFFF4F1E6))
            }
        }
        val days = if (moon.name == "Full moon") moon.daysUntilNew else moon.daysUntilFull
        val next = if (moon.name == "Full moon") "New moon" else "Full moon"
        Caption("$next in ${days.roundToInt().coerceAtLeast(1)} ${if (days.roundToInt() == 1) "day" else "days"}.")
    }
}

@Composable
private fun CloudTile(now: NowConditions, modifier: Modifier) {
    Tile(Icons.Rounded.Cloud, "Cloud cover", modifier) {
        BigValue(Units.formatPercent(now.cloudCover))
        Spacer(Modifier.height(8.dp))
        ScaleBar((now.cloudCover ?: 0.0).toFloat() / 100f, listOf(Color(0xFF7DBAF0), Color(0xFFB0BCC9), Color(0xFF6E7C8C)))
        Spacer(Modifier.weight(1f))
        now.cloudCover?.let { Caption(Scales.cloudDescription(it) + ".") }
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
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(value.roundToInt().toString())
            Spacer(Modifier.size(10.dp))
            Text(level.label, style = MaterialTheme.typography.titleMedium, color = Color(level.color), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        ScaleBar(level.fraction, if (european) EuAqiColors else UsAqiColors)
        Spacer(Modifier.height(10.dp))
        Caption(level.advice + ".")
        val pollutants = listOfNotNull(
            air.pm25?.let { "PM2.5 ${it.roundToInt()}" },
            air.pm10?.let { "PM10 ${it.roundToInt()}" },
            air.ozone?.let { "O₃ ${it.roundToInt()}" },
            air.nitrogenDioxide?.let { "NO₂ ${it.roundToInt()}" },
        )
        if (pollutants.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(pollutants.joinToString("   ") + "  µg/m³", style = MaterialTheme.typography.labelMedium, color = SkyText.Tertiary)
        }
        val pollen = air.pollen.filterValues { it >= 1.0 }
        if (pollen.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Pollen: " + pollen.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${Scales.pollen(it.value).label.lowercase()}" },
                style = MaterialTheme.typography.bodySmall,
                color = SkyText.Secondary,
            )
        }
    }
}
