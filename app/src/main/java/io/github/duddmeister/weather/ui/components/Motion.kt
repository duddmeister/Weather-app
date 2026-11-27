package io.github.duddmeister.weather.ui.components

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The springs and timings the app moves with, so everything shares one feel. */
object Motion {
    /** Things arriving and settling into place. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 170f)

    /** Quick responses to a touch: presses, toggles, sliding highlights. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 520f)

    /** Moving without any overshoot, for things that must not wobble (sizes, positions of text). */
    fun <T> glide(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

    const val FADE_MILLIS = 220
}

/**
 * Remembers which cards on a page have already animated in, so scrolling back or a data refresh
 * doesn't replay the entrance.
 */
@Stable
class RevealState internal constructor(private val createdAt: Long) {
    private val shown = HashSet<String>()

    /** True the first time [key] asks, false afterwards. */
    internal fun claim(key: String): Boolean = shown.add(key)

    /** Cards on screen when the page first appears arrive one after another; later ones don't wait. */
    internal fun delayFor(order: Int): Long =
        if (SystemClock.uptimeMillis() - createdAt < STAGGER_WINDOW_MILLIS) (order * STAGGER_MILLIS).coerceAtMost(MAX_DELAY_MILLIS) else 0L

    private companion object {
        const val STAGGER_WINDOW_MILLIS = 700L
        const val STAGGER_MILLIS = 55L
        const val MAX_DELAY_MILLIS = 385L
    }
}

@Composable
fun rememberRevealState(key: Any?): RevealState = remember(key) { newRevealState() }

/** A record that starts now; for holding one per page somewhere that outlives the page. */
fun newRevealState(): RevealState = RevealState(SystemClock.uptimeMillis())

/** When content inside a [Reveal] may start its own entrance (ms), or null if it has been seen before. */
private val LocalAppearDelay = compositionLocalOf<Long?> { null }

/** Fades, lifts and settles [content] into place the first time it appears on the page. */
@Composable
fun Reveal(state: RevealState, key: String, order: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val first = remember(state, key) { state.claim(key) }
    val delayMillis = remember(state, key) { if (first) state.delayFor(order) else 0L }
    val progress = remember(state, key) { Animatable(if (first) 0f else 1f) }
    if (first) {
        LaunchedEffect(progress) {
            // With the system's animations off, everything is simply there: no staggered popping in.
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                progress.snapTo(1f)
            } else {
                delay(delayMillis)
                progress.animateTo(1f, Motion.settle())
            }
        }
    }
    val lift = with(LocalDensity.current) { 30.dp.toPx() }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            // Opaque again a little before it stops moving, so the landing is crisp.
            alpha = (p * 1.25f).coerceIn(0f, 1f)
            translationY = (1f - p) * lift
            val scale = 0.95f + 0.05f * p
            scaleX = scale
            scaleY = scale
        },
    ) {
        CompositionLocalProvider(LocalAppearDelay provides if (first) delayMillis else null) { content() }
    }
}

/**
 * Goes 0 → 1 once, just after the surrounding [Reveal] brings its card in (or starts at 1 if the card
 * was seen before). Gauges, bars and chart lines use it to draw themselves in.
 */
@Composable
fun rememberAppearProgress(durationMillis: Int = 900, extraDelayMillis: Int = 0): State<Float> {
    val appearDelay = LocalAppearDelay.current
    val progress = remember { Animatable(if (appearDelay == null) 1f else 0f) }
    if (appearDelay != null) {
        LaunchedEffect(progress) {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                progress.snapTo(1f)
            } else {
                progress.animateTo(1f, tween(durationMillis, (appearDelay + 150 + extraDelayMillis).toInt(), FastOutSlowInEasing))
            }
        }
    }
    return progress.asState()
}

/** Three softly pulsing dots: an activity indicator that isn't a spinning circle. */
@Composable
fun PulsingDots(color: Color, modifier: Modifier = Modifier, dotSize: Dp = 5.dp) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dotSize * 0.7f), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val pulse by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 170),
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        alpha = pulse
                        val scale = 0.7f + 0.3f * pulse
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

/** Dips slightly while pressed and springs back on release. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.975f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.snappy(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Sweeps 0 → 1 over and over; placeholders use it to shimmer while the first forecast loads. */
@Composable
fun rememberShimmer(): State<Float> = rememberInfiniteTransition(label = "shimmer").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
    label = "sweep",
)

/** Small taps of feedback for touches that change something. */
@Stable
class Haptics internal constructor(private val view: View) {
    /** Moving a selection: a segment, an hour, a day. */
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT < 34 -> HapticFeedbackConstants.CLOCK_TICK
            on -> HapticFeedbackConstants.TOGGLE_ON
            else -> HapticFeedbackConstants.TOGGLE_OFF
        },
    )

    /** Crossing the point where letting go will do something. */
    fun threshold() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK,
    )

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
