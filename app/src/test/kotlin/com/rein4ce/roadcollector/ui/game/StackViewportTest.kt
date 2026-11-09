package com.rein4ce.roadcollector.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 積む画面の座標の変換と、カメラ（狙っている間は大きさを変えずに上げる・置き切ったら全体が入るように引く）（#42） */
class StackViewportTest {
    /** 1080×2400 の端末で、情報と操作の段を除いたキャンバスの大きさの目安 [px] */
    private val w = 1080f
    private val h = 1575f

    private fun focus(minX: Float = -0.7f, maxX: Float = 0.7f, minY: Float = -0.3f, maxY: Float = 0f) =
        WorldRect(minX, maxX, minY, maxY)

    // ---- 狙っている間と落下中（follow）：大きさは変えない ----

    @Test
    fun `while aiming at a low tower the camera stays where it was`() {
        // 台の上 1.5 m に出てくる、半径 0.86 m のパーツ
        val vp = StackViewport.follow(w, h, top = 1.5f + 0.86f)
        assertEquals(1f, vp.zoom, 0f)
        assertEquals(0f, vp.cameraY, 0f)
        assertEquals(h * StackViewport.PLATFORM_ANCHOR, vp.toScreenY(0f), 1e-3f) // 台の上面はいつもの位置
    }

    @Test
    fun `while aiming at a tall tower the camera rises just enough and never zooms out`() {
        val top = 9f
        val vp = StackViewport.follow(w, h, top)
        assertEquals(1f, vp.zoom, 0f) // 縮めない（同じ指の動きで、いつも同じだけ動く）
        assertTrue(vp.cameraY > 0f)
        // 必要なぶんだけ上げる（余白の上が、ちょうどキャンバスの上端）
        assertEquals(0f, vp.toScreenY(top + StackViewport.MARGIN_Y_M), 1f)
    }

    @Test
    fun `on a small phone the next part and the top of the tower stay in view without zooming out`() {
        // 360×640dp 級の端末では、キャンバスの高さが幅の 0.7 倍ほどしかない（#63 のレビュー）
        val sw = 720f
        val sh = 500f
        val towerTop = 2.0f
        val spawnTop = towerTop + 1.5f + 0.86f // 高さ 2 m の塔の上に出てくるパーツの、いちばん上
        val vp = StackViewport.follow(sw, sh, spawnTop)
        assertEquals(1f, vp.zoom, 0f)
        assertTrue(vp.toScreenY(spawnTop) >= 0f) // 次のパーツの上が切れない
        assertTrue(vp.toScreenY(towerTop) in 0f..sh) // 塔のてっぺんも見える（台はキャンバスの下へ外れてよい）
    }

    // ---- 置き切ったあと（overview / zoomToFit）：全体が入るように引く ----

    @Test
    fun `a tower that fits keeps the default size`() {
        val vp = StackViewport.overview(w, h, focus(minX = -1.3f, maxX = 1.3f, maxY = 2f))
        assertEquals(1f, vp.zoom, 0f)
        assertEquals(0f, vp.cameraY, 0f)
    }

    @Test
    fun `a tall tower is zoomed out until its top fits and the platform stays in place`() {
        val top = 9f
        val vp = StackViewport.overview(w, h, focus(maxY = top))
        assertTrue(vp.zoom < 1f)
        assertEquals(h * StackViewport.PLATFORM_ANCHOR, vp.toScreenY(0f), 1e-3f) // 台の上面は動かさない
        assertTrue("top at ${vp.toScreenY(top)}", vp.toScreenY(top) >= 0f)
        // 必要なぶんだけ縮める（余白の上が、ちょうどキャンバスの上端）
        assertEquals(0f, vp.toScreenY(top + StackViewport.MARGIN_Y_M), 1f)
    }

    @Test
    fun `a wide focus is zoomed out so both sides fit around the centre`() {
        val vp = StackViewport.overview(w, h, focus(minX = -3.5f, maxX = 1f))
        assertTrue(vp.toScreenX(-3.5f) >= 0f)
        assertEquals(w / 2, vp.toScreenX(0f), 1e-3f) // 真ん中は x=0 のまま
    }

    @Test
    fun `a part hanging far below the platform is kept in view`() {
        val vp = StackViewport.overview(w, h, focus(minY = -2.5f))
        assertTrue(vp.toScreenY(-2.5f) <= h)
    }

    @Test
    fun `it never zooms in beyond the default and never below the minimum`() {
        assertEquals(1f, StackViewport.zoomToFit(w, h, focus()), 0f)
        assertEquals(StackViewport.MIN_ZOOM, StackViewport.zoomToFit(w, h, focus(maxY = 1000f)), 0f)
        assertEquals(1f, StackViewport.zoomToFit(0f, 0f, focus(maxY = 1000f)), 0f) // キャンバスの大きさが決まる前
    }

    // ---- 変換 ----

    @Test
    fun `screen and world positions round trip at any zoom and camera height`() {
        for ((z, cameraY) in listOf(1f to 0f, 1f to 3.2f, 0.6f to 0f, 0.25f to 0f)) {
            val vp = StackViewport(w, h, z, cameraY)
            for ((x, y) in listOf(0f to 0f, -2.3f to 4.1f, 1.7f to -0.9f)) {
                assertEquals(x, vp.toWorldX(vp.toScreenX(x)), 1e-4f)
                assertEquals(y, vp.toWorldY(vp.toScreenY(y)), 1e-4f)
            }
        }
    }

    @Test
    fun `a drag moves the ghost by the same distance on screen at any zoom and camera height`() {
        for ((z, cameraY) in listOf(1f to 0f, 1f to 3.2f, 0.5f to 0f, 0.2f to 0f)) {
            val vp = StackViewport(w, h, z, cameraY)
            val ghostX = 0.4f
            val dragPx = 137f
            val moved = ghostX + vp.dragToMeters(dragPx)
            assertEquals(dragPx, vp.toScreenX(moved) - vp.toScreenX(ghostX), 1e-3f)
        }
    }
}
