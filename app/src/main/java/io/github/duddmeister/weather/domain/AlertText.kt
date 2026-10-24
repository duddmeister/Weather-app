package io.github.duddmeister.weather.domain

import io.github.duddmeister.weather.model.WeatherAlert

/** Whether the alert hasn't started yet; warnings are often issued a day or more ahead. */
fun WeatherAlert.isUpcoming(nowMillis: Long): Boolean = onset != null && onset * 1000 > nowMillis

/** When an alert applies: "Until tomorrow 10:00", or "From 20:00 until tomorrow 10:00" before it starts. */
fun WeatherAlert.window(text: TimeText, nowMillis: Long): String? {
    val until = endTime?.let { text.timeWithDay(it, nowMillis) }
    val from = onset?.takeIf { isUpcoming(nowMillis) }?.let { text.timeWithDay(it, nowMillis) }
    return when {
        from != null && until != null -> "From $from until $until"
        from != null -> "From $from"
        until != null -> "Until $until"
        else -> null
    }
}
