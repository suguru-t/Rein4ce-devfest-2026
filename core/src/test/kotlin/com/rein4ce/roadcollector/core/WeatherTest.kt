package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.weather.OpenMeteo
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 天気：天気コードの読み替え、Open-Meteo に送る URL と返事の読み取り */
class WeatherTest {

    @Test
    fun `weather codes become skies`() {
        assertEquals(Sky.CLEAR, Sky.of(0))
        assertEquals(Sky.CLOUDY, Sky.of(3))
        assertEquals(Sky.FOG, Sky.of(45))
        assertEquals(Sky.DRIZZLE, Sky.of(53))
        assertEquals(Sky.RAIN, Sky.of(63))
        assertEquals(Sky.RAIN, Sky.of(81)) // にわか雨
        assertEquals(Sky.SNOW, Sky.of(73))
        assertEquals(Sky.SNOW, Sky.of(85)) // にわか雪
        assertEquals(Sky.THUNDER, Sky.of(95))
        assertTrue(Sky.THUNDER.isRainy)
        assertFalse(Sky.SNOW.isRainy)
    }

    @Test
    fun `the place sent out is rounded to a tenth of a degree`() {
        // 歩いた正確な場所を外に出さない（0.1 度は約 10 km）
        val url = OpenMeteo.currentUrl(35.74963, 139.80491)
        assertTrue(url, url.contains("latitude=35.7&longitude=139.8&"))
        assertFalse(url.contains("35.749"))
    }

    @Test
    fun `the hour of a walk is asked in world time`() {
        // 2026-11-06 19:42（日本時間）＝ 10:42（世界時）→ 10 時台の 1 時間
        val url = OpenMeteo.hourUrl(35.75, 139.80, timeMs = 1_793_961_720_000L)
        assertTrue(url, url.contains("start_hour=2026-11-06T10:00&end_hour=2026-11-06T10:00"))
    }

    @Test
    fun `answers are read`() {
        val current = """{"latitude":35.7,"current":{"time":"2026-11-07T03:00","weather_code":61,"precipitation":0.4}}"""
        assertEquals(WalkWeather(61, 0.4), OpenMeteo.parseCurrent(current))
        val hour = """{"hourly":{"time":["2026-11-06T10:00"],"weather_code":[2],"precipitation":[0.00]}}"""
        assertEquals(WalkWeather(2, 0.0), OpenMeteo.parseHour(hour))
        assertNull(OpenMeteo.parseHour("""{"error":true,"reason":"out of range"}"""))
        assertNull(OpenMeteo.parseCurrent("not json"))
    }

    @Test
    fun `every sample has a weather`() {
        SampleParts.all.forEach { assertTrue(it.id, SampleParts.weather.containsKey(it.id)) }
    }
}
