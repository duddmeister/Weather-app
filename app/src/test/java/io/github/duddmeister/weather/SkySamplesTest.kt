package io.github.duddmeister.weather

import io.github.duddmeister.weather.ui.Screen
import io.github.duddmeister.weather.ui.sky.SkySamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkySamplesTest {
    private val skies = SkySamples.all(moonPhase = 0.5f)

    private fun sky(id: String) = skies.single { it.id == id }.look

    @Test
    fun everySkyHasItsOwnNameAndSomethingToLookFor() {
        assertEquals(skies.size, skies.map { it.id }.toSet().size)
        assertEquals(skies.size, skies.map { it.name }.toSet().size)
        skies.forEach { sample ->
            assertTrue(sample.name, sample.note.length > 20 && sample.note.endsWith("."))
            // Ids are typed into `adb shell am start … --es …extra.SKY <id>`.
            assertTrue(sample.id, sample.id.matches(Regex("[a-z]+(-[a-z]+)*")))
        }
    }

    @Test
    fun theWetSkiesPutWaterOnTheGlass() {
        // Drops land wherever the look says it's raining, so these are the skies that show them.
        assertEquals(listOf("drizzle", "rain", "storm"), skies.filter { it.look.rain > 0.01f }.map { it.id })
        assertTrue(sky("drizzle").rain < sky("rain").rain)
        assertEquals(1f, sky("storm").lightning, 0f)
        assertEquals(0f, sky("rain").lightning, 0f)
        // The rain leans with the wind; the storm's blows the other way.
        assertTrue(sky("rain").windTilt > 0f && sky("storm").windTilt < 0f)
    }

    @Test
    fun eachSkyShowsWhatItsNoteDescribes() {
        assertTrue(sky("sunny").sun > 0.9f && sky("sunny").clouds < 0.2f)
        assertTrue(sky("partly-cloudy").sun > 0f && sky("partly-cloudy").clouds > sky("sunny").clouds)
        assertTrue(sky("cloudy").clouds > 0.9f && sky("cloudy").sun == 0f)
        assertTrue(sky("snow").snow > 0.5f && sky("snow").rain == 0f)
        assertTrue(sky("fog").fog > 0.9f)
        // A low, warm sun.
        assertEquals(1f, sky("golden-hour").sunWarmth, 0f)
        assertTrue(sky("golden-hour").sunY > sky("sunny").sunY)
        // Stars and the moon, in the phase asked for.
        assertTrue(sky("night").stars > 0.9f && sky("night").moon > 0.9f && sky("night").sun == 0f)
        assertEquals(0.5f, sky("night").moonPhase, 0f)
    }

    @Test
    fun theGalleryShowsSkiesAtFullStrength() {
        // Behind Settings and Places the sky is dimmed and nothing falls through it; not here.
        skies.forEach { assertEquals(it.name, 0f, it.look.dim, 0f) }
        assertTrue(!Screen.Skies.calm && Screen.Settings.calm && Screen.Places.calm && !Screen.Home.calm)
    }

    @Test
    fun backFromTheGalleryIsSettingsThenTheForecast() {
        assertEquals(Screen.Settings, Screen.Skies.parent)
        assertEquals(Screen.Home, Screen.Settings.parent)
        assertEquals(Screen.Home, Screen.Places.parent)
        assertNull(Screen.Home.parent)
        assertEquals(listOf(0, 1, 1, 2), listOf(Screen.Home, Screen.Places, Screen.Settings, Screen.Skies).map { it.depth })
    }
}
