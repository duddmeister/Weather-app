package io.github.duddmeister.weather.widget

import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.DpSize
import io.github.duddmeister.weather.R
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.sky.SkyScene

/** A light in the widget's top-end corner. */
enum class SkyGlow(@DrawableRes val drawable: Int) {
    Sun(R.drawable.fx_sun_glow),
    Moon(R.drawable.fx_moon_glow),
}

/**
 * A full-bleed sky effect. Even fields (stars, rain, snow, lightning) are centre-cropped from one
 * drawable. Clouds and fog follow the widget's edges, so they're stretched to fit, and clouds come
 * in wide, standard and square versions so they keep their shape on any widget.
 */
enum class SkyEffect(
    @DrawableRes private val standard: Int,
    @DrawableRes private val wide: Int = standard,
    @DrawableRes private val square: Int = standard,
    val stretch: Boolean = false,
) {
    Stars(R.drawable.fx_stars),
    LightClouds(R.drawable.fx_clouds_light, R.drawable.fx_clouds_light_wide, R.drawable.fx_clouds_light_square, stretch = true),
    HeavyClouds(R.drawable.fx_clouds_heavy, R.drawable.fx_clouds_heavy_wide, R.drawable.fx_clouds_heavy_square, stretch = true),
    Fog(R.drawable.fx_fog, stretch = true),
    Rain(R.drawable.fx_rain),
    HeavyRain(R.drawable.fx_rain_heavy),
    Snow(R.drawable.fx_snow),
    Lightning(R.drawable.fx_lightning),
    ;

    /** The version drawn on a widget of [size]; the drawables are 400×110, 400×220 and 300×300. */
    @DrawableRes
    fun drawable(size: DpSize): Int {
        val aspect = size.width.value / size.height.value.coerceAtLeast(1f)
        return when {
            aspect >= 2.6f -> wide
            aspect < 1.35f -> square
            else -> standard
        }
    }
}

/** Still versions of the app's animated sky for widgets, which can't animate. Effects are listed back to front. */
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
