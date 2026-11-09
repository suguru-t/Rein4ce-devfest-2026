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

    @Test
    fun `今日のパーツが落ちた回数を数え、置き直しは 1 日 3 回まで（決定 70）`() {
        val day = "2026-11-10"
        var tower = DailyTower(listOf(block("a", "2026-11-09")))
        assertEquals(0, tower.fallsOn(day))
        assertEquals(DailyTower.MAX_RETRIES, tower.retriesLeft(day))
        assertTrue(tower.canPlaceOn(day))
        // 1〜3 回落ちても、まだ置き直せる
        for (n in 1..DailyTower.MAX_RETRIES) {
            tower = tower.settledWith(block("b", day), listOf(Placement("a", 0f, 0.1f, 0f)), height = 0.2)
            assertEquals(n, tower.fallsOn(day))
            assertTrue("$n 回落ちたあと", tower.canPlaceOn(day))
        }
        assertEquals(1, tower.retriesLeft(day))
        // 4 回目に落ちたら、その日はもう積めない
        tower = tower.settledWith(block("b", day), listOf(Placement("a", 0f, 0.1f, 0f)), height = 0.2)
        assertEquals(0, tower.retriesLeft(day))
        assertFalse(tower.canPlaceOn(day))
        assertEquals("昨日までの塔はそのまま", listOf("a"), tower.blocks.map { it.part.id })
        // 次の日は数え直す
        assertEquals(0, tower.fallsOn("2026-11-11"))
        assertTrue(tower.canPlaceOn("2026-11-11"))
    }

    @Test
    fun `置き直して台に残ったら、今日の 1 つは積んだことになり、落ちた回数は増えない`() {
        val day = "2026-11-10"
        val fell = DailyTower(listOf(block("a", "2026-11-09"))).settledWith(block("b", day), listOf(Placement("a", 0f, 0.1f, 0f)), height = 0.2)
        val kept = fell.settledWith(block("b", day), listOf(Placement("a", 0f, 0.1f, 0f), Placement("b", 0f, 0.3f, 0f)), height = 0.5)
        assertTrue(kept.placedOn(day))
        assertFalse(kept.canPlaceOn(day))
        assertEquals(1, kept.fallsOn(day))
        assertEquals(listOf("a", "b"), kept.blocks.map { it.part.id })
    }

    @Test
    fun `連続日数は、最後に積んだ日からさかのぼって途切れずに積んだ日数（決定 77）`() {
        assertEquals(0, DailyTower().streakDays)
        val tower = DailyTower(listOf(block("a", "2026-11-05"), block("b", "2026-11-07"), block("c", "2026-11-08"), block("d", "2026-11-09")))
        assertEquals(3, tower.streakDays)
        assertEquals("2026-11-09", tower.lastDay)
        // 月をまたいでも続く
        assertEquals(2, DailyTower(listOf(block("a", "2026-10-31"), block("b", "2026-11-01"))).streakDays)
    }

    @Test
    fun `「みんな」に送る形：パーツは 1 つにまとめ、配置は積んだ数だけ、まだ送っていない形があるか（決定 77）`() {
        val tower = DailyTower(listOf(block("a", "2026-11-09", y = 0.1f), block("b", "2026-11-10", y = 0.3f)), height = 0.6)
        assertTrue(tower.needsSend)
        val sent = tower.toTower(platformWidth = 2.8, createdAt = 7L)
        assertEquals(com.rein4ce.roadcollector.core.model.TowerMode.DAILY, sent.mode)
        assertEquals(listOf("a", "b"), sent.parts.map { it.id })
        assertEquals(2, sent.placements.size)
        assertEquals(0.6f, sent.height)
        assertEquals(2.8f, sent.platformWidth)
        assertEquals(2, sent.streakDays)
        assertEquals("2026-11-10", sent.lastDay)
        assertFalse(tower.copy(sentDay = "2026-11-10").needsSend)
        assertFalse(DailyTower().needsSend)
    }

    @Test
    fun `落ちた回数も保存し、前の形式（落ちた回数が無い）のファイルも読める`() {
        val tower = DailyTower(listOf(block("a", "2026-11-09")), height = 0.3, fellDay = "2026-11-10", fellCount = 2)
        val text = AppJson.encodeToString(DailyTower.serializer(), tower)
        assertEquals(tower, AppJson.decodeFromString(DailyTower.serializer(), text))
        val old = AppJson.decodeFromString(DailyTower.serializer(), """{"blocks":[],"height":0.0}""")
        assertEquals(0, old.fallsOn("2026-11-10"))
        assertTrue(old.canPlaceOn("2026-11-10"))
    }
}
