package io.github.duddmeister.weather.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Open-Meteo responses requested with timeformat=unixtime. Weather codes and flags are decoded as
// Double so an integer or decimal encoding both parse; arrays may contain nulls.

@Serializable
data class ForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val timezone: String = "GMT",
    @SerialName("timezone_abbreviation") val timezoneAbbreviation: String? = null,
    val elevation: Double? = null,
    val current: CurrentDto? = null,
    @SerialName("minutely_15") val minutely15: Minutely15Dto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null,
)

@Serializable
data class CurrentDto(
    val time: Long = 0,
    val interval: Int? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("is_day") val isDay: Double? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Double? = null,
    @SerialName("cloud_cover") val cloudCover: Double? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Double? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
    val visibility: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
)

@Serializable
data class Minutely15Dto(
    val time: List<Long> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    val snowfall: List<Double?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Double?> = emptyList(),
)

@Serializable
data class HourlyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Double?> = emptyList(),
    @SerialName("dew_point_2m") val dewPoint: List<Double?> = emptyList(),
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    val snowfall: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Double?> = emptyList(),
    @SerialName("pressure_msl") val pressureMsl: List<Double?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Double?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
    @SerialName("is_day") val isDay: List<Double?> = emptyList(),
)

@Serializable
data class DailyDto(
    val time: List<Long> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Double?> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_max") val apparentMax: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_min") val apparentMin: List<Double?> = emptyList(),
    val sunrise: List<Long?> = emptyList(),
    val sunset: List<Long?> = emptyList(),
    @SerialName("daylight_duration") val daylightDuration: List<Double?> = emptyList(),
    @SerialName("sunshine_duration") val sunshineDuration: List<Double?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @SerialName("rain_sum") val rainSum: List<Double?> = emptyList(),
    @SerialName("snowfall_sum") val snowfallSum: List<Double?> = emptyList(),
    @SerialName("precipitation_hours") val precipitationHours: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m_max") val windGustsMax: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirectionDominant: List<Double?> = emptyList(),
)

@Serializable
data class AirQualityResponse(
    val timezone: String? = null,
    val current: AirQualityCurrentDto? = null,
    val hourly: AirQualityHourlyDto? = null,
)

@Serializable
data class AirQualityCurrentDto(
    val time: Long = 0,
    @SerialName("us_aqi") val usAqi: Double? = null,
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    val pm10: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    @SerialName("carbon_monoxide") val carbonMonoxide: Double? = null,
    @SerialName("nitrogen_dioxide") val nitrogenDioxide: Double? = null,
    @SerialName("sulphur_dioxide") val sulphurDioxide: Double? = null,
    val ozone: Double? = null,
    @SerialName("alder_pollen") val alderPollen: Double? = null,
    @SerialName("birch_pollen") val birchPollen: Double? = null,
    @SerialName("grass_pollen") val grassPollen: Double? = null,
    @SerialName("mugwort_pollen") val mugwortPollen: Double? = null,
    @SerialName("olive_pollen") val olivePollen: Double? = null,
    @SerialName("ragweed_pollen") val ragweedPollen: Double? = null,
)

@Serializable
data class AirQualityHourlyDto(
    val time: List<Long> = emptyList(),
    @SerialName("us_aqi") val usAqi: List<Double?> = emptyList(),
    @SerialName("european_aqi") val europeanAqi: List<Double?> = emptyList(),
)

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult> = emptyList(),
)

@Serializable
data class GeocodingResult(
    val id: Long = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val elevation: Double? = null,
    @SerialName("feature_code") val featureCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val timezone: String? = null,
    val population: Long? = null,
)
