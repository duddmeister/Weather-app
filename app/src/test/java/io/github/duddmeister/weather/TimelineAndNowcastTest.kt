package io.github.duddmeister.weather

import io.github.duddmeister.weather.domain.NowcastBuilder
import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.domain.precipitationLast24h
import io.github.duddmeister.weather.domain.upcomingHours
import io.github.duddmeister.weather.model.PrecipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TimelineAndNowcastTest {
    private val t0 = Instant.parse("2026-10-21T12:00:00Z").epochSecond
    private val t0Millis = t0 * 1000

    @Test
    fun freshObservationIsUsedAsIs() {
        val s = TestData.snapshot(fetchedAtMillis = t0Millis, startEpoch = t0 - 3600, temperature = { 10.0 + 2 * it })
        val now = s.conditionsAt(t0Millis + 5 * 60_000)
        assertFalse(now.estimated)
        assertEquals(20.0, now.temperature, 1e-9)
    }

    @Test
    fun staleObservationFollowsTheForecast() {
        // Hourly: t0-1h → 10, t0 → 12, t0+1h → 14, t0+2h → 16, t0+3h → 18 ...
        val s = TestData.snapshot(
            fetchedAtMillis = t0Millis,
            startEpoch = t0 - 3600,
            temperature = { 10.0 + 2 * it },
            windDirection = { if (it == 3) 350.0 else 10.0 },
        )
        val later = s.conditionsAt(t0Millis + 150 * 60_000) // t0 + 2h30m
        assertTrue(later.estimated)
        assertEquals(17.0, later.temperature, 1e-9)
        // 350° → 10° interpolates through north, not south.
        assertEquals(0.0, later.windDirection!!, 1e-6)
    }

    @Test
    fun dataThatEndedLongAgoFallsBackToTheObservation() {
        val s = TestData.snapshot(fetchedAtMillis = t0Millis, startEpoch = t0 - 3600, hours = 3)
        val farFuture = s.conditionsAt(t0Millis + 10 * 3600_000)
        assertTrue(farFuture.estimated)
        assertEquals(20.0, farFuture.temperature, 1e-9)
    }

    @Test
    fun upcomingHoursStartAtTheCurrentHour() {
        val s = TestData.snapshot(fetchedAtMillis = t0Millis, startEpoch = t0 - 3 * 3600)
        val hours = s.upcomingHours(t0Millis + 20 * 60_000, count = 5)
        assertEquals(t0, hours.first().time)
        assertEquals(5, hours.size)
    }

    @Test
    fun last24hPrecipitationSumsPrecedingHours() {
        val s = TestData.snapshot(
            fetchedAtMillis = t0Millis,
            startEpoch = t0 - 30 * 3600,
            hours = 40,
            precipitation = { if (it in 20..22) 1.5 else 0.0 },
        )
        assertEquals(4.5, s.precipitationLast24h(t0Millis)!!, 1e-9)
    }

    @Test
    fun nowcastRainStarting() {
        val s = TestData.snapshot(
            fetchedAtMillis = t0Millis,
            startEpoch = t0 - 3600,
            minutely = TestData.minutely(t0 + 900, 0.0, 0.0, 0.2, 0.3, 0.2, 0.0, 0.0, 0.0, 0.0),
        )
        val nowcast = NowcastBuilder.build(s, t0Millis)!!
        assertFalse(nowcast.precipitatingNow)
        assertEquals(PrecipType.Rain, nowcast.kind)
        assertEquals(30, nowcast.startsInMinutes)
        assertEquals("Rain starting in about 30 min, lasting about 45 min", nowcast.headline)
    }

    @Test
    fun nowcastRainStopping() {
        val s = TestData.snapshot(
            fetchedAtMillis = t0Millis,
            startEpoch = t0 - 3600,
            minutely = TestData.minutely(t0 + 900, 0.3, 0.3, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        )
        val nowcast = NowcastBuilder.build(s, t0Millis)!!
        assertTrue(nowcast.precipitatingNow)
        assertEquals(45, nowcast.endsInMinutes)
        assertEquals("Rain stopping in about 45 min", nowcast.headline)
    }

    @Test
    fun nowcastHeavyAndPersistent() {
        val s = TestData.snapshot(
            fetchedAtMillis = t0Millis,
            startEpoch = t0 - 3600,
            minutely = TestData.minutely(t0 + 900, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5),
        )
        val nowcast = NowcastBuilder.build(s, t0Millis)!!
        assertNull(nowcast.endsInMinutes)
        assertEquals("Heavy rain for at least the next 2 hours", nowcast.headline)
    }

    @Test
    fun nowcastDry() {
        val s = TestData.snapshot(
            fetchedAtMillis = t0Millis,
            startEpoch = t0 - 3600,
            minutely = TestData.minutely(t0 + 900, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        )
        val nowcast = NowcastBuilder.build(s, t0Millis)!!
        assertFalse(nowcast.hasPrecipitation)
        assertEquals("No precipitation for the next 2 hours", nowcast.headline)
    }

    @Test
    fun nowcastNeedsMinutelyData() {
        val s = TestData.snapshot(fetchedAtMillis = t0Millis, startEpoch = t0 - 3600)
        assertNull(NowcastBuilder.build(s, t0Millis))
    }

    @Test
    fun durationPhrases() {
        assertEquals("5 min", NowcastBuilder.durationPhrase(2))
        assertEquals("1 hour", NowcastBuilder.durationPhrase(58))
        assertEquals("1 hr 15 min", NowcastBuilder.durationPhrase(76))
        assertEquals("now", NowcastBuilder.inMinutesPhrase(1))
    }
}
