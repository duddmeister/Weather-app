package io.github.duddmeister.weather.model

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val isCurrent: Boolean = false,
) {
    val subtitle: String
        get() = listOfNotNull(region, country).filter { it.isNotBlank() && it != name }.distinct().joinToString(", ")

    val isUnitedStates: Boolean get() = countryCode.equals("US", ignoreCase = true)

    fun distanceKmTo(lat: Double, lon: Double): Double = haversineKm(latitude, longitude, lat, lon)

    companion object {
        const val CURRENT_ID = "current"

        fun idForGeoname(geonameId: Long) = "geo:$geonameId"

        fun idForCoordinates(lat: Double, lon: Double) =
            "ll:" + String.format(Locale.US, "%.3f,%.3f", lat, lon)
    }
}

fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Converts an ISO country code ("US") into its flag emoji. */
fun flagEmoji(countryCode: String?): String {
    val cc = countryCode?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } } ?: return ""
    val first = Character.toChars(0x1F1E6 + (cc[0] - 'A'))
    val second = Character.toChars(0x1F1E6 + (cc[1] - 'A'))
    return String(first) + String(second)
}
