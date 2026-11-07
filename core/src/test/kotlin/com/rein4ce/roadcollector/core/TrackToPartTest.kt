package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.Projection
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
        // まっすぐ 400 m。±2 m の揺れは ε 10 m より小さいので、形に残らない
        val part = (TrackToPart.convert(walk(listOf(0.0 to 0.0, 0.0 to 400.0))) as TrackToPart.Result.Success).part
        assertEquals(2, part.points.size)
        assertEquals(Kind.BAR, part.kind)
    }

    @Test
    fun `accurate gps keeps the base epsilon`() {
        // 精度 ±3 m の記録は、ε 10 m のまま細かく残す
        val r = TrackToPart.convert(walk(listOf(0.0 to 0.0, 0.0 to 400.0), accuracyM = 3f)) as TrackToPart.Result.Success
        assertEquals(10.0, r.stats.epsilonM, 1e-9)
    }

    @Test
    fun `a walk between 150 and 200 m becomes a part`() {
        // 東へ 100 m、北へ 70 m（約 170 m）。以前の最低 200 m では弾かれていた長さ
        val r = TrackToPart.convert(walk(listOf(0.0 to 0.0, 100.0 to 0.0, 100.0 to 70.0)))
        assertTrue("$r", r is TrackToPart.Result.Success)
        assertEquals(Kind.L, (r as TrackToPart.Result.Success).part.kind)
    }

    @Test
    fun `gps jumps at the start are dropped by the speed filter`() {
        // 記録開始直後に位置が定まらず、1 秒ごとに東西 40 m に飛ぶ点が 5 つ → そのあと普通に 300 m 歩く
        val straight = walk(listOf(0.0 to 0.0, 0.0 to 300.0))
        val t0 = straight.first().timeMs
        val lat0 = straight.first().lat
        val lng0 = straight.first().lng
        val jumps = (5 downTo 1).map { k ->
            // 東西に 40 m ずつ交互に飛ぶ（0.00044° ≈ 40 m）。最後の飛びから歩き始めまでも 40 m / 5 秒 = 8 m/s で歩けない
            TrackPoint(lat0, lng0 + (if (k % 2 == 0) 0.00044 else -0.00044), accuracyM = 8f, timeMs = t0 - k * 1_000L)
        }
        val track = jumps + straight

        val withFilter = TrackToPart.convert(track) as TrackToPart.Result.Success
        val withoutFilter = TrackToPart.convert(track, ConversionParams.DEFAULT.copy(maxSpeedMps = Double.MAX_VALUE)) as TrackToPart.Result.Success

        // 速度フィルタで飛びが落ちて、まっすぐ（棒）になる。フィルタなしだと飛びが形に残る
        assertEquals(Kind.BAR, withFilter.part.kind)
        assertEquals(2, withFilter.part.points.size)
        assertTrue("without=${withoutFilter.part.points.size}", withoutFilter.part.points.size > 2)
    }

    @Test
    fun `rough gps is simplified to its accuracy`() {
        // まっすぐ 400 m を、±6 m 揺れる粗い GPS（精度 15 m）で記録したもの。
        // 細かい ε（8 m）に固定したままだと揺れがいくつも頂点になる（揺れがもっと大きいと頂点の上限で ε が勝手に上がるので、この程度で試す）
        val track = walk(listOf(0.0 to 0.0, 0.0 to 400.0), noiseM = 6.0, accuracyM = 15f)
        val fixed = TrackToPart.convert(track, ConversionParams.DEFAULT.copy(epsilonFollowsAccuracy = false, epsilonM = 8.0)) as TrackToPart.Result.Success
        val adaptive = TrackToPart.convert(track) as TrackToPart.Result.Success

        // ε を精度（15 m）に合わせると、揺れが形にならず、名札もまっすぐ（棒）になる
        assertEquals(15.0, adaptive.stats.epsilonM, 1e-9)
        assertTrue("fixed=${fixed.part.points.size} adaptive=${adaptive.part.points.size}", adaptive.part.points.size < fixed.part.points.size)
        assertTrue("adaptive=${adaptive.part.points.size}", adaptive.part.points.size <= 3)
        assertEquals(Kind.BAR, adaptive.part.kind)
    }

    @Test
    fun `unknown accuracy keeps the base epsilon`() {
        // GPX など、精度が分からない（0）点だけなら、ε 10 m のまま
        val r = TrackToPart.convert(walk(listOf(0.0 to 0.0, 0.0 to 400.0), accuracyM = 0f)) as TrackToPart.Result.Success
        assertEquals(10.0, r.stats.epsilonM, 1e-9)
    }

    @Test
    fun `the place is the center of the shape and its long side`() {
        // 東へ 300 m、北へ 100 m の L 字。形の中心は（150 m 東, 50 m 北）、長辺は 300 m
        val r = TrackToPart.convert(walk(listOf(0.0 to 0.0, 300.0 to 0.0, 300.0 to 100.0), noiseM = 0.0))
        require(r is TrackToPart.Result.Success)
        val place = requireNotNull(r.place)
        val mPerDegLat = 110_574.0
        val mPerDegLng = 111_320.0 * cos(Math.toRadians(35.7496))
        assertEquals(35.7496 + 50.0 / mPerDegLat, place.lat, 1.0 / mPerDegLat) // 1 m 以内
        assertEquals(139.8049 + 150.0 / mPerDegLng, place.lng, 1.0 / mPerDegLng)
        assertEquals(300.0, place.sizeM, 1.0)
        assertEquals(r.stats.longSideM, place.sizeM, 1e-9)
    }

    @Test
    fun `projection back to lat lng returns the original points`() {
        val points = walk(listOf(0.0 to 0.0, 400.0 to 0.0, 400.0 to 250.0))
        val xy = Projection.toMeters(points)
        for (i in points.indices step 17) {
            val (lat, lng) = Projection.toLatLng(points.first(), xy[i])
            assertEquals(points[i].lat, lat, 1e-9)
            assertEquals(points[i].lng, lng, 1e-9)
        }
    }

    @Test
    fun `every sample has a place near the campus and the place stays out of the part`() {
        assertEquals(SampleParts.all.map { it.id }.toSet(), SampleParts.places.keys)
        for ((_, place) in SampleParts.places) {
            assertTrue(abs(place.lat - 35.7496) < 0.01 && abs(place.lng - 139.8049) < 0.01)
            assertTrue(place.sizeM > 50)
        }
        // 場所はパーツの JSON（サーバに送る形）には出ない
        val json = AppJson.encodeToString(Part.serializer(), SampleParts.all.first())
        assertTrue(json, !json.contains("lat") && !json.contains("lng"))
    }

    /**
     * 経路点（メートル）を 4 m 刻みで補間し、±noiseM の揺れを足して GPS の点列にする（SampleTracks と同じ作り方）。
     * accuracyM は全点に同じ値を入れる（0 は「精度が分からない」）。
     */
    private fun walk(
        waypointsM: List<Pair<Double, Double>>,
        seed: Int = 42,
        noiseM: Double = 2.0,
        accuracyM: Float = 8f,
    ): List<TrackPoint> {
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
                val x = x0 + (x1 - x0) * f + (rnd.nextDouble() * 2 - 1) * noiseM
                val y = y0 + (y1 - y0) * f + (rnd.nextDouble() * 2 - 1) * noiseM
                out += TrackPoint(lat0 + y / mPerDegLat, lng0 + x / mPerDegLng, accuracyM = accuracyM, timeMs = t)
                t += 3_000L
            }
        }
        return out
    }
}
