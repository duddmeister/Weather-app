package io.github.duddmeister.weather

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.duddmeister.weather.data.WidgetRefreshState
import io.github.duddmeister.weather.model.AppSettings
import io.github.duddmeister.weather.model.CurrentConditions
import io.github.duddmeister.weather.model.SavedLocation
import io.github.duddmeister.weather.widget.SkyEffect
import io.github.duddmeister.weather.widget.SkyGlow
import io.github.duddmeister.weather.widget.WidgetInputs
import io.github.duddmeister.weather.widget.WidgetModels
import io.github.duddmeister.weather.widget.WidgetSky
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class WidgetModelTest {
    private val utc = ZoneOffset.UTC
    private val fetched = Instant.parse("2026-10-21T12:00:00Z")
    private val home = SavedLocation(id = "geo:1", name = "Home", latitude = 40.0, longitude = -74.0)
    private val work = SavedLocation(id = "geo:2", name = "Work", latitude = 41.0, longitude = -73.0)
    private val defaultLocale = Locale.getDefault()

    @Before
    fun useUsLocale() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun inputs(refresh: WidgetRefreshState = WidgetRefreshState()) = WidgetInputs(
        locations = listOf(home, work),
        snapshots = mapOf(
            home.id to TestData.snapshot(
                fetchedAtMillis = fetched.toEpochMilli(),
                startEpoch = fetched.epochSecond - 3600,
                temperature = { 10.0 + 2 * it },
                probability = { if (it == 3) 60.0 else 0.0 },
                daily = listOf(TestData.day(LocalDate.of(2026, 10, 21), utc, max = 21.0, min = 9.0)),
            ),
        ),
        settings = AppSettings(),
        refresh = refresh,
    )

    @Test
    fun freshDataShowsObservationAndUpdateTime() {
        val model = WidgetModels.build(inputs(), null, fetched.toEpochMilli() + 60_000, use24h = true, deviceZone = utc)
        assertEquals("Home", model.locationName)
        assertEquals("20°", model.temperature)
        assertEquals("Updated 12:00", model.status)
        assertFalse(model.statusIsWarning)
        assertEquals("H 21°  L 9°", model.highLow)
        assertEquals("Now", model.hours.first().label)
        assertEquals("60%", model.hours[2].precipitation)
        assertTrue(model.hasData)
    }

    @Test
    fun oldDataFollowsTheForecastAndWarns() {
        // Three hours later the widget shows the forecast for now (16° at 14:00 → 18° at 15:00).
        val model = WidgetModels.build(inputs(), null, fetched.toEpochMilli() + 3 * 3600_000, use24h = true, deviceZone = utc)
        assertEquals("18°", model.temperature)
        assertTrue(model.statusIsWarning)
    }

    @Test
    fun failedRefreshIsShownAsOffline() {
        val failed = WidgetRefreshState(refreshing = false, lastFailureAt = fetched.toEpochMilli() + 120_000)
        val model = WidgetModels.build(inputs(failed), null, fetched.toEpochMilli() + 180_000, use24h = false, deviceZone = utc)
        assertEquals("Offline · 12:00 PM", model.status)
        assertTrue(model.statusIsWarning)
    }

    @Test
    fun refreshingAndMissingData() {
        val refreshing = WidgetModels.build(inputs(WidgetRefreshState(refreshing = true)), "geo:2", fetched.toEpochMilli(), use24h = true, deviceZone = utc)
        assertEquals("Work", refreshing.locationName)
        assertFalse(refreshing.hasData)
        assertTrue(refreshing.refreshing)
        assertEquals("Updating…", refreshing.status)
        assertEquals(WidgetSky.None, refreshing.sky)
    }

    private fun skyFor(code: Int, isDay: Boolean): WidgetSky {
        val snapshot = TestData.snapshot(
            fetchedAtMillis = fetched.toEpochMilli(),
            startEpoch = fetched.epochSecond - 3600,
            current = CurrentConditions(time = fetched.epochSecond, temperature = 8.0, weatherCode = code, isDay = isDay),
        )
        val inputs = WidgetInputs(listOf(home), mapOf(home.id to snapshot), AppSettings(), WidgetRefreshState())
        return WidgetModels.build(inputs, null, fetched.toEpochMilli() + 60_000, use24h = true, deviceZone = utc).sky
    }

    @Test
    fun skyEffectsFollowTheConditions() {
        assertEquals(WidgetSky(SkyGlow.Moon, listOf(SkyEffect.Stars)), skyFor(0, isDay = false))
        assertEquals(WidgetSky(SkyGlow.Sun, emptyList()), skyFor(0, isDay = true))
        assertEquals(WidgetSky(SkyGlow.Moon, listOf(SkyEffect.Stars, SkyEffect.LightClouds)), skyFor(2, isDay = false))
        assertEquals(WidgetSky(null, listOf(SkyEffect.HeavyClouds)), skyFor(3, isDay = false))
        assertEquals(WidgetSky(null, listOf(SkyEffect.HeavyClouds, SkyEffect.Rain)), skyFor(63, isDay = true))
        assertEquals(WidgetSky(null, listOf(SkyEffect.HeavyClouds, SkyEffect.HeavyRain)), skyFor(65, isDay = false))
        assertEquals(WidgetSky(null, listOf(SkyEffect.HeavyClouds, SkyEffect.HeavyRain, SkyEffect.Lightning)), skyFor(95, isDay = true))
        assertEquals(WidgetSky(null, listOf(SkyEffect.HeavyClouds, SkyEffect.Snow)), skyFor(73, isDay = true))
        assertEquals(WidgetSky(null, listOf(SkyEffect.Fog)), skyFor(45, isDay = true))
    }

    @Test
    fun cloudsKeepTheirShapeOnEveryWidget() {
        // A slim 4×1 clock, a 4×2 clock and a 2×2 widget each get clouds drawn for their shape.
        assertEquals(R.drawable.fx_clouds_heavy_wide, SkyEffect.HeavyClouds.drawable(DpSize(300.dp, 70.dp)))
        assertEquals(R.drawable.fx_clouds_heavy, SkyEffect.HeavyClouds.drawable(DpSize(300.dp, 160.dp)))
        assertEquals(R.drawable.fx_clouds_heavy_square, SkyEffect.HeavyClouds.drawable(DpSize(150.dp, 150.dp)))
        assertEquals(R.drawable.fx_clouds_light_wide, SkyEffect.LightClouds.drawable(DpSize(330.dp, 60.dp)))
        // Even fields are cropped from a single drawable.
        assertEquals(R.drawable.fx_rain, SkyEffect.Rain.drawable(DpSize(300.dp, 70.dp)))
        assertTrue(SkyEffect.HeavyClouds.stretch)
        assertFalse(SkyEffect.Rain.stretch)
    }
}
