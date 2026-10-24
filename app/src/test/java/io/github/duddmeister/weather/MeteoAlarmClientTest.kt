package io.github.duddmeister.weather

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.data.remote.MeteoAlarmClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Uses real Met Office warnings from MeteoAlarm's UK feed of 24 October 2026 (polygons thinned to
 * keep the file small): a yellow wind warning for all of Northern Ireland, an amber one for its
 * north only, an expired amber for South West England and an Environment Agency flood warning.
 */
class MeteoAlarmClientTest {
    private val feed = MeteoAlarmClient.parseFeed(AppJson, javaClass.getResource("/meteoalarm-uk-sample.json")!!.readText())
    private val now = Instant.parse("2026-10-24T17:33:00Z").epochSecond

    private fun eventsAt(latitude: Double, longitude: Double, at: Long = now) =
        MeteoAlarmClient.matching(feed, latitude, longitude, at, "en").map { it.event }

    @Test
    fun belfastGetsNorthernIrelandsWarningButNotTheNorthCoastAmber() {
        val alerts = MeteoAlarmClient.matching(feed, 54.5973, -5.9301, now, "en")
        assertEquals(listOf("Yellow wind warning"), alerts.map { it.event })
        val yellow = alerts.single()
        assertEquals(2, yellow.level)
        assertEquals("Moderate", yellow.severity)
        assertEquals("UK Met Office", yellow.sender)
        assertEquals("Northern Ireland", yellow.areaDesc)
        assertEquals(Instant.parse("2026-10-24T09:00:00Z").epochSecond, yellow.onset)
        assertEquals(Instant.parse("2026-10-25T12:00:00Z").epochSecond, yellow.endTime)
        assertTrue(yellow.headline!!.startsWith("Storm Boelo"))
        // This is the warning's second version; it keeps the first one's id so it's notified once.
        assertEquals("2.49.0.0.826.0.GB_261022095919_43d25d86.v1.0.W", yellow.id)
    }

    @Test
    fun theNorthCoastGetsTheAmberFirst() {
        val alerts = MeteoAlarmClient.matching(feed, 55.2042, -6.6524, now, "en")
        assertEquals(listOf("Amber wind warning", "Yellow wind warning"), alerts.map { it.event })
        assertEquals(3, alerts.first().level)
    }

    @Test
    fun expiredWarningsAreDropped() {
        // Truro's amber ended on 23 October...
        assertEquals(emptyList<String>(), eventsAt(50.263, -5.051))
        // ...and applied that morning.
        assertEquals(listOf("Amber wind warning"), eventsAt(50.263, -5.051, Instant.parse("2026-10-23T03:00:00Z").epochSecond))
    }

    @Test
    fun floodWarningsMatchTheirRiverArea() {
        assertEquals(1, eventsAt(51.1068, -3.6511).size)
        assertTrue(eventsAt(51.1068, -3.6511).single().startsWith("Flood Warning: River Barle"))
        assertEquals(emptyList<String>(), eventsAt(51.5072, -0.1276))
    }

    @Test
    fun cancelledAndReplacedVersionsAreDropped() {
        fun alert(id: String, msgType: String, references: String? = null, event: String = "Yellow rain warning") = """
            {"alert": {"identifier": "$id", "status": "Actual", "msgType": "$msgType", "sent": "2026-10-20T10:00:00+00:00",
              ${references?.let { "\"references\": \"$it\"," }.orEmpty()}
              "info": [{"language": "en-GB", "event": "$event", "severity": "Moderate", "expires": "2026-10-21T00:00:00+00:00",
                "area": [{"areaDesc": "Box", "polygon": ["0,0 0,2 2,2 2,0 0,0"]}]}]}}
        """
        val body = """{"warnings": [
            ${alert("A1", "Alert")},
            ${alert("A2", "Update", "met,A1,2026-10-19T09:00:00+00:00")},
            ${alert("B1", "Alert", event = "Yellow snow warning")},
            ${alert("B2", "Cancel", "met,B1,2026-10-19T09:00:00+00:00", event = "Yellow snow warning")},
            {"alert": {"identifier": "T1", "status": "Test", "msgType": "Alert", "info": [{"event": "Test", "area": []}]}}
        ]}"""
        val alerts = MeteoAlarmClient.matching(MeteoAlarmClient.parseFeed(AppJson, body), 1.0, 1.0, 1_760_900_000L, "en")
        assertEquals(listOf("Yellow rain warning"), alerts.map { it.event })
        assertEquals("A1", alerts.single().id)
    }

    @Test
    fun impactsAreListedAfterTheDescription() {
        val body = """{"warnings": [{"alert": {"identifier": "I1", "status": "Actual", "msgType": "Alert", "info": [{
            "language": "en-GB", "event": "Amber wind warning", "description": "Very strong winds.",
            "parameter": [{"valueName": "awareness_level", "value": "3; Orange; Severe"},
                          {"valueName": "impacts", "value": "Power cuts are likely"}, {"valueName": "impacts", "value": "Roads may close"}],
            "area": {"areaDesc": "Box", "polygon": "0,0 0,2 2,2 2,0 0,0"}}]}}]}"""
        // A single area or polygon may come as a plain value rather than a list.
        val alert = MeteoAlarmClient.matching(MeteoAlarmClient.parseFeed(AppJson, body), 1.0, 1.0, now, "en").single()
        assertEquals("Very strong winds.\n\nWhat to expect:\n• Power cuts are likely\n• Roads may close", alert.description)
        assertEquals(3, alert.level)
    }

    @Test
    fun picksTheDevicesLanguageThenEnglish() {
        fun info(language: String, event: String) = MeteoAlarmClient.Info(
            language, event, null, null, null, null, null, null, null, null, null, null, emptyList(),
        )
        val infos = listOf(info("no", "Gult farevarsel"), info("en-GB", "Yellow warning"), info("de-DE", "Gelbe Warnung"))
        assertEquals("Gult farevarsel", MeteoAlarmClient.pickLanguage(infos, "nb").event)
        assertEquals("Gelbe Warnung", MeteoAlarmClient.pickLanguage(infos, "de").event)
        assertEquals("Yellow warning", MeteoAlarmClient.pickLanguage(infos, "fr").event)
    }

    @Test
    fun coversCountriesWhoseWarningsComeWithAreas() {
        assertTrue(MeteoAlarmClient.covers("GB"))
        assertTrue(MeteoAlarmClient.covers("gb"))
        assertTrue(MeteoAlarmClient.covers("NO"))
        // Ireland and Germany only name regions by code, which needs boundary data to place a point in.
        assertFalse(MeteoAlarmClient.covers("IE"))
        assertFalse(MeteoAlarmClient.covers("DE"))
        assertFalse(MeteoAlarmClient.covers(null))
    }

    @Test
    fun polygons() {
        val square = MeteoAlarmClient.parsePolygon("0,0 0,2 2,2 2,0 0,0")!!
        assertTrue(MeteoAlarmClient.contains(square, 1.0, 1.0))
        assertFalse(MeteoAlarmClient.contains(square, 3.0, 1.0))
        assertNull(MeteoAlarmClient.parsePolygon("0,0 1,1"))
        assertNull(MeteoAlarmClient.parsePolygon("not a polygon"))
    }
}
