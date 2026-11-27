package io.github.duddmeister.weather.ui.skies

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.GlassIconButton
import io.github.duddmeister.weather.ui.components.Motion
import io.github.duddmeister.weather.ui.components.WeatherIcon
import io.github.duddmeister.weather.ui.sky.SkySample
import io.github.duddmeister.weather.ui.theme.SkyText

/**
 * Every sky the app can draw, one to a page, whatever the weather is doing: the way to see rain on
 * the glass on a dry day. The sky itself is the app's one sky, drawn behind this screen and blended
 * from page to page as they're swiped, so all this shows is which one it is and what to look for.
 *
 * @param moving false when the sky is standing still (Animated sky is off, or the phone's animations are)
 */
@Composable
fun SkiesScreen(samples: List<SkySample>, pagerState: PagerState, moving: Boolean, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { samples[it].id }) { index ->
            val sample = samples[index]
            // The sky has the screen to itself; what it's showing sits on a pane of glass at the foot.
            Box(
                Modifier.fillMaxSize().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 56.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                GlassCard(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sample.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        Spacer(Modifier.width(12.dp))
                        WeatherIcon(sample.weatherCode, sample.isDay, 40.dp, described = false)
                    }
                    Spacer(Modifier.height(8.dp))
                    // Room for the longest note, so the glass doesn't change height from one sky to the next.
                    Text(sample.note, style = MaterialTheme.typography.bodyMedium, color = SkyText.Secondary, minLines = 3)
                    if (!moving) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Animations are off, so this sky is standing still.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SkyText.Tertiary,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(8.dp))
            Text("Skies", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.semantics { heading() })
        }
        Dots(
            count = samples.size,
            current = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 25.dp),
        )
    }
}

/** Which sky of how many; the one showing stretches into a pill, as the places on the forecast do. */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Sky ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val emphasis by animateFloatAsState(if (index == current) 1f else 0f, Motion.snappy(), label = "dot")
            Box(
                Modifier
                    .width(6.dp + 12.dp * emphasis.coerceIn(0f, 1.2f))
                    .height(6.dp)
                    .graphicsLayer { alpha = 0.4f + 0.6f * emphasis.coerceIn(0f, 1f) }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
