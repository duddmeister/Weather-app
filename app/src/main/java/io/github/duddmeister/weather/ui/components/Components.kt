package io.github.duddmeister.weather.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.ui.WeatherVisuals
import io.github.duddmeister.weather.ui.sky.SkyPalette
import io.github.duddmeister.weather.ui.sky.SkyScenes
import io.github.duddmeister.weather.ui.theme.SkyText

/** The palette of the sky currently behind the content, so cards can tint to match. */
val LocalSkyPalette = staticCompositionLocalOf<SkyPalette> { SkyScenes.Default.palette }

val CardShape = RoundedCornerShape(24.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSkyPalette.current
    val card by animateColorAsState(palette.card, tween(800), label = "card")
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(card)
            .border(1.dp, palette.cardBorder, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun CardLabel(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = SkyText.Tertiary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = SkyText.Tertiary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun WeatherIcon(code: Int?, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(WeatherVisuals.icon(code, isDay)),
        contentDescription = WeatherCondition.fromCode(code).label(isDay),
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
