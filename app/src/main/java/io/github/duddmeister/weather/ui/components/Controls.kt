package io.github.duddmeister.weather.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.ui.theme.SkyText
import kotlin.math.roundToInt

/**
 * A compact segmented control: the options sit on a rounded track and a highlight slides to the
 * chosen one. Options share the widest one's width, shrinking if the row is too narrow.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    horizontalPadding: Dp = 11.dp,
    verticalPadding: Dp = 7.dp,
) {
    val haptics = rememberHaptics()
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(selectedIndex.toFloat(), Motion.snappy(), label = "segment")
    Layout(
        content = {
            Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.24f)))
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val colour by animateColorAsState(if (isSelected) Color.White else SkyText.Secondary, label = "segmentText")
                Box(
                    Modifier
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton) {
                            if (!isSelected) {
                                haptics.tick()
                                onSelect(option)
                            }
                        }
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = textStyle, color = colour, maxLines = 1)
                }
            }
        },
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .padding(3.dp)
            .selectableGroup(),
    ) { measurables, constraints ->
        val highlight = measurables.first()
        val segments = measurables.drop(1)
        val widest = segments.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val width = if (constraints.hasBoundedWidth) minOf(widest, constraints.maxWidth / segments.size) else widest
        val height = segments.maxOf { it.minIntrinsicHeight(width) }
        val placeables = segments.map { it.measure(Constraints.fixed(width, height)) }
        val pill = highlight.measure(Constraints.fixed(width, height))
        layout(width * segments.size, height) {
            pill.placeRelative((position * width).roundToInt(), 0)
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(index * width, 0) }
        }
    }
}

/** A rounded button: filled for the main action, outlined for the other. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.95f)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Color.White.copy(alpha = 0.18f))
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                },
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

/** A full-width button for a screen's main choices: solid white for the first, glass for the rest. */
@Composable
fun WideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val content = if (primary) Color(0xFF0C3B78) else Color.White
    Row(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(interaction, pressedScale = 0.97f)
            .then(if (primary) Modifier.clip(CircleShape).background(Color.White) else Modifier.glass(CircleShape))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
    }
}

/** A round glass button with an icon, for the corners of the screen. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    // The touch target is 48dp; the glass you see is a little smaller.
    Box(
        modifier = modifier
            .size(48.dp)
            .pressScale(interaction, pressedScale = 0.9f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun nimbusSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = SkyText.Accent,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
    uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
    uncheckedBorderColor = Color.Transparent,
)
