package io.github.duddmeister.weather.domain

import io.github.duddmeister.weather.model.AqiScale

/** A category on a scale. [color] is ARGB; [fraction] places the value on a 0..1 gauge. */
data class ScaleLevel(val label: String, val advice: String, val color: Long, val fraction: Float)

object Scales {
    fun uv(index: Double): ScaleLevel {
        val fraction = (index / 11.0).toFloat().coerceIn(0f, 1f)
        return when {
            index < 3 -> ScaleLevel("Low", "No protection needed", 0xFF66BB6A, fraction)
            index < 6 -> ScaleLevel("Moderate", "Wear sunscreen at midday", 0xFFFFD54F, fraction)
            index < 8 -> ScaleLevel("High", "Use sun protection", 0xFFFF9800, fraction)
            index < 11 -> ScaleLevel("Very high", "Avoid midday sun", 0xFFF4511E, fraction)
            else -> ScaleLevel("Extreme", "Stay in the shade", 0xFFAB47BC, fraction)
        }
    }

    fun usAqi(aqi: Double): ScaleLevel {
        val fraction = (aqi / 300.0).toFloat().coerceIn(0f, 1f)
        return when {
            aqi <= 50 -> ScaleLevel("Good", "Air quality is satisfactory", 0xFF66BB6A, fraction)
            aqi <= 100 -> ScaleLevel("Moderate", "Unusually sensitive people should limit exertion", 0xFFFFD54F, fraction)
            aqi <= 150 -> ScaleLevel("Unhealthy for sensitive groups", "Sensitive groups should reduce outdoor exertion", 0xFFFF9800, fraction)
            aqi <= 200 -> ScaleLevel("Unhealthy", "Everyone should reduce prolonged exertion", 0xFFF4511E, fraction)
            aqi <= 300 -> ScaleLevel("Very unhealthy", "Avoid prolonged outdoor exertion", 0xFFAB47BC, fraction)
            else -> ScaleLevel("Hazardous", "Avoid all outdoor activity", 0xFF8E2442, fraction)
        }
    }

    fun europeanAqi(aqi: Double): ScaleLevel {
        val fraction = (aqi / 100.0).toFloat().coerceIn(0f, 1f)
        return when {
            aqi <= 20 -> ScaleLevel("Good", "Enjoy outdoor activities", 0xFF4FC3F7, fraction)
            aqi <= 40 -> ScaleLevel("Fair", "Enjoy outdoor activities", 0xFF66BB6A, fraction)
            aqi <= 60 -> ScaleLevel("Moderate", "Sensitive people should consider reducing exertion", 0xFFFFD54F, fraction)
            aqi <= 80 -> ScaleLevel("Poor", "Consider reducing intense outdoor activity", 0xFFFF9800, fraction)
            aqi <= 100 -> ScaleLevel("Very poor", "Reduce outdoor physical activity", 0xFFF4511E, fraction)
            else -> ScaleLevel("Extremely poor", "Avoid outdoor physical activity", 0xFFAB47BC, fraction)
        }
    }

    private val europeanCountries = setOf(
        "AD", "AL", "AT", "BA", "BE", "BG", "BY", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FO", "FR",
        "GB", "GI", "GR", "HR", "HU", "IE", "IM", "IS", "IT", "JE", "GG", "LI", "LT", "LU", "LV", "MC", "MD",
        "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "SE", "SI", "SK", "SM", "UA", "VA", "XK",
    )

    /** Which index to show: the user's choice, or EAQI in Europe and US AQI elsewhere. */
    fun useEuropean(scale: AqiScale, countryCode: String?): Boolean = when (scale) {
        AqiScale.Us -> false
        AqiScale.European -> true
        AqiScale.Auto -> countryCode?.uppercase() in europeanCountries
    }

    /** Pollen grains/m³ → level (thresholds roughly follow common European pollen scales). */
    fun pollen(grains: Double): ScaleLevel = when {
        grains < 1 -> ScaleLevel("None", "", 0xFF66BB6A, 0f)
        grains < 20 -> ScaleLevel("Low", "", 0xFF66BB6A, 0.2f)
        grains < 100 -> ScaleLevel("Moderate", "", 0xFFFFD54F, 0.5f)
        grains < 500 -> ScaleLevel("High", "", 0xFFFF9800, 0.8f)
        else -> ScaleLevel("Very high", "", 0xFFF4511E, 1f)
    }

    fun visibilityDescription(meters: Double): String = when {
        meters >= 30_000 -> "Perfectly clear view"
        meters >= 10_000 -> "Clear view"
        meters >= 4_000 -> "Good visibility"
        meters >= 1_000 -> "Hazy"
        meters >= 200 -> "Fog is reducing visibility"
        else -> "Dense fog"
    }

    fun humidityDescription(humidity: Double, dewPointCelsius: Double?): String = when {
        dewPointCelsius != null && dewPointCelsius >= 21 -> "Very muggy"
        dewPointCelsius != null && dewPointCelsius >= 16 -> "Humid"
        humidity < 30 -> "Dry air"
        else -> "Comfortable"
    }

    fun cloudDescription(cover: Double): String = when {
        cover < 10 -> "Clear skies"
        cover < 30 -> "Mostly clear"
        cover < 60 -> "Partly cloudy"
        cover < 90 -> "Mostly cloudy"
        else -> "Overcast"
    }
}
