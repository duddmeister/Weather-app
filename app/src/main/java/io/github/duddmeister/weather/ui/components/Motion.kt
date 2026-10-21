package io.github.duddmeister.weather.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
        const val STAGGER_MILLIS = 60L
        const val MAX_DELAY_MILLIS = 420L
    }
}

@Composable
fun rememberRevealState(key: Any?): RevealState = remember(key) { RevealState(SystemClock.uptimeMillis()) }

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
            delay(delayMillis)
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
        }
    }
    val lift = with(LocalDensity.current) { 36.dp.toPx() }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * lift
            val scale = 0.94f + 0.06f * p
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
            progress.animateTo(1f, tween(durationMillis, (appearDelay + 150 + extraDelayMillis).toInt(), FastOutSlowInEasing))
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
