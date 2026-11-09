package com.rein4ce.roadcollector.ui.collect

import org.junit.Assert.assertEquals
import org.junit.Test

/** 向いている方位の扇：センサーの方位のならし方 */
class HeadingBeamTest {

    @Test
    fun `the first reading is used as is, within 0 to 360`() {
        assertEquals(90f, smoothHeading(null, 90f), 0.001f)
        assertEquals(350f, smoothHeading(null, -10f), 0.001f) // getOrientation は -180〜180 で来る
    }

    @Test
    fun `moves a quarter of the way toward the target`() {
        assertEquals(10f, smoothHeading(0f, 40f), 0.001f)
        assertEquals(-10f, smoothHeading(0f, -40f), 0.001f)
    }

    @Test
    fun `turns the short way across north`() {
        // 350° から 10° へは、340° 戻らずに 20° 進む（値は 360 を超えてつながる）
        assertEquals(355f, smoothHeading(350f, 10f), 0.001f)
        // 前の値が何周していても、近い回り方で寄せる
        assertEquals(725f, smoothHeading(720f, 20f), 0.001f)
    }

    @Test
    fun `tiny shakes are ignored`() {
        assertEquals(100f, smoothHeading(100f, 100.5f), 0.001f)
        assertEquals(100f, smoothHeading(100f, 460.5f), 0.001f)
    }
}
