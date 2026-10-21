package io.github.duddmeister.weather

import io.github.duddmeister.weather.model.DistanceUnit
import io.github.duddmeister.weather.model.PrecipitationUnit
import io.github.duddmeister.weather.model.PressureUnit
import io.github.duddmeister.weather.model.TemperatureUnit
import io.github.duddmeister.weather.model.UnitPrefs
import io.github.duddmeister.weather.model.Units
import io.github.duddmeister.weather.model.WeatherCondition
import io.github.duddmeister.weather.model.WindUnit
import io.github.duddmeister.weather.model.flagEmoji
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsAndConditionsTest {
    @Test
    fun temperatures() {
        assertEquals("0°", Units.formatTemperature(-0.4, TemperatureUnit.Celsius))
        assertEquals("22°", Units.formatTemperature(21.6, TemperatureUnit.Celsius))
        assertEquals("32°", Units.formatTemperature(0.0, TemperatureUnit.Fahrenheit))
        assertEquals("-4°", Units.formatTemperature(-20.0, TemperatureUnit.Fahrenheit))
        assertEquals("--°", Units.formatTemperature(null, TemperatureUnit.Celsius))
        assertEquals(9, Units.temperatureDelta(5.0, TemperatureUnit.Fahrenheit))
    }

    @Test
    fun windPressureDistancePrecipitation() {
        assertEquals("10 m/s", Units.formatWind(36.0, WindUnit.Ms))
        assertEquals("10 mph", Units.formatWind(16.09344, WindUnit.Mph))
        assertEquals("27 kn", Units.formatWind(50.0, WindUnit.Knots))
        assertEquals("29.92 inHg", Units.formatPressure(1013.25, PressureUnit.InHg))
        assertEquals("1013 hPa", Units.formatPressure(1013.25, PressureUnit.Hpa))
        assertEquals("24 km", Units.formatDistance(24_000.0, DistanceUnit.Km))
        assertEquals("500 m", Units.formatDistance(500.0, DistanceUnit.Km))
        assertEquals("10 mi", Units.formatDistance(16_093.44, DistanceUnit.Mi))
        assertEquals("0 mm", Units.formatPrecipitation(0.0, PrecipitationUnit.Mm))
        assertEquals("2.3 mm", Units.formatPrecipitation(2.345, PrecipitationUnit.Mm))
        assertEquals("1.00 in", Units.formatPrecipitation(25.4, PrecipitationUnit.Inch))
        assertEquals("2.0 in", Units.formatSnowfall(5.08, PrecipitationUnit.Inch))
    }

    @Test
    fun compassAndBeaufort() {
        assertEquals("N", Units.compass(0.0))
        assertEquals("NNE", Units.compass(22.5))
        assertEquals("N", Units.compass(359.0))
        assertEquals("S", Units.compass(180.0))
        assertEquals("W", Units.compass(-90.0))
        assertEquals(0, Units.beaufort(0.5))
        assertEquals(2, Units.beaufort(10.0))
        assertEquals(12, Units.beaufort(130.0))
        assertEquals("Gale", Units.beaufortDescription(70.0))
    }

    @Test
    fun defaultsByCountry() {
        assertEquals(TemperatureUnit.Fahrenheit, UnitPrefs.defaultsFor("us").temperature)
        assertEquals(WindUnit.Mph, UnitPrefs.defaultsFor("GB").wind)
        assertEquals(TemperatureUnit.Celsius, UnitPrefs.defaultsFor("GB").temperature)
        assertEquals(UnitPrefs(), UnitPrefs.defaultsFor("DE"))
        assertEquals(UnitPrefs(), UnitPrefs.defaultsFor(null))
    }

    @Test
    fun weatherCodes() {
        assertEquals(WeatherCondition.Clear, WeatherCondition.fromCode(0))
        assertEquals("Clear", WeatherCondition.fromCode(0).label(isDay = false))
        assertEquals("Sunny", WeatherCondition.fromCode(0).label(isDay = true))
        assertEquals(WeatherCondition.RainHeavy, WeatherCondition.fromCode(65))
        assertEquals(WeatherCondition.ThunderstormHeavyHail, WeatherCondition.fromCode(99))
        assertEquals(WeatherCondition.Unknown, WeatherCondition.fromCode(42))
        assertEquals(WeatherCondition.Unknown, WeatherCondition.fromCode(null))
    }

    @Test
    fun flags() {
        assertEquals("🇺🇸", flagEmoji("us"))
        assertEquals("", flagEmoji("USA"))
        assertEquals("", flagEmoji(null))
    }
}
