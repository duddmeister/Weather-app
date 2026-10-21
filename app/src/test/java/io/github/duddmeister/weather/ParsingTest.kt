package io.github.duddmeister.weather

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.data.remote.AirQualityResponse
import io.github.duddmeister.weather.data.remote.ForecastResponse
import io.github.duddmeister.weather.data.remote.GitHubRelease
import io.github.duddmeister.weather.data.remote.GeocodingResponse
import io.github.duddmeister.weather.data.remote.NwsAlertsResponse
import io.github.duddmeister.weather.data.remote.NwsClient
import io.github.duddmeister.weather.data.remote.OpenMeteoClient
import io.github.duddmeister.weather.data.remote.toAirQuality
import io.github.duddmeister.weather.data.remote.toSavedLocation
import io.github.duddmeister.weather.data.remote.toSnapshot
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.model.WeatherSnapshot
import io.github.duddmeister.weather.update.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ParsingTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val midnight = LocalDate.of(2026, 10, 21).atStartOfDay(berlin).toEpochSecond()
    private val location = SavedLocation(id = "geo:1", name = "Berlin", latitude = 52.52, longitude = 13.405)

    private val forecastJson = """
        {"latitude":52.52,"longitude":13.419998,"generationtime_ms":0.6,"utc_offset_seconds":7200,
         "timezone":"Europe/Berlin","timezone_abbreviation":"GMT+2","elevation":38.0,
         "current_units":{"time":"unixtime","interval":"seconds","temperature_2m":"°C"},
         "current":{"time":${midnight + 13 * 3600},"interval":900,"temperature_2m":12.3,"relative_humidity_2m":80,
           "apparent_temperature":10.1,"is_day":1,"precipitation":0.0,"weather_code":3,"cloud_cover":100,
           "pressure_msl":1012.4,"wind_speed_10m":14.2,"wind_direction_10m":250,"wind_gusts_10m":30.6,
           "dew_point_2m":9.0,"visibility":24000.0,"uv_index":1.2},
         "minutely_15":{"time":[${midnight + 13 * 3600},${midnight + 13 * 3600 + 900}],"precipitation":[0.0,0.1],
           "snowfall":[0.0,0.0],"temperature_2m":[12.3,12.1],"weather_code":[3,61]},
         "hourly":{"time":[$midnight,${midnight + 3600}],"temperature_2m":[10.0,null],"weather_code":[2,null],
           "is_day":[0,0],"precipitation_probability":[null,15]},
         "daily":{"time":[$midnight,${midnight + 86400}],"weather_code":[61,3],"temperature_2m_max":[14.2,15.0],
           "temperature_2m_min":[8.1,7.5],"sunrise":[${midnight + 27000},${midnight + 86400 + 27100}],
           "sunset":[${midnight + 65000},${midnight + 86400 + 64900}],"uv_index_max":[2.1,null]}}
    """.trimIndent()

    @Test
    fun forecastMapsToSnapshot() {
        val response = AppJson.decodeFromString(ForecastResponse.serializer(), forecastJson)
        val snapshot = response.toSnapshot(location, fetchedAtMillis = (midnight + 13 * 3600) * 1000)
        assertEquals("Europe/Berlin", snapshot.timezone)
        assertEquals(12.3, snapshot.current.temperature, 1e-9)
        assertEquals(3, snapshot.current.weatherCode)
        assertTrue(snapshot.current.isDay)
        assertEquals(24000.0, snapshot.current.visibility!!, 1e-9)
        assertEquals(61, snapshot.minutely[1].weatherCode)
        assertNull(snapshot.hourly[1].temperature)
        assertEquals(false, snapshot.hourly[0].isDay)
        assertEquals(15.0, snapshot.hourly[1].precipitationProbability!!, 1e-9)
        assertEquals("2026-10-21", snapshot.daily[0].date)
        assertEquals("2026-10-22", snapshot.daily[1].date)
        assertNull(snapshot.daily[1].uvIndexMax)
        // The cache format must round-trip.
        val encoded = AppJson.encodeToString(WeatherSnapshot.serializer(), snapshot)
        assertEquals(snapshot, AppJson.decodeFromString(WeatherSnapshot.serializer(), encoded))
    }

    @Test
    fun forecastWithoutCurrentFallsBackToHourly() {
        val json = """{"timezone":"UTC","hourly":{"time":[1000,4600],"temperature_2m":[5.0,6.0],"weather_code":[1,2]}}"""
        val snapshot = AppJson.decodeFromString(ForecastResponse.serializer(), json).toSnapshot(location, 4_000_000)
        assertEquals(6.0, snapshot.current.temperature, 1e-9)
        assertEquals(2, snapshot.current.weatherCode)
    }

    @Test
    fun airQualityAndGeocoding() {
        val aq = AppJson.decodeFromString(
            AirQualityResponse.serializer(),
            """{"current":{"time":1,"us_aqi":42,"european_aqi":18.0,"pm2_5":5.1,"grass_pollen":12.0,"birch_pollen":null},
               "hourly":{"time":[1,2],"us_aqi":[40,null],"european_aqi":[17,19]}}""",
        ).toAirQuality()!!
        assertEquals(42.0, aq.usAqi!!, 1e-9)
        assertEquals(mapOf("Grass" to 12.0), aq.pollen)
        assertNull(aq.hourly[1].usAqi)

        val results = AppJson.decodeFromString(
            GeocodingResponse.serializer(),
            """{"results":[{"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"feature_code":"PPLC",
               "country_code":"DE","admin1":"Land Berlin","timezone":"Europe/Berlin","population":3426354,"country":"Germany"}],
               "generationtime_ms":1.0}""",
        ).results
        val saved = results.single().toSavedLocation()
        assertEquals("geo:2950159", saved.id)
        assertEquals("Land Berlin, Germany", saved.subtitle)
        assertTrue(AppJson.decodeFromString(GeocodingResponse.serializer(), """{"generationtime_ms":1.0}""").results.isEmpty())
    }

    @Test
    fun nwsAlertsAreFilteredAndSorted() {
        val now = Instant.parse("2026-10-21T12:00:00Z").epochSecond
        val json = """
            {"features":[
              {"id":"a","properties":{"id":"a","event":"Wind Advisory","severity":"Moderate","status":"Actual",
                "messageType":"Alert","onset":"2026-10-21T10:00:00-04:00","expires":"2026-10-21T20:00:00-04:00",
                "senderName":"NWS New York NY"}},
              {"id":"b","properties":{"id":"b","event":"Tornado Warning","severity":"Extreme","status":"Actual",
                "messageType":"Alert","expires":"2026-10-21T13:00:00Z"}},
              {"id":"c","properties":{"id":"c","event":"Test Message","severity":"Unknown","status":"Test"}},
              {"id":"d","properties":{"id":"d","event":"Frost Advisory","severity":"Minor","status":"Actual",
                "expires":"2026-10-21T06:00:00Z"}}
            ]}
        """.trimIndent()
        val alerts = NwsClient.parseAlerts(AppJson.decodeFromString(NwsAlertsResponse.serializer(), json), now)
        assertEquals(listOf("Tornado Warning", "Wind Advisory"), alerts.map { it.event })
        assertEquals(Instant.parse("2026-10-22T00:00:00Z").epochSecond, alerts[1].expires)
    }

    @Test
    fun gitHubReleaseAndVersions() {
        val release = AppJson.decodeFromString(
            GitHubRelease.serializer(),
            """{"tag_name":"v1.0.42","name":"Nimbus v1.0.42","body":"- Faster widgets","draft":false,"prerelease":false,
               "html_url":"https://github.com/o/r/releases/tag/v1.0.42","published_at":"2026-10-21T10:00:00Z",
               "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10},
                         {"name":"Nimbus-v1.0.42.apk","browser_download_url":"https://x/Nimbus-v1.0.42.apk","size":5242880,
                          "content_type":"application/vnd.android.package-archive"}]}""",
        )
        assertEquals("Nimbus-v1.0.42.apk", release.apkAsset?.name)
        assertTrue(AppVersion.isNewer(release.tagName, "1.0.41"))
        assertFalse(AppVersion.isNewer(release.tagName, "1.0.42"))
        assertFalse(AppVersion.isNewer(release.tagName, "1.1.0"))
        assertTrue(AppVersion.isNewer("v1.0.42", "1.0.42-dev"))
        assertTrue(AppVersion.isNewer("1.0.10", "1.0.9"))
        assertTrue(AppVersion.isNewer("2.0", "1.9.99"))
        assertFalse(AppVersion.isNewer("garbage", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.0.1", null))
        assertEquals(AppVersion(listOf(1, 0, 3), "debug"), AppVersion.parse("1.0.3-debug"))
    }

    @Test
    fun urlsAreLocaleIndependent() {
        val defaultLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val url = OpenMeteoClient.forecastUrl(52.52, 13.405).toString()
            assertTrue(url, url.contains("latitude=52.5200"))
            assertTrue(url, url.contains("hourly=temperature_2m,relative_humidity_2m"))
            assertTrue(NwsClient.alertsUrl(40.7128, -74.006).toString().endsWith("point=40.7128,-74.0060"))
        } finally {
            java.util.Locale.setDefault(defaultLocale)
        }
    }
}
