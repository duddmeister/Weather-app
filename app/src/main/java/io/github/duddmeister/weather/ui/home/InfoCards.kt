package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.data.WeatherRepository
import io.github.duddmeister.weather.domain.Nowcast
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.window
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.PrecipType
import io.github.duddmeister.weather.model.WeatherAlert
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.components.CardLabel
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.Motion
import io.github.duddmeister.weather.ui.components.rememberAppearProgress
import io.github.duddmeister.weather.ui.components.rememberHaptics
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlin.math.min

private fun severityColor(alert: WeatherAlert): Color = when (alert.level) {
    // Colour-coded warnings (Met Office and other European services) keep their own colours.
    4 -> Color(0xFFFF5252)
    3 -> Color(0xFFFFA726)
    2 -> Color(0xFFFFEE58)
    else -> when (alert.severityRank) {
        4 -> Color(0xFFE040FB)
        3 -> Color(0xFFFF5252)
        2 -> Color(0xFFFFA726)
        else -> Color(0xFFFFEE58)
    }
}

@Composable
fun AlertsCard(snapshot: WeatherSnapshot, nowMillis: Long, use24h: Boolean) {
    val text = remember(snapshot.timezone, use24h) { TimeText(snapshot.zoneId(), use24h) }
    // The card takes a wash of its most serious warning's colour.
    val worst = remember(snapshot.alerts) { snapshot.alerts.maxByOrNull { (it.level ?: 0) * 10 + it.severityRank }?.let(::severityColor) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
        tint = worst?.copy(alpha = 0.10f) ?: Color.Unspecified,
    ) {
        // The US service issues "alerts" (watches, warnings, advisories); European services issue warnings.
        val noun = if (snapshot.alertsSource == WeatherRepository.SOURCE_METEOALARM) "warning" else "alert"
        CardLabel(
            Icons.Rounded.Warning,
            if (snapshot.alerts.size == 1) "Weather $noun" else "${snapshot.alerts.size} weather ${noun}s",
            Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        snapshot.alerts.forEach { alert -> AlertRow(alert, text, nowMillis) }
    }
}

@Composable
private fun AlertRow(alert: WeatherAlert, text: TimeText, nowMillis: Long) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.snappy(), label = "chevron")
    val haptics = rememberHaptics()
    val colour = severityColor(alert)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Show less" else "Show more", role = Role.Button) {
                haptics.tick()
                expanded = !expanded
            }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // A bar in the warning's colour runs down the side of everything it says.
            .drawBehind {
                drawRoundRect(colour, topLeft = Offset(0f, 2.dp.toPx()), size = Size(4.dp.toPx(), size.height - 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
            }
            .padding(start = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(alert.event, style = MaterialTheme.typography.titleMedium, color = Color.White)
                val details = listOfNotNull(alert.window(text, nowMillis), alert.sender).joinToString(" · ")
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = SkyText.Secondary,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.glide()) + fadeIn(tween(Motion.FADE_MILLIS, delayMillis = 80)),
            exit = shrinkVertically(Motion.glide()) + fadeOut(tween(120)),
        ) {
            Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                alert.headline?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White) }
                alert.description?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
                }
                alert.instruction?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun NowcastCard(nowcast: Nowcast, snapshot: WeatherSnapshot, use24h: Boolean) {
    val text = remember(snapshot.timezone, use24h) { TimeText(snapshot.zoneId(), use24h) }
    val colour = when (nowcast.kind) {
        PrecipType.Snow -> Color.White
        PrecipType.FreezingRain, PrecipType.Hail -> Color(0xFFBFEAFF)
        else -> Color(0xFF6CC7FF)
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(Icons.Rounded.Umbrella, "Next 2 hours")
        Spacer(Modifier.height(8.dp))
        Text(nowcast.headline, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(14.dp))
        val bars = nowcast.bars
        val grow by rememberAppearProgress(durationMillis = 900)
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val scaleStyle = MaterialTheme.typography.labelSmall.copy(color = SkyText.Faint)
        val density = LocalDensity.current
        val scale = remember(scaleStyle, density) { listOf(4f to measurer.measure("Heavy", scaleStyle), 1f to measurer.measure("Light", scaleStyle)) }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clearAndSetSemantics { }
                .drawWithCache {
                    val maxRate = 6f
                    val fillBrush = Brush.verticalGradient(listOf(colour, colour.copy(alpha = 0.55f)))
                    val dashes = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                    onDrawBehind {
                        val slot = size.width / bars.size
                        // Where light rain ends and heavy rain begins.
                        scale.forEach { (level, label) ->
                            val y = size.height - size.height * min(level / maxRate, 1f)
                            drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dashes)
                            drawText(label, topLeft = Offset(size.width - label.size.width, y - label.size.height - 1.dp.toPx()))
                        }
                        bars.forEachIndexed { i, bar ->
                            // Bars rise left to right as the card comes in.
                            val rise = (grow * 1.5f - 0.5f * i / bars.size).coerceIn(0f, 1f)
                            val fraction = min(bar.mmPerHour.toFloat() / maxRate, 1f)
                            val wet = bar.mmPerHour > 0.0
                            val height = (if (wet) (size.height * fraction).coerceAtLeast(4.dp.toPx()) else 3.dp.toPx()) * rise
                            drawRoundRect(
                                brush = fillBrush,
                                topLeft = Offset(i * slot + slot * 0.17f, size.height - height),
                                size = Size(slot * 0.66f, height),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                alpha = if (wet) 0.95f else 0.22f,
                            )
                        }
                    }
                },
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            val labelled = listOf(0, bars.size / 2, bars.size - 1).distinct()
            labelled.forEachIndexed { i, index ->
                val label = if (index == 0) "Now" else text.time(bars[index].start)
                Text(label, style = MaterialTheme.typography.labelSmall, color = SkyText.Tertiary)
                if (i < labelled.lastIndex) Spacer(Modifier.weight(1f))
            }
        }
    }
}
