package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.weather.Moon
import com.rein4ce.roadcollector.core.weather.OpenMeteo
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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
    fun `day or night is asked and read, and missing means day`() {
        assertTrue(OpenMeteo.currentUrl(35.7, 139.8).contains("current=weather_code,precipitation,is_day"))
        assertTrue(OpenMeteo.hourUrl(35.7, 139.8, 0L).contains("hourly=weather_code,precipitation,is_day"))
        val night = """{"current":{"time":"2026-11-07T15:00","weather_code":0,"precipitation":0.0,"is_day":0}}"""
        assertEquals(WalkWeather(0, 0.0, isDay = false), OpenMeteo.parseCurrent(night))
        val hour = """{"hourly":{"time":["2026-11-07T03:00"],"weather_code":[1],"precipitation":[0.0],"is_day":[1]}}"""
        assertEquals(WalkWeather(1, 0.0, isDay = true), OpenMeteo.parseHour(hour))
        // 前の版の答え（is_day が無い）は昼
        assertEquals(true, OpenMeteo.parseCurrent("""{"current":{"weather_code":0,"precipitation":0.0}}""")?.isDay)
    }

    @Test
    fun `weather saved before day and night existed reads as day`() {
        val old = AppJson.decodeFromString(WalkWeather.serializer(), """{"code":0,"precipitationMm":0.0}""")
        assertTrue(old.isDay)
    }

    @Test
    fun `the moon phase follows known new and full moons`() {
        // 2024-04-08 18:21 UTC は新月（皆既日食の日）、2024-04-23 23:49 UTC は満月。1 日ほどのずれは許す
        val newMoon = Moon.phase(Instant.parse("2024-04-08T18:21:00Z").toEpochMilli())
        assertTrue("new moon: $newMoon", newMoon < 0.04 || newMoon > 0.96)
        val fullMoon = Moon.phase(Instant.parse("2024-04-23T23:49:00Z").toEpochMilli())
        assertEquals(0.5, fullMoon, 0.04)
        assertEquals(0.0, Moon.illumination(0.0), 1e-9)
        assertEquals(1.0, Moon.illumination(0.5), 1e-9)
        assertEquals(0.5, Moon.illumination(0.25), 1e-9)
        // 満月の夜は前後 1 日まで（満月は日本時間 4/24 朝なので、4/23 の夜も 4/24 の夜も満月）。半月や新月は満月ではない
        assertTrue(Moon.isFull(fullMoon))
        assertTrue(Moon.isFull(Moon.phase(Instant.parse("2024-04-23T12:00:00Z").toEpochMilli())))
        assertTrue(Moon.isFull(Moon.phase(Instant.parse("2024-04-24T12:00:00Z").toEpochMilli())))
        assertFalse(Moon.isFull(0.25))
        assertFalse(Moon.isFull(newMoon))
    }

    @Test
    fun `every sample has a weather and the samples show all seven`() {
        SampleParts.all.forEach { assertTrue(it.id, SampleParts.weather.containsKey(it.id)) }
        // 見本だけで 7 つの天気が全部見られる（デモで見せるため）
        assertEquals(Sky.entries.toSet(), SampleParts.weather.values.map { it.sky }.toSet())
    }

    @Test
    fun `samples range from short to long walks`() {
        // 積むときの大きさの違い（0.7〜1.4 倍）が見本で見られるように、短い道と長い道がある
        val distances = SampleParts.all.map { it.distanceM }
        assertTrue("shortest ${distances.min()}", distances.min() <= 200)
        assertTrue("longest ${distances.max()}", distances.max() >= 1_500)
    }
}
