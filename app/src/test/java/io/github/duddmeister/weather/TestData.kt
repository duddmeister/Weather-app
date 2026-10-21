package io.github.duddmeister.weather

import io.github.duddmeister.weather.model.CurrentConditions
import io.github.duddmeister.weather.model.DailyPoint
import io.github.duddmeister.weather.model.HourlyPoint
import io.github.duddmeister.weather.model.MinutelyPoint
import io.github.duddmeister.weather.model.WeatherSnapshot
import java.time.LocalDate
import java.time.ZoneId

/** Builders for synthetic snapshots. Hourly point i is at [startEpoch] + i hours. */
object TestData {
    fun snapshot(
        fetchedAtMillis: Long,
        startEpoch: Long,
        hours: Int = 72,
        timezone: String = "UTC",
        temperature: (Int) -> Double? = { 10.0 },
        precipitation: (Int) -> Double? = { 0.0 },
        probability: (Int) -> Double? = { 0.0 },
        code: (Int) -> Int? = { 0 },
        cloud: (Int) -> Double? = { 0.0 },
        windSpeed: (Int) -> Double? = { 5.0 },
        windDirection: (Int) -> Double? = { 180.0 },
        windGusts: (Int) -> Double? = { 10.0 },
        uv: (Int) -> Double? = { null },
        minutely: List<MinutelyPoint> = emptyList(),
        daily: List<DailyPoint> = emptyList(),
        current: CurrentConditions = CurrentConditions(time = fetchedAtMillis / 1000, temperature = 20.0),
    ) = WeatherSnapshot(
        locationId = "test",
        latitude = 40.0,
        longitude = -74.0,
        timezone = timezone,
        fetchedAt = fetchedAtMillis,
        current = current,
        minutely = minutely,
        hourly = (0 until hours).map { i ->
            HourlyPoint(
                time = startEpoch + i * 3600L,
                temperature = temperature(i),
                precipitation = precipitation(i),
                precipitationProbability = probability(i),
                weatherCode = code(i),
                cloudCover = cloud(i),
                windSpeed = windSpeed(i),
                windDirection = windDirection(i),
                windGusts = windGusts(i),
                uvIndex = uv(i),
            )
        },
        daily = daily,
    )

    fun day(date: LocalDate, zone: ZoneId, max: Double, min: Double, code: Int = 0) = DailyPoint(
        date = date.toString(),
        time = date.atStartOfDay(zone).toEpochSecond(),
        weatherCode = code,
        temperatureMax = max,
        temperatureMin = min,
        sunrise = date.atTime(7, 0).atZone(zone).toEpochSecond(),
        sunset = date.atTime(18, 30).atZone(zone).toEpochSecond(),
    )

    /** 15-minute points; point i ends at [firstSlotEnd] + i*15min and holds [mm] (in 15 min). */
    fun minutely(firstSlotEnd: Long, vararg mm: Double) = mm.mapIndexed { i, v ->
        MinutelyPoint(time = firstSlotEnd + i * 900L, precipitation = v, snowfall = 0.0, weatherCode = if (v > 0) 61 else 3)
    }
}
