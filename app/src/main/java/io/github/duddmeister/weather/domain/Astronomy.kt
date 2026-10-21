package io.github.duddmeister.weather.domain

import kotlin.math.PI
import kotlin.math.cos

data class MoonInfo(
    /** 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    val phase: Double,
    /** Illuminated fraction of the disc, 0..1. */
    val illumination: Double,
    val name: String,
    val daysUntilFull: Double,
    val daysUntilNew: Double,
) {
    val waxing: Boolean get() = phase < 0.5
}

object Astronomy {
    private const val SYNODIC_MONTH = 29.530588853
    /** Julian day of the new moon on 2000-01-06 18:14 UTC. */
    private const val REFERENCE_NEW_MOON_JD = 2451550.1

    fun julianDay(epochMillis: Long): Double = epochMillis / 86_400_000.0 + 2440587.5

    fun moon(epochMillis: Long): MoonInfo {
        val days = julianDay(epochMillis) - REFERENCE_NEW_MOON_JD
        val phase = ((days / SYNODIC_MONTH) % 1.0 + 1.0) % 1.0
        val illumination = (1 - cos(2 * PI * phase)) / 2
        val name = when {
            phase < 0.0339 || phase > 0.9661 -> "New moon"
            phase < 0.216 -> "Waxing crescent"
            phase < 0.284 -> "First quarter"
            phase < 0.466 -> "Waxing gibbous"
            phase < 0.534 -> "Full moon"
            phase < 0.716 -> "Waning gibbous"
            phase < 0.784 -> "Last quarter"
            else -> "Waning crescent"
        }
        val untilFull = ((0.5 - phase) % 1.0 + 1.0) % 1.0 * SYNODIC_MONTH
        val untilNew = ((1.0 - phase) % 1.0 + 1.0) % 1.0 * SYNODIC_MONTH
        return MoonInfo(phase, illumination, name, untilFull, untilNew)
    }

    /** 0 at sunrise, 1 at sunset; below 0 before sunrise and above 1 after sunset. */
    fun sunProgress(nowSeconds: Long, sunrise: Long, sunset: Long): Double {
        if (sunset <= sunrise) return 0.5
        return (nowSeconds - sunrise).toDouble() / (sunset - sunrise)
    }
}
