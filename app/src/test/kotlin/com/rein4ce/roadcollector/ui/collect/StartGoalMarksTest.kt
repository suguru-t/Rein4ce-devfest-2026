package com.rein4ce.roadcollector.ui.collect

import com.rein4ce.roadcollector.core.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** スタートとゴールの旗を左右に開くかを決める、2 点の距離 */
class StartGoalMarksTest {

    private fun pt(lat: Double, lng: Double) = TrackPoint(lat = lat, lng = lng)

    @Test
    fun `rough distance is close to the real one at Tokyo`() {
        // 東京（北緯 35.7°）で、北へ 0.001°（約 111 m）と東へ 0.001°（約 90 m）
        assertEquals(110.5, roughMeters(pt(35.7, 139.8), pt(35.701, 139.8)), 1.0)
        assertEquals(90.4, roughMeters(pt(35.7, 139.8), pt(35.7, 139.801)), 1.0)
    }

    @Test
    fun `the flag width on the ground follows the map zoom`() {
        // 北緯 35.75°（北千住）で、旗の幅 22dp は 拡大 16 で約 43 m。拡大が 1 上がるごとに半分
        assertEquals(42.6, flagWidthMeters(35.75, 16f), 0.5)
        assertEquals(21.3, flagWidthMeters(35.75, 17f), 0.5)
        assertEquals(85.2, flagWidthMeters(35.75, 15f), 1.0)
    }

    @Test
    fun `coming back near the start spreads the flags, walking away does not`() {
        val start = pt(35.7, 139.8)
        // 一周して 10 m 手前で止めた：最初の拡大（16）でも、寄った拡大（18）でも開く
        assertTrue(roughMeters(start, pt(35.70009, 139.8)) < flagWidthMeters(start.lat, 16f))
        assertTrue(roughMeters(start, pt(35.70009, 139.8)) < flagWidthMeters(start.lat, 18f))
        // 35 m 手前で止めた：拡大 16 では旗が重なるので開き、拡大 17 では離れて見えるので開かない
        val near = pt(35.700316, 139.8)
        assertTrue(roughMeters(start, near) < flagWidthMeters(start.lat, 16f))
        assertTrue(roughMeters(start, near) >= flagWidthMeters(start.lat, 17f))
        // 100 m 先で止めた
        assertTrue(roughMeters(start, pt(35.7009, 139.8)) >= flagWidthMeters(start.lat, 16f))
    }
}
