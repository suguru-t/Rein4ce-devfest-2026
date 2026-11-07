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
    fun `高いタワーは高さが範囲を決める`() {
        val b = sketchBounds(tower(height = 2.4f, Placement("p1", x = 0f, y = 2.3f, angleRad = 0f)))
        assertTrue("maxY=${b.maxY}", b.maxY >= 2.4f)
        assertEquals(b.maxY - b.minY, b.height, 1e-6f)
    }
}
