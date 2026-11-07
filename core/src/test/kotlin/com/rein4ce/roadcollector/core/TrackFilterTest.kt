package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.geo.TrackFilter
import com.rein4ce.roadcollector.core.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackFilterTest {

    private val lat0 = 35.7496
    private val lng0 = 139.8049
    private val mPerDegLat = Projection.M_PER_DEG_LAT

    /** 北へ 4 m おき・1 秒おきに歩いた n 点（揺れなし） */
    private fun straight(n: Int): List<TrackPoint> = List(n) { i ->
        TrackPoint(lat0 + i * 4.0 / mPerDegLat, lng0, accuracyM = 8f, timeMs = 1_760_000_000_000L + i * 1_000L)
    }

    /** 点を東へ [meters] m ずらす */
    private fun jumpEast(p: TrackPoint, meters: Double): TrackPoint =
        p.copy(lng = p.lng + meters / Projection.metersPerDegLng(lat0))

    @Test
    fun `an isolated jump in the middle is dropped`() {
        val pts = straight(61).toMutableList()
        pts[30] = jumpEast(pts[30], 40.0)
        val out = TrackFilter.dropSpikes(pts, maxSpeedMps = 5.0)
        assertEquals(60, out.size)
        assertEquals(pts[29], out[29])
        assertEquals(pts[31], out[30])
    }

    @Test
    fun `a jump at the second point does not take the first point with it`() {
        val pts = straight(61).toMutableList()
        pts[1] = jumpEast(pts[1], 40.0)
        val out = TrackFilter.dropSpikes(pts, maxSpeedMps = 5.0)
        assertEquals(60, out.size)
        assertEquals(pts[0], out[0]) // 正しい 1 点目は残る
        assertEquals(pts[2], out[1])
    }

    @Test
    fun `a jump at the first or last point is dropped`() {
        val pts = straight(61).toMutableList()
        pts[0] = jumpEast(pts[0], 40.0)
        pts[60] = jumpEast(pts[60], 40.0)
        val out = TrackFilter.dropSpikes(pts, maxSpeedMps = 5.0)
        assertEquals(59, out.size)
        assertEquals(pts[1], out[0])
        assertEquals(pts[59], out[58])
    }

    @Test
    fun `a burst of jumps at the start is trimmed`() {
        val pts = straight(61).toMutableList()
        for (i in 0 until 4) pts[i] = jumpEast(pts[i], if (i % 2 == 0) 40.0 else -40.0)
        val out = TrackFilter.dropSpikes(pts, maxSpeedMps = 5.0)
        assertEquals(57, out.size)
        assertEquals(pts[4], out[0])
    }

    @Test
    fun `points without time are never treated as jumps`() {
        val pts = straight(10).mapIndexed { i, p -> if (i == 5) jumpEast(p, 40.0).copy(timeMs = 0L) else p }
        assertEquals(10, TrackFilter.dropSpikes(pts, maxSpeedMps = 5.0).size)
    }
}
