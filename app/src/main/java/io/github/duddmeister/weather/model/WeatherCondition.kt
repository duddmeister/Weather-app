package io.github.duddmeister.weather.model

enum class PrecipType { None, Drizzle, Rain, FreezingRain, Snow, Hail }

/** Broad sky categories used for backgrounds, icons and summaries. */
enum class SkyCategory { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Showers, FreezingRain, Snow, SnowShowers, Thunderstorm }

/**
 * WMO weather interpretation codes as returned by Open-Meteo, with human labels.
 * See https://open-meteo.com/en/docs (WMO Weather interpretation codes).
 */
enum class WeatherCondition(
    val dayLabel: String,
    val nightLabel: String,
    val category: SkyCategory,
    val precip: PrecipType,
    /** 0 = none/light, 1 = light, 2 = moderate, 3 = heavy. */
    val intensity: Int,
) {
    Clear("Sunny", "Clear", SkyCategory.Clear, PrecipType.None, 0),
    MainlyClear("Mostly sunny", "Mostly clear", SkyCategory.Clear, PrecipType.None, 0),
    PartlyCloudy("Partly cloudy", "Partly cloudy", SkyCategory.PartlyCloudy, PrecipType.None, 0),
    Overcast("Cloudy", "Cloudy", SkyCategory.Cloudy, PrecipType.None, 0),
    Fog("Fog", "Fog", SkyCategory.Fog, PrecipType.None, 0),
    RimeFog("Freezing fog", "Freezing fog", SkyCategory.Fog, PrecipType.None, 0),
    DrizzleLight("Light drizzle", "Light drizzle", SkyCategory.Drizzle, PrecipType.Drizzle, 1),
    Drizzle("Drizzle", "Drizzle", SkyCategory.Drizzle, PrecipType.Drizzle, 2),
    DrizzleDense("Heavy drizzle", "Heavy drizzle", SkyCategory.Drizzle, PrecipType.Drizzle, 3),
    FreezingDrizzleLight("Freezing drizzle", "Freezing drizzle", SkyCategory.FreezingRain, PrecipType.FreezingRain, 1),
    FreezingDrizzleDense("Heavy freezing drizzle", "Heavy freezing drizzle", SkyCategory.FreezingRain, PrecipType.FreezingRain, 2),
    RainLight("Light rain", "Light rain", SkyCategory.Rain, PrecipType.Rain, 1),
    Rain("Rain", "Rain", SkyCategory.Rain, PrecipType.Rain, 2),
    RainHeavy("Heavy rain", "Heavy rain", SkyCategory.Rain, PrecipType.Rain, 3),
    FreezingRainLight("Freezing rain", "Freezing rain", SkyCategory.FreezingRain, PrecipType.FreezingRain, 1),
    FreezingRainHeavy("Heavy freezing rain", "Heavy freezing rain", SkyCategory.FreezingRain, PrecipType.FreezingRain, 3),
    SnowLight("Light snow", "Light snow", SkyCategory.Snow, PrecipType.Snow, 1),
    Snow("Snow", "Snow", SkyCategory.Snow, PrecipType.Snow, 2),
    SnowHeavy("Heavy snow", "Heavy snow", SkyCategory.Snow, PrecipType.Snow, 3),
    SnowGrains("Snow grains", "Snow grains", SkyCategory.Snow, PrecipType.Snow, 1),
    ShowersLight("Light showers", "Light showers", SkyCategory.Showers, PrecipType.Rain, 1),
    Showers("Showers", "Showers", SkyCategory.Showers, PrecipType.Rain, 2),
    ShowersViolent("Heavy showers", "Heavy showers", SkyCategory.Showers, PrecipType.Rain, 3),
    SnowShowersLight("Snow showers", "Snow showers", SkyCategory.SnowShowers, PrecipType.Snow, 1),
    SnowShowersHeavy("Heavy snow showers", "Heavy snow showers", SkyCategory.SnowShowers, PrecipType.Snow, 3),
    Thunderstorm("Thunderstorms", "Thunderstorms", SkyCategory.Thunderstorm, PrecipType.Rain, 2),
    ThunderstormHail("Thunderstorms with hail", "Thunderstorms with hail", SkyCategory.Thunderstorm, PrecipType.Hail, 2),
    ThunderstormHeavyHail("Severe thunderstorms", "Severe thunderstorms", SkyCategory.Thunderstorm, PrecipType.Hail, 3),
    Unknown("—", "—", SkyCategory.Cloudy, PrecipType.None, 0);

    fun label(isDay: Boolean): String = if (isDay) dayLabel else nightLabel

    val isPrecipitation: Boolean get() = precip != PrecipType.None

    companion object {
        fun fromCode(code: Int?): WeatherCondition = when (code) {
            0 -> Clear
            1 -> MainlyClear
            2 -> PartlyCloudy
            3 -> Overcast
            45 -> Fog
            48 -> RimeFog
            51 -> DrizzleLight
            53 -> Drizzle
            55 -> DrizzleDense
            56 -> FreezingDrizzleLight
            57 -> FreezingDrizzleDense
            61 -> RainLight
            63 -> Rain
            65 -> RainHeavy
            66 -> FreezingRainLight
            67 -> FreezingRainHeavy
            71 -> SnowLight
            73 -> Snow
            75 -> SnowHeavy
            77 -> SnowGrains
            80 -> ShowersLight
            81 -> Showers
            82 -> ShowersViolent
            85 -> SnowShowersLight
            86 -> SnowShowersHeavy
            95 -> Thunderstorm
            96 -> ThunderstormHail
            99 -> ThunderstormHeavyHail
            else -> Unknown
        }
    }
}
