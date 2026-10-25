package io.github.duddmeister.weather.ui.sky

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.duddmeister.weather.ui.SkyKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen animated sky: a gradient for the conditions and time of day, plus stars, sun glow,
 * moon phase, drifting clouds, rain, snow, fog and lightning. Everything is drawn in one layer and
 * only the draw phase runs per frame (no recomposition). Animation pauses when the app isn't visible.
 *
 * Behind other screens ([calm]), rain, snow, lightning, stars, the sun and the moon fade out and the
 * sky then holds still, leaving its colours and soft clouds behind settings and lists.
 */
@Composable
fun SkyBackground(scene: SkyScene, animate: Boolean, calm: Boolean = false, modifier: Modifier = Modifier) {
    val colorSpec = tween<Color>(durationMillis = 1200)
    val floatSpec = tween<Float>(durationMillis = 1200)
    val top by animateColorAsState(scene.palette.top, colorSpec, label = "skyTop")
    val middle by animateColorAsState(scene.palette.middle, colorSpec, label = "skyMiddle")
    val bottom by animateColorAsState(scene.palette.bottom, colorSpec, label = "skyBottom")
    // Whatever would show through cards clears quickly when another screen opens, and returns
    // gently after it closes.
    val calmSpec = if (calm) tween<Float>(durationMillis = 400) else floatSpec
    val stars by animateFloatAsState(if (calm) 0f else scene.stars, calmSpec, label = "stars")
    val sun by animateFloatAsState(if (calm) 0f else scene.sun, calmSpec, label = "sun")
    val moon by animateFloatAsState(if (calm) 0f else scene.moon, calmSpec, label = "moon")
    val clouds by animateFloatAsState(scene.clouds, floatSpec, label = "clouds")
    val darkness by animateFloatAsState(if (scene.darkClouds) 1f else 0f, floatSpec, label = "cloudDarkness")
    val rainState = animateFloatAsState(if (calm) 0f else scene.rain, calmSpec, label = "rain")
    val snowState = animateFloatAsState(if (calm) 0f else scene.snow, calmSpec, label = "snow")
    val rain by rainState
    val snow by snowState
    val fog by animateFloatAsState(scene.fog, floatSpec, label = "fog")
    val tilt by animateFloatAsState(scene.windTilt, floatSpec, label = "tilt")
    val lowSun = scene.kind == SkyKind.Dawn || scene.kind == SkyKind.Dusk
    val sunOnLeft = scene.kind == SkyKind.Dawn
    val lightning = scene.lightning && !calm
    val moonPhase = scene.moonPhase
    // Keep falling while it fades out, so it doesn't freeze mid-air.
    val falling by remember(rainState, snowState) { derivedStateOf { rainState.value > 0.01f || snowState.value > 0.01f } }
    val time = rememberSkyClock(animate && (!calm || falling))
    val field = remember { ParticleField(seed = 1337) }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val sky = Brush.verticalGradient(listOf(top, middle, bottom))
                val sunCenter = when {
                    !lowSun -> Offset(size.width * 0.86f, size.height * 0.05f)
                    sunOnLeft -> Offset(size.width * 0.12f, size.height * 0.56f)
                    else -> Offset(size.width * 0.88f, size.height * 0.56f)
                }
                val glowRadius = size.maxDimension * if (lowSun) 0.55f else 0.65f
                val sunGlow = Brush.radialGradient(
                    colors = if (lowSun) {
                        listOf(Color(0x99FFB070), Color(0x33FF8A5C), Color.Transparent)
                    } else {
                        listOf(Color(0x80FFF3C4), Color(0x22FFE7A0), Color.Transparent)
                    },
                    center = sunCenter,
                    radius = glowRadius,
                )
                val coreRadius = 56.dp.toPx()
                val sunCore = Brush.radialGradient(
                    listOf(Color(0xFFFFFBEA), Color(0xB3FFF1BF), Color.Transparent),
                    center = sunCenter,
                    radius = coreRadius,
                )
                val moonCenter = Offset(size.width * 0.80f, size.height * 0.10f)
                val moonRadius = 18.dp.toPx()
                val moonGlow = Brush.radialGradient(listOf(Color(0x40E8EEFF), Color.Transparent), center = moonCenter, radius = moonRadius * 5f)
                val moonPath = moonLitPath(moonCenter, moonRadius, moonPhase)
                val puffs = cloudPuffs(this, lerp(Color.White, Color(0xFF3A4352), darkness))
                val fogPuffs = cloudPuffs(this, Color.White)

                onDrawBehind {
                    val t = time.floatValue
                    drawRect(sky)
                    if (stars > 0.01f) drawStars(field, t, stars)
                    if (sun > 0.01f) {
                        drawRect(sunGlow, alpha = sun * (0.85f + 0.15f * sin(t * 0.5f)))
                        drawCircle(sunCore, radius = coreRadius, center = sunCenter, alpha = sun)
                    }
                    if (moon > 0.01f) {
                        drawCircle(moonGlow, radius = moonRadius * 5f, center = moonCenter, alpha = moon)
                        drawCircle(Color.White, radius = moonRadius, center = moonCenter, alpha = 0.10f * moon)
                        drawPath(moonPath, Color(0xFFF4F1E6), alpha = 0.92f * moon)
                    }
                    if (clouds > 0.01f) drawClouds(field, puffs, t, clouds, darkness)
                    if (fog > 0.01f) drawFog(field, fogPuffs, t, fog)
                    if (rain > 0.01f) drawRain(field, t, rain, tilt)
                    if (snow > 0.01f) drawSnow(field, t, snow)
                    if (lightning) drawLightning(t)
                }
            },
    )
}

/** Seconds since the sky started animating; only ticks while the app is visible. */
@Composable
private fun rememberSkyClock(animate: Boolean): MutableFloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate, lifecycle) {
        if (!animate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { frame ->
                    val delta = ((frame - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    last = frame
                    time.floatValue = (time.floatValue + delta) % 3600f
                }
            }
        }
    }
    return time
}

/** Pre-generated random numbers so particle positions are pure functions of time. */
private class ParticleField(seed: Int) {
    val count = 320
    val a = FloatArray(count)
    val b = FloatArray(count)
    val c = FloatArray(count)
    val d = FloatArray(count)
    val e = FloatArray(count)

    init {
        val random = Random(seed)
        for (i in 0 until count) {
            a[i] = random.nextFloat()
            b[i] = random.nextFloat()
            c[i] = random.nextFloat()
            d[i] = random.nextFloat()
            e[i] = random.nextFloat()
        }
    }
}

private class Puff(val center: Offset, val radius: Float, val brush: Brush)

/** A cloud is five soft circles; brushes are built once and moved with transforms. */
private fun cloudPuffs(density: Density, color: Color): List<Puff> = with(density) {
    listOf(
        Triple(-70f, 12f, 46f),
        Triple(-28f, -10f, 58f),
        Triple(26f, -4f, 54f),
        Triple(72f, 12f, 42f),
        Triple(0f, 22f, 60f),
    ).map { (x, y, r) ->
        val center = Offset(x.dp.toPx(), y.dp.toPx())
        val radius = r.dp.toPx()
        Puff(
            center,
            radius,
            Brush.radialGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.22f), Color.Transparent), center = center, radius = radius),
        )
    }
}

private fun DrawScope.drawStars(field: ParticleField, t: Float, amount: Float) {
    val count = 110
    for (i in 0 until count) {
        val x = field.a[i] * size.width
        val y = field.b[i] * size.height * 0.62f
        val radius = (0.6f + field.c[i] * 1.3f).dp.toPx()
        val twinkle = 0.55f + 0.45f * sin(t * (0.6f + field.d[i] * 2.2f) + field.e[i] * 6.28f)
        val alpha = (amount * twinkle * (0.35f + 0.65f * field.e[i])).coerceIn(0f, 1f)
        drawCircle(Color.White, radius = radius, center = Offset(x, y), alpha = alpha)
    }
    // An occasional shooting star.
    val period = 11f
    val cycle = floor(t / period)
    val local = t - cycle * period
    if (local < 0.9f) {
        val seed = Random(cycle.toInt() * 31 + 7)
        val start = Offset(size.width * (0.1f + 0.6f * seed.nextFloat()), size.height * (0.04f + 0.22f * seed.nextFloat()))
        val direction = Offset(0.9f, 0.42f)
        val progress = local / 0.9f
        val travel = 260.dp.toPx()
        val head = start + direction * (travel * progress)
        val tail = head - direction * (90.dp.toPx() * (1f - 0.4f * progress))
        drawLine(
            brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * amount * (1f - progress))), start = tail, end = head),
            start = tail,
            end = head,
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawClouds(field: ParticleField, puffs: List<Puff>, t: Float, amount: Float, darkness: Float) {
    val count = 3 + (amount * 6).toInt()
    val baseAlpha = (0.30f + 0.5f * amount) * (0.75f + 0.15f * darkness)
    val span = 300.dp.toPx()
    for (i in 0 until count) {
        val k = 200 + i
        val s = 0.7f + field.b[k] + amount * 0.3f
        val y = size.height * (0.0f + field.a[k] * (0.28f + 0.17f * amount))
        val speed = (5f + field.c[k] * 12f).dp.toPx()
        val travel = size.width + span * s
        val x = ((field.d[k] * travel + t * speed) % travel) - span * s / 2f
        val alpha = baseAlpha * (0.6f + 0.4f * field.e[k])
        withTransform({
            translate(x, y)
            scale(s, s, pivot = Offset.Zero)
        }) {
            for (puff in puffs) drawCircle(puff.brush, radius = puff.radius, center = puff.center, alpha = alpha)
        }
    }
}

private fun DrawScope.drawFog(field: ParticleField, puffs: List<Puff>, t: Float, amount: Float) {
    val span = 600.dp.toPx()
    for (i in 0 until 6) {
        val k = 240 + i
        val y = size.height * (0.12f + 0.13f * i + 0.05f * field.a[k])
        val speed = (6f + field.c[k] * 10f).dp.toPx()
        val travel = size.width + span
        val x = ((field.d[k] * travel + t * speed) % travel) - span / 2f
        withTransform({
            translate(x, y)
            scale(3.2f, 0.85f, pivot = Offset.Zero)
        }) {
            for (puff in puffs) drawCircle(puff.brush, radius = puff.radius, center = puff.center, alpha = 0.22f * amount)
        }
    }
}

private fun DrawScope.drawRain(field: ParticleField, t: Float, amount: Float, tilt: Float) {
    val count = (amount * 200).toInt().coerceAtMost(field.count)
    val height = size.height
    val drift = height * abs(tilt)
    for (i in 0 until count) {
        val speed = height * (1.0f + field.a[i] * 0.7f)
        val length = (12f + field.b[i] * 20f).dp.toPx()
        val travel = height + length
        val y = ((t * speed + field.c[i] * travel) % travel) - length / 2f
        val x0 = field.d[i] * (size.width + drift) - if (tilt > 0f) drift else 0f
        val x = x0 + y * tilt
        drawLine(
            color = Color.White,
            start = Offset(x - tilt * length, y - length),
            end = Offset(x, y),
            strokeWidth = (0.9f + field.e[i] * 1.1f).dp.toPx(),
            cap = StrokeCap.Round,
            alpha = 0.16f + 0.3f * field.e[i],
        )
    }
}

private fun DrawScope.drawSnow(field: ParticleField, t: Float, amount: Float) {
    val count = (amount * 160).toInt().coerceAtMost(field.count)
    for (i in 0 until count) {
        val radius = (1.3f + field.b[i] * 2.8f).dp.toPx()
        val speed = size.height * (0.045f + field.b[i] * 0.05f + field.a[i] * 0.03f)
        val travel = size.height + radius * 4f
        val y = ((t * speed + field.c[i] * travel) % travel) - radius * 2f
        val sway = (8f + field.e[i] * 22f).dp.toPx() * sin(t * (0.4f + field.d[i]) + field.e[i] * 6.28f)
        val x = field.d[i] * size.width + sway
        drawCircle(Color.White, radius = radius, center = Offset(x, y), alpha = 0.45f + field.b[i] * 0.5f)
    }
}

private fun DrawScope.drawLightning(t: Float) {
    val period = 7f
    val cycle = floor(t / period)
    val random = Random(cycle.toInt() * 7919 + 13)
    val start = 1.2f + random.nextFloat() * 4f
    val local = t - cycle * period - start
    if (local < 0f || local > 0.65f) return
    val flash = when {
        local < 0.06f -> 1f
        local < 0.12f -> 0.3f
        local < 0.2f -> 0.85f
        else -> 0.85f * (1f - (local - 0.2f) / 0.45f)
    }.coerceIn(0f, 1f)
    drawRect(Color.White, alpha = 0.28f * flash)
    if (local < 0.22f) {
        val path = Path()
        var x = size.width * (0.2f + random.nextFloat() * 0.6f)
        var y = 0f
        path.moveTo(x, y)
        val segments = 9
        val step = size.height * 0.5f / segments
        repeat(segments) {
            y += step
            x += (random.nextFloat() - 0.5f) * 60.dp.toPx()
            path.lineTo(x, y)
        }
        drawPath(path, Color.White.copy(alpha = 0.35f * flash), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White.copy(alpha = 0.95f * flash), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The lit part of the moon for [phase] (0 new, 0.5 full), as seen from the northern hemisphere:
 * a semicircular limb on the lit side closed by a half-ellipse terminator.
 */
fun moonLitPath(center: Offset, radius: Float, phase: Float): Path {
    val path = Path()
    val p = ((phase % 1f) + 1f) % 1f
    if (p < 0.02f || p > 0.98f) return path
    val waxing = p < 0.5f
    val tx = radius * cos(2.0 * PI * p).toFloat()
    val limb = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    path.arcTo(limb, -90f, if (waxing) 180f else -180f, forceMoveTo = true)
    val ex = abs(tx)
    val terminator = Rect(center.x - ex, center.y - radius, center.x + ex, center.y + radius)
    val sweep = if (waxing xor (tx < 0f)) -180f else 180f
    path.arcTo(terminator, 90f, sweep, forceMoveTo = false)
    path.close()
    return path
}
