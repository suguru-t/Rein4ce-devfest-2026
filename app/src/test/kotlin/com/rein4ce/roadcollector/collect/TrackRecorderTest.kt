package com.rein4ce.roadcollector.collect

import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.data.PartOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 記録中に速すぎたら自動で止める（TrackRecorder） */
class TrackRecorderTest {

    private val lat0 = 35.7496
    private val lng0 = 139.8049
    private val t0 = 1_760_000_000_000L

    /** 東へまっすぐ、[speedMps] で [seconds] 秒ぶんの点（2 秒おき。実機の GPS と同じ間隔）。[fromX] m 地点・[fromT] から */
    private fun east(speedMps: Double, seconds: Int, fromX: Double = 0.0, fromT: Long = t0): List<TrackPoint> =
        (0..seconds / 2).map { i ->
            val x = fromX + speedMps * 2 * i
            TrackPoint(lat0, lng0 + x / Projection.metersPerDegLng(lat0), accuracyM = 8f, timeMs = fromT + 2_000L * i)
        }

    @Test
    fun `a walk keeps recording`() {
        TrackRecorder.start(PartOrigin.WALK)
        east(1.3, 120).forEach { TrackRecorder.add(it) }
        assertTrue(TrackRecorder.isRecording.value)
        assertNull(TrackRecorder.tooFast.value)
        assertEquals(61, TrackRecorder.points.value.size)
    }

    @Test
    fun `a jog keeps recording`() {
        TrackRecorder.start(PartOrigin.WALK)
        east(3.3, 120).forEach { TrackRecorder.add(it) } // 時速 12 km
        assertTrue(TrackRecorder.isRecording.value)
        assertNull(TrackRecorder.tooFast.value)
    }

    @Test
    fun `a bicycle ride stops the recording and keeps the walk before it`() {
        TrackRecorder.start(PartOrigin.WALK)
        val walk = east(1.3, 120)
        walk.forEach { TrackRecorder.add(it) }
        val ride = east(4.4, 120, fromX = 1.3 * 120, fromT = walk.last().timeMs).drop(1) // 時速 16 km
        var stoppedAt: Long? = null
        for (p in ride) {
            TrackRecorder.add(p)
            if (!TrackRecorder.isRecording.value) { stoppedAt = p.timeMs; break }
        }

        // 30 秒の窓が自転車で埋まる前（上限は時速 14 km なので、歩きが少し混ざっていても超える）に超え始め、
        // 10 秒続いたところで止まる
        val at = requireNotNull(stoppedAt) { "should have stopped" }
        val riding = at - walk.last().timeMs
        assertTrue("stopped after ${riding / 1000} s of riding", riding in 30_000L..50_000L)
        val stop = requireNotNull(TrackRecorder.tooFast.value) { "reason should be set" }
        assertTrue("speed ${stop.speedMps}", stop.speedMps > ConversionParams.DEFAULT.walkMaxSpeedMps)
        // 残した点は、超え始めた時刻の 30 秒前より前だけ。超え始めるまでに自転車で 30 秒近く進んでいるので、
        // 切り落とすのはほぼ自転車の分だけで、歩きは最後の数秒を除いて残る
        assertTrue("kept ${stop.keptPoints.size} of ${walk.size}", stop.keptPoints.size >= walk.size - 5)
        assertTrue(stop.keptPoints.none { it in ride })
        // 止まったあとの点は無視される
        TrackRecorder.add(ride.last())
        assertFalse(TrackRecorder.points.value.contains(ride.last()))
    }

    @Test
    fun `a single gps spike does not stop the recording`() {
        // 歩きの途中で、GPS が 1 点だけ 90 m 北に飛ぶ（高架やビルの近くで起きる）。次の点で元に戻るので止めない。
        // 飛んだ点が 30 秒後に窓の始点になったときも、1〜2 点だけ速く見えるが続かないので止めない
        TrackRecorder.start(PartOrigin.WALK)
        val before = east(1.3, 60)
        before.forEach { TrackRecorder.add(it) }
        val last = before.last()
        TrackRecorder.add(last.copy(lat = lat0 + 90.0 / Projection.M_PER_DEG_LAT, timeMs = last.timeMs + 1_000L, accuracyM = 10f))
        val after = east(1.3, 90, fromX = 1.3 * 60, fromT = last.timeMs + 2_000L)
        after.forEach {
            TrackRecorder.add(it)
            assertTrue("stopped at ${(it.timeMs - last.timeMs) / 1000} s after the spike", TrackRecorder.isRecording.value)
        }
        assertNull(TrackRecorder.tooFast.value)
    }

    @Test
    fun `a car ride stops the recording even though the spike filter would drop its points`() {
        TrackRecorder.start(PartOrigin.WALK)
        east(10.0, 60).forEach { TrackRecorder.add(it) } // 時速 36 km。飛びの上限 5 m/s を超え続ける
        assertFalse(TrackRecorder.isRecording.value)
        assertTrue(requireNotNull(TrackRecorder.tooFast.value).speedMps > 9.0)
    }

    @Test
    fun `start clears the reason and consume clears it too`() {
        TrackRecorder.start(PartOrigin.WALK)
        east(10.0, 60).forEach { TrackRecorder.add(it) }
        assertNotNull(TrackRecorder.tooFast.value)
        TrackRecorder.consumeTooFast()
        assertNull(TrackRecorder.tooFast.value)

        TrackRecorder.start(PartOrigin.WALK)
        east(10.0, 60).forEach { TrackRecorder.add(it) }
        assertNotNull(TrackRecorder.tooFast.value)
        TrackRecorder.start(PartOrigin.WALK)
        assertNull(TrackRecorder.tooFast.value)
        assertTrue(TrackRecorder.isRecording.value)
    }

    @Test
    fun `出どころは記録の側が覚えていて、止めたときに点と一緒に返す`() {
        // 画面（ViewModel）が作り直されても、本当に歩いた記録は「歩いた」のまま（#87 のレビュー）
        TrackRecorder.start(PartOrigin.WALK)
        val walk = east(1.3, 20)
        walk.forEach { TrackRecorder.add(it) }
        assertEquals(TrackRecorder.Recording(walk, PartOrigin.WALK), TrackRecorder.stop())

        // 次の記録は、前の出どころを持ち越さない
        TrackRecorder.start(PartOrigin.DEMO)
        assertEquals(PartOrigin.DEMO, TrackRecorder.stop().origin)
        TrackRecorder.start(PartOrigin.FILE)
        assertEquals(PartOrigin.FILE, TrackRecorder.stop().origin)
    }

    @Test
    fun `速すぎて自動で止めたときも、出どころを一緒に渡す`() {
        TrackRecorder.start(PartOrigin.DEMO)
        east(10.0, 60).forEach { TrackRecorder.add(it) }
        assertEquals(PartOrigin.DEMO, requireNotNull(TrackRecorder.tooFast.value).origin)
        TrackRecorder.consumeTooFast()
    }
}
