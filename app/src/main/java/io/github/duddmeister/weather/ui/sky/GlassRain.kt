package io.github.duddmeister.weather.ui.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * How water behaves on a pane of glass, as pure functions of time: nothing is remembered from one
 * frame to the next, so the rain can be drawn for any moment (and stands still as a single frame
 * when motion is off).
 */
internal object GlassRainMotion {
    /** Seconds a drop takes to land: it spreads out past its size and settles back. */
    const val LANDING = 0.24f

    /** Seconds a still drop takes to dry away at the end of its life. */
    const val DRYING = 1.6f

    /**
     * How much of a drop is there, [age] seconds after it landed, if it lives [life] seconds.
     * Slightly more than 1 while it lands (the splash spreads, then pulls back), 1 while it sits,
     * falling to 0 as it dries. 0 outside its life.
     */
    fun presence(age: Float, life: Float): Float = when {
        age < 0f || age >= life -> 0f
        age < LANDING -> {
            // Out to 1.18 and back to 1.
            val p = age / LANDING
            val overshoot = 2.2f
            1f + (overshoot + 1f) * (p - 1f) * (p - 1f) * (p - 1f) + overshoot * (p - 1f) * (p - 1f)
        }
        age > life - DRYING -> (life - age) / DRYING
        else -> 1f
    }

    /**
     * The ring a drop throws out as it lands: 0 when it hits, 1 when the ring has spread and gone.
     * Null when there's no ring to draw.
     */
    fun splash(age: Float): Float? = if (age in 0f..SPLASH) age / SPLASH else null

    private const val SPLASH = 0.38f

    /**
     * How far a drop has run, in seconds' worth of its average speed, [seconds] after it let go.
     * Water on glass sticks and slips: it creeps, darts, and creeps again, but never goes back up.
     */
    fun slide(seconds: Float): Float = if (seconds <= 0f) 0f else seconds - STICK * sin(seconds * SLIP_RATE) / SLIP_RATE

    /** The drop's speed at that moment, as a multiple of its average: between 0.15 and 1.85. */
    fun pace(seconds: Float): Float = if (seconds <= 0f) 0f else 1f - STICK * cos(seconds * SLIP_RATE)

    private const val STICK = 0.85f
    private const val SLIP_RATE = 2.6f

    /** A repeatable random number in 0..1 for the [slot]-th property of drop [index] in its [cycle]-th life. */
    fun chance(index: Int, cycle: Int, slot: Int): Float {
        var h = index * 374_761_393 + cycle * 668_265_263 + slot * 1_274_126_177
        h = (h xor (h ushr 13)) * 1_274_126_177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2_147_483_648f
    }
}

/** Brushes and shapes for water on glass, all in a drop's own space: its centre at 0, its radius 1. */
internal class DropArt(density: Density) {
    /** Darker where the drop is thick at the top, bright where it gathers the light at the bottom. */
    val body = Brush.verticalGradient(
        0f to Color(0x5C000000),
        0.42f to Color(0x14000000),
        0.6f to Color(0x1FFFFFFF),
        1f to Color(0x85FFFFFF),
        startY = -1f,
        endY = 1f,
    )
    val rim = Brush.verticalGradient(listOf(Color(0x66000000), Color(0xA6FFFFFF)), startY = -1f, endY = 1f)
    val rimStroke = Stroke(width = 0.12f)

    private val shape = Path()

    /**
     * The outline of a drop on the move: round below, drawn out above into a tail [tail] radii
     * long (0 leaves it a circle). The same path is reshaped for every drop, so use it at once.
     */
    fun teardrop(tail: Float): Path {
        val tip = -1f - tail
        val pull = tail / 1.1f
        // The top two curves ease from a circle's to a teardrop's as the tail grows.
        val shoulderX = 0.5523f + (0.34f - 0.5523f) * pull
        val shoulderY = tip + (0.75f * pull)
        val flankY = -0.5523f - 0.07f * pull
        return shape.apply {
            rewind()
            moveTo(0f, tip)
            cubicTo(shoulderX, shoulderY, 1f, flankY, 1f, 0f)
            cubicTo(1f, 0.5523f, 0.5523f, 1f, 0f, 1f)
            cubicTo(-0.5523f, 1f, -1f, 0.5523f, -1f, 0f)
            cubicTo(-1f, flankY, -shoulderX, shoulderY, 0f, tip)
            close()
        }
    }

    /** The wet track behind a running drop, in a space where it runs from 0 (dry end) to 1 (the drop). */
    val trail = Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0x14FFFFFF), 1f to Color(0x3DFFFFFF), startY = 0f, endY = 1f)
    val trailPath = Path()

    val splashStroke = Stroke(width = with(density) { 1.2.dp.toPx() })
    val dp = with(density) { 1.dp.toPx() }
}

/**
 * Rain on the glass of the screen. Small drops land with a splash, sit as beads and dry away;
 * bigger ones gather, let go and run down the pane in fits and starts, meandering a little and
 * leaving a wet track that breaks into beads behind them.
 *
 * @param amount how hard it's raining, 0..1
 * @param tilt how far the wind pushes a running drop sideways per unit it falls
 */
internal fun DrawScope.drawGlassRain(art: DropArt, t: Float, amount: Float, tilt: Float) {
    val fade = (amount / 0.2f).coerceIn(0f, 1f)
    if (fade <= 0f) return
    val dp = art.dp

    // Beads: where a drop has landed and stays put. As the rain picks up or eases off (it does both
    // while one place's sky blends into another's), the newest drop fades rather than popping.
    val beads = 16f + amount * 58f
    for (i in 0 until ceil(beads).toInt()) {
        val shown = fade * (beads - i).coerceAtMost(1f)
        val life = 7f + 10f * GlassRainMotion.chance(i, 0, 0)
        val clock = t + life * GlassRainMotion.chance(i, 0, 1)
        val cycle = floor(clock / life).toInt()
        val age = clock - cycle * life
        val presence = GlassRainMotion.presence(age, life)
        if (presence <= 0f) continue
        val x = GlassRainMotion.chance(i, cycle, 2) * size.width
        val y = GlassRainMotion.chance(i, cycle, 3) * size.height
        // Mostly small, now and then a fat one.
        val big = GlassRainMotion.chance(i, cycle, 4)
        val radius = (1.8f + 4.4f * big * big * big) * dp
        if (radius > 2.6f * dp) drawSplash(art, x, y, radius, age, seed = i * 31 + cycle, alpha = shown)
        drawDrop(art, x, y, radius * presence.coerceAtMost(1.2f), tail = 0f, alpha = presence.coerceAtMost(1f) * shown)
    }

    // Runners: drops heavy enough to let go.
    val runners = 3f + amount * 9f
    for (n in 0 until ceil(runners).toInt()) {
        val i = 500 + n
        val shown = fade * (runners - n).coerceAtMost(1f)
        // Long enough for the slowest drop to wait, let go and run off the bottom of the screen.
        val life = 10f + 4f * GlassRainMotion.chance(i, 0, 0)
        val clock = t + life * GlassRainMotion.chance(i, 0, 1)
        val cycle = floor(clock / life).toInt()
        val age = clock - cycle * life
        val radius = (4.2f + 3.2f * GlassRainMotion.chance(i, cycle, 2)) * dp
        val startX = (0.05f + 0.9f * GlassRainMotion.chance(i, cycle, 3)) * size.width
        val startY = (-0.04f + 0.6f * GlassRainMotion.chance(i, cycle, 4)) * size.height
        val hold = 0.6f + 1.2f * GlassRainMotion.chance(i, cycle, 5)
        // Between a fifth and nearly half the screen a second. Nothing about where a drop is depends
        // on how hard it's raining, so a change in the rain never moves one.
        val speed = size.height * (0.2f + 0.25f * GlassRainMotion.chance(i, cycle, 6))
        val sway = (2.5f + 5f * GlassRainMotion.chance(i, cycle, 7)) * dp
        val bend = (2f * PI).toFloat() / ((70f + 90f * GlassRainMotion.chance(i, cycle, 8)) * dp)
        val turn = GlassRainMotion.chance(i, cycle, 9) * 6.28f

        // Where the track is at a given depth below its start: every later point of the drop's path.
        fun pathX(fallen: Float) = startX + sway * (sin(fallen * bend + turn) - sin(turn)) + tilt * 0.35f * fallen

        val running = age - hold
        val fallen = speed * GlassRainMotion.slide(running)
        val y = startY + fallen
        val landed = GlassRainMotion.presence(age, life = Float.MAX_VALUE).coerceAtMost(1.2f)
        // It swells while it waits, then lets go.
        val swell = if (running < 0f) 0.85f + 0.15f * (age / hold) else 1f

        if (fallen > radius) {
            // The track it has left: narrower than the drop, and drying to nothing behind it.
            val length = minOf(speed * 1.2f, size.height * 0.36f, fallen)
            val top = fallen - length
            // Once the drop has run off the bottom, its track follows it out.
            if (startY + top > size.height) continue
            // Enough points to follow every bend.
            val steps = (length / (7f * dp)).toInt().coerceIn(4, 64)
            art.trailPath.rewind()
            for (s in 0..steps) {
                val f = s / steps.toFloat()
                val half = radius * (0.08f + 0.34f * f)
                val edge = pathX(top + length * f) - half
                if (s == 0) art.trailPath.moveTo(edge, f) else art.trailPath.lineTo(edge, f)
            }
            for (s in steps downTo 0) {
                val f = s / steps.toFloat()
                val half = radius * (0.08f + 0.34f * f)
                art.trailPath.lineTo(pathX(top + length * f) + half, f)
            }
            art.trailPath.close()
            withTransform({
                translate(0f, startY + top)
                scale(1f, length, pivot = Offset.Zero)
            }) {
                drawPath(art.trailPath, art.trail, alpha = shown)
            }
            // Beads the track breaks into, each drying in its own time.
            for (b in 0 until 4) {
                val depth = (0.1f + 0.2f * b + 0.14f * GlassRainMotion.chance(i, cycle, 20 + b)) * size.height
                if (depth > fallen - radius * 1.5f) continue
                val since = running - depth / speed
                val left = 1f - since / 3.2f
                if (left <= 0f) continue
                val side = (GlassRainMotion.chance(i, cycle, 30 + b) - 0.5f) * radius * 0.5f
                drawDrop(art, pathX(depth) + side, startY + depth, radius * (0.22f + 0.2f * GlassRainMotion.chance(i, cycle, 40 + b)), tail = 0f, alpha = left.coerceAtMost(1f) * shown)
            }
        } else if (running < 0f) {
            drawSplash(art, startX, startY, radius, age, seed = i * 31 + cycle, alpha = shown)
        }
        if (y - radius * 3f > size.height) continue
        // The faster it goes, the longer it's drawn out behind.
        val tail = if (running <= 0f) 0f else 1.1f * ((GlassRainMotion.pace(running) - 0.15f) / 1.7f).coerceIn(0f, 1f)
        drawDrop(art, pathX(fallen), y, radius * landed * swell, tail, alpha = shown)
    }
}

/**
 * One drop of water: a soft shadow, a body that's dark above and bright below, a rim and two glints.
 * [tail] draws it out upwards, in radii, as it runs.
 */
private fun DrawScope.drawDrop(art: DropArt, x: Float, y: Float, radius: Float, tail: Float, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(radius, radius, pivot = Offset.Zero)
    }) {
        drawCircle(Color.Black, radius = 1.04f, center = Offset(0.1f, 0.3f), alpha = 0.16f * alpha)
        if (tail > 0.02f) {
            val outline = art.teardrop(tail)
            drawPath(outline, art.body, alpha = alpha)
            drawPath(outline, art.rim, alpha = alpha, style = art.rimStroke)
        } else {
            drawCircle(art.body, radius = 1f, center = Offset.Zero, alpha = alpha)
            drawCircle(art.rim, radius = 1f, center = Offset.Zero, alpha = alpha, style = art.rimStroke)
        }
        drawCircle(Color.White, radius = 0.21f, center = Offset(-0.34f, -0.36f), alpha = 0.92f * alpha)
        drawCircle(Color.White, radius = 0.09f, center = Offset(0.36f, 0.48f), alpha = 0.55f * alpha)
    }
}

/** A drop hitting the glass: a ring spreading from where it lands, and a few specks thrown clear. */
private fun DrawScope.drawSplash(art: DropArt, x: Float, y: Float, radius: Float, age: Float, seed: Int, alpha: Float) {
    val spread = GlassRainMotion.splash(age) ?: return
    val fading = (1f - spread) * alpha
    drawCircle(Color.White, radius = radius * (1f + 2.2f * spread), center = Offset(x, y), alpha = 0.3f * fading, style = art.splashStroke)
    for (k in 0 until 6) {
        val angle = (k + GlassRainMotion.chance(seed, k, 50)) * (2f * PI.toFloat() / 6f)
        val reach = radius * (1.2f + (1.8f + 1.6f * GlassRainMotion.chance(seed, k, 51)) * spread)
        drawCircle(
            Color.White,
            radius = (0.5f + 0.5f * GlassRainMotion.chance(seed, k, 52)) * art.dp,
            center = Offset(x + reach * cos(angle), y + reach * sin(angle)),
            alpha = 0.7f * fading,
        )
    }
}
