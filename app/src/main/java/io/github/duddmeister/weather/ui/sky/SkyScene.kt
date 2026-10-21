package io.github.duddmeister.weather.ui.sky

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.duddmeister.weather.domain.Astronomy
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.model.SkyCategory
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.WeatherVisuals
import kotlin.math.sin

@Immutable
data class SkyPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    /** Translucent "glass" behind cards. */
    val card: Color,
    val cardBorder: Color,
)

/** Everything the animated background needs: colours plus how much of each effect to draw (0..1). */
@Immutable
data class SkyScene(
    val kind: SkyKind,
    val palette: SkyPalette,
    val stars: Float = 0f,
    val sun: Float = 0f,
    val moon: Float = 0f,
    val clouds: Float = 0f,
    val darkClouds: Boolean = false,
    val rain: Float = 0f,
    val snow: Float = 0f,
    val fog: Float = 0f,
    val lightning: Boolean = false,
    /** Horizontal drift of rain per unit of fall; positive moves right. */
    val windTilt: Float = 0.12f,
    val moonPhase: Float = 0.5f,
)

object SkyScenes {
    private val dayCard = Color.Black.copy(alpha = 0.16f)
    private val nightCard = Color.White.copy(alpha = 0.08f)
    private val border = Color.White.copy(alpha = 0.10f)

    fun palette(kind: SkyKind): SkyPalette = when (kind) {
        SkyKind.ClearDay -> SkyPalette(Color(0xFF0F5FC2), Color(0xFF3D8FE0), Color(0xFF7DBAF0), dayCard, border)
        SkyKind.ClearNight -> SkyPalette(Color(0xFF070B1F), Color(0xFF121B3D), Color(0xFF24315E), nightCard, border)
        SkyKind.CloudyDay -> SkyPalette(Color(0xFF2F6DB5), Color(0xFF5A8FCB), Color(0xFF93B6DD), dayCard, border)
        SkyKind.CloudyNight -> SkyPalette(Color(0xFF0E1428), Color(0xFF1E2742), Color(0xFF36405E), nightCard, border)
        SkyKind.OvercastDay -> SkyPalette(Color(0xFF4A5868), Color(0xFF66768A), Color(0xFF8896A6), dayCard, border)
        SkyKind.OvercastNight -> SkyPalette(Color(0xFF151A22), Color(0xFF232A35), Color(0xFF343D4B), nightCard, border)
        SkyKind.RainDay -> SkyPalette(Color(0xFF2E3E50), Color(0xFF45566A), Color(0xFF617386), dayCard, border)
        SkyKind.RainNight -> SkyPalette(Color(0xFF0D141D), Color(0xFF19232F), Color(0xFF283545), nightCard, border)
        SkyKind.Storm -> SkyPalette(Color(0xFF17142A), Color(0xFF2C2645), Color(0xFF4A3F66), nightCard, border)
        SkyKind.SnowDay -> SkyPalette(Color(0xFF4F6684), Color(0xFF6E86A4), Color(0xFF93A8C2), dayCard, border)
        SkyKind.SnowNight -> SkyPalette(Color(0xFF172033), Color(0xFF2A3750), Color(0xFF45536E), nightCard, border)
        SkyKind.FogDay -> SkyPalette(Color(0xFF55606B), Color(0xFF727C86), Color(0xFF939CA4), dayCard, border)
        SkyKind.FogNight -> SkyPalette(Color(0xFF1A1F25), Color(0xFF2B3138), Color(0xFF40474F), nightCard, border)
        SkyKind.Dawn -> SkyPalette(Color(0xFF2B3A6B), Color(0xFF9C5C86), Color(0xFFE79A7A), dayCard, border)
        SkyKind.Dusk -> SkyPalette(Color(0xFF1F2A5A), Color(0xFF93466A), Color(0xFFE0845A), dayCard, border)
    }

    /** A gentle default before any data has loaded. */
    val Default = SkyScene(SkyKind.CloudyDay, palette(SkyKind.CloudyDay), sun = 0.5f, clouds = 0.4f)

    fun forConditions(snapshot: WeatherSnapshot?, now: NowConditions?, nowMillis: Long): SkyScene {
        if (snapshot == null || now == null) return Default
        val kind = WeatherVisuals.skyKind(snapshot, now, nowMillis)
        val condition = now.condition
        val intensity = condition.intensity.coerceAtLeast(1) / 3f
        val windSpeed = now.windSpeed ?: 10.0
        val direction = now.windDirection
        val tilt = if (direction == null) {
            0.12f
        } else {
            val strength = (windSpeed / 80.0).coerceIn(0.05, 0.45)
            (-sin(Math.toRadians(direction)) * strength).toFloat()
        }
        val night = !now.isDay
        val scene = SkyScene(kind = kind, palette = palette(kind), windTilt = tilt, moonPhase = Astronomy.moon(nowMillis).phase.toFloat())
        return when (condition.category) {
            SkyCategory.Clear -> when (kind) {
                SkyKind.Dawn, SkyKind.Dusk -> scene.copy(sun = 0.8f, stars = 0.15f, clouds = 0.15f)
                else -> if (night) scene.copy(stars = 1f, moon = 1f, clouds = 0.05f) else scene.copy(sun = 1f, clouds = 0.08f)
            }
            SkyCategory.PartlyCloudy -> when (kind) {
                SkyKind.Dawn, SkyKind.Dusk -> scene.copy(sun = 0.6f, clouds = 0.45f)
                else -> if (night) scene.copy(stars = 0.45f, moon = 0.6f, clouds = 0.5f) else scene.copy(sun = 0.6f, clouds = 0.5f)
            }
            SkyCategory.Cloudy -> scene.copy(clouds = 1f, darkClouds = night, stars = if (night) 0.05f else 0f)
            SkyCategory.Fog -> scene.copy(fog = 1f, clouds = 0.25f)
            SkyCategory.Drizzle -> scene.copy(clouds = 0.9f, darkClouds = true, rain = 0.22f + 0.1f * intensity)
            SkyCategory.Rain, SkyCategory.Showers -> scene.copy(clouds = 0.95f, darkClouds = true, rain = 0.35f + 0.65f * intensity)
            SkyCategory.FreezingRain -> scene.copy(clouds = 0.95f, darkClouds = true, rain = 0.45f, snow = 0.15f)
            SkyCategory.Snow, SkyCategory.SnowShowers -> scene.copy(clouds = 0.85f, snow = 0.3f + 0.7f * intensity)
            SkyCategory.Thunderstorm -> scene.copy(clouds = 1f, darkClouds = true, rain = 0.8f, lightning = true)
        }
    }
}
