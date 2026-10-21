package io.github.duddmeister.weather.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Formats times in a location's own timezone (so a Tokyo forecast shows Tokyo times). */
class TimeText(
    val zone: ZoneId,
    private val use24h: Boolean,
    private val locale: Locale = Locale.getDefault(),
) {
    private val hourFormat = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h a", locale)
    private val timeFormat = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale)
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", locale)
    private val shortDateFormat = DateTimeFormatter.ofPattern("MMM d", locale)

    /** "3 PM" or "15:00". */
    fun hour(epochSeconds: Long): String = hourFormat.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    /** "3:42 PM" or "15:42". */
    fun time(epochSeconds: Long): String = timeFormat.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    fun timeMillis(epochMillis: Long): String = time(epochMillis / 1000)

    fun weekdayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    fun weekdayLong(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    /** "Today" / "Tue" for list rows. */
    fun dayLabel(date: LocalDate, today: LocalDate): String =
        if (date == today) "Today" else weekdayShort(date)

    /** "Tuesday, Oct 21". */
    fun fullDate(date: LocalDate): String = dateFormat.format(date)

    fun shortDate(date: LocalDate): String = shortDateFormat.format(date)

    /** "3:42 PM", or "Tue 3:42 PM" when it isn't today in this zone. */
    fun timeWithDay(epochSeconds: Long, nowMillis: Long): String {
        val target = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (target.toLocalDate()) {
            today -> time(epochSeconds)
            today.plusDays(1) -> "tomorrow ${time(epochSeconds)}"
            else -> "${weekdayShort(target.toLocalDate())} ${time(epochSeconds)}"
        }
    }

    companion object {
        /** "just now", "4 min ago", "2 hr ago", "3 days ago". */
        fun relativeAge(nowMillis: Long, thenMillis: Long): String {
            val minutes = ((nowMillis - thenMillis) / 60_000L).coerceAtLeast(0)
            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "$minutes min ago"
                minutes < 24 * 60 -> "${minutes / 60} hr ago"
                minutes < 48 * 60 -> "yesterday"
                else -> "${minutes / (24 * 60)} days ago"
            }
        }

        /** "10h 32m". */
        fun hoursMinutes(seconds: Double): String {
            val total = (seconds / 60).toLong()
            return "${total / 60}h ${total % 60}m"
        }
    }
}
