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

    private fun tower(height: Float, vararg placements: Placement) =
        Tower(parts = listOf(bar), placements = placements.toList(), height = height, createdAt = 0L)

    @Test
    fun `パーツがなくても台と 1 m ぶんの高さは入る`() {
        val b = sketchBounds(tower(height = 0f))
        assertTrue(b.minX <= -PLATFORM_WIDTH / 2 && b.maxX >= PLATFORM_WIDTH / 2)
        assertTrue(b.minY <= -PLATFORM_THICKNESS)
        assertTrue(b.maxY >= 1f)
    }

    @Test
    fun `右にはみ出して置いたパーツも範囲に入る`() {
        val b = sketchBounds(tower(height = 0.2f, Placement("p1", x = 1.5f, y = 0.1f, angleRad = 0f)))
        assertTrue("maxX=${b.maxX}", b.maxX >= 1.5f + 0.5f)
    }

    @Test
    fun `高いタワーは高さが範囲を決める`() {
        val b = sketchBounds(tower(height = 2.4f, Placement("p1", x = 0f, y = 2.3f, angleRad = 0f)))
        assertTrue("maxY=${b.maxY}", b.maxY >= 2.4f)
        assertEquals(b.maxY - b.minY, b.height, 1e-6f)
    }
}
