package com.rein4ce.roadcollector.ui.collect

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.RoundingMode
import java.util.Locale

/** 速さの表示（採集画面の状態行と結果ダイアログ） */
class SpeedFormatTest {

    @Test
    fun `speed is shown in km per hour with one decimal`() {
        assertEquals("時速 8.1 km", CollectViewModel.kmh(2.25))
        assertEquals("時速 7.9 km", CollectViewModel.kmh(2.2)) // 歩きの上限（7.92）
        assertEquals("時速 36.0 km", CollectViewModel.kmh(10.0))
    }

    @Test
    fun `a speed just over the limit never reads the same as the limit`() {
        // 上限 2.2 m/s（時速 7.92 km）のすぐ上、2.203 m/s（時速 7.93 km）で弾かれたとき。
        // 弾かれた速さは切り上げ、上限は切り捨てなので、「最高 時速 8.0 km」「時速 7.9 km まで」と読める
        assertEquals("時速 8.0 km", CollectViewModel.kmh(2.203, RoundingMode.CEILING))
        assertEquals("時速 7.9 km", CollectViewModel.kmh(2.2, RoundingMode.FLOOR))
    }

    @Test
    fun `the decimal point does not depend on the device language`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY) // 小数点が「,」の言語
            assertEquals("時速 8.1 km", CollectViewModel.kmh(2.25))
        } finally {
            Locale.setDefault(before)
        }
    }
}
