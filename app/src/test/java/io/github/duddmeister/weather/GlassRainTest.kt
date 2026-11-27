package io.github.duddmeister.weather

import io.github.duddmeister.weather.ui.sky.GlassRainMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassRainTest {
    @Test
    fun aRunningDropNeverGoesBackUp() {
        var last = 0f
        var seconds = 0f
        while (seconds < 12f) {
            val fallen = GlassRainMotion.slide(seconds)
            assertTrue("slid back at $seconds s", fallen >= last)
            if (seconds > 0f) assertTrue("standing still or reversing at $seconds s", GlassRainMotion.pace(seconds) > 0f)
            last = fallen
            seconds += 0.01f
        }
        // It keeps to its average speed over time, however much it sticks and slips on the way.
        assertEquals(12f, last, 0.4f)
        assertEquals(0f, GlassRainMotion.slide(-1f), 0f)
    }

    @Test
    fun theTrackDriesSteadilyBehindTheDrop() {
        // Nothing to leave a track before the drop lets go, and it grows from where the drop started.
        assertEquals(0f, GlassRainMotion.track(0f), 0f)
        assertEquals(GlassRainMotion.slide(0.5f), GlassRainMotion.track(0.5f), 1e-5f)
        var shortest = Float.MAX_VALUE
        var longest = 0f
        var seconds = GlassRainMotion.TRACK
        while (seconds < 12f) {
            val length = GlassRainMotion.track(seconds)
            // The dry end never overtakes the drop, and never goes back up either.
            assertTrue("no track at $seconds s", length > 0f)
            assertTrue(GlassRainMotion.slide(seconds - GlassRainMotion.TRACK) >= GlassRainMotion.slide(seconds - GlassRainMotion.TRACK - 0.01f))
            shortest = minOf(shortest, length)
            longest = maxOf(longest, length)
            seconds += 0.01f
        }
        // Short while the drop sticks, long when it darts: that's what makes it look left behind.
        assertTrue("shortest $shortest", shortest < GlassRainMotion.TRACK * 0.6f)
        assertTrue("longest $longest", longest > GlassRainMotion.TRACK * 1.4f)
    }

    @Test
    fun aDropLandsSitsAndDriesAway() {
        val life = 10f
        assertEquals(0f, GlassRainMotion.presence(-0.1f, life), 0f)
        assertEquals(0f, GlassRainMotion.presence(0f, life), 1e-4f)
        // The splash spreads a little past the drop's size before it settles.
        val peak = generateSequence(0f) { it + 0.005f }.takeWhile { it < GlassRainMotion.LANDING }.maxOf { GlassRainMotion.presence(it, life) }
        assertTrue(peak > 1.05f && peak < 1.3f)
        assertEquals(1f, GlassRainMotion.presence(GlassRainMotion.LANDING, life), 1e-4f)
        assertEquals(1f, GlassRainMotion.presence(5f, life), 0f)
        assertEquals(0.5f, GlassRainMotion.presence(life - GlassRainMotion.DRYING / 2f, life), 1e-4f)
        assertEquals(0f, GlassRainMotion.presence(life, life), 0f)
    }

    @Test
    fun theSplashIsBrief() {
        assertEquals(0f, GlassRainMotion.splash(0f)!!, 0f)
        assertTrue(GlassRainMotion.splash(0.2f)!! in 0f..1f)
        assertNull(GlassRainMotion.splash(1f))
        assertNull(GlassRainMotion.splash(-0.1f))
    }

    @Test
    fun everyLifeOfADropIsDifferentButRepeatable() {
        val first = GlassRainMotion.chance(3, 0, 2)
        assertEquals(first, GlassRainMotion.chance(3, 0, 2), 0f)
        assertNotEquals(first, GlassRainMotion.chance(3, 1, 2))
        assertNotEquals(first, GlassRainMotion.chance(4, 0, 2))
        // Spread across the range, not bunched.
        val values = (0 until 2000).map { GlassRainMotion.chance(it, it / 7, it % 11) }
        assertTrue(values.all { it >= 0f && it < 1f })
        assertEquals(0.5f, values.average().toFloat(), 0.05f)
        assertTrue(values.count { it < 0.1f } in 120..280)
    }
}
