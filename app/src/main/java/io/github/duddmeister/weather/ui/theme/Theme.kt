package io.github.duddmeister.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private val base = Typography()

val NimbusTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Thin, fontSize = 112.sp, lineHeight = 116.sp, letterSpacing = (-4).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
)

/** Text colours used on top of the sky. */
object SkyText {
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.78f)
    val Tertiary = Color.White.copy(alpha = 0.6f)
    val Warning = Color(0xFFFFD27A)
    val Rain = Color(0xFF9AD8FF)
}

@Composable
fun NimbusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NimbusColors, typography = NimbusTypography, content = content)
}
