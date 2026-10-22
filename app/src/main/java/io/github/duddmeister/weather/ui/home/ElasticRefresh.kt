package io.github.duddmeister.weather.ui.home

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.ui.components.PulsingDots
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.roundToInt

private enum class RefreshPhase { Pulling, Armed, Refreshing, Done, Failed }

/** Pull distance (after Material's 2:1 drag resistance) that triggers a refresh. */
private val Threshold = 60.dp

/** However far it's pulled, the page never moves further than this. */
private val MaxPull = 150.dp

private val HeaderHeight = 28.dp

/** How long "Updated just now" shows before the page springs back: long enough to register, no longer. */
private const val DONE_HOLD_MILLIS = 400L

/**
 * Pull-to-refresh that drags the whole page down and shows a status line in the gap above it,
 * instead of a spinner over the content. While a refresh started this way runs, the page rests a
 * little lower; when it finishes, the result shows for a moment and the page springs back.
 */
@Composable
fun ElasticRefreshBox(
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    headerTop: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    // pulled: this gesture asked for a refresh; started: the app picked it up; done: show the result.
    var pulled by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            done = false
            if (pulled) started = true
        } else if (pulled && started) {
            pulled = false
            started = false
            done = true
            delay(DONE_HOLD_MILLIS)
            done = false
        }
    }
    // Don't hold the page down if the refresh never starts.
    LaunchedEffect(pulled) {
        if (pulled) {
            delay(2_500)
            if (!started) pulled = false
        }
    }

    val armed by remember { derivedStateOf { state.distanceFraction >= 1f } }
    val view = LocalView.current
    LaunchedEffect(armed) {
        if (armed && !pulled && !done) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                },
            )
        }
    }

    val density = LocalDensity.current
    val thresholdPx = with(density) { Threshold.toPx() }
    val maxPx = with(density) { MaxPull.toPx() }
    val headerTopPx = with(density) { headerTop.toPx() }
    val headerHalfPx = with(density) { HeaderHeight.toPx() / 2f }

    // Material reports progress as a fraction of the threshold after halving the drag. Undo that so
    // the page follows the finger closely at first, then stiffens smoothly towards MaxPull.
    fun pagePx(fraction: Float): Float = maxPx * (1f - exp(-(fraction * thresholdPx * 2f) / maxPx))
    val restPx = pagePx(1f)

    Box(
        modifier.pullToRefresh(
            isRefreshing = pulled || done,
            state = state,
            threshold = Threshold,
            onRefresh = {
                pulled = true
                // An automatic refresh may already be running; then this pull just waits for it.
                started = refreshing
                onRefresh()
            },
        ),
    ) {
        val phase = when {
            done && error != null -> RefreshPhase.Failed
            done -> RefreshPhase.Done
            pulled -> RefreshPhase.Refreshing
            armed -> RefreshPhase.Armed
            else -> RefreshPhase.Pulling
        }
        RefreshHeader(
            phase = phase,
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderHeight)
                .offset {
                    // Keep the status centred in the gap that opens above the page.
                    val gap = pagePx(state.distanceFraction)
                    IntOffset(0, (headerTopPx + gap / 2f - headerHalfPx).roundToInt())
                }
                .graphicsLayer {
                    val shown = (pagePx(state.distanceFraction) / restPx).coerceIn(0f, 1f)
                    alpha = shown * shown
                    val scale = 0.85f + 0.15f * shown
                    scaleX = scale
                    scaleY = scale
                },
        )
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = pagePx(state.distanceFraction) }) {
            content()
        }
    }
}

@Composable
private fun RefreshHeader(phase: RefreshPhase, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(
        targetValue = if (phase == RefreshPhase.Armed) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "arrowTurn",
    )
    val group = when (phase) {
        RefreshPhase.Pulling, RefreshPhase.Armed -> 0
        RefreshPhase.Refreshing -> 1
        RefreshPhase.Done -> 2
        RefreshPhase.Failed -> 3
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = group,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(140)) },
            label = "refreshHeader",
        ) { shown ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (shown) {
                    0 -> {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (phase == RefreshPhase.Armed) "Release to refresh" else "Pull to refresh",
                            style = MaterialTheme.typography.labelLarge,
                            color = SkyText.Secondary,
                        )
                    }
                    1 -> {
                        Text("Updating", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        Spacer(Modifier.width(10.dp))
                        PulsingDots(Color.White)
                    }
                    2 -> {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF8BE0A4), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Updated just now", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    else -> {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = SkyText.Warning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Couldn't update", style = MaterialTheme.typography.labelLarge, color = SkyText.Warning)
                    }
                }
            }
        }
    }
}
