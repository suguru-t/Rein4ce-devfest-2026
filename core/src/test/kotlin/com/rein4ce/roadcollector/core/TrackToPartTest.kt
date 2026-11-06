package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.TrackToPart
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.sample.SampleTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

class TrackToPartTest {

    @Test
    fun `sample tracks convert to the expected kinds`() {
        for (track in SampleTracks.all) {
            val r = TrackToPart.convert(track.points, ConversionParams.DEFAULT, id = track.id)
            assertTrue("${track.id} should convert: $r", r is TrackToPart.Result.Success)
            val part = (r as TrackToPart.Result.Success).part
            assertEquals("${track.id} kind", track.expectedKind, part.kind)
            assertTrue("${track.id} vertices <= 12", part.points.size <= ConversionParams.DEFAULT.maxVertices)
        }
    }

    @Test
    fun `normalized part has long side 1 and is centered`() {
        for (part in SampleParts.all) {
            val xs = part.points.map { it.x }
            val ys = part.points.map { it.y }
            val w = xs.max() - xs.min()
            val h = ys.max() - ys.min()
            assertEquals("${part.id} long side", 1.0, max(w, h).toDouble(), 1e-4)
            assertTrue("${part.id} centered x", abs((xs.max() + xs.min()) / 2) < 1e-4)
            assertTrue("${part.id} centered y", abs((ys.max() + ys.min()) / 2) < 1e-4)
        }
    }

    @Test
    fun `too short walk is rejected`() {
        val pts = SampleTracks.bar.points.take(10)
        val r = TrackToPart.convert(pts, ConversionParams.DEFAULT)
        assertTrue(r is TrackToPart.Result.TooShort)
    }

    @Test
    fun `bad accuracy points are dropped`() {
        val noisy = SampleTracks.l.points.mapIndexed { i, p ->
            if (i % 3 == 0) p.copy(lat = p.lat + 0.01, accuracyM = 500f) else p
        }
        val r = TrackToPart.convert(noisy, ConversionParams.DEFAULT)
        assertTrue(r is TrackToPart.Result.Success)
        assertEquals(com.rein4ce.roadcollector.core.model.Kind.L, (r as TrackToPart.Result.Success).part.kind)
    }

    @Test
    fun `part survives json round trip`() {
        val part = SampleParts.all.first()
        val json = AppJson.encodeToString(Part.serializer(), part)
        val back = AppJson.decodeFromString(Part.serializer(), json)
        assertEquals(part, back)
    }

    @Test
    fun `empty track is handled`() {
        val r = TrackToPart.convert(emptyList<TrackPoint>())
        assertTrue(r is TrackToPart.Result.TooFewPoints)
    }

    @Test
    fun `a winding walk keeps its corners`() {
        // 東へ 40 m・北へ 40 m を 8 回くり返す階段状の散歩（始点・角 15 個・終点の 17 点）
        val waypoints = ArrayList<Pair<Double, Double>>()
        waypoints += 0.0 to 0.0
        for (i in 0 until 8) {
            waypoints += (i + 1) * 40.0 to i * 40.0
            waypoints += (i + 1) * 40.0 to (i + 1) * 40.0
        }
        val track = walk(waypoints)

        val now = (TrackToPart.convert(track) as TrackToPart.Result.Success).part
        val before = (TrackToPart.convert(track, ConversionParams(epsilonM = 15.0, maxVertices = 12)) as TrackToPart.Result.Success).part

        // 以前の設定（頂点 12 個まで）では角が削られていたが、今は 17 点すべて残る
        assertTrue("before: ${before.points.size}", before.points.size <= 12)
        assertEquals(17, now.points.size)
    }

    @Test
    fun `gps jitter on a straight walk does not become a wiggle`() {
        // まっすぐ 400 m。±2 m の揺れは ε 8 m より小さいので、形に残らない
        val part = (TrackToPart.convert(walk(listOf(0.0 to 0.0, 0.0 to 400.0))) as TrackToPart.Result.Success).part
        assertEquals(2, part.points.size)
        assertEquals(Kind.BAR, part.kind)
    }

    /** 経路点（メートル）を 4 m 刻みで補間し、±2 m の揺れを足して GPS の点列にする（SampleTracks と同じ作り方）。 */
    private fun walk(waypointsM: List<Pair<Double, Double>>, seed: Int = 42): List<TrackPoint> {
        val rnd = Random(seed)
        val lat0 = 35.7496
        val lng0 = 139.8049
        val mPerDegLat = 110_574.0
        val mPerDegLng = 111_320.0 * cos(Math.toRadians(lat0))
        val out = ArrayList<TrackPoint>()
        var t = 1_760_000_000_000L
        for (i in 1 until waypointsM.size) {
            val (x0, y0) = waypointsM[i - 1]
            val (x1, y1) = waypointsM[i]
            val n = (hypot(x1 - x0, y1 - y0) / 4.0).toInt().coerceAtLeast(1)
            for (k in (if (i == 1) 0 else 1)..n) {
                val f = k.toDouble() / n
                val x = x0 + (x1 - x0) * f + (rnd.nextDouble() * 2 - 1) * 2.0
                val y = y0 + (y1 - y0) * f + (rnd.nextDouble() * 2 - 1) * 2.0
                out += TrackPoint(lat0 + y / mPerDegLat, lng0 + x / mPerDegLng, accuracyM = 8f, timeMs = t)
                t += 3_000L
            }
        }
        return out
    }
}
