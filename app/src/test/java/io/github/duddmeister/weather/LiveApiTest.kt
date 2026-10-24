package io.github.duddmeister.weather

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpException
import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.core.createHttpClient
import io.github.duddmeister.weather.data.remote.MeteoAlarmClient
import io.github.duddmeister.weather.data.remote.NwsClient
import io.github.duddmeister.weather.data.remote.OpenMeteoClient
import io.github.duddmeister.weather.data.remote.ReverseGeocodeClient
import io.github.duddmeister.weather.data.remote.toAirQuality
import io.github.duddmeister.weather.data.remote.toSnapshot
import io.github.duddmeister.weather.domain.NowcastBuilder
import io.github.duddmeister.weather.domain.SummaryBuilder
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.UnitPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Contract test against the real APIs, so a renamed or unsupported parameter fails CI instead of
 * the app. Runs only when LIVE_API_TEST=1 (CI sets it); plain network failures skip rather than fail.
 */
class LiveApiTest {
    private val http = HttpFetcher(createHttpClient(), "NimbusWeatherTest/1.0 (+https://github.com/duddmeister/Weather-app)")
    private val newYork = SavedLocation(id = "test", name = "New York", countryCode = "US", latitude = 40.7128, longitude = -74.0060)

    @Before
    fun onlyWhenEnabled() = assumeTrue(System.getenv("LIVE_API_TEST") == "1")

    private fun <T> live(block: suspend () -> T): T = runBlocking {
        try {
            block()
        } catch (e: HttpException) {
            throw AssertionError("API rejected the request: ${e.message}", e)
        } catch (e: IOException) {
            assumeNoException(e)
            throw e
        }
    }

    @Test
    fun forecast() {
        val client = OpenMeteoClient(http)
        val now = System.currentTimeMillis()
        val snapshot = live { client.forecast(newYork.latitude, newYork.longitude) }.toSnapshot(newYork, now)
        println("Forecast: tz=${snapshot.timezone} hourly=${snapshot.hourly.size} daily=${snapshot.daily.size} minutely=${snapshot.minutely.size}")
        println("Current: ${snapshot.current}")
        assertEquals("America/New_York", snapshot.timezone)
        assertTrue(snapshot.hourly.size >= 24 * OpenMeteoClient.FORECAST_DAYS)
        assertEquals(OpenMeteoClient.FORECAST_DAYS + 1, snapshot.daily.size)
        assertTrue("minutely=${snapshot.minutely.size}", snapshot.minutely.size in 20..40)
        // Daily timestamps are local midnight, aligned with the first hourly timestamp.
        assertEquals(snapshot.hourly.first().time, snapshot.daily.first().time)
        assertNotNull(snapshot.current.apparentTemperature)
        assertNotNull(snapshot.current.uvIndex)
        assertNotNull(snapshot.current.visibility)
        assertNotNull(snapshot.daily[1].sunrise)
        assertTrue(snapshot.hourly.count { it.precipitationProbability != null } > 100)
        val minutelyStart = snapshot.minutely.first().time
        assertTrue("minutely starts ${(now / 1000 - minutelyStart) / 60} min before now", now / 1000 - minutelyStart in 0..90 * 60)

        println("Now: ${snapshot.conditionsAt(now)}")
        println("Nowcast: ${NowcastBuilder.build(snapshot, now)?.headline}")
        println("Summary: ${SummaryBuilder.build(snapshot, now, UnitPrefs(), use24h = false)}")
    }

    @Test
    fun airQuality() {
        val aq = live { OpenMeteoClient(http).airQuality(newYork.latitude, newYork.longitude) }.toAirQuality()
        println("Air quality: $aq")
        assertNotNull(aq?.usAqi)
        assertTrue(aq!!.hourly.size >= 48)
    }

    @Test
    fun geocodingAndReverseGeocoding() {
        val results = live { OpenMeteoClient(http).search("Berlin", "en") }
        assertTrue(results.any { it.countryCode == "DE" && it.timezone == "Europe/Berlin" })
        val place = live { ReverseGeocodeClient(http).reverse(48.8566, 2.3522, "en") }
        println("Reverse: $place")
        assertEquals("FR", place?.countryCode)
    }

    @Test
    fun nwsAlerts() {
        val alerts = live { NwsClient(http).activeAlerts(newYork.latitude, newYork.longitude, System.currentTimeMillis() / 1000) }
        println("NWS alerts for New York: ${alerts.map { it.event }}")
    }

    @Test
    fun meteoAlarmWarnings() {
        // Every covered country's feed parses; the UK's warnings must still come with polygons to match against.
        for (slug in listOf("united-kingdom", "norway", "sweden", "estonia", "slovenia", "luxembourg", "ukraine")) {
            val warnings = live { MeteoAlarmClient.parseFeed(AppJson, http.getBody(MeteoAlarmClient.feedUrl(slug))) }
            val areas = warnings.flatMap { warning -> warning.infos.flatMap { it.areas } }
            println("MeteoAlarm $slug: ${warnings.size} warnings, ${areas.count { it.polygons.isEmpty() }} of ${areas.size} areas without a polygon")
            if (slug == "united-kingdom") assertTrue("UK warnings without polygons", areas.all { it.polygons.isNotEmpty() })
        }
        val now = System.currentTimeMillis() / 1000
        val belfast = live { MeteoAlarmClient(http).warningsAt("GB", 54.5973, -5.9301, now, "en") }
        println("Warnings for Belfast: ${belfast?.map { "${it.event} (${it.sender})" }}")
        assertNotNull(belfast)
    }
}
