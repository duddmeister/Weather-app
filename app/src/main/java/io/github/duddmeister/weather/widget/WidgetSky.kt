package io.github.duddmeister.weather.widget

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.compose.ui.unit.DpSize
import io.github.duddmeister.weather.R
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.sky.SkyScene

/** A light in the widget's top-end corner. */
enum class SkyGlow(@DrawableRes val drawable: Int) {
    Sun(R.drawable.fx_sun_glow),
    Moon(R.drawable.fx_moon_glow),
}

/** One version of an effect: a still image, and a layout that plays its animation (see tools/gen_widget_fx.py). */
data class SkyArt(@DrawableRes val still: Int, @LayoutRes val animated: Int)

/**
 * A full-bleed sky effect, stretched to fit the widget. Each comes in a wide (400×110), standard
 * (400×220) and square (300×300) version, so clouds stay whole and rain keeps its slant on any widget.
 */
enum class SkyEffect(private val wide: SkyArt, private val standard: SkyArt, private val square: SkyArt) {
    Stars(
        SkyArt(R.drawable.fx_stars_wide, R.layout.fx_stars_wide),
        SkyArt(R.drawable.fx_stars, R.layout.fx_stars),
        SkyArt(R.drawable.fx_stars_square, R.layout.fx_stars_square),
    ),
    LightClouds(
        SkyArt(R.drawable.fx_clouds_light_wide, R.layout.fx_clouds_light_wide),
        SkyArt(R.drawable.fx_clouds_light, R.layout.fx_clouds_light),
        SkyArt(R.drawable.fx_clouds_light_square, R.layout.fx_clouds_light_square),
    ),
    HeavyClouds(
        SkyArt(R.drawable.fx_clouds_heavy_wide, R.layout.fx_clouds_heavy_wide),
        SkyArt(R.drawable.fx_clouds_heavy, R.layout.fx_clouds_heavy),
        SkyArt(R.drawable.fx_clouds_heavy_square, R.layout.fx_clouds_heavy_square),
    ),
    Fog(
        SkyArt(R.drawable.fx_fog_wide, R.layout.fx_fog_wide),
        SkyArt(R.drawable.fx_fog, R.layout.fx_fog),
        SkyArt(R.drawable.fx_fog_square, R.layout.fx_fog_square),
    ),
    Rain(
        SkyArt(R.drawable.fx_rain_wide, R.layout.fx_rain_wide),
        SkyArt(R.drawable.fx_rain, R.layout.fx_rain),
        SkyArt(R.drawable.fx_rain_square, R.layout.fx_rain_square),
    ),
    HeavyRain(
        SkyArt(R.drawable.fx_rain_heavy_wide, R.layout.fx_rain_heavy_wide),
        SkyArt(R.drawable.fx_rain_heavy, R.layout.fx_rain_heavy),
        SkyArt(R.drawable.fx_rain_heavy_square, R.layout.fx_rain_heavy_square),
    ),
    Snow(
        SkyArt(R.drawable.fx_snow_wide, R.layout.fx_snow_wide),
        SkyArt(R.drawable.fx_snow, R.layout.fx_snow),
        SkyArt(R.drawable.fx_snow_square, R.layout.fx_snow_square),
    ),
    Lightning(
        SkyArt(R.drawable.fx_lightning_wide, R.layout.fx_lightning_wide),
        SkyArt(R.drawable.fx_lightning, R.layout.fx_lightning),
        SkyArt(R.drawable.fx_lightning_square, R.layout.fx_lightning_square),
    ),
    ;

    /** The version for a widget of [size], by its aspect ratio. */
    fun art(size: DpSize): SkyArt {
        val aspect = size.width.value / size.height.value.coerceAtLeast(1f)
        return when {
            aspect >= 2.6f -> wide
            aspect < 1.35f -> square
            else -> standard
        }
    }
}

/** Versions of the app's sky for widgets. Effects are listed back to front. */
data class WidgetSky(val glow: SkyGlow? = null, val effects: List<SkyEffect> = emptyList()) {
    companion object {
        val None = WidgetSky()

        fun of(scene: SkyScene): WidgetSky = WidgetSky(
            glow = when {
                // At dawn and dusk the gradient is the low sun; a glow up in the corner would contradict it.
                scene.kind == SkyKind.Dawn || scene.kind == SkyKind.Dusk -> null
                scene.sun >= 0.5f -> SkyGlow.Sun
                scene.moon >= 0.5f -> SkyGlow.Moon
                else -> null
            },
            effects = buildList {
                if (scene.stars >= 0.3f) add(SkyEffect.Stars)
                when {
                    scene.clouds >= 0.8f -> add(SkyEffect.HeavyClouds)
                    scene.clouds >= 0.3f -> add(SkyEffect.LightClouds)
                }
                if (scene.fog > 0f) add(SkyEffect.Fog)
                when {
                    scene.rain <= 0f -> Unit
                    scene.rain >= 0.9f || scene.lightning -> add(SkyEffect.HeavyRain)
                    else -> add(SkyEffect.Rain)
                }
                if (scene.snow > 0f) add(SkyEffect.Snow)
                if (scene.lightning) add(SkyEffect.Lightning)
            },
        )
    }
}
