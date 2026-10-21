package io.github.duddmeister.weather.domain

import io.github.duddmeister.weather.model.HourlyPoint
import io.github.duddmeister.weather.model.SkyCategory
import io.github.duddmeister.weather.model.TemperatureUnit
import io.github.duddmeister.weather.model.UnitPrefs
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.model.WeatherSnapshot
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Writes a short plain-English summary of the rest of today (or tonight, after 6 PM), e.g.
 * "Partly cloudy today. Rain likely from 3 PM to 6 PM. High of 18°, 4° cooler than yesterday."
 */
object SummaryBuilder {

    fun build(
        snapshot: WeatherSnapshot,
        nowMillis: Long,
        units: UnitPrefs,
        use24h: Boolean,
        locale: Locale = Locale.getDefault(),
    ): String {
        val zone = snapshot.zoneId()
        val text = TimeText(zone, use24h, locale)
        val nowLocal = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val evening = nowLocal.hour >= 18
        val tomorrowDate = nowLocal.toLocalDate().plusDays(1)
        val periodEnd = if (evening) {
            tomorrowDate.atTime(7, 0).atZone(zone).toEpochSecond()
        } else {
            tomorrowDate.atStartOfDay(zone).toEpochSecond()
        }
        val nowSec = nowMillis / 1000
        // Each hourly point describes the hour ending at its timestamp.
        val hours = snapshot.hourly.filter { it.time > nowSec && it.time <= periodEnd }
        if (hours.isEmpty()) return ""

        val period = if (evening) "tonight" else "today"
        val sentences = mutableListOf<String>()
        sentences += "${skyPhrase(hours, evening)} $period."
        precipitationPhrase(hours, nowSec, periodEnd, period, text)?.let { sentences += it }
        sentences += temperaturePhrase(snapshot, hours, nowMillis, evening, units)
        windPhrase(hours, units)?.let { sentences += it }
        if (!evening) uvPhrase(hours, text)?.let { sentences += it }
        if (evening) tomorrowPhrase(snapshot, nowMillis, units)?.let { sentences += it }
        return sentences.joinToString(" ")
    }

    private fun condition(h: HourlyPoint) = WeatherCondition.fromCode(h.weatherCode)

    private fun skyPhrase(hours: List<HourlyPoint>, evening: Boolean): String {
        val foggy = hours.count { condition(it).category == SkyCategory.Fog }
        if (foggy * 5 >= hours.size * 2) return "Foggy"
        val night = evening || hours.count { it.isDay == false } * 2 > hours.size
        val clouds = hours.mapNotNull { it.cloudCover }
        val average = if (clouds.isNotEmpty()) clouds.average() else {
            hours.map {
                when (condition(it).category) {
                    SkyCategory.Clear -> 10.0
                    SkyCategory.PartlyCloudy -> 50.0
                    else -> 90.0
                }
            }.average()
        }
        return when {
            average < 15 -> if (night) "Clear" else "Sunny"
            average < 35 -> if (night) "Mostly clear" else "Mostly sunny"
            average < 65 -> "Partly cloudy"
            average < 85 -> "Mostly cloudy"
            else -> "Cloudy"
        }
    }

    private fun isWet(h: HourlyPoint): Boolean {
        val amount = h.precipitation ?: 0.0
        val probability = h.precipitationProbability ?: 100.0
        return (amount >= 0.2 && probability >= 40.0) || amount >= 1.0
    }

    private fun isPossible(h: HourlyPoint): Boolean =
        (h.precipitationProbability ?: 0.0) >= 30.0 && (h.precipitation ?: 0.0) >= 0.1

    private fun windows(hours: List<HourlyPoint>): List<List<HourlyPoint>> {
        val result = mutableListOf<MutableList<HourlyPoint>>()
        for (h in hours) {
            if (!isWet(h)) continue
            val last = result.lastOrNull()?.lastOrNull()
            if (last != null && h.time - last.time <= 3600) result.last() += h else result += mutableListOf(h)
        }
        return result
    }

    private fun kindNoun(points: List<HourlyPoint>): String {
        val categories = points.map { condition(it).category }
        fun share(vararg c: SkyCategory) = categories.count { it in c }
        return when {
            share(SkyCategory.Thunderstorm) > 0 -> "Thunderstorms"
            share(SkyCategory.Snow, SkyCategory.SnowShowers) * 2 >= points.size -> "Snow"
            share(SkyCategory.FreezingRain) * 2 >= points.size -> "Freezing rain"
            share(SkyCategory.Showers) * 2 >= points.size -> "Showers"
            share(SkyCategory.Drizzle) * 2 >= points.size -> "Drizzle"
            else -> "Rain"
        }
    }

    private fun precipitationPhrase(
        hours: List<HourlyPoint>,
        nowSec: Long,
        periodEnd: Long,
        period: String,
        text: TimeText,
    ): String? {
        val wet = windows(hours)
        if (wet.isEmpty()) {
            val possible = hours.filter(::isPossible)
            if (possible.isEmpty()) return null
            val noun = kindNoun(possible).lowercase()
            return "A chance of $noun around ${text.hour(possible.first().time - 3600)}."
        }
        fun describe(window: List<HourlyPoint>, first: Boolean): String {
            val start = window.first().time - 3600
            val end = window.last().time
            val startsNow = start <= nowSec + 1800
            val untilEnd = end >= periodEnd - 1800
            return when {
                startsNow && untilEnd -> if (first) "continuing $period" else "again later"
                startsNow -> "until around ${text.hour(end)}"
                untilEnd -> if (first) "likely after ${text.hour(start)}" else "again after ${text.hour(start)}"
                else -> if (first) "likely from ${text.hour(start)} to ${text.hour(end)}" else "again from ${text.hour(start)} to ${text.hour(end)}"
            }
        }
        val noun = kindNoun(wet.flatten())
        val first = describe(wet[0], first = true)
        val second = wet.getOrNull(1)?.let { ", and ${describe(it, first = false)}" }.orEmpty()
        return "$noun $first$second."
    }

    private fun temperaturePhrase(
        snapshot: WeatherSnapshot,
        hours: List<HourlyPoint>,
        nowMillis: Long,
        evening: Boolean,
        units: UnitPrefs,
    ): String {
        if (evening) {
            val low = hours.mapNotNull { it.temperature }.minOrNull()
            return "Low of ${Units.formatTemperature(low, units.temperature)}."
        }
        val today = snapshot.today(nowMillis)
        val high = today?.temperatureMax ?: hours.mapNotNull { it.temperature }.maxOrNull()
        val base = "High of ${Units.formatTemperature(high, units.temperature)}"
        val yesterdayHigh = snapshot.yesterday(nowMillis)?.temperatureMax
        if (high == null || yesterdayHigh == null) return "$base."
        val delta = Units.temperatureDelta(high - yesterdayHigh, units.temperature)
        val threshold = if (units.temperature == TemperatureUnit.Fahrenheit) 5 else 3
        return when {
            delta >= threshold -> "$base, $delta° warmer than yesterday."
            delta <= -threshold -> "$base, ${abs(delta)}° cooler than yesterday."
            else -> "$base."
        }
    }

    private fun windPhrase(hours: List<HourlyPoint>, units: UnitPrefs): String? {
        val gust = hours.mapNotNull { it.windGusts }.maxOrNull()
        val speed = hours.mapNotNull { it.windSpeed }.maxOrNull()
        return when {
            gust != null && gust >= 50 -> "Gusts up to ${Units.formatWind(gust, units.wind)}."
            speed != null && speed >= 30 -> "Breezy, with winds around ${Units.formatWind(speed, units.wind)}."
            else -> null
        }
    }

    private fun uvPhrase(hours: List<HourlyPoint>, text: TimeText): String? {
        val peak = hours.filter { it.isDay != false }.maxByOrNull { it.uvIndex ?: 0.0 } ?: return null
        val uv = peak.uvIndex ?: return null
        if (uv < 6) return null
        return "UV index peaks at ${uv.roundToInt()} around ${text.hour(peak.time)}."
    }

    private fun tomorrowPhrase(snapshot: WeatherSnapshot, nowMillis: Long, units: UnitPrefs): String? {
        val day = snapshot.tomorrow(nowMillis) ?: return null
        val condition = WeatherCondition.fromCode(day.weatherCode)
        val high = Units.formatTemperature(day.temperatureMax, units.temperature)
        val chance = day.precipitationProbabilityMax?.roundToInt()
        val rain = if (!condition.isPrecipitation && chance != null && chance >= 40) ", $chance% chance of precipitation" else ""
        return "Tomorrow: ${condition.dayLabel.lowercase().replaceFirstChar { it.uppercase() }}, high of $high$rain."
    }
}
