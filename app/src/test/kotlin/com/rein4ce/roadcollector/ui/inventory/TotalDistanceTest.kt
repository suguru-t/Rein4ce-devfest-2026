package com.rein4ce.roadcollector.ui.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** コレクションの見出しのメーターに出す合計距離の形（1 km からは km、小数 1 桁） */
class TotalDistanceTest {

    @Test
    fun `under 1 km stays in meters`() {
        assertEquals(TotalDistance("0", DistanceUnit.M), totalDistance(0))
        assertEquals(TotalDistance("834", DistanceUnit.M), totalDistance(834))
        assertEquals(TotalDistance("999", DistanceUnit.M), totalDistance(999))
    }

    @Test
    fun `from 1 km shows km with one decimal`() {
        assertEquals(TotalDistance("1.0", DistanceUnit.KM), totalDistance(1_000))
        assertEquals(TotalDistance("1.3", DistanceUnit.KM), totalDistance(1_349))
        assertEquals(TotalDistance("2.1", DistanceUnit.KM), totalDistance(2_115))
        assertEquals(TotalDistance("12.3", DistanceUnit.KM), totalDistance(12_345))
    }

    @Test
    fun `the decimal is cut off, not rounded up`() {
        // 歩いた分より多くは見せない（1,999 m を 2.0 km とは出さない）
        assertEquals(TotalDistance("1.9", DistanceUnit.KM), totalDistance(1_999))
    }

    @Test
    fun `no thousands separator`() {
        assertFalse(totalDistance(123_456).number.contains(','))
        assertEquals("123.4", totalDistance(123_456).number)
    }

    @Test
    fun `the unit is read out in words that match the screen`() {
        // 読み上げ（TalkBack）は、画面の単位と同じ単位を言葉で読む
        assertEquals("km" to "キロメートル", totalDistance(2_115).unit.let { it.label to it.spoken })
        assertEquals("m" to "メートル", totalDistance(834).unit.let { it.label to it.spoken })
    }
}
