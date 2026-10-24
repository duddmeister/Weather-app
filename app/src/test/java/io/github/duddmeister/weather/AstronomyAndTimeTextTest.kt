package io.github.duddmeister.weather

import io.github.duddmeister.weather.domain.Astronomy
import io.github.duddmeister.weather.domain.TimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

class AstronomyAndTimeTextTest {
    private val utc: ZoneId = ZoneOffset.UTC

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
