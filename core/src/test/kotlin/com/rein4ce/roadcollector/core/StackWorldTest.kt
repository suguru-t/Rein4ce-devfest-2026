package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.physics.PartBodyFactory
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.svg.TowerSvg
import com.rein4ce.roadcollector.core.model.Tower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackWorldTest {

    private fun settle(world: StackWorld, maxSeconds: Double = 10.0): Double {
        var t = 0.0
        while (t < maxSeconds) {
            world.step(1.0 / 60.0)
            t += 1.0 / 60.0
            if (t > 1.0 && world.isSettled()) break
        }
        return t
    }

    @Test
    fun `part body has one fixture per segment plus joints`() {
        val bar = SampleParts.byId("sample-bar")!!
        val body = PartBodyFactory.create(bar)
        // 棒：線分 1 本 + 関節 2 個
        assertEquals(bar.points.size - 1 + bar.points.size, body.fixtureCount)
        assertTrue(body.mass.mass > 0)
    }

    @Test
    fun `a bar dropped flat on the platform settles and has height`() {
        val world = StackWorld()
        val bar = SampleParts.byId("sample-bar")!!
        // 棒は縦長（北向き）なので 90° 回して横に寝かせる
        world.place(bar, x = 0.0, y = 1.0, angle = Math.PI / 2)
        val t = settle(world)
        assertTrue("should settle within 10 s (took $t)", world.isSettled())
        assertTrue(world.removeFallen().isEmpty())
        val h = world.height()
        assertTrue("height should be about the thickness, was $h", h in 0.10..0.30)
    }

    @Test
    fun `a part dropped far off the platform falls and is removed`() {
        val world = StackWorld()
        val l = SampleParts.byId("sample-l")!!
        world.place(l, x = 3.0, y = 1.0, angle = 0.0)
        settle(world, maxSeconds = 6.0)
        val fallen = world.removeFallen()
        assertEquals(1, fallen.size)
        assertEquals(0.0, world.height(), 1e-9)
    }

    @Test
    fun `stacking several parts increases height`() {
        val world = StackWorld()
        var y = 1.0
        for (part in SampleParts.all.take(3)) {
            world.place(part, x = 0.0, y = y, angle = 0.0)
            settle(world)
            world.removeFallen()
            y = world.height() + 1.0
        }
        assertTrue("some parts should remain", world.placedParts.isNotEmpty())
        assertTrue("height ${world.height()}", world.height() > 0.3)
        assertEquals(world.placedParts.size, world.placements().size)

        // SVG も壊れていないこと
        val tower = Tower(parts = world.placedParts, placements = world.placements(), height = world.height().toFloat(), createdAt = 0L)
        val svg = TowerSvg.render(tower)
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.endsWith("</svg>"))
    }

    @Test
    fun `render shapes include the platform`() {
        val world = StackWorld()
        val shapes = world.renderShapes()
        assertEquals(1, shapes.size)
        assertTrue(shapes.first() is StackWorld.RenderShape.Poly)
    }
}
