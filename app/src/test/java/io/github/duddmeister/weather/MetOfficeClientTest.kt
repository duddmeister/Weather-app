package io.github.duddmeister.weather

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.core.HttpFetcher
import io.github.duddmeister.weather.data.remote.MetOfficeClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The fixtures follow DataHub's site-specific format, with made-up values for Belfast. */
class MetOfficeClientTest {
    private fun resource(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test
    fun hourlyForecastsAreConvertedToTheAppsUnits() {
        val hours = MetOfficeClient.parseHourly(AppJson, resource("metoffice-hourly-sample.json"))
        assertEquals(6, hours.size)
        val first = hours.first()
        assertEquals(Instant.parse("2026-10-24T17:00:00Z").epochSecond, first.time)
        assertEquals(11.2, first.temperature!!, 1e-9)
        assertEquals(7.9, first.feelsLike!!, 1e-9)
        assertEquals(8.9, first.dewPoint!!, 1e-9)
        assertEquals(86.0, first.humidity!!, 1e-9)
        // m/s to km/h, Pa to hPa.
        assertEquals(27.0, first.windSpeed!!, 1e-9)
        assertEquals(54.0, first.windGusts!!, 1e-9)
        assertEquals(225.0, first.windDirection!!, 1e-9)
        assertEquals(998.5, first.pressure!!, 1e-9)
        assertEquals(18000.0, first.visibility!!, 1e-9)
        assertEquals(0.6, first.precipitation!!, 1e-9)
        assertEquals(70.0, first.precipitationProbability!!, 1e-9)
        assertEquals(61, first.weatherCode) // light rain
        assertEquals(listOf(61, 0, 65, null, 3, 2), hours.map { it.weatherCode })
        // A missing value stays unknown rather than becoming zero; -1 is "no symbol".
        assertNull(hours[3].temperature)
        assertEquals(6.0, hours[3].feelsLike!!, 1e-9)
    }

    @Test
    fun dailyForecastsKeepDayAndNightApart() {
        val days = MetOfficeClient.parseDaily(AppJson, resource("metoffice-daily-sample.json"))
        assertEquals(listOf("2026-10-23", "2026-10-24", "2026-10-25", "2026-10-26"), days.map { it.date })
        // Yesterday's daytime is over, so only its night is forecast.
        val yesterday = days.first()
        assertNull(yesterday.dayWeatherCode)
        assertNull(yesterday.dayPrecipitationProbability)
        assertEquals(7.4, yesterday.nightMin!!, 1e-9)
        assertEquals(61, yesterday.nightWeatherCode)
        val today = days[1]
        assertEquals(12.3, today.dayMax!!, 1e-9)
        assertEquals(8.1, today.nightMin!!, 1e-9)
        assertEquals(8.6, today.dayMaxFeelsLike!!, 1e-9)
        assertEquals(4.9, today.nightMinFeelsLike!!, 1e-9)
        assertEquals(82, today.dayWeatherCode) // heavy shower
        assertEquals(65, today.nightWeatherCode) // heavy rain
        assertEquals(81.0, today.dayPrecipitationProbability!!, 1e-9)
        assertEquals(92.0, today.nightPrecipitationProbability!!, 1e-9)
        assertEquals(1.0, today.maxUvIndex!!, 1e-9)
        assertEquals(0, days.last().dayWeatherCode) // sunny
    }

    @Test
    fun weatherCodes() {
        val expected = mapOf(
            0 to 0, 1 to 0, 2 to 2, 3 to 2, 5 to 45, 6 to 45, 7 to 3, 8 to 3, 9 to 80, 10 to 80, 11 to 53, 12 to 61,
            13 to 82, 14 to 82, 15 to 65, 16 to 85, 17 to 85, 18 to 71, 19 to 82, 20 to 82, 21 to 82, 22 to 85, 23 to 85,
            24 to 71, 25 to 86, 26 to 86, 27 to 75, 28 to 95, 29 to 95, 30 to 95,
        )
        expected.forEach { (code, wmo) -> assertEquals("code $code", wmo, MetOfficeClient.wmoCode(code)) }
        assertNull(MetOfficeClient.wmoCode(4))
        assertNull(MetOfficeClient.wmoCode(-1))
        assertNull(MetOfficeClient.wmoCode(null))
    }

    @Test
    fun coversTheUkAndIreland() {
        listOf("GB", "gb", "UK", "IE", "IM", "JE", "GG").forEach { assertTrue(it, MetOfficeClient.covers(it)) }
        listOf("FR", "US", "", null).forEach { assertFalse("$it", MetOfficeClient.covers(it)) }
    }

    @Test
    fun requestsSendTheKeyAsAHeader() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(resource("metoffice-hourly-sample.json")))
        server.start()
        try {
            val client = MetOfficeClient(HttpFetcher(OkHttpClient(), "test"), baseUrl = server.url("/sitespecific/v0/point/"))
            assertEquals(6, client.hourly("my-key", 54.59731, -5.93012).size)
            val request = server.takeRequest()
            assertEquals("my-key", request.getHeader("apikey"))
            assertEquals("application/json", request.getHeader("Accept"))
            val url = request.requestUrl!!
            assertEquals("/sitespecific/v0/point/hourly", url.encodedPath)
            assertEquals("54.5973", url.queryParameter("latitude"))
            assertEquals("-5.9301", url.queryParameter("longitude"))
            assertEquals("true", url.queryParameter("excludeParameterMetadata"))
            // The key never goes in the URL, which can end up in logs.
            assertFalse(url.toString().contains("my-key"))
        } finally {
            server.shutdown()
        }
    }
}
