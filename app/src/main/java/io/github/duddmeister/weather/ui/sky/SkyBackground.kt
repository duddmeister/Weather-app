package io.github.duddmeister.weather.ui.sky

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * The soft parts of the sky (colours, glows, clouds, fog), kept as two layers so that glass
 * surfaces can paint them again inside themselves. Everything sharp is drawn between and after
 * them, so falling rain, snow and stars pass behind the glass instead of showing through text.
 * A third layer holds the rain that has landed on the screen, which glass wears on its surface.
 */
@Stable
class SkyBackdrop internal constructor(
    private val base: GraphicsLayer,
    private val veil: GraphicsLayer,
    private val water: GraphicsLayer,
) {
    private var dry = false

    /** Tint of the glass for the sky as it is right now. */
    var glass: Color by mutableStateOf(SkyScenes.Default.palette.card)
        internal set

    /** Paints the sky's soft layers, as they were last drawn, with the sky's origin at this scope's origin. */
    fun DrawScope.drawSoftSky() {
        drawLayer(base)
        drawLayer(veil)
    }

    /**
     * Paints the rain that's on the glass of the screen: glass surfaces draw it over themselves and
     * under their content, so drops run across a card without ever crossing its text.
     */
    fun DrawScope.drawWater() {
        drawLayer(water)
    }

    /** Records the water on the screen and draws it; when it isn't raining the layer is simply emptied. */
    internal fun DrawScope.recordWater(density: Density, wet: Boolean, block: DrawScope.() -> Unit) {
        if (wet) {
            water.record(density, layoutDirection, size.toIntSize(), block)
            dry = false
            drawLayer(water)
        } else if (!dry) {
            water.record(density, layoutDirection, size.toIntSize()) { }
            dry = true
        }
    }

    // Recorded through the layer itself with a plain Density: inside DrawScope's own record(), the
    // scope stands in for its own density and reading it (any dp.toPx()) never returns.
    internal fun DrawScope.recordBase(density: Density, block: DrawScope.() -> Unit) {
        base.record(density, layoutDirection, size.toIntSize(), block)
        drawLayer(base)
    }

    internal fun DrawScope.recordVeil(density: Density, block: DrawScope.() -> Unit) {
        veil.record(density, layoutDirection, size.toIntSize(), block)
        drawLayer(veil)
    }
}

@Composable
fun rememberSkyBackdrop(): SkyBackdrop {
    val base = rememberGraphicsLayer()
    val veil = rememberGraphicsLayer()
    val water = rememberGraphicsLayer()
    return remember(base, veil, water) { SkyBackdrop(base, veil, water) }
}

/** The sky behind the content, if there is one (there isn't in dialogs or the widget picker). */
val LocalSkyBackdrop = staticCompositionLocalOf<SkyBackdrop?> { null }

/**
 * Full-screen animated sky: a gradient for the conditions and time of day, plus stars, the sun
 * with slowly turning rays, the moon in its phase, drifting clouds, rain, snow, fog and lightning.
 * Only the draw phase runs per frame (no recomposition), and animation pauses when the app isn't
 * visible.
 *
 * [target] is the look to aim for. It's read every frame and followed with a short lag, so it can
 * change continuously (while swiping between places) or jump (when new data arrives) and the sky
 * eases after it either way. [lift] is how far the page has been scrolled, in pixels: the sky's
 * contents rise a little with it, the far ones least.
 *
 * Behind other screens ([calm]) the sky stops moving once it has settled.
 */
@Composable
fun SkyBackground(
    target: () -> SkyLook,
    backdrop: SkyBackdrop,
    animate: Boolean,
    modifier: Modifier = Modifier,
    calm: Boolean = false,
    lift: () -> Float = { 0f },
) {
    val animator = remember { SkyAnimator() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentTarget by rememberUpdatedState(target)
    LaunchedEffect(animate, calm, lifecycle, backdrop) {
        if (!animate) {
            // A still sky shows its target as it is.
            snapshotFlow { currentTarget().glass }.collect { backdrop.glass = it }
        } else {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var last = withFrameNanos { it }
                var moving = true
                while (moving) {
                    withFrameNanos { frame ->
                        val delta = ((frame - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                        last = frame
                        val settled = animator.advance(delta, currentTarget())
                        animator.current?.let { backdrop.glass = it.glass }
                        moving = !(calm && settled)
                    }
                }
            }
        }
    }
    val field = remember { ParticleField(seed = 1337) }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val art = SkyArt(this, size)
                val drops = DropArt(this)
                val plainDensity = Density(density, fontScale)
                onDrawBehind {
                    val t = animator.time.floatValue
                    val look = if (animate) animator.current ?: target() else target()
                    val rise = lift()
                    art.prepare(look)
                    val sunCenter = Offset(size.width * look.sunX, size.height * look.sunY - rise * 0.35f)
                    val moonCenter = Offset(size.width * MOON_X, size.height * MOON_Y - rise * 0.35f)

                    with(backdrop) {
                        recordBase(plainDensity) {
                            drawRect(art.gradient)
                            if (look.sun > 0.01f) drawSunGlow(art, sunCenter, look.sun, look.sunWarmth, t)
                            if (look.moon > 0.01f) {
                                withTransform({ translate(moonCenter.x, moonCenter.y) }) {
                                    drawCircle(art.moonGlow, radius = art.moonRadius * 6f, center = Offset.Zero, alpha = look.moon)
                                }
                            }
                        }
                    }
                    // What only lasts a moment (a shooting star, lightning, a splash) is left out of a sky
                    // that's standing still: caught for good, it would just be a mark on the picture.
                    if (look.stars > 0.01f) drawStars(field, t, look.stars, rise * 0.15f, shooting = animate)
                    if (look.sun > 0.01f) drawSun(art, sunCenter, look.sun, look.sunWarmth, t)
                    if (look.moon > 0.01f) drawMoon(art, moonCenter, look.moon)
                    with(backdrop) {
                        recordVeil(plainDensity) {
                            if (look.clouds > 0.01f) drawClouds(field, art, t, look.clouds, look.cloudDarkness, rise * 0.1f)
                            if (look.fog > 0.01f) drawFog(field, art, t, look.fog)
                            if (animate && look.lightning > 0.5f) drawLightningFlash(t, look.lightning)
                            if (look.dim > 0.001f) drawRect(Color.Black, alpha = look.dim)
                        }
                    }
                    if (look.rain > 0.01f) drawRain(field, t, look.rain, look.windTilt)
                    if (look.snow > 0.01f) drawSnow(field, art, t, look.snow)
                    if (animate && look.lightning > 0.5f) drawLightningBolt(t, look.lightning)
                    // Last, what's on the glass of the screen itself.
                    with(backdrop) {
                        recordWater(plainDensity, wet = look.rain > 0.01f) {
                            drawGlassRain(drops, t, look.rain, look.windTilt, splashes = animate)
                        }
                    }
                }
            },
    )
}

private const val MOON_X = 0.82f
private const val MOON_Y = 0.118f

/** How quickly the sky follows its target: it closes about two thirds of the gap in this many seconds. */
private const val FOLLOW_SECONDS = 0.22f

/** The sky's clock and the look it currently shows. */
@Stable
private class SkyAnimator {
    /** Seconds the sky has been moving for; only ticks while the app is visible. */
    val time = mutableFloatStateOf(0f)
    var current: SkyLook? = null
        private set

    /** Moves a frame on and returns true once the sky looks like [target]. */
    fun advance(delta: Float, target: SkyLook): Boolean {
        val from = current
        val next = when {
            from == null || from === target -> target
            else -> lerp(from, target, 1f - exp(-delta / FOLLOW_SECONDS)).let { if (it.closeTo(target)) target else it }
        }
        current = next
        time.floatValue = (time.floatValue + delta) % 3600f
        return next === target
    }
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

/**
 * Brushes and paths for the sky. Most are built once per screen size and moved around with
 * transforms; the few that depend on the look are rebuilt only when it changes.
 */
private class SkyArt(density: Density, private val size: Size) {
    val moonRadius = with(density) { 18.dp.toPx() }
    val sunRadius = with(density) { 44.dp.toPx() }

    // Glows are unit circles, scaled to size where they're drawn.
    val highGlow = Brush.radialGradient(listOf(Color(0x80FFF3C4), Color(0x24FFE7A0), Color.Transparent), center = Offset.Zero, radius = 1f)
    val lowGlow = Brush.radialGradient(listOf(Color(0x99FFB070), Color(0x33FF8A5C), Color.Transparent), center = Offset.Zero, radius = 1f)
    val highSun = Brush.radialGradient(
        0f to Color(0xFFFFFDF2), 0.36f to Color(0xF2FFF6D2), 0.46f to Color(0x99FFF1BF), 1f to Color.Transparent,
        center = Offset.Zero, radius = 1f,
    )
    val lowSun = Brush.radialGradient(
        0f to Color(0xFFFFF1D6), 0.36f to Color(0xF2FFD08A), 0.46f to Color(0x99FFA95E), 1f to Color.Transparent,
        center = Offset.Zero, radius = 1f,
    )
    val moonGlow = Brush.radialGradient(listOf(Color(0x40E8EEFF), Color.Transparent), center = Offset.Zero, radius = moonRadius * 6f)
    val flake = Brush.radialGradient(listOf(Color.White, Color(0x99FFFFFF), Color.Transparent), center = Offset.Zero, radius = 1f)

    /** One ray of sunlight, pointing up from the origin: a sliver that fades as it thins. */
    val ray = Path().apply {
        moveTo(-0.045f, 0f)
        lineTo(0f, -1f)
        lineTo(0.045f, 0f)
        close()
    }
    val rayBrush = Brush.verticalGradient(listOf(Color.Transparent, Color(0x66FFF6D8)), startY = -1f, endY = 0f)

    /** A cloud is five soft circles, drawn in white and tinted to the weather. */
    val puffs: List<Puff> = with(density) {
        listOf(
            Triple(-70f, 12f, 46f),
            Triple(-28f, -10f, 58f),
            Triple(26f, -4f, 54f),
            Triple(72f, 12f, 42f),
            Triple(0f, 22f, 60f),
        ).map { (x, y, r) ->
            val center = Offset(x.dp.toPx(), y.dp.toPx())
            val radius = r.dp.toPx()
            Puff(center, radius, Brush.radialGradient(listOf(Color(0x8CFFFFFF), Color(0x38FFFFFF), Color.Transparent), center = center, radius = radius))
        }
    }

    var gradient: Brush = Brush.verticalGradient(listOf(Color.Black, Color.Black))
        private set
    var cloudTint: ColorFilter? = null
        private set
    var moonLit = Path()
        private set
    var moonDark = Path()
        private set

    private var colours: Triple<Color, Color, Color>? = null
    private var darkness = -1f
    private var phase = -1f

    fun prepare(look: SkyLook) {
        val wanted = Triple(look.top, look.middle, look.bottom)
        if (wanted != colours) {
            colours = wanted
            gradient = Brush.verticalGradient(listOf(look.top, look.middle, look.bottom), startY = 0f, endY = size.height)
        }
        if (look.cloudDarkness != darkness) {
            darkness = look.cloudDarkness
            cloudTint = if (darkness <= 0f) null else ColorFilter.tint(lerp(Color.White, Color(0xFF3A4352), darkness), BlendMode.SrcIn)
        }
        if (abs(look.moonPhase - phase) > 0.002f) {
            phase = look.moonPhase
            moonLit = moonLitPath(Offset.Zero, moonRadius, phase)
            moonDark = Path().apply { addOval(Rect(Offset.Zero, moonRadius)) }
        }
    }
}

private fun DrawScope.drawSunGlow(art: SkyArt, center: Offset, amount: Float, warmth: Float, t: Float) {
    val pulse = 0.88f + 0.12f * sin(t * 0.5f)
    val radius = size.maxDimension * (0.64f - 0.1f * warmth)
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, pivot = Offset.Zero)
    }) {
        if (warmth < 0.99f) drawCircle(art.highGlow, radius = 1f, center = Offset.Zero, alpha = amount * pulse * (1f - warmth))
        if (warmth > 0.01f) drawCircle(art.lowGlow, radius = 1f, center = Offset.Zero, alpha = amount * pulse * warmth)
    }
}

/** The sun's disc, and around a high sun, rays that turn slowly and breathe. */
private fun DrawScope.drawSun(art: SkyArt, center: Offset, amount: Float, warmth: Float, t: Float) {
    val rays = (amount - 0.35f) / 0.65f * (1f - warmth)
    if (rays > 0.01f) {
        val count = 14
        for (i in 0 until count) {
            val long = i % 2 == 0
            val breathe = 0.82f + 0.18f * sin(t * 0.6f + i * 1.7f)
            val length = (if (long) 112f else 70f).dp.toPx() * breathe
            val width = (if (long) 130f else 96f).dp.toPx()
            withTransform({
                translate(center.x, center.y)
                rotate(i * 360f / count + t * 2.2f, pivot = Offset.Zero)
                translate(0f, -art.sunRadius * 0.5f)
                scale(width, length, pivot = Offset.Zero)
            }) {
                drawPath(art.ray, art.rayBrush, alpha = rays * (if (long) 0.4f else 0.26f))
            }
        }
    }
    withTransform({
        translate(center.x, center.y)
        scale(art.sunRadius, art.sunRadius, pivot = Offset.Zero)
    }) {
        if (warmth < 0.99f) drawCircle(art.highSun, radius = 1f, center = Offset.Zero, alpha = amount * (1f - warmth))
        if (warmth > 0.01f) drawCircle(art.lowSun, radius = 1f, center = Offset.Zero, alpha = amount * warmth)
    }
}

private fun DrawScope.drawMoon(art: SkyArt, center: Offset, amount: Float) {
    val r = art.moonRadius
    withTransform({ translate(center.x, center.y) }) {
        // The unlit part, faintly, as earthshine.
        drawPath(art.moonDark, Color.White, alpha = 0.10f * amount)
        drawPath(art.moonLit, Color(0xFFF4F1E6), alpha = 0.94f * amount)
        // Seas, where the lit part shows them.
        clipPath(art.moonLit) {
            drawCircle(Color(0xFF8C8A80), radius = r * 0.26f, center = Offset(-r * 0.28f, -r * 0.30f), alpha = 0.22f * amount)
            drawCircle(Color(0xFF8C8A80), radius = r * 0.18f, center = Offset(r * 0.30f, -r * 0.05f), alpha = 0.18f * amount)
            drawCircle(Color(0xFF8C8A80), radius = r * 0.22f, center = Offset(-r * 0.05f, r * 0.42f), alpha = 0.16f * amount)
            drawCircle(Color(0xFF8C8A80), radius = r * 0.10f, center = Offset(r * 0.42f, r * 0.46f), alpha = 0.16f * amount)
        }
    }
}

private fun DrawScope.drawStars(field: ParticleField, t: Float, amount: Float, rise: Float, shooting: Boolean) {
    val count = 120
    for (i in 0 until count) {
        val x = field.a[i] * size.width
        val y = field.b[i] * size.height * 0.62f - rise
        val radius = (0.6f + field.c[i] * 1.3f).dp.toPx()
        val twinkle = 0.55f + 0.45f * sin(t * (0.6f + field.d[i] * 2.2f) + field.e[i] * 6.28f)
        val alpha = (amount * twinkle * (0.35f + 0.65f * field.e[i])).coerceIn(0f, 1f)
        drawCircle(Color.White, radius = radius, center = Offset(x, y), alpha = alpha)
        // The brightest few glint.
        if (field.c[i] > 0.9f && field.e[i] > 0.5f) {
            val arm = radius * (2.4f + 2.2f * twinkle)
            val glint = alpha * 0.55f
            drawLine(Color.White, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = radius * 0.5f, cap = StrokeCap.Round, alpha = glint)
            drawLine(Color.White, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = radius * 0.5f, cap = StrokeCap.Round, alpha = glint)
        }
    }
    // An occasional shooting star.
    val period = 11f
    val cycle = floor(t / period)
    val local = t - cycle * period
    if (shooting && local < 0.9f) {
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

private fun DrawScope.drawClouds(field: ParticleField, art: SkyArt, t: Float, amount: Float, darkness: Float, rise: Float) {
    val count = 3 + (amount * 6).toInt()
    val baseAlpha = (0.30f + 0.5f * amount) * (0.75f + 0.15f * darkness)
    val span = 300.dp.toPx()
    for (i in 0 until count) {
        val k = 200 + i
        val s = 0.7f + field.b[k] + amount * 0.3f
        val y = size.height * (0.0f + field.a[k] * (0.28f + 0.17f * amount)) - rise * s
        // Bigger clouds are nearer, so they cross the sky faster.
        val speed = (4f + field.c[k] * 7f + s * 4f).dp.toPx()
        val travel = size.width + span * s
        val x = ((field.d[k] * travel + t * speed) % travel) - span * s / 2f
        val alpha = baseAlpha * (0.6f + 0.4f * field.e[k])
        withTransform({
            translate(x, y)
            scale(s, s, pivot = Offset.Zero)
        }) {
            for (puff in art.puffs) drawCircle(puff.brush, radius = puff.radius, center = puff.center, alpha = alpha, colorFilter = art.cloudTint)
        }
    }
}

private fun DrawScope.drawFog(field: ParticleField, art: SkyArt, t: Float, amount: Float) {
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
            for (puff in art.puffs) drawCircle(puff.brush, radius = puff.radius, center = puff.center, alpha = 0.22f * amount)
        }
    }
}

private fun DrawScope.drawRain(field: ParticleField, t: Float, amount: Float, tilt: Float) {
    val count = (amount * 210).toInt().coerceAtMost(field.count)
    val height = size.height
    val drift = height * abs(tilt)
    for (i in 0 until count) {
        // Nearer drops are longer, thicker, brighter and faster.
        val near = field.e[i]
        val speed = height * (0.85f + near * 0.75f + field.a[i] * 0.2f)
        val length = (9f + near * 24f + field.b[i] * 6f).dp.toPx()
        val travel = height + length
        val y = ((t * speed + field.c[i] * travel) % travel) - length / 2f
        val x0 = field.d[i] * (size.width + drift) - if (tilt > 0f) drift else 0f
        val x = x0 + y * tilt
        drawLine(
            color = Color.White,
            start = Offset(x - tilt * length, y - length),
            end = Offset(x, y),
            strokeWidth = (0.8f + near * 1.2f).dp.toPx(),
            cap = StrokeCap.Round,
            alpha = 0.12f + 0.32f * near,
        )
    }
}

private fun DrawScope.drawSnow(field: ParticleField, art: SkyArt, t: Float, amount: Float) {
    val count = (amount * 170).toInt().coerceAtMost(field.count)
    for (i in 0 until count) {
        val near = field.b[i]
        val radius = (1.2f + near * 2.6f).dp.toPx()
        val speed = size.height * (0.04f + near * 0.055f + field.a[i] * 0.025f)
        val travel = size.height + radius * 8f
        val y = ((t * speed + field.c[i] * travel) % travel) - radius * 4f
        val sway = (8f + field.e[i] * 22f).dp.toPx() * sin(t * (0.4f + field.d[i]) + field.e[i] * 6.28f)
        val x = field.d[i] * size.width + sway
        if (near > 0.86f) {
            // The nearest flakes are out of focus.
            val soft = radius * 2.6f
            withTransform({
                translate(x, y)
                scale(soft, soft, pivot = Offset.Zero)
            }) {
                drawCircle(art.flake, radius = 1f, center = Offset.Zero, alpha = 0.5f)
            }
        } else {
            drawCircle(Color.White, radius = radius, center = Offset(x, y), alpha = 0.4f + near * 0.55f)
        }
    }
}

/** When the next strike is, and how bright it is at [t]: 0 when the sky is dark. */
private fun lightning(t: Float): Triple<Float, Float, Random> {
    val period = 7f
    val cycle = floor(t / period)
    val random = Random(cycle.toInt() * 7919 + 13)
    val start = 1.2f + random.nextFloat() * 4f
    val local = t - cycle * period - start
    val flash = when {
        local < 0f || local > 0.65f -> 0f
        local < 0.06f -> 1f
        local < 0.12f -> 0.3f
        local < 0.2f -> 0.85f
        else -> 0.85f * (1f - (local - 0.2f) / 0.45f)
    }.coerceIn(0f, 1f)
    return Triple(local, flash, random)
}

private fun DrawScope.drawLightningFlash(t: Float, amount: Float) {
    val (_, flash, _) = lightning(t)
    if (flash > 0f) drawRect(Color.White, alpha = 0.28f * flash * amount)
}

private fun DrawScope.drawLightningBolt(t: Float, amount: Float) {
    val (local, flash, random) = lightning(t)
    if (flash <= 0f || local >= 0.22f) return
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
    drawPath(path, Color.White.copy(alpha = 0.35f * flash * amount), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Color.White.copy(alpha = 0.95f * flash * amount), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
