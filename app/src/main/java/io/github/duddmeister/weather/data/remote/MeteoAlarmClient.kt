package io.github.duddmeister.weather.data.remote

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.model.WeatherAlert
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Official weather warnings for European countries from MeteoAlarm (EUMETNET), which republishes
 * each national weather service's warnings, such as the Met Office's, in CAP form under CC BY 4.0.
 *
 * A country's feed is fetched whole and each warning is matched to the location by its area
 * polygons, so a warning for part of a region (say, a north coast) doesn't show elsewhere in it.
 * Only countries whose warnings all come with polygons are covered; the others describe areas by
 * region codes, which would need boundary data to place a point in.
 */
class MeteoAlarmClient(
    private val http: HttpFetcher,
    private val json: Json = AppJson,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val feeds = mutableMapOf<String, Pair<Long, List<FeedWarning>>>()

    /** Current and upcoming warnings at a point, or null if MeteoAlarm can't cover [countryCode]. */
    suspend fun warningsAt(countryCode: String?, latitude: Double, longitude: Double, nowEpochSeconds: Long, language: String): List<WeatherAlert>? {
        val slug = feedSlug(countryCode) ?: return null
        return matching(feed(slug), latitude, longitude, nowEpochSeconds, language)
    }

    /** Several places in one country share a download; feeds are reused for a few minutes. */
    private suspend fun feed(slug: String): List<FeedWarning> = mutex.withLock {
        val now = clock()
        feeds[slug]?.let { (fetchedAt, warnings) -> if (now - fetchedAt < REUSE_MILLIS) return warnings }
        val warnings = parseFeed(json, http.getBody(feedUrl(slug)))
        feeds[slug] = now to warnings
        warnings
    }

    /** A warning as published, reduced to what the app shows and the areas it applies to. */
    data class FeedWarning(
        val identifier: String,
        /** The first version's identifier, so updates of a warning keep one id (and notify once). */
        val stableId: String,
        val infos: List<Info>,
    )

    data class Info(
        val language: String,
        val event: String,
        val headline: String?,
        val description: String?,
        val instruction: String?,
        val severity: String?,
        val urgency: String?,
        val certainty: String?,
        val onset: Long?,
        val expires: Long?,
        val sender: String?,
        /** MeteoAlarm awareness level: 2 yellow, 3 orange (amber), 4 red. */
        val level: Int?,
        val areas: List<Area>,
    )

    /** An area's polygons, each as lat, lon pairs in one array. */
    class Area(val description: String?, val polygons: List<DoubleArray>)

    companion object {
        private const val REUSE_MILLIS = 10 * 60_000L

        /** Countries whose warnings all come with polygons, and whose feeds are small enough to poll. */
        private val FEEDS = mapOf(
            "GB" to "united-kingdom",
            "UK" to "united-kingdom",
            "NO" to "norway",
            "SE" to "sweden",
            "EE" to "estonia",
            "SI" to "slovenia",
            "LU" to "luxembourg",
            "UA" to "ukraine",
        )

        fun feedSlug(countryCode: String?): String? = countryCode?.uppercase(Locale.ROOT)?.let(FEEDS::get)

        fun covers(countryCode: String?): Boolean = feedSlug(countryCode) != null

        fun feedUrl(slug: String): HttpUrl = "https://feeds.meteoalarm.org/api/v1/warnings/feeds-$slug".toHttpUrl()

        /** Parses a feed, keeping only actual, current versions (not cancelled or superseded ones). */
        fun parseFeed(json: Json, body: String): List<FeedWarning> {
            val alerts = (json.parseToJsonElement(body) as? JsonObject)?.get("warnings").list()
                .mapNotNull { (it as? JsonObject)?.get("alert") as? JsonObject }
                .filter { it.string("status").equals("Actual", ignoreCase = true) }
            // An update or cancellation names the versions it replaces in its references.
            val replaced = alerts.flatMap { references(it.string("references")) }.map { it.first }.toSet()
            return alerts.mapNotNull { alert ->
                val identifier = alert.string("identifier") ?: return@mapNotNull null
                if (identifier in replaced || alert.string("msgType").equals("Cancel", ignoreCase = true)) return@mapNotNull null
                val first = references(alert.string("references")).minByOrNull { it.second }?.first
                val infos = alert["info"].list().mapNotNull { (it as? JsonObject)?.let(::parseInfo) }
                if (infos.isEmpty()) null else FeedWarning(identifier, first ?: identifier, infos)
            }
        }

        /** (identifier, sent) pairs from a CAP references value: "sender,identifier,sent" triples. */
        private fun references(value: String?): List<Pair<String, Long>> = value.orEmpty().split(' ', '\n', '\t')
            .mapNotNull { triple ->
                val parts = triple.split(',')
                if (parts.size < 3) null else parts[1] to (parseTime(parts[2]) ?: Long.MAX_VALUE)
            }

        private fun parseInfo(info: JsonObject): Info? {
            val event = info.string("event")?.takeIf { it.isNotBlank() } ?: return null
            val parameters = info["parameter"].list().mapNotNull { it as? JsonObject }
            fun parameter(name: String) = parameters.filter { it.string("valueName").equals(name, ignoreCase = true) }
                .mapNotNull { it.string("value")?.trim()?.takeIf { value -> value.isNotEmpty() } }
            val level = parameter("awareness_level").firstOrNull()?.substringBefore(';')?.trim()?.toIntOrNull()
            // The Met Office lists likely impacts separately; show them as its own site does.
            val impacts = parameter("impacts").takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "What to expect:\n") { "• $it" }
            val description = listOfNotNull(info.string("description")?.trim()?.takeIf { it.isNotEmpty() }, impacts)
            return Info(
                language = info.string("language").orEmpty(),
                event = event,
                headline = info.string("headline")?.trim()?.takeIf { it.isNotEmpty() },
                description = description.joinToString("\n\n").ifEmpty { null },
                instruction = info.string("instruction")?.trim()?.takeIf { it.isNotEmpty() },
                severity = info.string("severity"),
                urgency = info.string("urgency"),
                certainty = info.string("certainty"),
                onset = parseTime(info.string("onset")) ?: parseTime(info.string("effective")),
                expires = parseTime(info.string("expires")),
                sender = info.string("senderName"),
                level = level,
                areas = info["area"].list().mapNotNull { it as? JsonObject }.map { area ->
                    Area(area.string("areaDesc"), area["polygon"].list().mapNotNull { (it as? JsonPrimitive)?.content?.let(::parsePolygon) })
                },
            )
        }

        /** CAP polygons are "lat,lon lat,lon …" with the first point repeated at the end. */
        fun parsePolygon(text: String): DoubleArray? {
            val points = text.trim().split(Regex("\\s+")).mapNotNull { pair ->
                val (lat, lon) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
                val la = lat.toDoubleOrNull() ?: return@mapNotNull null
                val lo = lon.toDoubleOrNull() ?: return@mapNotNull null
                la to lo
            }
            if (points.size < 4) return null
            return DoubleArray(points.size * 2) { i -> if (i % 2 == 0) points[i / 2].first else points[i / 2].second }
        }

        /** Ray casting; points exactly on an edge may land either side, which a warning area tolerates. */
        fun contains(polygon: DoubleArray, latitude: Double, longitude: Double): Boolean {
            var inside = false
            val n = polygon.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val yi = polygon[2 * i]
                val xi = polygon[2 * i + 1]
                val yj = polygon[2 * j]
                val xj = polygon[2 * j + 1]
                if ((yi > latitude) != (yj > latitude) && longitude < (xj - xi) * (latitude - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            return inside
        }

        /** Warnings whose areas contain the point and that haven't expired, most severe first. */
        fun matching(warnings: List<FeedWarning>, latitude: Double, longitude: Double, nowEpochSeconds: Long, language: String): List<WeatherAlert> =
            warnings.mapNotNull { warning ->
                val info = pickLanguage(warning.infos, language)
                val area = info.areas.filter { area -> area.polygons.any { contains(it, latitude, longitude) } }
                if (area.isEmpty() || (info.expires ?: Long.MAX_VALUE) <= nowEpochSeconds) return@mapNotNull null
                WeatherAlert(
                    id = warning.stableId,
                    event = info.event,
                    headline = info.headline,
                    description = info.description,
                    instruction = info.instruction,
                    severity = info.severity,
                    urgency = info.urgency,
                    certainty = info.certainty,
                    onset = info.onset,
                    expires = info.expires,
                    sender = info.sender,
                    areaDesc = area.mapNotNull { it.description }.joinToString(", ").ifBlank { null },
                    level = info.level,
                )
            }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<WeatherAlert> { it.level ?: it.severityRank }.thenBy { it.onset ?: 0L })

        /** The device's language if the warning has it, else English, else whatever it has. */
        fun pickLanguage(infos: List<Info>, language: String): Info {
            // Android reports Norwegian as Bokmål or Nynorsk ("nb", "nn"); MeteoAlarm labels it "no".
            val wanted = language.lowercase(Locale.ROOT).let { if (it == "nb" || it == "nn") "no" else it }
            return infos.firstOrNull { it.language.substringBefore('-').equals(wanted, ignoreCase = true) }
                ?: infos.firstOrNull { it.language.startsWith("en", ignoreCase = true) }
                ?: infos.first()
        }

        private fun parseTime(value: String?): Long? =
            value?.let { runCatching { OffsetDateTime.parse(it.trim()).toEpochSecond() }.getOrNull() }

        /** Feeds aren't uniform: a single value can come as an object or as a one-item list. */
        private fun JsonElement?.list(): List<JsonElement> = when (this) {
            null, JsonNull -> emptyList()
            is JsonArray -> this
            else -> listOf(this)
        }

        private fun JsonObject.string(key: String): String? = when (val value = this[key]) {
            is JsonPrimitive -> value.content.takeUnless { value is JsonNull }
            is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.content
            else -> null
        }
    }
}
