package io.github.duddmeister.weather.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.ui.sky.LocalSkyBackdrop
import io.github.duddmeister.weather.ui.theme.SkyText

val CardShape = RoundedCornerShape(26.dp)

/** Glass where there's no sky behind it to borrow a tint from. */
private val PlainGlass = Color.Black.copy(alpha = 0.20f)

/**
 * Frosted glass over the sky. The glass paints the sky's soft layers again inside its own shape,
 * then tints them, so clouds and glows carry on behind it while rain, snow and stars are hidden:
 * the look of a blur without blurring anything.
 *
 * This draws in a layer of its own, so put it on something with no content (see [GlassCard]) when
 * that content is expensive to draw: the glass redraws whenever it moves on screen.
 *
 * @param tint an extra wash of colour over the glass, such as a warning's
 */
@Composable
fun Modifier.glass(shape: Shape = CardShape, tint: Color = Color.Unspecified, outlined: Boolean = true): Modifier {
    val backdrop = LocalSkyBackdrop.current
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position = it.positionInRoot() }
        .graphicsLayer {
            clip = true
            this.shape = shape
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent)
            val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.07f)))
            // The clip is antialiased, so a stroke twice the wanted width leaves half of it showing.
            val stroke = Stroke(width = 2.dp.toPx())
            onDrawWithContent {
                if (backdrop != null) {
                    translate(-position.x, -position.y) { with(backdrop) { drawSoftSky() } }
                    drawRect(backdrop.glass)
                } else {
                    drawRect(PlainGlass)
                }
                if (tint.isSpecified) drawRect(tint)
                drawRect(sheen)
                // Rain on the screen runs across the glass too, beneath whatever the glass holds.
                if (backdrop != null) translate(-position.x, -position.y) { with(backdrop) { drawWater() } }
                drawContent()
                if (outlined) drawOutline(outline, edge, style = stroke)
            }
        }
}

/**
 * Hides whatever scrolls beneath it behind a copy of the sky, fading out along its bottom [fade] so
 * content dissolves into the sky instead of meeting an edge. [strength] is read while drawing.
 */
@Composable
fun Modifier.skyVeil(fade: Dp, strength: () -> Float): Modifier {
    val backdrop = LocalSkyBackdrop.current ?: return this
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position = it.positionInRoot() }
        .graphicsLayer {
            alpha = strength().coerceIn(0f, 1f)
            // The fade is cut out of the veil, which needs a buffer of its own.
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithCache {
            val solid = (1f - fade.toPx() / size.height).coerceIn(0f, 1f)
            val rest = 1f - solid
            val mask = Brush.verticalGradient(
                0f to Color.Black,
                solid to Color.Black,
                solid + rest * 0.3f to Color.Black.copy(alpha = 0.8f),
                solid + rest * 0.6f to Color.Black.copy(alpha = 0.36f),
                solid + rest * 0.85f to Color.Black.copy(alpha = 0.08f),
                1f to Color.Transparent,
            )
            onDrawBehind {
                translate(-position.x, -position.y) {
                    with(backdrop) {
                        drawSoftSky()
                        // Rain on the screen stays on the screen, whatever scrolls away beneath it.
                        drawWater()
                    }
                }
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }
}

/** A card of glass. With [onClick] it dips slightly when pressed. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = CardShape,
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = if (onClick != null) remember { MutableInteractionSource() } else null
    Box(
        modifier = if (interaction != null) modifier.pressScale(interaction) else modifier,
        propagateMinConstraints = true,
    ) {
        Spacer(Modifier.matchParentSize().glass(shape, tint))
        Column(
            modifier = Modifier
                .then(
                    if (onClick != null && interaction != null) {
                        Modifier.clip(shape).clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClickLabel = onClickLabel,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/** The small heading at the top of a card. */
@Composable
fun CardLabel(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = SkyText.Tertiary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = SkyText.Tertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}
