package io.github.duddmeister.weather.domain

import io.github.duddmeister.weather.model.CurrentConditions
import io.github.duddmeister.weather.model.DailyPoint
import io.github.duddmeister.weather.model.HourlyPoint
import io.github.duddmeister.weather.model.MetOfficeData
import io.github.duddmeister.weather.model.MetOfficeDay
import io.github.duddmeister.weather.model.MetOfficeHour
import io.github.duddmeister.weather.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate

/**
 * Lays the Met Office's own forecast over Open-Meteo's. Its hourly forecast (two days) replaces the
 * matching hours and the current conditions; its daily one (a week) replaces the matching days'
 * weather, temperatures, chance of rain and UV. Open-Meteo fills in the rest: the 15-minute nowcast,
 * cloud cover, sunrise and sunset, rain totals, wind for the day and the second week.
 */
fun WeatherSnapshot.withMetOffice(data: MetOfficeData): WeatherSnapshot {
    val hours = data.hourly.associateBy { it.time }
    val zone = zoneId()
    val hoursByDate = data.hourly.groupBy { Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate().toString() }
    val days = data.daily.associateBy { it.date }
    return copy(
        current = current.overlaidWith(data.hourly, fetchedAt / 1000),
        hourly = hourly.map { point -> hours[point.time]?.let(point::overlaidWith) ?: point },
        daily = daily.map { day ->
            val metOfficeDay = days[day.date] ?: return@map day
            val nightBefore = days[LocalDate.parse(day.date).minusDays(1).toString()]
            day.overlaidWith(metOfficeDay, nightBefore, hoursByDate[day.date].orEmpty())
        },
        metOffice = data,
    )
}

private fun HourlyPoint.overlaidWith(hour: MetOfficeHour) = copy(
    temperature = hour.temperature ?: temperature,
    apparentTemperature = hour.feelsLike ?: apparentTemperature,
    humidity = hour.humidity ?: humidity,
    dewPoint = hour.dewPoint ?: dewPoint,
    precipitationProbability = hour.precipitationProbability ?: precipitationProbability,
    precipitation = hour.precipitation ?: precipitation,
    weatherCode = hour.weatherCode ?: weatherCode,
    pressure = hour.pressure ?: pressure,
    visibility = hour.visibility ?: visibility,
    windSpeed = hour.windSpeed ?: windSpeed,
    windDirection = hour.windDirection ?: windDirection,
    windGusts = hour.windGusts ?: windGusts,
    uvIndex = hour.uvIndex ?: uvIndex,
)

/** Conditions at [at] (epoch seconds), between the Met Office's hours on either side. */
private fun CurrentConditions.overlaidWith(hours: List<MetOfficeHour>, at: Long): CurrentConditions {
    val before = hours.lastOrNull { it.time <= at }?.takeIf { at - it.time <= 3600 }
    val after = hours.firstOrNull { it.time > at }?.takeIf { it.time - at <= 3600 }
    if (before == null && after == null) return this
    val fraction = if (before != null && after != null) (at - before.time).toDouble() / (after.time - before.time) else 0.0
    fun value(of: (MetOfficeHour) -> Double?): Double? = interpolate(before?.let(of), after?.let(of), fraction)
    val nearest = if (before != null && (after == null || fraction < 0.5)) before else after!!
    return copy(
        temperature = value { it.temperature } ?: temperature,
        apparentTemperature = value { it.feelsLike } ?: apparentTemperature,
        humidity = value { it.humidity } ?: humidity,
        dewPoint = value { it.dewPoint } ?: dewPoint,
        weatherCode = nearest.weatherCode ?: weatherCode,
        pressure = value { it.pressure } ?: pressure,
        windSpeed = value { it.windSpeed } ?: windSpeed,
        windDirection = interpolateAngle(before?.windDirection, after?.windDirection, fraction) ?: windDirection,
        windGusts = value { it.windGusts } ?: windGusts,
        visibility = value { it.visibility } ?: visibility,
        uvIndex = value { it.uvIndex } ?: uvIndex,
    )
}

/**
 * The Met Office splits a day into its daytime and the night that follows. A calendar day's low
 * comes from the night leading into it, and its range also covers the hourly temperatures that
 * fall on it, so it agrees with the hourly forecast. Its chance of rain is for the day and the
 * night after it, as the Met Office gives a day. Values the Met Office leaves out (as it does for
 * parts of a day that are over) stay as they were.
 */
private fun DailyPoint.overlaidWith(day: MetOfficeDay, nightBefore: MetOfficeDay?, hours: List<MetOfficeHour>): DailyPoint {
    val temperatures = hours.mapNotNull { it.temperature }
    val feelsLike = hours.mapNotNull { it.feelsLike }
    return copy(
        weatherCode = day.dayWeatherCode ?: weatherCode,
        temperatureMax = maxOfKnown(day.dayMax ?: temperatureMax, temperatures.maxOrNull()),
        temperatureMin = minOfKnown(nightBefore?.nightMin ?: temperatureMin, temperatures.minOrNull()),
        apparentMax = maxOfKnown(day.dayMaxFeelsLike ?: apparentMax, feelsLike.maxOrNull()),
        apparentMin = minOfKnown(nightBefore?.nightMinFeelsLike ?: apparentMin, feelsLike.minOrNull()),
        precipitationProbabilityMax = maxOfKnown(day.dayPrecipitationProbability, day.nightPrecipitationProbability)
            ?: precipitationProbabilityMax,
        uvIndexMax = maxOfKnown(day.maxUvIndex ?: uvIndexMax, hours.mapNotNull { it.uvIndex }.maxOrNull()),
    )
}

private fun maxOfKnown(a: Double?, b: Double?): Double? = if (a == null || b == null) a ?: b else maxOf(a, b)
private fun minOfKnown(a: Double?, b: Double?): Double? = if (a == null || b == null) a ?: b else minOf(a, b)
