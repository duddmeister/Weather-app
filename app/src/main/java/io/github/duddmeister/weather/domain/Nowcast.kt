package io.github.duddmeister.weather.domain

import io.github.duddmeister.weather.model.MinutelyPoint
import io.github.duddmeister.weather.model.PrecipType
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.model.WeatherSnapshot
import kotlin.math.max
import kotlin.math.roundToInt

/** One 15-minute slot of the precipitation outlook; [mmPerHour] is the slot's rate. */
data class NowcastBar(val start: Long, val mmPerHour: Double)

data class Nowcast(
    val headline: String,
    val kind: PrecipType,
    val bars: List<NowcastBar>,
    val precipitatingNow: Boolean,
    /** Minutes until precipitation starts (null if it's already falling or none is expected). */
    val startsInMinutes: Int?,
    /** Minutes until it stops (null if it doesn't stop within the window). */
    val endsInMinutes: Int?,
) {
    val hasPrecipitation: Boolean get() = kind != PrecipType.None
}

/**
 * Short-term precipitation outlook from Open-Meteo's 15-minute data. Each 15-minute value is the
 * amount that falls in the 15 minutes *before* its timestamp.
 */
object NowcastBuilder {
    /** 0.05 mm in 15 minutes = 0.2 mm/h: the threshold for "it's actually precipitating". */
    const val WET_MM_PER_SLOT = 0.05
    private const val SLOT_SECONDS = 900L

    fun build(snapshot: WeatherSnapshot, nowMillis: Long, horizonMinutes: Int = 120): Nowcast? {
        val nowSec = nowMillis / 1000
        val slotCount = horizonMinutes / 15 + 1
        val slots = snapshot.minutely.filter { it.time > nowSec }.take(slotCount)
        if (slots.size < 4) return null

        val bars = slots.map { NowcastBar(start = it.time - SLOT_SECONDS, mmPerHour = (it.precipitation ?: 0.0) * 4) }
        val fresh = nowMillis - snapshot.fetchedAt < 30 * 60 * 1000L
        val observedNow = fresh &&
            (snapshot.current.precipitation ?: 0.0) >= WET_MM_PER_SLOT &&
            WeatherCondition.fromCode(snapshot.current.weatherCode).isPrecipitation
        val precipitatingNow = isWet(slots.first()) || observedNow

        if (precipitatingNow) {
            val stopIndex = slots.indexOfFirstFrom(1) { !isWet(it) }
            val wetSlots = if (stopIndex == -1) slots else slots.subList(0, stopIndex)
            val kind = kindOf(wetSlots, snapshot.current.weatherCode)
            val noun = describe(kind, wetSlots)
            return if (stopIndex == -1) {
                Nowcast("$noun for at least the next ${horizonPhrase(horizonMinutes)}", kind, bars, true, null, null)
            } else {
                val endsIn = minutesUntil(slots[stopIndex].time - SLOT_SECONDS, nowSec)
                Nowcast("$noun stopping ${inMinutesPhrase(endsIn)}", kind, bars, true, null, endsIn)
            }
        }

        val startIndex = slots.indexOfFirstFrom(1) { isWet(it) }
        if (startIndex == -1) {
            return Nowcast("No precipitation for the next ${horizonPhrase(horizonMinutes)}", PrecipType.None, bars, false, null, null)
        }
        val stopIndex = slots.indexOfFirstFrom(startIndex + 1) { !isWet(it) }
        val wetSlots = if (stopIndex == -1) slots.subList(startIndex, slots.size) else slots.subList(startIndex, stopIndex)
        val kind = kindOf(wetSlots, wetSlots.firstNotNullOfOrNull { it.weatherCode })
        val startsIn = minutesUntil(slots[startIndex].time - SLOT_SECONDS, nowSec)
        val headline = buildString {
            append(describe(kind, wetSlots))
            append(" starting ")
            append(inMinutesPhrase(startsIn))
            if (stopIndex != -1) {
                val duration = ((slots[stopIndex].time - slots[startIndex].time) / 60).toInt()
                append(", lasting about ")
                append(durationPhrase(duration))
            }
        }
        val endsIn = if (stopIndex == -1) null else minutesUntil(slots[stopIndex].time - SLOT_SECONDS, nowSec)
        return Nowcast(headline, kind, bars, false, startsIn, endsIn)
    }

    private fun isWet(point: MinutelyPoint): Boolean = (point.precipitation ?: 0.0) >= WET_MM_PER_SLOT

    private inline fun <T> List<T>.indexOfFirstFrom(from: Int, predicate: (T) -> Boolean): Int {
        for (i in from until size) if (predicate(this[i])) return i
        return -1
    }

    private fun kindOf(points: List<MinutelyPoint>, fallbackCode: Int?): PrecipType {
        val snowy = points.count { (it.snowfall ?: 0.0) > 0.0 }
        if (snowy * 2 >= points.size && snowy > 0) return PrecipType.Snow
        val types = points.mapNotNull { it.weatherCode }.map { WeatherCondition.fromCode(it).precip }
            .filter { it != PrecipType.None }
        val dominant = types.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return dominant ?: WeatherCondition.fromCode(fallbackCode).precip.takeIf { it != PrecipType.None } ?: PrecipType.Rain
    }

    /** "Light rain", "Heavy snow", "Drizzle"… based on the peak rate. */
    private fun describe(kind: PrecipType, points: List<MinutelyPoint>): String {
        val peak = points.maxOfOrNull { (it.precipitation ?: 0.0) * 4 } ?: 0.0
        val noun = when (kind) {
            PrecipType.Snow -> "snow"
            PrecipType.Drizzle -> "drizzle"
            PrecipType.FreezingRain -> "freezing rain"
            PrecipType.Hail -> "hail"
            else -> "rain"
        }
        val adjective = when {
            kind == PrecipType.Drizzle -> ""
            peak < 1.0 -> "light "
            peak >= 4.0 -> "heavy "
            else -> ""
        }
        return (adjective + noun).replaceFirstChar { it.uppercase() }
    }

    private fun minutesUntil(epochSeconds: Long, nowSec: Long): Int = max(0, ((epochSeconds - nowSec) / 60).toInt())

    private fun roundTo5(minutes: Int): Int = max(5, ((minutes / 5.0).roundToInt()) * 5)

    fun inMinutesPhrase(minutes: Int): String = if (minutes < 3) "now" else "in about ${durationPhrase(roundTo5(minutes))}"

    fun durationPhrase(minutes: Int): String {
        val m = roundTo5(minutes)
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> if (m == 60) "1 hour" else "${m / 60} hours"
            else -> "${m / 60} hr ${m % 60} min"
        }
    }

    private fun horizonPhrase(minutes: Int): String = if (minutes % 60 == 0) {
        if (minutes == 60) "hour" else "${minutes / 60} hours"
    } else {
        "$minutes minutes"
    }
}
