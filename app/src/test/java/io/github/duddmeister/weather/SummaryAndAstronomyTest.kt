package io.github.duddmeister.weather

import io.github.duddmeister.weather.domain.Astronomy
import io.github.duddmeister.weather.domain.SummaryBuilder
import io.github.duddmeister.weather.domain.TimeText
import io.github.duddmeister.weather.model.UnitPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

class SummaryAndAstronomyTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val start = Instant.parse("2026-10-20T00:00:00Z").epochSecond
    private fun hourIndex(isoUtc: String) = ((Instant.parse(isoUtc).epochSecond - start) / 3600).toInt()

    @Test
    fun daytimeSummaryMentionsRainWindowAndTemperatureChange() {
        val rainy = setOf(hourIndex("2026-10-21T16:00:00Z"), hourIndex("2026-10-21T17:00:00Z"), hourIndex("2026-10-21T18:00:00Z"))
        val snapshot = TestData.snapshot(
            fetchedAtMillis = Instant.parse("2026-10-21T09:00:00Z").toEpochMilli(),
            startEpoch = start,
            hours = 72,
            precipitation = { if (it in rainy) 0.5 else 0.0 },
            probability = { if (it in rainy) 80.0 else 5.0 },
            code = { if (it in rainy) 63 else 2 },
            cloud = { 50.0 },
            daily = listOf(
                TestData.day(LocalDate.of(2026, 10, 20), utc, max = 22.0, min = 12.0),
                TestData.day(LocalDate.of(2026, 10, 21), utc, max = 18.0, min = 9.0, code = 63),
                TestData.day(LocalDate.of(2026, 10, 22), utc, max = 15.0, min = 7.0, code = 61),
            ),
        )
        val text = SummaryBuilder.build(
            snapshot,
            Instant.parse("2026-10-21T09:00:00Z").toEpochMilli(),
            UnitPrefs(),
            use24h = false,
            locale = Locale.US,
        )
        assertEquals(
            "Partly cloudy today. Rain likely from 3 PM to 6 PM. High of 18°, 4° cooler than yesterday.",
            text,
        )
    }

    @Test
    fun eveningSummaryTalksAboutTonightAndTomorrow() {
        val snapshot = TestData.snapshot(
            fetchedAtMillis = Instant.parse("2026-10-21T20:00:00Z").toEpochMilli(),
            startEpoch = start,
            hours = 72,
            temperature = { 8.0 },
            cloud = { 5.0 },
            windGusts = { 60.0 },
            daily = listOf(
                TestData.day(LocalDate.of(2026, 10, 21), utc, max = 18.0, min = 9.0),
                TestData.day(LocalDate.of(2026, 10, 22), utc, max = 15.0, min = 7.0, code = 61),
            ),
        )
        val text = SummaryBuilder.build(
            snapshot,
            Instant.parse("2026-10-21T20:00:00Z").toEpochMilli(),
            UnitPrefs(),
            use24h = true,
            locale = Locale.US,
        )
        assertEquals("Clear tonight. Low of 8°. Gusts up to 60 km/h. Tomorrow: Light rain, high of 15°.", text)
    }

    @Test
    fun moonPhasesMatchKnownDates() {
        val full = Astronomy.moon(Instant.parse("2024-01-25T17:54:00Z").toEpochMilli())
        assertTrue("phase ${full.phase}", abs(full.phase - 0.5) < 0.04)
        assertEquals("Full moon", full.name)
        assertTrue(full.illumination > 0.98)

        val new = Astronomy.moon(Instant.parse("2024-01-11T11:57:00Z").toEpochMilli())
        assertTrue("phase ${new.phase}", new.phase < 0.04 || new.phase > 0.96)
        assertEquals("New moon", new.name)
    }

    @Test
    fun timeText() {
        val text = TimeText(utc, use24h = false, locale = Locale.US)
        assertEquals("3 PM", text.hour(Instant.parse("2026-10-21T15:00:00Z").epochSecond))
        assertEquals("3:42 PM", text.time(Instant.parse("2026-10-21T15:42:00Z").epochSecond))
        assertEquals("Today", text.dayLabel(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 21)))
        assertEquals("Wed", text.dayLabel(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 20)))
        assertEquals("just now", TimeText.relativeAge(1_000_000, 990_000))
        assertEquals("5 min ago", TimeText.relativeAge(10 * 60_000, 5 * 60_000))
        assertEquals("2 hr ago", TimeText.relativeAge(3 * 3600_000L, 3600_000L))
        assertEquals("10h 32m", TimeText.hoursMinutes(37_920.0))
    }
}
