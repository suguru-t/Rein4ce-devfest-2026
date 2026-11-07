package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.physics.PartBodyFactory
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.svg.TowerSvg
import com.rein4ce.roadcollector.core.weather.Sky
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
    fun `rainy and snowy parts are slippery`() {
        assertEquals(Grip.NORMAL, Grip.of(null))
        assertEquals(Grip.NORMAL, Grip.of(Sky.CLEAR))
        assertEquals(Grip.CLOUDY, Grip.of(Sky.CLOUDY))
        assertEquals(Grip.FOGGY, Grip.of(Sky.FOG))
        assertEquals(Grip.DAMP, Grip.of(Sky.DRIZZLE))
        assertEquals(Grip.WET, Grip.of(Sky.RAIN))
        assertEquals(Grip.WET, Grip.of(Sky.THUNDER))
        assertEquals(Grip.ICY, Grip.of(Sky.SNOW))
        // 晴れ → くもり → 霧 → 霧雨 → 雨 → 雪 の順に滑りやすい（摩擦が小さい）。晴れは前と同じ値
        val order = listOf(Grip.NORMAL, Grip.CLOUDY, Grip.FOGGY, Grip.DAMP, Grip.WET, Grip.ICY)
        assertTrue(order.zipWithNext().all { (a, b) -> a.friction > b.friction })
        assertEquals(PartBodyFactory.DEFAULT_MATERIAL.friction, Grip.NORMAL.friction, 0.0)
        // 一言を出すのは、はっきり滑る霧雨・雨・雪だけ
        assertEquals(listOf(Grip.DAMP, Grip.WET, Grip.ICY), order.filter { it.label != null })

        // 止まった棒を、横に同じ速さで押す。
        // 晴れの棒の上に乗せた棒なら、雪の日の棒のほうが遠くまで滑る。台の上に直接置いた棒なら、雪の日でも晴れと同じ（台は滑らない）
        fun slide(grip: Grip, onBar: Boolean): Double {
            val world = StackWorld()
            val bar = SampleParts.byId("sample-bar")!!
            if (onBar) {
                world.place(bar, x = 0.0, y = 0.3, angle = Math.PI / 2)
                settle(world)
            }
            val body = world.place(bar, x = -0.2, y = world.height() + 0.3, angle = Math.PI / 2, grip = grip)
            assertTrue(body.fixtures.all { it.friction == grip.friction })
            settle(world)
            val start = body.worldCenter.x
            body.setAtRest(false) // 止まったボディは、起こさないと押しても動かない
            body.setLinearVelocity(1.5, 0.0)
            repeat(120) { world.step(1.0 / 60.0) }
            return body.worldCenter.x - start
        }
        val normalOnBar = slide(Grip.NORMAL, onBar = true)
        val icyOnBar = slide(Grip.ICY, onBar = true)
        assertTrue("on a bar: normal $normalOnBar, icy $icyOnBar", icyOnBar > normalOnBar * 1.5)
        val normalOnPlatform = slide(Grip.NORMAL, onBar = false)
        val icyOnPlatform = slide(Grip.ICY, onBar = false)
        assertEquals("on the platform", normalOnPlatform, icyOnPlatform, 0.01)
    }

    @Test
    fun `slippery parts slip on each other but never on the platform`() {
        val mixer = StackWorld().world.valueMixer
        val platform = StackWorld.PLATFORM_FRICTION
        val sunnyOnPlatform = kotlin.math.sqrt(Grip.NORMAL.friction * platform)
        // 今までどおりの面どうし（晴れのパーツと台・晴れのパーツどうし）は、前と同じ（掛けて平方根）
        assertEquals(sunnyOnPlatform, mixer.mixFriction(Grip.NORMAL.friction, platform), 1e-9)
        assertEquals(0.7, mixer.mixFriction(Grip.NORMAL.friction, Grip.NORMAL.friction), 1e-9)
        // 台との間は、どの天気のパーツでも晴れと同じ（台は滑らない）。どちらの順で渡しても同じ
        for (grip in Grip.entries) {
            assertEquals(grip.name, sunnyOnPlatform, mixer.mixFriction(grip.friction, platform), 1e-9)
            assertEquals(grip.name, sunnyOnPlatform, mixer.mixFriction(platform, grip.friction), 1e-9)
        }
        // パーツどうしは、滑りやすい面があれば滑りやすい方の値（乗せても、乗せられても滑る）
        assertEquals(Grip.WET.friction, mixer.mixFriction(Grip.NORMAL.friction, Grip.WET.friction), 1e-9)
        assertEquals(Grip.ICY.friction, mixer.mixFriction(Grip.ICY.friction, Grip.NORMAL.friction), 1e-9)
        assertEquals(Grip.ICY.friction, mixer.mixFriction(Grip.WET.friction, Grip.ICY.friction), 1e-9)
    }

    @Test
    fun `longer walks make bigger parts`() {
        assertEquals(PartSize.MIN, PartSize.of(0), 1e-9)
        // パーツになる最短の道（150 m）で、いちばん小さい 0.7 倍になる（前は 100 m にしていて、0.8 倍より小さくならなかった）
        assertEquals(ConversionParams.DEFAULT.minDistanceM, PartSize.SHORT_M, 0.0)
        assertEquals(PartSize.MIN, PartSize.of(150), 1e-9)
        assertEquals(PartSize.MAX, PartSize.of(2000), 1e-9)
        assertEquals(PartSize.MAX, PartSize.of(50_000), 1e-9)
        // 450 m ほどでちょうど 1 倍。長いほど大きい
        assertEquals(1.0, PartSize.of(450), 0.02)
        val sizes = listOf(150, 200, 450, 800, 1600, 2000).map { PartSize.of(it) }
        assertTrue(sizes.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `a bigger part is bigger in the world and its size is saved in the placement`() {
        // 寝かせた棒の高さは太さ×大きさ。大きさ 1.4 は 0.7 の 2 倍の高さになる
        fun lay(scale: Double): Pair<Double, Placement> {
            val world = StackWorld()
            world.place(SampleParts.byId("sample-bar")!!, x = 0.0, y = 0.3, angle = Math.PI / 2, scale = scale)
            settle(world)
            return world.height() to world.placements().single()
        }
        val (small, smallPlacement) = lay(0.7)
        val (big, bigPlacement) = lay(1.4)
        assertEquals(0.7f, smallPlacement.scale, 1e-6f)
        assertEquals(1.4f, bigPlacement.scale, 1e-6f)
        assertEquals("small $small, big $big", 2.0, big / small, 0.1)
    }

    @Test
    fun `towers saved before sizes existed read as size 1`() {
        val old = """{"partId":"a","x":0.0,"y":0.1,"angleRad":0.0}"""
        assertEquals(1f, AppJson.decodeFromString(Placement.serializer(), old).scale)
        // 新しいタワーは大きさも書き出す
        assertTrue("\"scale\":1.4" in AppJson.encodeToString(Placement.serializer(), Placement("a", 0f, 0f, 0f, scale = 1.4f)))
    }

    @Test
    fun `render shapes include the platform`() {
        val world = StackWorld()
        val shapes = world.renderShapes()
        assertEquals(1, shapes.size)
        assertTrue(shapes.first() is StackWorld.RenderShape.Poly)
    }
}
