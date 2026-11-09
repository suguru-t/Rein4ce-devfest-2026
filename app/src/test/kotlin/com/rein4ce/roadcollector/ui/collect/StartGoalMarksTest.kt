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
    fun `coming back near the start spreads the flags, walking away does not`() {
        val start = pt(35.7, 139.8)
        // 一周して 10 m 手前で止めた
        assertTrue(roughMeters(start, pt(35.70009, 139.8)) < FLAG_SPREAD_METERS)
        // 100 m 先で止めた
        assertTrue(roughMeters(start, pt(35.7009, 139.8)) >= FLAG_SPREAD_METERS)
    }
}
