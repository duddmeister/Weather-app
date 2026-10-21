package io.github.duddmeister.weather.model

import kotlinx.serialization.Serializable

/**
 * Everything we know about the weather at one location, as fetched at [fetchedAt]. Values are in
 * the API's metric units (°C, km/h, mm, cm of snow, hPa, metres); times are epoch seconds unless
 * noted. This is also the on-disk cache format, so new fields must have defaults.
 */
@Serializable
data class WeatherSnapshot(
    val locationId: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val utcOffsetSeconds: Int = 0,
    /** Epoch millis of the successful fetch. */
    val fetchedAt: Long,
    val current: CurrentConditions,
    val minutely: List<MinutelyPoint> = emptyList(),
    val hourly: List<HourlyPoint> = emptyList(),
    val daily: List<DailyPoint> = emptyList(),
    val airQuality: AirQuality? = null,
    val alerts: List<WeatherAlert> = emptyList(),
    /** True when an alert source covers this location (so "no alerts" is meaningful). */
    val alertsSupported: Boolean = false,
)

@Serializable
data class CurrentConditions(
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double? = null,
    val humidity: Double? = null,
    val dewPoint: Double? = null,
    val weatherCode: Int = 0,
    val isDay: Boolean = true,
    /** Precipitation in the preceding 15 minutes, mm. */
    val precipitation: Double? = null,
    val cloudCover: Double? = null,
    val pressure: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val windGusts: Double? = null,
    val visibility: Double? = null,
    val uvIndex: Double? = null,
)

@Serializable
data class MinutelyPoint(
    val time: Long,
    /** mm in this 15-minute slot. */
    val precipitation: Double? = null,
    /** cm in this 15-minute slot. */
    val snowfall: Double? = null,
    val temperature: Double? = null,
    val weatherCode: Int? = null,
)

@Serializable
data class HourlyPoint(
    val time: Long,
    val temperature: Double? = null,
    val apparentTemperature: Double? = null,
    val humidity: Double? = null,
    val dewPoint: Double? = null,
    val precipitationProbability: Double? = null,
    val precipitation: Double? = null,
    val snowfall: Double? = null,
    val weatherCode: Int? = null,
    val pressure: Double? = null,
    val cloudCover: Double? = null,
    val visibility: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val windGusts: Double? = null,
    val uvIndex: Double? = null,
    val isDay: Boolean? = null,
)

@Serializable
data class DailyPoint(
    /** ISO local date in the location's timezone, e.g. 2026-10-21. */
    val date: String,
    /** Epoch seconds of local midnight. */
    val time: Long,
    val weatherCode: Int? = null,
    val temperatureMax: Double? = null,
    val temperatureMin: Double? = null,
    val apparentMax: Double? = null,
    val apparentMin: Double? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    /** Seconds. */
    val daylightDuration: Double? = null,
    /** Seconds. */
    val sunshineDuration: Double? = null,
    val uvIndexMax: Double? = null,
    val precipitationSum: Double? = null,
    val rainSum: Double? = null,
    val snowfallSum: Double? = null,
    val precipitationHours: Double? = null,
    val precipitationProbabilityMax: Double? = null,
    val windSpeedMax: Double? = null,
    val windGustsMax: Double? = null,
    val windDirectionDominant: Double? = null,
)

@Serializable
data class AirQuality(
    val time: Long? = null,
    val usAqi: Double? = null,
    val europeanAqi: Double? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
    val nitrogenDioxide: Double? = null,
    val sulphurDioxide: Double? = null,
    val carbonMonoxide: Double? = null,
    /** Pollen grains/m³ by plant name, only where available (Europe, in season). */
    val pollen: Map<String, Double> = emptyMap(),
    val hourly: List<AqiPoint> = emptyList(),
)

@Serializable
data class AqiPoint(
    val time: Long,
    val usAqi: Double? = null,
    val europeanAqi: Double? = null,
)

@Serializable
data class WeatherAlert(
    val id: String,
    val event: String,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    /** Extreme, Severe, Moderate, Minor, Unknown. */
    val severity: String? = null,
    val urgency: String? = null,
    val certainty: String? = null,
    val onset: Long? = null,
    val expires: Long? = null,
    val ends: Long? = null,
    val sender: String? = null,
    val areaDesc: String? = null,
) {
    val severityRank: Int
        get() = when (severity?.lowercase()) {
            "extreme" -> 4
            "severe" -> 3
            "moderate" -> 2
            "minor" -> 1
            else -> 0
        }

    /** When the alert stops applying (epoch seconds), if known. */
    val endTime: Long? get() = ends ?: expires
}
