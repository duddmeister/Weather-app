package io.github.duddmeister.weather.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TemperatureUnit(val label: String) { Celsius("°C"), Fahrenheit("°F") }
enum class WindUnit(val label: String) { Kmh("km/h"), Mph("mph"), Ms("m/s"), Knots("kn") }
enum class PrecipitationUnit(val label: String) { Mm("mm"), Inch("in") }
enum class PressureUnit(val label: String) { Hpa("hPa"), InHg("inHg"), MmHg("mmHg") }
enum class DistanceUnit(val label: String) { Km("km"), Mi("mi") }
enum class TimeFormat(val label: String) { System("Auto"), H12("12-hour"), H24("24-hour") }
enum class AqiScale(val label: String) { Auto("Auto"), Us("US AQI"), European("European AQI") }

data class UnitPrefs(
    val temperature: TemperatureUnit = TemperatureUnit.Celsius,
    val wind: WindUnit = WindUnit.Kmh,
    val precipitation: PrecipitationUnit = PrecipitationUnit.Mm,
    val pressure: PressureUnit = PressureUnit.Hpa,
    val distance: DistanceUnit = DistanceUnit.Km,
) {
    companion object {
        private val imperialCountries = setOf("US", "LR", "MM", "PR", "GU", "VI", "AS", "MP", "BS", "BZ", "KY", "PW", "FM", "MH")

        /** Sensible defaults for a device country (ISO 3166 alpha-2). */
        fun defaultsFor(countryCode: String?): UnitPrefs {
            val cc = countryCode?.uppercase(Locale.ROOT)
            return when {
                cc in imperialCountries -> UnitPrefs(
                    TemperatureUnit.Fahrenheit, WindUnit.Mph, PrecipitationUnit.Inch, PressureUnit.InHg, DistanceUnit.Mi,
                )
                cc == "GB" -> UnitPrefs(TemperatureUnit.Celsius, WindUnit.Mph, PrecipitationUnit.Mm, PressureUnit.Hpa, DistanceUnit.Mi)
                else -> UnitPrefs()
            }
        }
    }
}

/** Conversions and formatting. Inputs are always the API's metric units. */
object Units {
    fun temperature(celsius: Double, unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.Celsius -> celsius
        TemperatureUnit.Fahrenheit -> celsius * 9.0 / 5.0 + 32.0
    }

    /** "21°" — rounded, never "-0°". */
    fun formatTemperature(celsius: Double?, unit: TemperatureUnit): String {
        if (celsius == null || celsius.isNaN()) return "--°"
        val rounded = temperature(celsius, unit).roundToInt()
        return "${if (rounded == 0) 0 else rounded}°"
    }

    fun roundedTemperature(celsius: Double, unit: TemperatureUnit): Int = temperature(celsius, unit).roundToInt()

    /** Temperature difference (not absolute temperature), in the user's unit. */
    fun temperatureDelta(deltaCelsius: Double, unit: TemperatureUnit): Int = when (unit) {
        TemperatureUnit.Celsius -> deltaCelsius
        TemperatureUnit.Fahrenheit -> deltaCelsius * 9.0 / 5.0
    }.roundToInt()

    fun wind(kmh: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.Kmh -> kmh
        WindUnit.Mph -> kmh / 1.609344
        WindUnit.Ms -> kmh / 3.6
        WindUnit.Knots -> kmh / 1.852
    }

    fun formatWind(kmh: Double?, unit: WindUnit, withUnit: Boolean = true): String {
        if (kmh == null) return "--"
        val value = wind(kmh, unit).roundToInt()
        return if (withUnit) "$value ${unit.label}" else "$value"
    }

    fun precipitation(mm: Double, unit: PrecipitationUnit): Double = when (unit) {
        PrecipitationUnit.Mm -> mm
        PrecipitationUnit.Inch -> mm / 25.4
    }

    fun formatPrecipitation(mm: Double?, unit: PrecipitationUnit): String {
        if (mm == null) return "--"
        val value = precipitation(mm, unit)
        return when (unit) {
            PrecipitationUnit.Mm -> when {
                value == 0.0 -> "0 mm"
                value < 0.1 -> "<0.1 mm"
                value < 10 -> String.format(Locale.US, "%.1f mm", value)
                else -> "${value.roundToInt()} mm"
            }
            PrecipitationUnit.Inch -> when {
                value == 0.0 -> "0 in"
                value < 0.01 -> "<0.01 in"
                else -> String.format(Locale.US, "%.2f in", value)
            }
        }
    }

    /** Snowfall from the API is in centimetres. */
    fun formatSnowfall(cm: Double?, unit: PrecipitationUnit): String {
        if (cm == null) return "--"
        return when (unit) {
            PrecipitationUnit.Mm -> if (cm < 10) String.format(Locale.US, "%.1f cm", cm) else "${cm.roundToInt()} cm"
            PrecipitationUnit.Inch -> String.format(Locale.US, "%.1f in", cm / 2.54)
        }
    }

    fun pressure(hpa: Double, unit: PressureUnit): Double = when (unit) {
        PressureUnit.Hpa -> hpa
        PressureUnit.InHg -> hpa * 0.0295299830714
        PressureUnit.MmHg -> hpa * 0.750061683
    }

    fun formatPressure(hpa: Double?, unit: PressureUnit): String {
        if (hpa == null) return "--"
        val value = pressure(hpa, unit)
        return when (unit) {
            PressureUnit.Hpa -> "${value.roundToInt()} hPa"
            PressureUnit.InHg -> String.format(Locale.US, "%.2f inHg", value)
            PressureUnit.MmHg -> "${value.roundToInt()} mmHg"
        }
    }

    /** Visibility from the API is in metres. */
    fun formatDistance(meters: Double?, unit: DistanceUnit): String {
        if (meters == null) return "--"
        return when (unit) {
            DistanceUnit.Km -> {
                val km = meters / 1000.0
                when {
                    km < 1 -> "${(meters / 10).roundToInt() * 10} m"
                    km < 10 -> String.format(Locale.US, "%.1f km", km)
                    else -> "${km.roundToInt()} km"
                }
            }
            DistanceUnit.Mi -> {
                val mi = meters / 1609.344
                if (mi < 10) String.format(Locale.US, "%.1f mi", mi) else "${mi.roundToInt()} mi"
            }
        }
    }

    private val compassPoints = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    fun compass(degrees: Double?): String {
        if (degrees == null) return ""
        val normalized = ((degrees % 360) + 360) % 360
        return compassPoints[((normalized / 22.5) + 0.5).toInt() % 16]
    }

    /** Beaufort number for a wind speed in km/h. */
    fun beaufort(kmh: Double): Int {
        val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        val index = limits.indexOfFirst { kmh < it }
        return if (index == -1) 12 else index
    }

    fun beaufortDescription(kmh: Double): String = when (beaufort(kmh)) {
        0 -> "Calm"
        1 -> "Light air"
        2 -> "Light breeze"
        3 -> "Gentle breeze"
        4 -> "Moderate breeze"
        5 -> "Fresh breeze"
        6 -> "Strong breeze"
        7 -> "Near gale"
        8 -> "Gale"
        9 -> "Strong gale"
        10 -> "Storm"
        11 -> "Violent storm"
        else -> "Hurricane force"
    }

    /** Formats a percentage 0..100. */
    fun formatPercent(value: Double?): String = if (value == null) "--" else "${value.roundToInt()}%"

    fun isNearlyEqual(a: Double, b: Double, epsilon: Double = 1e-6) = abs(a - b) < epsilon
}
