package io.github.duddmeister.weather

import io.github.duddmeister.weather.domain.conditionsAt
import io.github.duddmeister.weather.model.CurrentConditions
import io.github.duddmeister.weather.ui.SkyKind
import io.github.duddmeister.weather.ui.sky.SkyScenes
import io.github.duddmeister.weather.ui.sky.lerp
import io.github.duddmeister.weather.ui.sky.look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SkyLookTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 7, 14)

    /** A sky for [code] at [hour]:[minute] on a day when the sun is up from 07:00 to 18:30. */
    private fun scene(code: Int, hour: Int, minute: Int = 0, isDay: Boolean = true): io.github.duddmeister.weather.ui.sky.SkyScene {
        val now = date.atTime(hour, minute).toEpochSecond(zone)
        val snapshot = TestData.snapshot(
            fetchedAtMillis = now * 1000,
            startEpoch = date.atStartOfDay().toEpochSecond(zone),
            code = { code },
            daily = listOf(TestData.day(date, zone, max = 20.0, min = 10.0, code = code)),
            current = CurrentConditions(time = now, temperature = 15.0, weatherCode = code, isDay = isDay, windSpeed = 20.0, windDirection = 270.0),
        )
        return SkyScenes.forConditions(snapshot, snapshot.conditionsAt(now * 1000), now * 1000)
    }

    @Test
    fun blendingStartsAndEndsOnTheLooksThemselves() {
        val sunny = scene(0, 12).look()
        val rainy = scene(63, 12).look()
        assertSame(sunny, lerp(sunny, rainy, 0f))
        assertSame(rainy, lerp(sunny, rainy, 1f))
    }

    @Test
    fun halfWayBetweenTwoPlacesIsHalfOfEach() {
        val sunny = scene(0, 12).look()
        val rainy = scene(63, 12).look()
        val between = lerp(sunny, rainy, 0.5f)
        assertEquals(rainy.rain / 2f, between.rain, 1e-4f)
        assertEquals((sunny.sun + rainy.sun) / 2f, between.sun, 1e-4f)
        assertEquals((sunny.clouds + rainy.clouds) / 2f, between.clouds, 1e-4f)
        assertTrue(between.top.blue < sunny.top.blue && between.top.blue > rainy.top.blue)
        assertFalse(between.closeTo(sunny))
        assertTrue(between.closeTo(lerp(sunny, rainy, 0.5f)))
    }

    @Test
    fun theSunDoesNotTravelFromWhereItNeverWas() {
        // Swiping from a rainy place to a sunset: the sun fades in where the sunset has it.
        val rainy = scene(63, 12).look()
        val dusk = scene(0, 18, 10).look()
        assertEquals(0f, rainy.sun, 0f)
        val between = lerp(rainy, dusk, 0.3f)
        assertEquals(dusk.sunX, between.sunX, 1e-4f)
        assertEquals(dusk.sunY, between.sunY, 1e-4f)
        assertEquals(dusk.sunWarmth, between.sunWarmth, 1e-4f)
    }

    @Test
    fun aLowSunSinksAsSunsetNears() {
        val early = scene(0, 17, 55)
        val late = scene(0, 18, 28)
        assertEquals(SkyKind.Dusk, early.kind)
        assertEquals(SkyKind.Dusk, late.kind)
        assertTrue(late.sunLowness > early.sunLowness)
        assertTrue(late.look().sunY > early.look().sunY)
        // By day the sun is high, and not golden.
        val noon = scene(0, 12).look()
        assertTrue(noon.sunY < early.look().sunY)
        assertEquals(0f, noon.sunWarmth, 0f)
        assertEquals(1f, late.look().sunWarmth, 0f)
    }

    @Test
    fun behindOtherScreensOnlyColoursAndCloudsRemain() {
        val storm = scene(95, 12)
        val showing = storm.look()
        val calm = storm.look(calm = true)
        assertTrue(showing.rain > 0f && showing.lightning == 1f)
        assertEquals(0f, calm.rain, 0f)
        assertEquals(0f, calm.snow, 0f)
        assertEquals(0f, calm.stars, 0f)
        assertEquals(0f, calm.sun, 0f)
        assertEquals(0f, calm.moon, 0f)
        assertEquals(0f, calm.lightning, 0f)
        assertEquals(showing.clouds, calm.clouds, 0f)
        assertEquals(showing.top, calm.top)
        assertTrue(calm.dim > 0f)
        assertEquals(0f, showing.dim, 0f)
    }

    @Test
    fun nightSkiesHaveStarsAndAMoonButNoSun() {
        val night = scene(0, 23, isDay = false).look()
        assertEquals(1f, night.stars, 0f)
        assertEquals(1f, night.moon, 0f)
        assertEquals(0f, night.sun, 0f)
    }
}
