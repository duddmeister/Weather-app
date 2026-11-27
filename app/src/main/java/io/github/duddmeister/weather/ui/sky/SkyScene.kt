package io.github.duddmeister.weather.ui.sky

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.duddmeister.weather.domain.Astronomy
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.domain.dayFor
import io.github.duddmeister.weather.domain.localDate
import io.github.duddmeister.weather.model.SkyCategory
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.WeatherVisuals
import kotlin.math.abs
import kotlin.math.sin

@Immutable
data class SkyPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    /** Tint of the "glass" that cards are made of. */
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
    /** How far through golden hour the sun is: 0 high above the horizon, 1 on it. */
    val sunLowness: Float = 0f,
)

object SkyScenes {
    private val border = Color.White.copy(alpha = 0.16f)

    private fun day(top: Long, middle: Long, bottom: Long, glass: Long, strength: Float = 0.34f) =
        SkyPalette(Color(top), Color(middle), Color(bottom), Color(glass).copy(alpha = strength), border)

    private fun night(top: Long, middle: Long, bottom: Long, glass: Long = 0xFFB9C8FF, strength: Float = 0.09f) =
        SkyPalette(Color(top), Color(middle), Color(bottom), Color(glass).copy(alpha = strength), border)

    // By day the glass is a deeper shade of the sky, so white text stays readable on a bright
    // background; at night it's a lighter one, so cards lift off the dark.
    fun palette(kind: SkyKind): SkyPalette = when (kind) {
        SkyKind.ClearDay -> day(0xFF0D5BC0, 0xFF3A8DE2, 0xFF86C2F4, glass = 0xFF07357A)
        SkyKind.ClearNight -> night(0xFF060A1E, 0xFF111A3C, 0xFF25325F)
        SkyKind.CloudyDay -> day(0xFF2C6AB4, 0xFF5990CC, 0xFF97BADF, glass = 0xFF123B6B)
        SkyKind.CloudyNight -> night(0xFF0D1327, 0xFF1D2641, 0xFF36405E)
        SkyKind.OvercastDay -> day(0xFF47566A, 0xFF64758B, 0xFF8997A8, glass = 0xFF1B2634, strength = 0.32f)
        SkyKind.OvercastNight -> night(0xFF141922, 0xFF222935, 0xFF343D4B, glass = 0xFFC9D3E6, strength = 0.08f)
        SkyKind.RainDay -> day(0xFF2B3B4E, 0xFF43556A, 0xFF627588, glass = 0xFF0E1A26, strength = 0.30f)
        SkyKind.RainNight -> night(0xFF0C131C, 0xFF18222E, 0xFF283545, glass = 0xFFBFD3EA, strength = 0.08f)
        SkyKind.Storm -> night(0xFF161329, 0xFF2B2545, 0xFF4B4068, glass = 0xFFCDBFFF, strength = 0.09f)
        SkyKind.SnowDay -> day(0xFF4A6384, 0xFF6C85A5, 0xFF98ADC6, glass = 0xFF1A2D4A, strength = 0.30f)
        SkyKind.SnowNight -> night(0xFF162033, 0xFF29364F, 0xFF45536E, glass = 0xFFD5E2FF, strength = 0.09f)
        SkyKind.FogDay -> day(0xFF535F6B, 0xFF717C87, 0xFF969FA7, glass = 0xFF1E262E, strength = 0.30f)
        SkyKind.FogNight -> night(0xFF191E24, 0xFF2A3037, 0xFF40474F, glass = 0xFFD0D6DC, strength = 0.08f)
        SkyKind.Dawn -> day(0xFF28386C, 0xFF99598A, 0xFFEC9C7A, glass = 0xFF241640, strength = 0.30f)
        SkyKind.Dusk -> day(0xFF1C275A, 0xFF8F436C, 0xFFE2835A, glass = 0xFF1E1238, strength = 0.32f)
    }

    /** A gentle default before any data has loaded. */
    val Default = SkyScene(SkyKind.CloudyDay, palette(SkyKind.CloudyDay), sun = 0.5f, clouds = 0.4f)

    fun forConditions(snapshot: WeatherSnapshot?, now: NowConditions?, nowMillis: Long): SkyScene {
        if (snapshot == null || now == null) return Default
        val kind = WeatherVisuals.skyKind(snapshot, now, nowMillis)
        val windSpeed = now.windSpeed ?: 10.0
        val direction = now.windDirection
        val tilt = if (direction == null) {
            0.12f
        } else {
            val strength = (windSpeed / 80.0).coerceIn(0.05, 0.45)
            (-sin(Math.toRadians(direction)) * strength).toFloat()
        }
        return forCondition(
            condition = now.condition,
            kind = kind,
            night = !now.isDay,
            windTilt = tilt,
            moonPhase = Astronomy.moon(nowMillis).phase.toFloat(),
            sunLowness = sunLowness(snapshot, kind, nowMillis),
        )
    }

    /**
     * The sky for a kind of weather, wherever that came from: the forecast, or the gallery of skies.
     *
     * @param windTilt how far rain leans per unit it falls (negative leans left)
     * @param sunLowness for dawn and dusk, how near the horizon the sun is, 0..1
     */
    fun forCondition(
        condition: WeatherCondition,
        kind: SkyKind,
        night: Boolean,
        windTilt: Float,
        moonPhase: Float,
        sunLowness: Float = 0f,
    ): SkyScene {
        val intensity = condition.intensity.coerceAtLeast(1) / 3f
        val scene = SkyScene(kind = kind, palette = palette(kind), windTilt = windTilt, moonPhase = moonPhase, sunLowness = sunLowness)
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

    /** Where the sun is in the golden hour around sunrise or sunset: 0 at its start, 1 on the horizon. */
    private fun sunLowness(snapshot: WeatherSnapshot, kind: SkyKind, nowMillis: Long): Float {
        val today = snapshot.dayFor(snapshot.localDate(nowMillis)) ?: return 0.5f
        val nowSec = nowMillis / 1000
        val horizon = when (kind) {
            SkyKind.Dawn -> today.sunrise
            SkyKind.Dusk -> today.sunset
            else -> return 0f
        } ?: return 0.5f
        return (1f - abs(nowSec - horizon) / (40f * 60f)).coerceIn(0f, 1f)
    }
}

/**
 * What the sky looks like at one instant. Unlike a [SkyScene] every part of it is a number or a
 * colour, so two looks can be blended: the sky morphs as you swipe from one place to the next.
 */
@Immutable
class SkyLook(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val glass: Color,
    val stars: Float,
    val sun: Float,
    val moon: Float,
    val clouds: Float,
    val cloudDarkness: Float,
    val rain: Float,
    val snow: Float,
    val fog: Float,
    val lightning: Float,
    val windTilt: Float,
    val moonPhase: Float,
    /** Where the sun is, as fractions of the screen. */
    val sunX: Float,
    val sunY: Float,
    /** 0 for a white midday sun, 1 for an orange one on the horizon. */
    val sunWarmth: Float,
    /** Darkens the whole sky behind screens of text (Settings, Places). */
    val dim: Float,
) {
    /** True when [other] would draw the same picture, give or take what the eye can't see. */
    fun closeTo(other: SkyLook): Boolean =
        near(top, other.top) && near(middle, other.middle) && near(bottom, other.bottom) && near(glass, other.glass) &&
            near(stars, other.stars) && near(sun, other.sun) && near(moon, other.moon) && near(clouds, other.clouds) &&
            near(cloudDarkness, other.cloudDarkness) && near(rain, other.rain) && near(snow, other.snow) &&
            near(fog, other.fog) && near(lightning, other.lightning) && near(windTilt, other.windTilt) &&
            near(moonPhase, other.moonPhase) && near(sunX, other.sunX) && near(sunY, other.sunY) &&
            near(sunWarmth, other.sunWarmth) && near(dim, other.dim)

    private fun near(a: Float, b: Float) = abs(a - b) < 0.003f

    private fun near(a: Color, b: Color) =
        near(a.red, b.red) && near(a.green, b.green) && near(a.blue, b.blue) && near(a.alpha, b.alpha)
}

private const val HIGH_SUN_X = 0.82f
private const val HIGH_SUN_Y = 0.118f

/**
 * The look of this scene. Behind other screens ([calm]) everything that would show through text
 * goes: rain, snow, lightning, stars, the sun and the moon, leaving colours and soft clouds.
 */
fun SkyScene.look(calm: Boolean = false): SkyLook {
    val low = kind == SkyKind.Dawn || kind == SkyKind.Dusk
    return SkyLook(
        top = palette.top,
        middle = palette.middle,
        bottom = palette.bottom,
        glass = palette.card,
        stars = if (calm) 0f else stars,
        sun = if (calm) 0f else sun,
        moon = if (calm) 0f else moon,
        clouds = clouds,
        cloudDarkness = if (darkClouds) 1f else 0f,
        rain = if (calm) 0f else rain,
        snow = if (calm) 0f else snow,
        fog = fog,
        lightning = if (lightning && !calm) 1f else 0f,
        windTilt = windTilt,
        moonPhase = moonPhase,
        sunX = when (kind) {
            SkyKind.Dawn -> 0.15f
            SkyKind.Dusk -> 0.85f
            else -> HIGH_SUN_X
        },
        // A low sun sinks towards the first card, which stands in for the horizon.
        sunY = if (low) 0.27f + 0.13f * sunLowness else HIGH_SUN_Y,
        sunWarmth = if (low) 1f else 0f,
        dim = if (calm) 0.3f else 0f,
    )
}

fun lerp(a: SkyLook, b: SkyLook, t: Float): SkyLook {
    if (t <= 0f) return a
    if (t >= 1f) return b
    fun mix(x: Float, y: Float) = x + (y - x) * t
    // A sun that isn't there has no position worth travelling from.
    val sunWeight = if (a.sun + b.sun <= 0.001f) t else (b.sun * t) / (a.sun * (1f - t) + b.sun * t)
    return SkyLook(
        top = lerp(a.top, b.top, t),
        middle = lerp(a.middle, b.middle, t),
        bottom = lerp(a.bottom, b.bottom, t),
        glass = lerp(a.glass, b.glass, t),
        stars = mix(a.stars, b.stars),
        sun = mix(a.sun, b.sun),
        moon = mix(a.moon, b.moon),
        clouds = mix(a.clouds, b.clouds),
        cloudDarkness = mix(a.cloudDarkness, b.cloudDarkness),
        rain = mix(a.rain, b.rain),
        snow = mix(a.snow, b.snow),
        fog = mix(a.fog, b.fog),
        lightning = mix(a.lightning, b.lightning),
        windTilt = mix(a.windTilt, b.windTilt),
        moonPhase = mix(a.moonPhase, b.moonPhase),
        sunX = a.sunX + (b.sunX - a.sunX) * sunWeight,
        sunY = a.sunY + (b.sunY - a.sunY) * sunWeight,
        sunWarmth = a.sunWarmth + (b.sunWarmth - a.sunWarmth) * sunWeight,
        dim = mix(a.dim, b.dim),
    )
}
