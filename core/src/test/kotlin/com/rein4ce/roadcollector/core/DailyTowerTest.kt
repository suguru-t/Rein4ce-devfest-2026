package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.daily.DailyBlock
import com.rein4ce.roadcollector.core.daily.DailyTower
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.physics.Grip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTowerTest {

    private fun part(id: String) = Part(id = id, points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)), kind = Kind.BAR, distanceM = 400, walkedAt = 0L)
    private fun block(id: String, day: String, y: Float = 0f) =
        DailyBlock(part(id), Placement(id, x = 0f, y = y, angleRad = 0f), Grip.NORMAL, day)

    @Test
    fun `1 日に 1 つ：積んだ日かどうか`() {
        val tower = DailyTower(listOf(block("a", "2026-11-09")))
        assertTrue(tower.placedOn("2026-11-09"))
        assertFalse(tower.placedOn("2026-11-10"))
    }

    @Test
    fun `止まったあとの配置に入れ替え、今日のパーツを足す`() {
        val tower = DailyTower(listOf(block("a", "2026-11-09", y = 0.1f)))
        val added = block("b", "2026-11-10", y = 2.0f)
        val next = tower.settledWith(
            added,
            listOf(Placement("a", x = 0.05f, y = 0.1f, angleRad = 0.02f), Placement("b", x = 0f, y = 0.3f, angleRad = 0f)),
            height = 0.8,
        )
        assertEquals(listOf("a", "b"), next.blocks.map { it.part.id })
        assertEquals(0.05f, next.blocks[0].placement.x)
        assertEquals(0.3f, next.blocks[1].placement.y)
        assertEquals("2026-11-10", next.blocks[1].day)
        assertEquals(0.8, next.height, 0.0)
        assertEquals(2, next.days)
    }

    @Test
    fun `台から落ちたパーツは塔から外れる（積んだ日の数にも入らない）`() {
        val tower = DailyTower(listOf(block("a", "2026-11-08"), block("b", "2026-11-09")))
        val next = tower.settledWith(block("c", "2026-11-10"), listOf(Placement("b", 0f, 0.1f, 0f), Placement("c", 0f, 0.3f, 0f)), height = 0.5)
        assertEquals(listOf("b", "c"), next.blocks.map { it.part.id })
        assertEquals(2, next.days)
    }

    @Test
    fun `今日のパーツが台から落ちたら、今日はまだ積んでいないことになる（もう一度選べる）`() {
        val tower = DailyTower(listOf(block("a", "2026-11-09")))
        val next = tower.settledWith(block("b", "2026-11-10"), listOf(Placement("a", 0f, 0.1f, 0f)), height = 0.2)
        assertFalse(next.placedOn("2026-11-10"))
        assertEquals(listOf("a"), next.blocks.map { it.part.id })
    }

    @Test
    fun `保存して読み直しても同じ塔（パーツの形・配置・すべりやすさ・日付）`() {
        val tower = DailyTower(listOf(block("a", "2026-11-09").copy(grip = Grip.WET)))
        val text = AppJson.encodeToString(DailyTower.serializer(), tower)
        assertEquals(tower, AppJson.decodeFromString(DailyTower.serializer(), text))
    }
}
