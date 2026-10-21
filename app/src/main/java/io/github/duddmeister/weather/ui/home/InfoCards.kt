package io.github.duddmeister.weather.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.domain.Nowcast
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.domain.zoneId
import io.github.duddmeister.weather.model.PrecipType
import io.github.duddmeister.weather.model.WeatherAlert
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.components.CardLabel
import io.github.duddmeister.weather.ui.components.GlassCard
import io.github.duddmeister.weather.ui.components.rememberAppearProgress
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlin.math.min

private fun severityColor(alert: WeatherAlert): Color = when (alert.severityRank) {
    4 -> Color(0xFFE040FB)
    3 -> Color(0xFFFF5252)
    2 -> Color(0xFFFFA726)
    else -> Color(0xFFFFEE58)
}

@Composable
fun AlertsCard(snapshot: WeatherSnapshot, nowMillis: Long, use24h: Boolean) {
    val text = remember(snapshot.timezone, use24h) { TimeText(snapshot.zoneId(), use24h) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(Icons.Rounded.Warning, if (snapshot.alerts.size == 1) "Weather alert" else "${snapshot.alerts.size} weather alerts")
        snapshot.alerts.forEachIndexed { index, alert ->
            Spacer(Modifier.height(if (index == 0) 10.dp else 14.dp))
            AlertRow(alert, text, nowMillis)
        }
    }
}

@Composable
private fun AlertRow(alert: WeatherAlert, text: TimeText, nowMillis: Long) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(severityColor(alert)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.event, style = MaterialTheme.typography.titleMedium, color = Color.White)
                val until = alert.endTime?.let { "Until " + text.timeWithDay(it, nowMillis) }
                val details = listOfNotNull(until, alert.sender).joinToString(" · ")
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = SkyText.Secondary)
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Show less" else "Show more",
                tint = SkyText.Secondary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
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

@Composable
fun NowcastCard(nowcast: Nowcast, snapshot: WeatherSnapshot, use24h: Boolean) {
    val text = remember(snapshot.timezone, use24h) { TimeText(snapshot.zoneId(), use24h) }
    val color = when (nowcast.kind) {
        PrecipType.Snow -> Color.White
        PrecipType.FreezingRain, PrecipType.Hail -> Color(0xFFBFEAFF)
        else -> Color(0xFF6CC7FF)
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(Icons.Rounded.Umbrella, "Next 2 hours")
        Spacer(Modifier.height(8.dp))
        Text(nowcast.headline, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(12.dp))
        val bars = nowcast.bars
        val grow by rememberAppearProgress(durationMillis = 900)
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val slot = size.width / bars.size
            val maxRate = 6f
            // Reference lines for light / moderate / heavy.
            listOf(1f, 4f).forEach { level ->
                val y = size.height - size.height * min(level / maxRate, 1f)
                drawLine(
                    Color.White.copy(alpha = 0.18f),
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
            bars.forEachIndexed { i, bar ->
                // Bars rise left to right as the card comes in.
                val rise = (grow * 1.5f - 0.5f * i / bars.size).coerceIn(0f, 1f)
                val fraction = min(bar.mmPerHour.toFloat() / maxRate, 1f)
                val height = (if (bar.mmPerHour > 0.0) (size.height * fraction).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()) * rise
                val alpha = if (bar.mmPerHour > 0.0) 0.9f else 0.25f
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(i * slot + slot * 0.18f, size.height - height),
                    size = Size(slot * 0.64f, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
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

@Composable
fun SummaryCard(summary: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(Icons.Rounded.AutoAwesome, "Overview")
        Spacer(Modifier.height(8.dp))
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.padding(end = 4.dp))
    }
}
