package com.rein4ce.roadcollector.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 積む画面の座標の変換と、全体が入る倍率（#42） */
class StackViewportTest {
    /** 1080×2400 の端末で、情報と操作の段を除いたキャンバスの大きさの目安 [px] */
    private val w = 1080f
    private val h = 1575f

    private fun focus(minX: Float = -0.7f, maxX: Float = 0.7f, minY: Float = -0.3f, maxY: Float = 0f) =
        WorldRect(minX, maxX, minY, maxY)

    @Test
    fun `everything that fits keeps the default size`() {
        // 台と、塔の上 1.5 m に出てきて左右 ±1.5 m まで動かせる、半径 0.86 m のパーツ
        val z = StackViewport.zoomToFit(w, h, focus(minX = -2.36f, maxX = 2.36f, maxY = 1.5f + 0.86f))
        assertEquals(1f, z, 0f)
    }

    @Test
    fun `a tall tower is zoomed out until its top fits and the platform stays in place`() {
        val top = 9f
        val z = StackViewport.zoomToFit(w, h, focus(maxY = top))
        assertTrue(z < 1f)
        val vp = StackViewport(w, h, z)
        assertEquals(h * StackViewport.PLATFORM_ANCHOR, vp.toScreenY(0f), 1e-3f) // 台の上面は動かさない
        assertTrue("top at ${vp.toScreenY(top)}", vp.toScreenY(top) >= 0f)
        // 必要なぶんだけ縮める（余白の上が、ちょうどキャンバスの上端）
        assertEquals(0f, vp.toScreenY(top + StackViewport.MARGIN_Y_M), 1f)
    }

    @Test
    fun `a wide focus is zoomed out so both sides fit around the centre`() {
        val z = StackViewport.zoomToFit(w, h, focus(minX = -3.5f, maxX = 1f))
        val vp = StackViewport(w, h, z)
        assertTrue(vp.toScreenX(-3.5f) >= 0f)
        assertEquals(w / 2, vp.toScreenX(0f), 1e-3f) // 真ん中は x=0 のまま
    }

    @Test
    fun `a part hanging far below the platform is kept in view`() {
        val z = StackViewport.zoomToFit(w, h, focus(minY = -2.5f))
        val vp = StackViewport(w, h, z)
        assertTrue(vp.toScreenY(-2.5f) <= h)
    }

    @Test
    fun `it never zooms in beyond the default and never below the minimum`() {
        assertEquals(1f, StackViewport.zoomToFit(w, h, focus()), 0f)
        assertEquals(StackViewport.MIN_ZOOM, StackViewport.zoomToFit(w, h, focus(maxY = 1000f)), 0f)
        assertEquals(1f, StackViewport.zoomToFit(0f, 0f, focus(maxY = 1000f)), 0f) // キャンバスの大きさが決まる前
    }

    @Test
    fun `screen and world positions round trip at any zoom`() {
        for (z in listOf(1f, 0.6f, 0.25f)) {
            val vp = StackViewport(w, h, z)
            for ((x, y) in listOf(0f to 0f, -2.3f to 4.1f, 1.7f to -0.9f)) {
                assertEquals(x, vp.toWorldX(vp.toScreenX(x)), 1e-4f)
                assertEquals(y, vp.toWorldY(vp.toScreenY(y)), 1e-4f)
            }
        }
    }

    @Test
    fun `a drag moves the ghost by the same distance on screen at any zoom`() {
        for (z in listOf(1f, 0.5f, 0.2f)) {
            val vp = StackViewport(w, h, z)
            val ghostX = 0.4f
            val dragPx = 137f
            val moved = ghostX + vp.dragToMeters(dragPx)
            assertEquals(dragPx, vp.toScreenX(moved) - vp.toScreenX(ghostX), 1e-3f)
        }
    }

    @Test
    fun `a short canvas on a small phone is zoomed out to show the next part and the platform`() {
        // 360×640dp 級の端末では、キャンバスの高さが幅の 0.7 倍ほどしかない（#63 のレビュー）
        val sw = 720f
        val sh = 500f
        val spawnTop = 2.0f + 1.5f + 0.86f // 高さ 2 m の塔の上に出てくるパーツの、いちばん上
        val z = StackViewport.zoomToFit(sw, sh, focus(minX = -2.36f, maxX = 2.36f, maxY = spawnTop))
        val vp = StackViewport(sw, sh, z)
        assertTrue(vp.toScreenY(spawnTop) >= 0f)
        assertTrue(vp.toScreenY(-0.3f) <= sh) // 台の下面も入る
    }
}
