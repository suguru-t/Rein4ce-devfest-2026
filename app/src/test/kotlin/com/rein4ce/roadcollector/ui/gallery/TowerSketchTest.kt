package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.model.Tower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「みんな」のタワーの小さな絵に入れる範囲（台とパーツ全部が収まるか） */
class TowerSketchTest {

    private val bar = Part(id = "p1", points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)), kind = Kind.BAR)

    /** 1.0×1.0 の箱いっぱいの輪。回転させると角が置いた点から √2/2 ≒ 0.71 まで届く */
    private val ring = Part(
        id = "p1",
        points = listOf(Pt(-0.5f, -0.5f), Pt(0.5f, -0.5f), Pt(0.5f, 0.5f), Pt(-0.5f, 0.5f)),
        closed = true,
        kind = Kind.RING,
    )

    private fun tower(height: Float, vararg placements: Placement, part: Part = bar) =
        Tower(parts = listOf(part), placements = placements.toList(), height = height, createdAt = 0L)

    @Test
    fun `パーツがなくても台と 1 m ぶんの高さは入る`() {
        val b = sketchBounds(tower(height = 0f))
        assertTrue(b.minX <= -PLATFORM_WIDTH / 2 && b.maxX >= PLATFORM_WIDTH / 2)
        assertTrue(b.minY <= -PLATFORM_THICKNESS)
        assertTrue(b.maxY >= 1f)
    }

    @Test
    fun `右にはみ出して斜めに置いた輪も、角まで範囲に入る`() {
        // 45° に回した輪の角は x = 1.5 + 0.71 まで届く。道の太さの分も含めて入ること
        val b = sketchBounds(tower(height = 0.2f, Placement("p1", x = 1.5f, y = 0.1f, angleRad = (Math.PI / 4).toFloat()), part = ring))
        assertTrue("maxX=${b.maxX}", b.maxX >= 1.5f + 0.71f + 0.075f)
    }

    @Test
    fun `台の端からぶら下がって止まったパーツは、下にも範囲を広げる`() {
        val b = sketchBounds(tower(height = 0.2f, Placement("p1", x = 0.9f, y = -0.9f, angleRad = 0f)))
        assertTrue("minY=${b.minY}", b.minY <= -0.9f - 0.5f)
    }

    @Test
    fun `台の上に乗っているパーツは、下には範囲を広げない`() {
        val b = sketchBounds(tower(height = 0.2f, Placement("p1", x = 0.3f, y = 0.07f, angleRad = 0f)))
        assertEquals(-PLATFORM_THICKNESS - 0.1f, b.minY, 1e-6f)
    }

    @Test
    fun `高いタワーは高さが範囲を決める`() {
        val b = sketchBounds(tower(height = 2.4f, Placement("p1", x = 0f, y = 2.3f, angleRad = 0f)))
        assertTrue("maxY=${b.maxY}", b.maxY >= 2.4f)
        assertEquals(b.maxY - b.minY, b.height, 1e-6f)
    }

    @Test
    fun `広い台で積んだタワーは、台の幅に合わせて範囲を広げ、台の上のパーツで下に広げない（決定 66）`() {
        val wide = tower(height = 0.2f, Placement("p1", x = 1.2f, y = 0.07f, angleRad = 0f)).copy(platformWidth = 2.8f)
        val b = sketchBounds(wide)
        assertTrue(b.minX <= -1.4f && b.maxX >= 1.4f)
        // x = 1.2 は広い台の上（今までの台なら外）なので、下には広げない
        assertTrue(b.minY >= -PLATFORM_THICKNESS - 0.1f - 0.0001f)
        assertTrue(rankingWidthFor(2.8f) > RANKING_WIDTH_M)
    }
}
