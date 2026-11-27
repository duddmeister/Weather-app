package io.github.duddmeister.weather.ui.sky

import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.ui.GoldenHour
import io.github.duddmeister.weather.ui.WeatherVisuals

/**
 * One sky in the gallery (Settings → Skies): what it's called, what to look for in it, and the look
 * itself. The gallery is there so every sky can be seen whatever the weather happens to be doing.
 */
class SkySample(
    /** A stable name, for opening the gallery on this sky. */
    val id: String,
    val name: String,
    val note: String,
    /** The weather it stands for, as a WMO code, for its icon. */
    val weatherCode: Int,
    val isDay: Boolean,
    val look: SkyLook,
)

object SkySamples {
    /**
     * Every kind of sky, built the way a forecast's is, in the order a day might run: fair, turning,
     * wet, wintry, then evening and night. The moon is drawn in [moonPhase] (0 new, 0.5 full).
     */
    fun all(moonPhase: Float): List<SkySample> {
        fun sample(
            id: String,
            name: String,
            note: String,
            code: Int,
            isDay: Boolean = true,
            golden: GoldenHour = GoldenHour.None,
            windTilt: Float = 0.12f,
        ): SkySample {
            val condition = WeatherCondition.fromCode(code)
            val scene = SkyScenes.forCondition(
                condition = condition,
                kind = WeatherVisuals.skyKind(condition, isDay, golden),
                night = !isDay,
                windTilt = windTilt,
                moonPhase = moonPhase,
                // Golden hour is shown with the sun well down, but not yet on the horizon.
                sunLowness = 0.55f,
            )
            return SkySample(id, name, note, code, isDay, scene.look())
        }
        return listOf(
            sample("sunny", "Sunny", "The sun's rays turn slowly, and the odd cloud drifts through.", code = 0),
            sample("partly-cloudy", "Partly cloudy", "Clouds cross in front of the sun, and carry on behind the glass of the cards.", code = 2),
            sample("cloudy", "Cloudy", "A full deck of cloud, drifting slowly.", code = 3),
            sample("drizzle", "Drizzle", "Light rain in the air, and a few drops on the screen.", code = 53, windTilt = 0.08f),
            sample(
                "rain",
                "Rain",
                "Rain falls at the wind's angle. Drops land on the screen, bead, and the heavy ones run down it: over the glass, under the text.",
                code = 63,
                windTilt = 0.2f,
            ),
            sample("storm", "Thunderstorm", "Hard rain, and every so often lightning lights the whole sky.", code = 95, windTilt = -0.24f),
            sample("snow", "Snow", "Flakes fall at different depths. The near ones are bigger and out of focus.", code = 73),
            sample("fog", "Fog", "Banks of fog drift across the screen.", code = 45),
            sample(
                "golden-hour",
                "Golden hour",
                "Around sunrise and sunset the sky turns warm and the sun sits low.",
                code = 0,
                golden = GoldenHour.Dusk,
            ),
            sample(
                "night",
                "Clear night",
                "Stars twinkle, the bright ones glint, and now and then one falls. The moon is in tonight's phase.",
                code = 0,
                isDay = false,
            ),
        )
    }
}
