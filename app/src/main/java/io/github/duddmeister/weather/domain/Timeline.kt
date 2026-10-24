package io.github.duddmeister.weather.domain

import io.github.duddmeister.weather.model.CurrentConditions
import io.github.duddmeister.weather.model.DailyPoint
import io.github.duddmeister.weather.model.HourlyPoint
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/** Conditions describing a specific moment, either observed (fresh "current") or read off the forecast. */
data class NowConditions(
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double?,
    val weatherCode: Int,
    val isDay: Boolean,
    val humidity: Double?,
    val dewPoint: Double?,
    val windSpeed: Double?,
    val windDirection: Double?,
    val windGusts: Double?,
    val pressure: Double?,
    val visibility: Double?,
    val uvIndex: Double?,
    val cloudCover: Double?,
    val precipitation: Double?,
    /** True when derived from the forecast timeline because the last observation is too old. */
    val estimated: Boolean,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

/** How long the API's "current" block is trusted before we switch to the forecast timeline. */
const val CURRENT_TRUST_MILLIS = 20 * 60 * 1000L

fun WeatherSnapshot.zoneId(): ZoneId =
    runCatching { ZoneId.of(timezone) }.getOrElse { ZoneOffset.ofTotalSeconds(utcOffsetSeconds) }

fun WeatherSnapshot.localDate(nowMillis: Long): LocalDate =
    Instant.ofEpochMilli(nowMillis).atZone(zoneId()).toLocalDate()

fun WeatherSnapshot.dayFor(date: LocalDate): DailyPoint? {
    val iso = date.toString()
    return daily.firstOrNull { it.date == iso }
}

fun WeatherSnapshot.today(nowMillis: Long): DailyPoint? = dayFor(localDate(nowMillis))
fun WeatherSnapshot.yesterday(nowMillis: Long): DailyPoint? = dayFor(localDate(nowMillis).minusDays(1))
fun WeatherSnapshot.tomorrow(nowMillis: Long): DailyPoint? = dayFor(localDate(nowMillis).plusDays(1))

/** Daily entries from today onwards. */
fun WeatherSnapshot.upcomingDays(nowMillis: Long): List<DailyPoint> {
    val today = localDate(nowMillis).toString()
    return daily.filter { it.date >= today }
}

/** Hourly entries starting with the hour that contains [nowMillis]. */
fun WeatherSnapshot.upcomingHours(nowMillis: Long, count: Int = 48): List<HourlyPoint> {
    val nowSec = nowMillis / 1000
    val start = hourly.indexOfLast { it.time <= nowSec }.coerceAtLeast(0)
    return hourly.drop(start).take(count)
}

/** Whether the sun is up at [epochSeconds], from the daily sunrise/sunset times; null if unknown. */
fun WeatherSnapshot.isDaylightAt(epochSeconds: Long): Boolean? {
    val date = Instant.ofEpochSecond(epochSeconds).atZone(zoneId()).toLocalDate()
    val day = dayFor(date) ?: return null
    val rise = day.sunrise ?: return null
    val set = day.sunset ?: return null
    if (rise == set) return null
    return epochSeconds in rise until set
}

private fun CurrentConditions.toNow(estimated: Boolean) = NowConditions(
    time = time,
    temperature = temperature,
    apparentTemperature = apparentTemperature,
    weatherCode = weatherCode,
    isDay = isDay,
    humidity = humidity,
    dewPoint = dewPoint,
    windSpeed = windSpeed,
    windDirection = windDirection,
    windGusts = windGusts,
    pressure = pressure,
    visibility = visibility,
    uvIndex = uvIndex,
    cloudCover = cloudCover,
    precipitation = precipitation,
    estimated = estimated,
)

/**
 * The best estimate of conditions at [nowMillis]. A fresh observation is used as-is; otherwise we
 * interpolate the hourly forecast (and use the 15-minute weather code when available), so a widget
 * re-rendered hours after the last fetch still shows the temperature for now, not for back then.
 */
fun WeatherSnapshot.conditionsAt(nowMillis: Long): NowConditions {
    val age = nowMillis - fetchedAt
    if (age in -5 * 60 * 1000L..CURRENT_TRUST_MILLIS) return current.toNow(estimated = false)

    val nowSec = nowMillis / 1000
    val beforeIndex = hourly.indexOfLast { it.time <= nowSec && it.temperature != null }
    if (beforeIndex < 0) return current.toNow(estimated = true)
    val before = hourly[beforeIndex]
    val after = hourly.getOrNull(beforeIndex + 1)?.takeIf { it.temperature != null }
    // Data that ends long before now can't tell us anything better than the last observation.
    if (after == null && nowSec - before.time > 3 * 3600) return current.toNow(estimated = true)

    val fraction = if (after != null && after.time > before.time) {
        ((nowSec - before.time).toDouble() / (after.time - before.time)).coerceIn(0.0, 1.0)
    } else {
        0.0
    }

    fun lerp(a: Double?, b: Double?): Double? = interpolate(a, b, fraction)
    fun lerpAngle(a: Double?, b: Double?): Double? = interpolateAngle(a, b, fraction)

    val slot = minutely.firstOrNull { it.time > nowSec && it.time - 900 <= nowSec }
    // The hour labelled T describes the hour ending at T, so conditions now belong to the next point.
    val hourForCode = after ?: before
    // The Met Office's symbols are for the time they're labelled with, and beat a generic 15-minute code.
    val metOfficeCode = metOffice?.hourly?.minByOrNull { abs(it.time - nowSec) }
        ?.takeIf { abs(it.time - nowSec) <= 1800 }?.weatherCode
    val code = metOfficeCode ?: slot?.weatherCode ?: hourForCode.weatherCode ?: current.weatherCode
    val isDay = isDaylightAt(nowSec) ?: hourForCode.isDay ?: current.isDay

    return NowConditions(
        time = nowSec,
        temperature = lerp(before.temperature, after?.temperature) ?: current.temperature,
        apparentTemperature = lerp(before.apparentTemperature, after?.apparentTemperature),
        weatherCode = code,
        isDay = isDay,
        humidity = lerp(before.humidity, after?.humidity),
        dewPoint = lerp(before.dewPoint, after?.dewPoint),
        windSpeed = lerp(before.windSpeed, after?.windSpeed),
        windDirection = lerpAngle(before.windDirection, after?.windDirection),
        windGusts = lerp(before.windGusts, after?.windGusts),
        pressure = lerp(before.pressure, after?.pressure),
        visibility = lerp(before.visibility, after?.visibility),
        uvIndex = lerp(before.uvIndex, after?.uvIndex),
        cloudCover = lerp(before.cloudCover, after?.cloudCover),
        precipitation = slot?.precipitation ?: hourForCode.precipitation?.div(4),
        estimated = true,
    )
}

/** Linear interpolation that falls back to whichever value is known. */
internal fun interpolate(a: Double?, b: Double?, fraction: Double): Double? = when {
    a == null -> b
    b == null -> a
    else -> a + (b - a) * fraction
}

/** Angles need shortest-path interpolation (350° → 10° passes through 0°, not 180°). */
internal fun interpolateAngle(a: Double?, b: Double?, fraction: Double): Double? {
    if (a == null || b == null) return a ?: b
    var delta = (b - a) % 360
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    return ((a + delta * fraction) % 360 + 360) % 360
}

enum class PressureTrend(val label: String) { Rising("Rising"), Falling("Falling"), Steady("Steady") }

/** Pressure tendency over the last three hours (±1 hPa counts as steady). */
fun WeatherSnapshot.pressureTrend(nowMillis: Long): PressureTrend? {
    val nowSec = nowMillis / 1000
    val nowPoint = hourly.filter { it.pressure != null }.minByOrNull { abs(it.time - nowSec) } ?: return null
    val earlier = hourly.filter { it.pressure != null }.minByOrNull { abs(it.time - (nowPoint.time - 3 * 3600)) }
        ?: return null
    if (abs(earlier.time - (nowPoint.time - 3 * 3600)) > 3600) return null
    val delta = nowPoint.pressure!! - earlier.pressure!!
    return when {
        delta > 1.0 -> PressureTrend.Rising
        delta < -1.0 -> PressureTrend.Falling
        else -> PressureTrend.Steady
    }
}

/** Precipitation (mm) that fell over the 24 hours before [nowMillis]. */
fun WeatherSnapshot.precipitationLast24h(nowMillis: Long): Double? {
    val nowSec = nowMillis / 1000
    val points = hourly.filter { it.time > nowSec - 24 * 3600 && it.time <= nowSec }
    if (points.size < 12) return null
    return points.sumOf { it.precipitation ?: 0.0 }
}

/** Precipitation (mm) expected over the next 24 hours. */
fun WeatherSnapshot.precipitationNext24h(nowMillis: Long): Double? {
    val nowSec = nowMillis / 1000
    val points = hourly.filter { it.time > nowSec && it.time <= nowSec + 24 * 3600 }
    if (points.size < 12) return null
    return points.sumOf { it.precipitation ?: 0.0 }
}
