package io.github.duddmeister.weather

import io.github.duddmeister.weather.core.AppJson
import io.github.duddmeister.weather.data.remote.MetOfficeClient
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.domain.withMetOffice
import io.github.duddmeister.weather.model.MetOfficeData
import io.github.duddmeister.weather.model.MetOfficeDay
import io.github.duddmeister.weather.model.MetOfficeHour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MetOfficeBlendTest {
    private val zone = ZoneId.of("Europe/London")
    private val fetchedAt = Instant.parse("2026-10-24T17:20:00Z").toEpochMilli()
    private val midnight = Instant.parse("2026-10-24T00:00:00Z").epochSecond
    private fun at(utc: String) = Instant.parse(utc).epochSecond

    /** Open-Meteo's side: a flat 15° with some cloud, and four days of 9–16°. */
    private val openMeteo = TestData.snapshot(
        fetchedAtMillis = fetchedAt,
        startEpoch = midnight,
        timezone = zone.id,
        temperature = { 15.0 },
        probability = { 10.0 },
        code = { 3 },
        cloud = { 50.0 },
        daily = (0L..3L).map { TestData.day(LocalDate.of(2026, 10, 24).plusDays(it), zone, max = 16.0, min = 9.0, code = 3) },
    )

    private fun resource(name: String) = javaClass.getResource("/$name")!!.readText()

    private val metOffice = MetOfficeData(
        hourly = MetOfficeClient.parseHourly(AppJson, resource("metoffice-hourly-sample.json")),
        daily = MetOfficeClient.parseDaily(AppJson, resource("metoffice-daily-sample.json")),
        hourlyFetchedAt = fetchedAt,
        dailyFetchedAt = fetchedAt,
    )

    @Test
    fun theMetOfficesHoursReplaceOpenMeteosWhereTheyOverlap() {
        val blended = openMeteo.withMetOffice(metOffice)
        val byTime = blended.hourly.associateBy { it.time }
        val five = byTime.getValue(at("2026-10-24T17:00:00Z"))
        assertEquals(11.2, five.temperature!!, 1e-9)
        assertEquals(61, five.weatherCode)
        assertEquals(70.0, five.precipitationProbability!!, 1e-9)
        assertEquals(27.0, five.windSpeed!!, 1e-9)
        // What the Met Office doesn't forecast stays Open-Meteo's.
        assertEquals(50.0, five.cloudCover!!, 1e-9)
        // A missing Met Office value doesn't blank Open-Meteo's.
        assertEquals(15.0, byTime.getValue(at("2026-10-24T20:00:00Z")).temperature!!, 1e-9)
        assertEquals(3, byTime.getValue(at("2026-10-24T20:00:00Z")).weatherCode)
        // Outside the Met Office's hours nothing changes.
        assertEquals(15.0, byTime.getValue(at("2026-10-24T16:00:00Z")).temperature!!, 1e-9)
        assertEquals(15.0, byTime.getValue(at("2026-10-24T23:00:00Z")).temperature!!, 1e-9)
        assertSame(metOffice, blended.metOffice)
    }

    @Test
    fun currentConditionsAreReadBetweenTheMetOfficesHours() {
        val current = openMeteo.withMetOffice(metOffice).current
        // 17:20 is a third of the way from 17:00 (11.2°) to 18:00 (10.8°).
        assertEquals(11.2 - 0.4 / 3, current.temperature, 1e-9)
        assertEquals(225.0 + 5.0 / 3, current.windDirection!!, 1e-9)
        assertEquals(61, current.weatherCode) // 17:00's light rain is the nearest
    }

    @Test
    fun laterTheNearestMetOfficeSymbolDescribesNow() {
        val blended = openMeteo.withMetOffice(metOffice)
        // At 18:00 the 15-minute and hour-ending conventions would show 19:00's heavy rain; the Met
        // Office's symbol for 18:00 is a clear night.
        assertEquals(0, blended.conditionsAt(Instant.parse("2026-10-24T18:00:00Z").toEpochMilli()).weatherCode)
        assertEquals(65, blended.conditionsAt(Instant.parse("2026-10-24T19:10:00Z").toEpochMilli()).weatherCode)
    }

    @Test
    fun daysTakeTheDaysHighTheNightBeforesLowAndTheHoursInBetween() {
        val days = openMeteo.withMetOffice(metOffice).daily.associateBy { it.date }
        val today = days.getValue("2026-10-24")
        assertEquals(12.3, today.temperatureMax!!, 1e-9)
        assertEquals(7.4, today.temperatureMin!!, 1e-9) // last night's low
        assertEquals(82, today.weatherCode)
        assertEquals(92.0, today.precipitationProbabilityMax!!, 1e-9) // tonight's rain
        assertEquals(8.6, today.apparentMax!!, 1e-9)
        assertEquals(4.2, today.apparentMin!!, 1e-9)
        assertEquals(1.0, today.uvIndexMax!!, 1e-9)
        val tomorrow = days.getValue("2026-10-25")
        assertEquals(11.0, tomorrow.temperatureMax!!, 1e-9)
        assertEquals(8.1, tomorrow.temperatureMin!!, 1e-9) // tonight's low
        assertEquals(2, tomorrow.weatherCode)
        assertEquals(12.0, tomorrow.precipitationProbabilityMax!!, 1e-9)
        assertEquals(5.5, days.getValue("2026-10-26").temperatureMin!!, 1e-9)
        // Beyond the Met Office's week, Open-Meteo's day stands.
        val later = days.getValue("2026-10-27")
        assertEquals(16.0, later.temperatureMax!!, 1e-9)
        assertEquals(9.0, later.temperatureMin!!, 1e-9)
        assertEquals(3, later.weatherCode)
        // Sunrise and sunset still come from Open-Meteo.
        assertEquals(openMeteo.daily.first().sunrise, today.sunrise)
    }

    @Test
    fun aDaysRangeCoversItsHourlyTemperatures() {
        // A mild night after a cool day: 12° at 23:00 is above the day's high, and an early 3° below last night's low.
        val data = MetOfficeData(
            hourly = listOf(
                MetOfficeHour(time = at("2026-10-24T05:00:00Z"), temperature = 3.0),
                MetOfficeHour(time = at("2026-10-24T22:00:00Z"), temperature = 12.0),
            ),
            daily = listOf(
                MetOfficeDay(date = "2026-10-23", nightMin = 5.0),
                MetOfficeDay(date = "2026-10-24", dayMax = 10.0),
            ),
        )
        val today = openMeteo.withMetOffice(data).daily.first { it.date == "2026-10-24" }
        assertEquals(12.0, today.temperatureMax!!, 1e-9)
        assertEquals(3.0, today.temperatureMin!!, 1e-9)
        // Without a weather symbol or rain chance for the day, Open-Meteo's are kept.
        assertEquals(3, today.weatherCode)
        assertNull(today.precipitationProbabilityMax)
    }

    @Test
    fun withoutNearbyHoursTheCurrentConditionsStay() {
        val data = MetOfficeData(hourly = listOf(MetOfficeHour(time = at("2026-10-24T21:00:00Z"), temperature = 2.0)))
        assertEquals(openMeteo.current, openMeteo.withMetOffice(data).current)
    }
}
