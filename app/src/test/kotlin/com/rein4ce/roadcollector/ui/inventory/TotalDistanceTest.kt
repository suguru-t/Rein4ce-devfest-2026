package com.rein4ce.roadcollector.ui.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** コレクションの見出しのメーターに出す合計距離の形（1 km からは km、小数 1 桁） */
class TotalDistanceTest {

    @Test
    fun `under 1 km stays in meters`() {
        assertEquals(TotalDistance("0", "m"), totalDistance(0))
        assertEquals(TotalDistance("834", "m"), totalDistance(834))
        assertEquals(TotalDistance("999", "m"), totalDistance(999))
    }

    @Test
    fun `from 1 km shows km with one decimal`() {
        assertEquals(TotalDistance("1.0", "km"), totalDistance(1_000))
        assertEquals(TotalDistance("1.3", "km"), totalDistance(1_349))
        assertEquals(TotalDistance("2.1", "km"), totalDistance(2_115))
        assertEquals(TotalDistance("12.3", "km"), totalDistance(12_345))
    }

    @Test
    fun `the decimal is cut off, not rounded up`() {
        // 歩いた分より多くは見せない（1,999 m を 2.0 km とは出さない）
        assertEquals(TotalDistance("1.9", "km"), totalDistance(1_999))
    }

    @Test
    fun `no thousands separator`() {
        assertFalse(totalDistance(123_456).number.contains(','))
        assertEquals("123.4", totalDistance(123_456).number)
    }
}
