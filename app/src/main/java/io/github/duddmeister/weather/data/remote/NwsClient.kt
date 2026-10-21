package io.github.duddmeister.weather.data.remote

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.model.WeatherAlert
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.OffsetDateTime
import java.util.Locale

/** Active watches, warnings and advisories from the US National Weather Service (US locations only). */
class NwsClient(
    private val http: HttpFetcher,
    private val json: Json = AppJson,
) {
    suspend fun activeAlerts(latitude: Double, longitude: Double, nowEpochSeconds: Long): List<WeatherAlert> {
        val body = http.getBody(alertsUrl(latitude, longitude), mapOf("Accept" to "application/geo+json"))
        return parseAlerts(json.decodeFromString(NwsAlertsResponse.serializer(), body), nowEpochSeconds)
    }

    companion object {
        fun alertsUrl(latitude: Double, longitude: Double): HttpUrl =
            "https://api.weather.gov/alerts/active".toHttpUrl().newBuilder()
                .addEncodedQueryParameter(
                    "point",
                    String.format(Locale.US, "%.4f,%.4f", latitude, longitude),
                )
                .build()

        fun parseAlerts(response: NwsAlertsResponse, nowEpochSeconds: Long): List<WeatherAlert> =
            response.features.mapNotNull { feature ->
                val p = feature.properties ?: return@mapNotNull null
                if (p.status != null && !p.status.equals("Actual", ignoreCase = true)) return@mapNotNull null
                if (p.messageType.equals("Cancel", ignoreCase = true)) return@mapNotNull null
                val event = p.event?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WeatherAlert(
                    id = p.id ?: feature.id ?: "$event-${p.onset ?: p.effective}",
                    event = event,
                    headline = p.headline,
                    description = p.description?.trim(),
                    instruction = p.instruction?.trim(),
                    severity = p.severity,
                    urgency = p.urgency,
                    certainty = p.certainty,
                    onset = parseTime(p.onset) ?: parseTime(p.effective),
                    expires = parseTime(p.expires),
                    ends = parseTime(p.ends),
                    sender = p.senderName,
                    areaDesc = p.areaDesc,
                )
            }
                .filter { (it.endTime ?: Long.MAX_VALUE) > nowEpochSeconds }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<WeatherAlert> { it.severityRank }.thenBy { it.onset ?: 0L })

        private fun parseTime(value: String?): Long? =
            value?.let { runCatching { OffsetDateTime.parse(it).toEpochSecond() }.getOrNull() }
    }
}

@Serializable
data class NwsAlertsResponse(val features: List<NwsFeature> = emptyList())

@Serializable
data class NwsFeature(val id: String? = null, val properties: NwsAlertProperties? = null)

@Serializable
data class NwsAlertProperties(
    val id: String? = null,
    val event: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val severity: String? = null,
    val urgency: String? = null,
    val certainty: String? = null,
    val effective: String? = null,
    val onset: String? = null,
    val expires: String? = null,
    val ends: String? = null,
    val senderName: String? = null,
    val areaDesc: String? = null,
    val status: String? = null,
    val messageType: String? = null,
)
