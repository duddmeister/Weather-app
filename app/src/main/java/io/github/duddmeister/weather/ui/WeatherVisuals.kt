package io.github.duddmeister.weather.ui

import androidx.annotation.DrawableRes
import io.github.duddmeister.weather.R
import io.github.duddmeister.weather.domain.NowConditions
import io.github.duddmeister.weather.domain.localDate
import io.github.duddmeister.weather.domain.dayFor
import io.github.duddmeister.weather.model.SkyCategory
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.model.WeatherSnapshot

enum class SkyKind {
    ClearDay, ClearNight, CloudyDay, CloudyNight, OvercastDay, OvercastNight, RainDay, RainNight,
    Storm, SnowDay, SnowNight, FogDay, FogNight, Dawn, Dusk,
}

enum class GoldenHour { None, Dawn, Dusk }

object WeatherVisuals {
    @DrawableRes
    fun icon(code: Int?, isDay: Boolean): Int = when (WeatherCondition.fromCode(code)) {
        WeatherCondition.Clear -> if (isDay) R.drawable.wx_clear_day else R.drawable.wx_clear_night
        WeatherCondition.MainlyClear, WeatherCondition.PartlyCloudy ->
            if (isDay) R.drawable.wx_partly_cloudy_day else R.drawable.wx_partly_cloudy_night
        WeatherCondition.Overcast -> R.drawable.wx_overcast
        WeatherCondition.Fog, WeatherCondition.RimeFog -> R.drawable.wx_fog
        WeatherCondition.DrizzleLight, WeatherCondition.Drizzle -> R.drawable.wx_drizzle
        WeatherCondition.DrizzleDense, WeatherCondition.RainLight, WeatherCondition.Rain -> R.drawable.wx_rain
        WeatherCondition.RainHeavy, WeatherCondition.ShowersViolent -> R.drawable.wx_heavy_rain
        WeatherCondition.FreezingDrizzleLight, WeatherCondition.FreezingDrizzleDense,
        WeatherCondition.FreezingRainLight, WeatherCondition.FreezingRainHeavy -> R.drawable.wx_freezing_rain
        WeatherCondition.SnowLight, WeatherCondition.Snow, WeatherCondition.SnowGrains -> R.drawable.wx_snow
        WeatherCondition.SnowHeavy, WeatherCondition.SnowShowersHeavy -> R.drawable.wx_heavy_snow
        WeatherCondition.ShowersLight, WeatherCondition.Showers ->
            if (isDay) R.drawable.wx_showers_day else R.drawable.wx_showers_night
        WeatherCondition.SnowShowersLight ->
            if (isDay) R.drawable.wx_snow_showers_day else R.drawable.wx_snow_showers_night
        WeatherCondition.Thunderstorm -> R.drawable.wx_thunderstorm
        WeatherCondition.ThunderstormHail, WeatherCondition.ThunderstormHeavyHail -> R.drawable.wx_hail
        WeatherCondition.Unknown -> R.drawable.wx_cloudy
    }

    fun goldenHour(nowSeconds: Long, sunrise: Long?, sunset: Long?): GoldenHour = when {
        sunrise != null && nowSeconds in (sunrise - 20 * 60)..(sunrise + 40 * 60) -> GoldenHour.Dawn
        sunset != null && nowSeconds in (sunset - 40 * 60)..(sunset + 20 * 60) -> GoldenHour.Dusk
        else -> GoldenHour.None
    }

    fun skyKind(condition: WeatherCondition, isDay: Boolean, golden: GoldenHour): SkyKind = when (condition.category) {
        SkyCategory.Clear, SkyCategory.PartlyCloudy -> when (golden) {
            GoldenHour.Dawn -> SkyKind.Dawn
            GoldenHour.Dusk -> SkyKind.Dusk
            GoldenHour.None -> when {
                condition.category == SkyCategory.Clear -> if (isDay) SkyKind.ClearDay else SkyKind.ClearNight
                else -> if (isDay) SkyKind.CloudyDay else SkyKind.CloudyNight
            }
        }
        SkyCategory.Cloudy -> if (isDay) SkyKind.OvercastDay else SkyKind.OvercastNight
        SkyCategory.Fog -> if (isDay) SkyKind.FogDay else SkyKind.FogNight
        SkyCategory.Drizzle, SkyCategory.Rain, SkyCategory.Showers, SkyCategory.FreezingRain ->
            if (isDay) SkyKind.RainDay else SkyKind.RainNight
        SkyCategory.Snow, SkyCategory.SnowShowers -> if (isDay) SkyKind.SnowDay else SkyKind.SnowNight
        SkyCategory.Thunderstorm -> SkyKind.Storm
    }

    /** Sky for the conditions at [nowMillis] (golden hour uses the day's sunrise/sunset). */
    fun skyKind(snapshot: WeatherSnapshot, now: NowConditions, nowMillis: Long): SkyKind {
        val today = snapshot.dayFor(snapshot.localDate(nowMillis))
        val golden = goldenHour(nowMillis / 1000, today?.sunrise, today?.sunset)
        return skyKind(now.condition, now.isDay, golden)
    }

    @DrawableRes
    fun widgetBackground(kind: SkyKind): Int = when (kind) {
        SkyKind.ClearDay -> R.drawable.widget_bg_clear_day
        SkyKind.ClearNight -> R.drawable.widget_bg_clear_night
        SkyKind.CloudyDay -> R.drawable.widget_bg_cloudy_day
        SkyKind.CloudyNight -> R.drawable.widget_bg_cloudy_night
        SkyKind.OvercastDay -> R.drawable.widget_bg_overcast_day
        SkyKind.OvercastNight -> R.drawable.widget_bg_overcast_night
        SkyKind.RainDay -> R.drawable.widget_bg_rain_day
        SkyKind.RainNight -> R.drawable.widget_bg_rain_night
        SkyKind.Storm -> R.drawable.widget_bg_storm
        SkyKind.SnowDay -> R.drawable.widget_bg_snow_day
        SkyKind.SnowNight -> R.drawable.widget_bg_snow_night
        SkyKind.FogDay -> R.drawable.widget_bg_fog_day
        SkyKind.FogNight -> R.drawable.widget_bg_fog_night
        SkyKind.Dawn -> R.drawable.widget_bg_dawn
        SkyKind.Dusk -> R.drawable.widget_bg_dusk
    }
}
