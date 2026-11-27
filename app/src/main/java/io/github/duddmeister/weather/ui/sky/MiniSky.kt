package io.github.duddmeister.weather.ui.sky

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A still, small version of the sky for [look], as the background of a card: its colours running
 * corner to corner, with a few stars, a sun or moon, some cloud and a little rain or snow.
 */
fun Modifier.miniSky(look: SkyLook, shape: Shape): Modifier = clip(shape).drawWithCache {
    val gradient = Brush.linearGradient(
        0f to look.top,
        0.55f to look.middle,
        1f to look.bottom,
        start = Offset(0f, 0f),
        end = Offset(size.width * 0.9f, size.height * 1.6f),
    )
    val random = Random(7)
    val stars = List(26) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    val drops = List(24) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    // The sun or moon hangs in the gap between the name on the left and the temperature on the right.
    val light = Offset(size.width * 0.56f, -size.height * 0.1f)
    val warm = lerp(Color(0xFFFFF3C4), Color(0xFFFFB070), look.sunWarmth)
    val sunGlow = Brush.radialGradient(listOf(warm.copy(alpha = 0.62f), warm.copy(alpha = 0.16f), Color.Transparent), center = light, radius = size.height * 1.5f)
    val moonGlow = Brush.radialGradient(listOf(Color(0x59E8EEFF), Color.Transparent), center = light, radius = size.height * 1.1f)
    val cloud = lerp(Color.White, Color(0xFF3A4352), look.cloudDarkness)
    val puffs = List(6) { i ->
        val centre = Offset(size.width * (0.42f + 0.11f * i + 0.05f * random.nextFloat()), size.height * (0.08f + 0.5f * random.nextFloat()))
        val radius = size.height * (0.42f + 0.25f * random.nextFloat())
        Triple(centre, radius, Brush.radialGradient(listOf(cloud.copy(alpha = 0.34f), cloud.copy(alpha = 0.12f), Color.Transparent), center = centre, radius = radius))
    }
    // Text sits on the left, so the left stays a little deeper whatever the weather.
    val shade = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent), endX = size.width * 0.7f)
    val outline = shape.createOutline(size, layoutDirection, this)
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f)))
    onDrawWithContent {
        drawRect(gradient)
        if (look.stars > 0.05f) {
            stars.forEach { (x, y, z) ->
                drawCircle(Color.White, radius = (0.5f + z).dp.toPx(), center = Offset(size.width * x, size.height * y), alpha = look.stars * (0.3f + 0.6f * z))
            }
        }
        // Just their light: the card's icon already shows which it is.
        if (look.sun > 0.05f) drawRect(sunGlow, alpha = look.sun)
        if (look.moon > 0.05f) drawRect(moonGlow, alpha = look.moon)
        val cover = maxOf(look.clouds, look.fog * 0.8f)
        if (cover > 0.05f) {
            val count = (2 + cover * 4).toInt().coerceAtMost(puffs.size)
            for (i in 0 until count) {
                val (centre, radius, brush) = puffs[i]
                drawCircle(brush, radius = radius, center = centre, alpha = 0.5f + 0.5f * cover)
            }
        }
        if (look.rain > 0.05f) {
            val count = (look.rain * drops.size).toInt()
            for (i in 0 until count) {
                val (x, y, z) = drops[i]
                val length = (6f + 8f * z).dp.toPx()
                val start = Offset(size.width * (0.42f + 0.58f * x), size.height * y)
                drawLine(
                    Color.White,
                    start,
                    Offset(start.x + look.windTilt * length, start.y + length),
                    strokeWidth = (0.8f + 0.7f * z).dp.toPx(),
                    cap = StrokeCap.Round,
                    alpha = 0.10f + 0.18f * z,
                )
            }
        }
        if (look.snow > 0.05f) {
            val count = (look.snow * drops.size).toInt()
            for (i in 0 until count) {
                val (x, y, z) = drops[i]
                drawCircle(Color.White, radius = (0.9f + 1.3f * z).dp.toPx(), center = Offset(size.width * (0.42f + 0.58f * x), size.height * y), alpha = 0.22f + 0.36f * z)
            }
        }
        drawRect(shade)
        drawContent()
        drawOutline(outline, edge, style = Stroke(width = 2.dp.toPx()))
    }
}
