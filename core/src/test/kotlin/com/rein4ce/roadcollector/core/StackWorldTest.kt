package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.physics.PartBodyFactory
import com.rein4ce.roadcollector.core.physics.PartGeometry
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
    fun `part body has one fixture per segment plus one per corner`() {
        val bar = SampleParts.byId("sample-bar")!!
        val body = PartBodyFactory.create(bar)
        // 棒：線分 1 本だけ（両端は四角く切った長方形。継ぎ目は無い）
        assertEquals(bar.points.size - 1, body.fixtureCount)
        assertTrue(body.mass.mass > 0)
        // L 字：線分 2 本＋曲がり角の継ぎ目 1 つ
        val l = SampleParts.byId("sample-l")!!
        assertEquals(3, l.points.size)
        assertEquals(3, PartBodyFactory.create(l).fixtureCount)
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
    fun `placed parts are drawn round while their bodies stay angular`() {
        val world = StackWorld()
        val l = SampleParts.all.first { it.points.size >= 3 }
        val body = world.place(l, x = 0.0, y = 2.0, angle = 0.3)
        // 当たり判定は角ばった形（fixture）、描くのは丸い見た目の形
        assertEquals(PartGeometry.collisionShapes(l).size, body.fixtureCount)
        val shapes = world.renderShapes()
        assertEquals(1 + PartGeometry.shapes(l).size, shapes.size) // 台＋見た目の多角形
        // 見た目の多角形は、置いた位置のまわりにある（ボディの位置・向きに動かしてある）
        val drawn = shapes.drop(1).filterIsInstance<StackWorld.RenderShape.Poly>()
        assertTrue(drawn.all { poly -> poly.ys.all { it > 1.0 && it < 3.0 } })
    }

    @Test
    fun `render shapes include the platform`() {
        val world = StackWorld()
        val shapes = world.renderShapes()
        assertEquals(1, shapes.size)
        assertTrue(shapes.first() is StackWorld.RenderShape.Poly)
    }
}
