package io.github.duddmeister.weather.data.remote

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpFetcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

data class PlaceName(
    val name: String,
    val region: String?,
    val country: String?,
    val countryCode: String?,
)

/**
 * Keyless reverse geocoding (BigDataCloud's client-side endpoint). Used only when the device's own
 * Geocoder is unavailable or returns nothing.
 */
class ReverseGeocodeClient(
    private val http: HttpFetcher,
    private val json: Json = AppJson,
) {
    suspend fun reverse(latitude: Double, longitude: Double, language: String): PlaceName? {
        val url = "https://api.bigdatacloud.net/data/reverse-geocode-client".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", OpenMeteoClient.coordinate(latitude))
            .addQueryParameter("longitude", OpenMeteoClient.coordinate(longitude))
            .addQueryParameter("localityLanguage", language.ifBlank { "en" })
            .build()
        val dto = json.decodeFromString(BigDataCloudReverse.serializer(), http.getBody(url))
        val name = listOf(dto.city, dto.locality, dto.principalSubdivision)
            .firstOrNull { !it.isNullOrBlank() } ?: return null
        return PlaceName(
            name = name,
            region = dto.principalSubdivision?.takeIf { it.isNotBlank() && it != name },
            country = dto.countryName?.takeIf { it.isNotBlank() },
            countryCode = dto.countryCode?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable
data class BigDataCloudReverse(
    val city: String? = null,
    val locality: String? = null,
    val principalSubdivision: String? = null,
    val countryName: String? = null,
    val countryCode: String? = null,
)
