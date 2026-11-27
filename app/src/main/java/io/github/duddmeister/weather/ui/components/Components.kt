package io.github.duddmeister.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.ui.WeatherVisuals

@Composable
fun WeatherIcon(code: Int?, isDay: Boolean, size: Dp, modifier: Modifier = Modifier, described: Boolean = true) {
    Image(
        painter = painterResource(WeatherVisuals.icon(code, isDay)),
        contentDescription = if (described) WeatherCondition.fromCode(code).label(isDay) else null,
        modifier = modifier.size(size),
    )
}

private val temperatureStops = listOf(
    -20.0 to Color(0xFFA08CFF),
    -5.0 to Color(0xFF6FA8FF),
    5.0 to Color(0xFF5AD1E6),
    14.0 to Color(0xFF7FE0A0),
    22.0 to Color(0xFFFFE066),
    29.0 to Color(0xFFFFA94D),
    36.0 to Color(0xFFFF6B6B),
)

/** A colour for a temperature in °C, from violet (cold) through green to red (hot). */
fun temperatureColor(celsius: Double): Color {
    if (celsius <= temperatureStops.first().first) return temperatureStops.first().second
    if (celsius >= temperatureStops.last().first) return temperatureStops.last().second
    val upper = temperatureStops.indexOfFirst { it.first >= celsius }
    val (t1, c1) = temperatureStops[upper - 1]
    val (t2, c2) = temperatureStops[upper]
    return lerp(c1, c2, ((celsius - t1) / (t2 - t1)).toFloat())
}
