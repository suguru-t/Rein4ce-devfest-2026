package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.physics.WeatherBonus
import com.rein4ce.roadcollector.core.physics.enlarged
import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 天気のボーナス（決定 68）：塔に残ったパーツの天気で、模型を大きくする倍率 */
class WeatherBonusTest {

    @Test
    fun `more slippery weather adds more`() {
        val order = listOf(Grip.NORMAL, Grip.CLOUDY, Grip.WET, Grip.ICY)
        // 晴れは何も足さず、滑りやすいほど多く足す
        assertEquals(0, Grip.NORMAL.bonusPercent)
        assertEquals(order.map { it.bonusPercent }.sorted(), order.map { it.bonusPercent })
        assertEquals(order.size, order.map { it.bonusPercent }.distinct().size)
    }

    @Test
    fun `three rainy parts make the model 1_3 times`() {
        val bonus = WeatherBonus.of(List(3) { Grip.WET })
        assertEquals(1.3, bonus.multiplier, 1e-9)
        assertEquals(mapOf(Grip.WET to 3), bonus.counts)
        assertTrue(bonus.active)
    }

    @Test
    fun `each part adds its own bonus and sunny parts add nothing`() {
        val bonus = WeatherBonus.of(listOf(Grip.ICY, Grip.NORMAL, Grip.WET, Grip.ICY, Grip.NORMAL, Grip.CLOUDY))
        // 雪 0.20 × 2 + 雨 0.10 + くもり 0.01
        assertEquals(1.51, bonus.multiplier, 1e-9)
        assertEquals(listOf(Grip.ICY to 2, Grip.WET to 1, Grip.CLOUDY to 1), bonus.entries)
    }

    @Test
    fun `more snowy parts keep adding`() {
        val multipliers = (1..5).map { n -> WeatherBonus.of(List(n) { Grip.ICY }).multiplier }
        assertEquals(listOf(1.2, 1.4, 1.6, 1.8, 2.0), multipliers.map { Math.round(it * 100) / 100.0 })
    }

    @Test
    fun `no bonus without slippery parts`() {
        assertEquals(1.0, WeatherBonus.of(emptyList()).multiplier, 0.0)
        assertEquals(1.0, WeatherBonus.of(List(4) { Grip.NORMAL }).multiplier, 0.0)
        assertFalse(WeatherBonus.of(List(4) { Grip.NORMAL }).active)
        assertEquals(WeatherBonus.NONE, WeatherBonus.of(listOf(Grip.NORMAL)))
    }

    @Test
    fun `the multiplier stops at the cap`() {
        val bonus = WeatherBonus.of(List(15) { Grip.ICY })
        assertEquals(WeatherBonus.MAX_MULTIPLIER, bonus.multiplier, 0.0)
        assertTrue(bonus.capped)
        assertFalse(WeatherBonus.of(List(10) { Grip.ICY }).capped)
    }

    @Test
    fun `adding hundredths does not drift`() {
        // 0.1 を小数で 13 回足すと 1.2999… になる。百分の一の整数で足すので、ちょうど 2.30
        assertEquals(230, 100 + WeatherBonus.of(List(13) { Grip.WET }).addedPercent)
    }

    @Test
    fun `an enlarged tower keeps its shape around the platform center`() {
        val tower = Tower(
            parts = listOf(SampleParts.byId("sample-bar")!!),
            placements = listOf(Placement("sample-bar", x = 0.4f, y = 0.1f, angleRad = 0.3f, scale = 0.8f)),
            height = 1.25f,
            createdAt = 0L,
            platformWidth = 2.8f,
        )
        val big = tower.enlarged(1.3)
        val p = big.placements.single()
        assertEquals(0.52f, p.x, 1e-5f)
        assertEquals(0.13f, p.y, 1e-5f)
        assertEquals(1.04f, p.scale, 1e-5f)
        assertEquals(0.3f, p.angleRad, 0f)
        assertEquals(1.625f, big.height, 1e-5f)
        assertEquals(3.64f, big.platformWidth, 1e-5f) // 台ごと大きくする
        assertEquals(tower, tower.enlarged(1.0))
    }

    @Test
    fun `the world remembers each placed part's grip until it falls`() {
        val world = StackWorld()
        val bar = SampleParts.byId("sample-bar")!!
        world.place(bar, 0.0, 0.5, 0.0, grip = Grip.ICY)
        world.place(bar, 50.0, 0.5, 0.0, grip = Grip.WET) // 台の外：落ちる
        assertEquals(listOf(Grip.ICY, Grip.WET), world.placedGrips)
        repeat(240) { world.step(1.0 / 60.0) }
        world.removeFallen()
        assertEquals(listOf(Grip.ICY), world.placedGrips)
        world.clear()
        assertEquals(emptyList<Grip>(), world.placedGrips)
    }
}
