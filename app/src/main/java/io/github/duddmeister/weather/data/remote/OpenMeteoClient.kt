package io.github.duddmeister.weather.data.remote

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.model.AirQuality
import io.github.duddmeister.weather.model.AqiPoint
import io.github.duddmeister.weather.model.CurrentConditions
import io.github.duddmeister.weather.model.DailyPoint
import io.github.duddmeister.weather.model.HourlyPoint
import io.github.duddmeister.weather.model.MinutelyPoint
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.WeatherSnapshot
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Client for the free Open-Meteo APIs (forecast, air quality, geocoding). No API key needed. */
class OpenMeteoClient(
    private val http: HttpFetcher,
    private val json: Json = AppJson,
) {
    suspend fun forecast(latitude: Double, longitude: Double): ForecastResponse =
        json.decodeFromString(ForecastResponse.serializer(), http.getBody(forecastUrl(latitude, longitude)))

    suspend fun airQuality(latitude: Double, longitude: Double): AirQualityResponse =
        json.decodeFromString(AirQualityResponse.serializer(), http.getBody(airQualityUrl(latitude, longitude)))

    suspend fun search(query: String, language: String, count: Int = 12): List<GeocodingResult> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val body = http.getBody(searchUrl(trimmed, language, count))
        return json.decodeFromString(GeocodingResponse.serializer(), body).results
    }

    companion object {
        const val CURRENT_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code," +
                "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,dew_point_2m," +
                "visibility,uv_index"
        const val MINUTELY_VARIABLES = "precipitation,snowfall,temperature_2m,weather_code"
        const val HOURLY_VARIABLES =
            "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation_probability," +
                "precipitation,snowfall,weather_code,pressure_msl,cloud_cover,visibility,wind_speed_10m," +
                "wind_direction_10m,wind_gusts_10m,uv_index,is_day"
        const val DAILY_VARIABLES =
            "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
                "sunrise,sunset,daylight_duration,sunshine_duration,uv_index_max,precipitation_sum,rain_sum," +
                "snowfall_sum,precipitation_hours,precipitation_probability_max,wind_speed_10m_max," +
                "wind_gusts_10m_max,wind_direction_10m_dominant"
        const val AIR_QUALITY_CURRENT =
            "us_aqi,european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone," +
                "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
        const val AIR_QUALITY_HOURLY = "us_aqi,european_aqi"
        const val FORECAST_DAYS = 14

        fun coordinate(value: Double): String = String.format(Locale.US, "%.4f", value)

        fun forecastUrl(latitude: Double, longitude: Double): HttpUrl =
            "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", coordinate(latitude))
                .addQueryParameter("longitude", coordinate(longitude))
                .addEncodedQueryParameter("current", CURRENT_VARIABLES)
                .addEncodedQueryParameter("minutely_15", MINUTELY_VARIABLES)
                .addEncodedQueryParameter("hourly", HOURLY_VARIABLES)
                .addEncodedQueryParameter("daily", DAILY_VARIABLES)
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("timeformat", "unixtime")
                .addQueryParameter("past_days", "1")
                .addQueryParameter("forecast_days", FORECAST_DAYS.toString())
                .addQueryParameter("past_minutely_15", "4")
                .addQueryParameter("forecast_minutely_15", "24")
                .build()

        fun airQualityUrl(latitude: Double, longitude: Double): HttpUrl =
            "https://air-quality-api.open-meteo.com/v1/air-quality".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", coordinate(latitude))
                .addQueryParameter("longitude", coordinate(longitude))
                .addEncodedQueryParameter("current", AIR_QUALITY_CURRENT)
                .addEncodedQueryParameter("hourly", AIR_QUALITY_HOURLY)
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("timeformat", "unixtime")
                .addQueryParameter("forecast_days", "3")
                .build()

        fun searchUrl(query: String, language: String, count: Int): HttpUrl =
            "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("count", count.toString())
                .addQueryParameter("language", language.ifBlank { "en" })
                .addQueryParameter("format", "json")
                .build()
    }
}

fun GeocodingResult.toSavedLocation(): SavedLocation = SavedLocation(
    id = SavedLocation.idForGeoname(id),
    name = name,
    region = admin1,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    timezone = timezone,
)

/** Maps an API response into the domain snapshot for [location] (air quality and alerts are added separately). */
fun ForecastResponse.toSnapshot(location: SavedLocation, fetchedAtMillis: Long): WeatherSnapshot {
    val hourlyPoints = mapHourly()
    return WeatherSnapshot(
        locationId = location.id,
        latitude = location.latitude,
        longitude = location.longitude,
        timezone = zone().id,
        utcOffsetSeconds = utcOffsetSeconds,
        fetchedAt = fetchedAtMillis,
        current = mapCurrent(hourlyPoints, fetchedAtMillis / 1000),
        minutely = mapMinutely(),
        hourly = hourlyPoints,
        daily = mapDaily(),
    )
}

internal fun ForecastResponse.zone(): ZoneId =
    runCatching { ZoneId.of(timezone) }.getOrElse { ZoneOffset.ofTotalSeconds(utcOffsetSeconds) }

private fun List<Double?>.at(index: Int): Double? = getOrNull(index)?.takeUnless { it.isNaN() }
private fun List<Double?>.intAt(index: Int): Int? = at(index)?.roundToInt()

internal fun ForecastResponse.mapMinutely(): List<MinutelyPoint> {
    val dto = minutely15 ?: return emptyList()
    return dto.time.mapIndexed { i, t ->
        MinutelyPoint(
            time = t,
            precipitation = dto.precipitation.at(i),
            snowfall = dto.snowfall.at(i),
            temperature = dto.temperature.at(i),
            weatherCode = dto.weatherCode.intAt(i),
        )
    }
}

internal fun ForecastResponse.mapHourly(): List<HourlyPoint> {
    val dto = hourly ?: return emptyList()
    return dto.time.mapIndexed { i, t ->
        HourlyPoint(
            time = t,
            temperature = dto.temperature.at(i),
            apparentTemperature = dto.apparentTemperature.at(i),
            humidity = dto.humidity.at(i),
            dewPoint = dto.dewPoint.at(i),
            precipitationProbability = dto.precipitationProbability.at(i),
            precipitation = dto.precipitation.at(i),
            snowfall = dto.snowfall.at(i),
            weatherCode = dto.weatherCode.intAt(i),
            pressure = dto.pressureMsl.at(i),
            cloudCover = dto.cloudCover.at(i),
            visibility = dto.visibility.at(i),
            windSpeed = dto.windSpeed.at(i),
            windDirection = dto.windDirection.at(i),
            windGusts = dto.windGusts.at(i),
            uvIndex = dto.uvIndex.at(i),
            isDay = dto.isDay.at(i)?.let { it >= 0.5 },
        )
    }
}

internal fun ForecastResponse.mapDaily(): List<DailyPoint> {
    val dto = daily ?: return emptyList()
    val zone = zone()
    return dto.time.mapIndexed { i, t ->
        DailyPoint(
            // Daily unixtime values are local midnight; shifting by a few hours guards against
            // rounding quirks around DST changes.
            date = Instant.ofEpochSecond(t + 3 * 3600).atZone(zone).toLocalDate().toString(),
            time = t,
            weatherCode = dto.weatherCode.intAt(i),
            temperatureMax = dto.temperatureMax.at(i),
            temperatureMin = dto.temperatureMin.at(i),
            apparentMax = dto.apparentMax.at(i),
            apparentMin = dto.apparentMin.at(i),
            sunrise = dto.sunrise.getOrNull(i),
            sunset = dto.sunset.getOrNull(i),
            daylightDuration = dto.daylightDuration.at(i),
            sunshineDuration = dto.sunshineDuration.at(i),
            uvIndexMax = dto.uvIndexMax.at(i),
            precipitationSum = dto.precipitationSum.at(i),
            rainSum = dto.rainSum.at(i),
            snowfallSum = dto.snowfallSum.at(i),
            precipitationHours = dto.precipitationHours.at(i),
            precipitationProbabilityMax = dto.precipitationProbabilityMax.at(i),
            windSpeedMax = dto.windSpeedMax.at(i),
            windGustsMax = dto.windGustsMax.at(i),
            windDirectionDominant = dto.windDirectionDominant.at(i),
        )
    }
}

/**
 * Current conditions from the "current" block; if it's missing (shouldn't happen) we fall back to
 * the hourly value closest to [nowEpochSeconds].
 */
internal fun ForecastResponse.mapCurrent(hourlyPoints: List<HourlyPoint>, nowEpochSeconds: Long): CurrentConditions {
    val c = current
    if (c?.temperature != null) {
        return CurrentConditions(
            time = c.time,
            temperature = c.temperature,
            apparentTemperature = c.apparentTemperature,
            humidity = c.humidity,
            dewPoint = c.dewPoint,
            weatherCode = c.weatherCode?.roundToInt() ?: 0,
            isDay = (c.isDay ?: 1.0) >= 0.5,
            precipitation = c.precipitation,
            cloudCover = c.cloudCover,
            pressure = c.pressureMsl,
            windSpeed = c.windSpeed,
            windDirection = c.windDirection,
            windGusts = c.windGusts,
            visibility = c.visibility,
            uvIndex = c.uvIndex,
        )
    }
    val nearest = hourlyPoints.filter { it.temperature != null }.minByOrNull { abs(it.time - nowEpochSeconds) }
        ?: throw IllegalStateException("Forecast response has no current or hourly temperature")
    return CurrentConditions(
        time = nearest.time,
        temperature = nearest.temperature!!,
        apparentTemperature = nearest.apparentTemperature,
        humidity = nearest.humidity,
        dewPoint = nearest.dewPoint,
        weatherCode = nearest.weatherCode ?: 0,
        isDay = nearest.isDay ?: true,
        precipitation = nearest.precipitation,
        cloudCover = nearest.cloudCover,
        pressure = nearest.pressure,
        windSpeed = nearest.windSpeed,
        windDirection = nearest.windDirection,
        windGusts = nearest.windGusts,
        visibility = nearest.visibility,
        uvIndex = nearest.uvIndex,
    )
}

fun AirQualityResponse.toAirQuality(): AirQuality? {
    val c = current
    val hourlyPoints = hourly?.let { h ->
        h.time.mapIndexed { i, t -> AqiPoint(t, h.usAqi.getOrNull(i), h.europeanAqi.getOrNull(i)) }
    }.orEmpty()
    if (c == null && hourlyPoints.isEmpty()) return null
    val pollen = buildMap {
        c?.alderPollen?.let { put("Alder", it) }
        c?.birchPollen?.let { put("Birch", it) }
        c?.grassPollen?.let { put("Grass", it) }
        c?.mugwortPollen?.let { put("Mugwort", it) }
        c?.olivePollen?.let { put("Olive", it) }
        c?.ragweedPollen?.let { put("Ragweed", it) }
    }
    return AirQuality(
        time = c?.time,
        usAqi = c?.usAqi,
        europeanAqi = c?.europeanAqi,
        pm25 = c?.pm25,
        pm10 = c?.pm10,
        ozone = c?.ozone,
        nitrogenDioxide = c?.nitrogenDioxide,
        sulphurDioxide = c?.sulphurDioxide,
        carbonMonoxide = c?.carbonMonoxide,
        pollen = pollen,
        hourly = hourlyPoints,
    )
}
