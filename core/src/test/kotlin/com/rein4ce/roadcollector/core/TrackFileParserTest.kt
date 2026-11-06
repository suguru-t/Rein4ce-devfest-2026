package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.geo.TrackTime
import com.rein4ce.roadcollector.core.geo.TrackToPart
import com.rein4ce.roadcollector.core.io.TrackFileParser
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Track
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleTracks
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFileParserTest {

    private val gpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk><name>walk</name><trkseg>
            <trkpt lat="35.7496" lon="139.8049"><ele>3</ele><time>2026-11-06T03:00:00Z</time></trkpt>
            <trkpt lat="35.7497" lon="139.8049"><time>2026-11-06T12:00:03+09:00</time></trkpt>
            <trkpt lat="35.7498" lon="139.8049"></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    @Test
    fun `gpx trkpt are read with time`() {
        val pts = TrackFileParser.parse(gpx)
        assertEquals(3, pts.size)
        assertEquals(35.7496, pts[0].lat, 1e-9)
        assertEquals(139.8049, pts[0].lng, 1e-9)
        assertEquals(1_793_934_000_000L, pts[0].timeMs) // 2026-11-06T03:00:00Z
        assertEquals(pts[0].timeMs + 3_000L, pts[1].timeMs) // +09:00 付きも同じ時刻軸に乗る
        assertEquals(0L, pts[2].timeMs) // time 無し
        assertEquals(0f, pts[0].accuracyM)
    }

    @Test
    fun `json array of points is read`() {
        val src = SampleTracks.l.points
        val text = AppJson.encodeToString(ListSerializer(TrackPoint.serializer()), src)
        assertEquals(src, TrackFileParser.parse(text))
    }

    @Test
    fun `json track object is read`() {
        val src = SampleTracks.u.points
        val track = Track(id = "t", points = src, startedAt = src.first().timeMs, endedAt = src.last().timeMs)
        val text = AppJson.encodeToString(Track.serializer(), track)
        assertEquals(src, TrackFileParser.parse(text))
    }

    @Test
    fun `json object with only points is read`() {
        val src = SampleTracks.bar.points.take(5)
        val text = "{\"points\":" + AppJson.encodeToString(ListSerializer(TrackPoint.serializer()), src) + "}"
        assertEquals(src, TrackFileParser.parse(text))
    }

    @Test
    fun `leading bom and whitespace are ignored`() {
        val src = SampleTracks.bar.points.take(3)
        val json = "﻿  \n" + AppJson.encodeToString(ListSerializer(TrackPoint.serializer()), src)
        assertEquals(TrackFileParser.Format.JSON, TrackFileParser.detect(json))
        assertEquals(src, TrackFileParser.parse(json))

        val gpxWithLeadingNewline = "﻿\n\n" + gpx
        assertEquals(TrackFileParser.Format.GPX, TrackFileParser.detect(gpxWithLeadingNewline))
        assertEquals(3, TrackFileParser.parse(gpxWithLeadingNewline).size)
    }

    @Test
    fun `unreadable input is rejected with a message`() {
        assertThrows(IllegalArgumentException::class.java) { TrackFileParser.parse("hello") }
        assertThrows(IllegalArgumentException::class.java) { TrackFileParser.parse("<gpx></gpx>") }
        assertThrows(IllegalArgumentException::class.java) { TrackFileParser.parse("[]") }
        assertThrows(IllegalArgumentException::class.java) { TrackFileParser.parse("{\"foo\":1}") }
        assertThrows(IllegalArgumentException::class.java) { TrackFileParser.parse("[{\"lat\":1}]") }
    }

    @Test
    fun `shiftToEndAt keeps spacing and ends at now`() {
        val src = SampleTracks.bar.points
        val now = 1_800_000_000_000L
        val shifted = TrackTime.shiftToEndAt(src, now)
        assertEquals(now, shifted.last().timeMs)
        assertEquals(src.last().timeMs - src.first().timeMs, shifted.last().timeMs - shifted.first().timeMs)
    }

    @Test
    fun `shiftToEndAt assigns walking-speed times when the file has none`() {
        // 北へ約 111 m ずつ（0.001°）の 3 点。時刻は「距離 ÷ 歩く速さ」で振られ、最後の点が now になる
        val src = listOf(TrackPoint(35.0, 139.0), TrackPoint(35.001, 139.0), TrackPoint(35.002, 139.0))
        val now = 1_800_000_000_000L
        val shifted = TrackTime.shiftToEndAt(src, now)
        assertEquals(now, shifted[2].timeMs)
        val gapSec = (shifted[2].timeMs - shifted[1].timeMs) / 1000.0
        val gapSec0 = (shifted[1].timeMs - shifted[0].timeMs) / 1000.0
        assertEquals(111.0 / TrackTime.WALKING_SPEED_MPS, gapSec, 2.0)
        assertEquals(gapSec, gapSec0, 0.5)
        // 同じ場所の点でも時刻は進む
        val same = TrackTime.shiftToEndAt(listOf(TrackPoint(35.0, 139.0), TrackPoint(35.0, 139.0)), now)
        assertEquals(TrackTime.MIN_STEP_MS, same[1].timeMs - same[0].timeMs)
    }

    @Test
    fun `route points without time survive the speed filter after shift`() {
        // 地図アプリで作ったような、角だけの経路（時刻なし、点の間が 100 m 以上）：東 150 m → 北 120 m
        val route = """
            <gpx version="1.1" creator="test"><rte>
              <rtept lat="35.7496" lon="139.8049"/>
              <rtept lat="35.7496" lon="139.80656"/>
              <rtept lat="35.75068" lon="139.80656"/>
            </rte></gpx>
        """.trimIndent()
        val pts = TrackFileParser.parse(route)
        assertEquals(3, pts.size)
        val shifted = TrackTime.shiftToEndAt(pts, 1_800_000_000_000L)
        val r = TrackToPart.convert(shifted)
        assertTrue("$r", r is TrackToPart.Result.Success)
        assertEquals(3, (r as TrackToPart.Result.Success).part.points.size)
    }
}
