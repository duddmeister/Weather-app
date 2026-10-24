package io.github.duddmeister.weather.data.remote

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.model.MetOfficeDay
import io.github.duddmeister.weather.model.MetOfficeHour
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.OffsetDateTime
import java.util.Locale

/**
 * The Met Office's site-specific ("Global Spot") forecasts from Weather DataHub, which need the
 * user's own API key; the free plan allows 360 calls a day. Values arrive in SI units (m/s, Pa)
 * and are converted to the app's (km/h, hPa).
 */
class MetOfficeClient(
    private val http: HttpFetcher,
    private val json: Json = AppJson,
    private val baseUrl: HttpUrl = BASE_URL,
) {
    suspend fun hourly(apiKey: String, latitude: Double, longitude: Double): List<MetOfficeHour> =
        parseHourly(json, get(apiKey, "hourly", latitude, longitude))

    suspend fun daily(apiKey: String, latitude: Double, longitude: Double): List<MetOfficeDay> =
        parseDaily(json, get(apiKey, "daily", latitude, longitude))

    private suspend fun get(apiKey: String, kind: String, latitude: Double, longitude: Double): String =
        http.getBody(pointUrl(kind, latitude, longitude, baseUrl), mapOf("apikey" to apiKey, "Accept" to "application/json"))

    companion object {
        /** Places the Met Office's UK models cover, where its forecasts beat the alternatives. */
        private val COUNTRIES = setOf("GB", "UK", "IE", "IM", "JE", "GG")

        fun covers(countryCode: String?): Boolean = countryCode?.uppercase(Locale.ROOT) in COUNTRIES

        val BASE_URL: HttpUrl = "https://data.hub.api.metoffice.gov.uk/sitespecific/v0/point/".toHttpUrl()

        fun pointUrl(kind: String, latitude: Double, longitude: Double, base: HttpUrl = BASE_URL): HttpUrl =
            base.newBuilder()
                .addPathSegment(kind)
                .addQueryParameter("latitude", OpenMeteoClient.coordinate(latitude))
                .addQueryParameter("longitude", OpenMeteoClient.coordinate(longitude))
                .addQueryParameter("excludeParameterMetadata", "true")
                .addQueryParameter("includeLocationName", "false")
                .build()

        fun parseHourly(json: Json, body: String): List<MetOfficeHour> = timeSeries(json, body).mapNotNull { step ->
            val time = step.time() ?: return@mapNotNull null
            MetOfficeHour(
                time = time.toEpochSecond(),
                temperature = step.number("screenTemperature"),
                feelsLike = step.number("feelsLikeTemperature"),
                dewPoint = step.number("screenDewPointTemperature"),
                humidity = step.number("screenRelativeHumidity"),
                windSpeed = step.number("windSpeed10m")?.times(MS_TO_KMH),
                windDirection = step.number("windDirectionFrom10m"),
                windGusts = step.number("windGustSpeed10m")?.times(MS_TO_KMH),
                visibility = step.number("visibility"),
                pressure = step.number("mslp")?.div(100),
                uvIndex = step.number("uvIndex"),
                weatherCode = wmoCode(step.number("significantWeatherCode")?.toInt()),
                precipitation = step.number("totalPrecipAmount"),
                precipitationProbability = step.number("probOfPrecipitation"),
            )
        }

        fun parseDaily(json: Json, body: String): List<MetOfficeDay> = timeSeries(json, body).mapNotNull { step ->
            val time = step.time() ?: return@mapNotNull null
            MetOfficeDay(
                date = time.toLocalDate().toString(),
                dayMax = step.number("dayMaxScreenTemperature"),
                nightMin = step.number("nightMinScreenTemperature"),
                dayMaxFeelsLike = step.number("dayMaxFeelsLikeTemp"),
                nightMinFeelsLike = step.number("nightMinFeelsLikeTemp"),
                dayWeatherCode = wmoCode(step.number("daySignificantWeatherCode")?.toInt()),
                nightWeatherCode = wmoCode(step.number("nightSignificantWeatherCode")?.toInt()),
                dayPrecipitationProbability = step.number("dayProbabilityOfPrecipitation"),
                nightPrecipitationProbability = step.number("nightProbabilityOfPrecipitation"),
                maxUvIndex = step.number("maxUvIndex"),
            )
        }

        /**
         * The Met Office's significant weather codes as the nearest WMO code the app knows. Day and
         * night variants share one (the app works out day or night itself); 4 is unused and -1 means
         * no data.
         */
        fun wmoCode(code: Int?): Int? = when (code) {
            0, 1 -> 0 // clear night, sunny day
            2, 3 -> 2 // partly cloudy
            5, 6 -> 45 // mist, fog
            7, 8 -> 3 // cloudy, overcast
            9, 10 -> 80 // light rain shower
            11 -> 53 // drizzle
            12 -> 61 // light rain
            13, 14 -> 82 // heavy rain shower
            15 -> 65 // heavy rain
            16, 17 -> 85 // sleet shower
            18 -> 71 // sleet
            19, 20, 21 -> 82 // hail shower, hail
            22, 23 -> 85 // light snow shower
            24 -> 71 // light snow
            25, 26 -> 86 // heavy snow shower
            27 -> 75 // heavy snow
            28, 29, 30 -> 95 // thunder shower, thunder
            else -> null
        }

        private const val MS_TO_KMH = 3.6

        private fun timeSeries(json: Json, body: String): List<JsonObject> {
            val feature = ((json.parseToJsonElement(body) as? JsonObject)?.get("features") as? JsonArray)?.firstOrNull() as? JsonObject
            val properties = feature?.get("properties") as? JsonObject
            return (properties?.get("timeSeries") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        }

        /** Times look like 2026-10-24T18:00Z. */
        private fun JsonObject.time(): OffsetDateTime? =
            (this["time"] as? JsonPrimitive)?.content?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

        private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull?.takeUnless { it.isNaN() }
    }
}
