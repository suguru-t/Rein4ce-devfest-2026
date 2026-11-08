package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.NicknameHints
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Test

/** ニックネームを AI に考えてもらうときに送る手がかり（見本を除く・名前は新しい順・場所は送らない） */
class NicknameHintsForTest {

    private fun part(id: String, name: String?, distanceM: Int, walkedAt: Long) =
        Part(id = id, points = listOf(Pt(0f, 0f), Pt(1f, 0f)), name = name, distanceM = distanceM, walkedAt = walkedAt)

    @Test
    fun `見本は入れず、名前は新しく歩いた順に、名前のないパーツは本数と距離にだけ数える`() {
        val parts = listOf(
            part("a", "古い道", 300, 1_000L),
            part("b", null, 200, 3_000L),
            part("c", "  新しい道 ", 500, 4_000L),
            part("d", "古い道", 100, 2_000L), // 同じ名前は 1 回だけ
        ) + SampleParts.all

        val hints = nicknameHintsFor(parts)

        assertEquals(listOf("新しい道", "古い道"), hints.partNames)
        assertEquals(4, hints.partCount)
        assertEquals(1_100, hints.totalDistanceM)
    }

    @Test
    fun `名前は 12 個まで`() {
        val parts = (1..20).map { part("p$it", "道$it", 100, it.toLong()) }

        val hints = nicknameHintsFor(parts)

        assertEquals(NicknameHints.MAX_PART_NAMES, hints.partNames.size)
        assertEquals("道20", hints.partNames.first())
    }

    @Test
    fun `まだ見本しかなければ、空の手がかり`() {
        assertEquals(NicknameHints(), nicknameHintsFor(SampleParts.all))
    }
}
