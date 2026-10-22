package io.github.duddmeister.weather.widget

import androidx.annotation.DrawableRes
import io.github.duddmeister.weather.R
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.sky.SkyScene

/**
 * Still versions of the app's animated sky for widgets, which can't animate: a sun or moon glow in
 * the top-end corner, then full-bleed layers (stars, clouds, fog, rain, snow, lightning) back to front.
 */
data class WidgetSky(@DrawableRes val glow: Int? = null, val layers: List<Int> = emptyList()) {
    companion object {
        val None = WidgetSky()

        fun of(scene: SkyScene): WidgetSky = WidgetSky(
            glow = when {
                // At dawn and dusk the gradient is the low sun; a glow up in the corner would contradict it.
                scene.kind == SkyKind.Dawn || scene.kind == SkyKind.Dusk -> null
                scene.sun >= 0.5f -> R.drawable.fx_sun_glow
                scene.moon >= 0.5f -> R.drawable.fx_moon_glow
                else -> null
            },
            layers = buildList {
                if (scene.stars >= 0.3f) add(R.drawable.fx_stars)
                when {
                    scene.clouds >= 0.8f -> add(R.drawable.fx_clouds_heavy)
                    scene.clouds >= 0.3f -> add(R.drawable.fx_clouds_light)
                }
                if (scene.fog > 0f) add(R.drawable.fx_fog)
                when {
                    scene.rain <= 0f -> Unit
                    scene.rain >= 0.9f || scene.lightning -> add(R.drawable.fx_rain_heavy)
                    else -> add(R.drawable.fx_rain)
                }
                if (scene.snow > 0f) add(R.drawable.fx_snow)
                if (scene.lightning) add(R.drawable.fx_lightning)
            },
        )
    }
}
