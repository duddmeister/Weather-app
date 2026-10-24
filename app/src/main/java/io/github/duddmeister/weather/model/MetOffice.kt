package io.github.duddmeister.weather.model

import kotlinx.serialization.Serializable

/**
 * The Met Office's own site-specific forecast (Weather DataHub), in the app's units. It's fetched
 * less often than the rest of the forecast to stay within the free plan, so it's kept with the
 * snapshot and laid over each fresh Open-Meteo forecast until it's due again.
 */
@Serializable
data class MetOfficeData(
    /** Hourly for the next two days. */
    val hourly: List<MetOfficeHour> = emptyList(),
    /** Daily for the week, from yesterday (whose night leads into today). */
    val daily: List<MetOfficeDay> = emptyList(),
    /** Epoch millis of the last successful fetch of each part. */
    val hourlyFetchedAt: Long = 0,
    val dailyFetchedAt: Long = 0,
)

@Serializable
data class MetOfficeHour(
    /** Epoch seconds, on the hour. */
    val time: Long,
    /** °C */
    val temperature: Double? = null,
    val feelsLike: Double? = null,
    val dewPoint: Double? = null,
    /** % */
    val humidity: Double? = null,
    /** km/h */
    val windSpeed: Double? = null,
    /** Degrees the wind blows from. */
    val windDirection: Double? = null,
    /** km/h */
    val windGusts: Double? = null,
    /** Metres. */
    val visibility: Double? = null,
    /** hPa */
    val pressure: Double? = null,
    val uvIndex: Double? = null,
    /** WMO code, translated from the Met Office's own codes. */
    val weatherCode: Int? = null,
    /** mm in the hour ending at [time]. */
    val precipitation: Double? = null,
    /** % */
    val precipitationProbability: Double? = null,
)

/** One day: its daytime high and the night that follows it, as the Met Office splits them. */
@Serializable
data class MetOfficeDay(
    /** ISO date, e.g. 2026-10-24. */
    val date: String,
    val dayMax: Double? = null,
    val nightMin: Double? = null,
    val dayMaxFeelsLike: Double? = null,
    val nightMinFeelsLike: Double? = null,
    val dayWeatherCode: Int? = null,
    val nightWeatherCode: Int? = null,
    val dayPrecipitationProbability: Double? = null,
    val nightPrecipitationProbability: Double? = null,
    val maxUvIndex: Double? = null,
)
