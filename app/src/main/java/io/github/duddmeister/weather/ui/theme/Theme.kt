package io.github.duddmeister.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.duddmeister.weather.R

val NimbusColors = darkColorScheme(
    primary = Color(0xFF9CCBFF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF214B7C),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFFFD27A),
    onSecondary = Color(0xFF3F2E00),
    tertiary = Color(0xFF9AE6C9),
    background = Color(0xFF0B1530),
    onBackground = Color.White,
    surface = Color(0xFF121C35),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C2742),
    onSurfaceVariant = Color(0xFFC9D3E6),
    surfaceContainer = Color(0xFF17223D),
    surfaceContainerHigh = Color(0xFF1E2A47),
    surfaceContainerHighest = Color(0xFF253252),
    outline = Color(0x59FFFFFF),
    outlineVariant = Color(0x26FFFFFF),
    error = Color(0xFFFFB4AB),
)

/** One cut of Outfit (a variable font, SIL Open Font License; see assets/licenses). */
@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) = Font(
    resId = R.font.outfit,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Nimbus's own typeface for numbers, titles and labels. Longer text stays in the system font. */
val Outfit = FontFamily(
    outfit(FontWeight.Thin),
    outfit(FontWeight.ExtraLight),
    outfit(FontWeight.Light),
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
    outfit(FontWeight.Bold),
)

/** Digits that all take the same width, so numbers line up in columns and don't jiggle as they change. */
const val TabularNumbers = "tnum"

private val base = Typography()

private fun TextStyle.outfit(weight: FontWeight, size: Float, line: Float, tracking: Float = 0f) = copy(
    fontFamily = Outfit,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val NimbusTypography = base.copy(
    // The temperature right now.
    displayLarge = base.displayLarge.outfit(FontWeight.ExtraLight, 116f, 120f, -3f),
    displayMedium = base.displayMedium.outfit(FontWeight.Light, 52f, 58f, -1f),
    displaySmall = base.displaySmall.outfit(FontWeight.Light, 40f, 46f, -0.5f),
    headlineLarge = base.headlineLarge.outfit(FontWeight.Normal, 32f, 38f, -0.3f),
    headlineMedium = base.headlineMedium.outfit(FontWeight.Normal, 29f, 34f, -0.2f),
    headlineSmall = base.headlineSmall.outfit(FontWeight.Medium, 24f, 30f),
    titleLarge = base.titleLarge.outfit(FontWeight.Medium, 21f, 27f),
    titleMedium = base.titleMedium.outfit(FontWeight.Medium, 16.5f, 22f, 0.1f),
    titleSmall = base.titleSmall.outfit(FontWeight.Medium, 14.5f, 20f, 0.1f),
    labelLarge = base.labelLarge.outfit(FontWeight.Medium, 14f, 19f, 0.1f),
    // Small capitals above each card.
    labelMedium = base.labelMedium.outfit(FontWeight.SemiBold, 11.5f, 16f, 1.2f),
    labelSmall = base.labelSmall.outfit(FontWeight.Medium, 11.5f, 15f, 0.2f),
)

/** Text colours used on top of the sky. */
object SkyText {
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.80f)
    val Tertiary = Color.White.copy(alpha = 0.62f)
    val Faint = Color.White.copy(alpha = 0.38f)
    val Warning = Color(0xFFFFD27A)
    val Rain = Color(0xFF9AD8FF)
    val Positive = Color(0xFF8BE0A4)
    val Negative = Color(0xFFFF8A80)
    val Accent = Color(0xFF5B9BFF)
}

@Composable
fun NimbusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NimbusColors, typography = NimbusTypography, content = content)
}
