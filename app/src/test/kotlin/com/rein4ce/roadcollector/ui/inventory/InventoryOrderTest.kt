package com.rein4ce.roadcollector.ui.inventory

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryOrderTest {

    private fun walk(id: String, walkedAt: Long) =
        Part(id = id, points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)), distanceM = 300, walkedAt = walkedAt)

    private val old = walk("old", 1_000L)
    private val mid = walk("mid", 2_000L)
    private val new = walk("new", 3_000L)
    private val samples = SampleParts.all

    private fun ids(list: List<Part>) = list.map { it.id }

    @Test
    fun `collected parts come first, newest first, then samples in their fixed order`() {
        // 保存の順番はばらばらでも、並びは同じになる
        val shown = inventoryOrder(listOf(samples[3], old, samples[0], new) + samples.drop(4) + listOf(mid) + samples.subList(1, 3), emptyMap())
        assertEquals(listOf("new", "mid", "old") + samples.map { it.id }, ids(shown))
    }

    @Test
    fun `moving to the top puts the part above newer ones`() {
        val shown = inventoryOrder(listOf(old, mid, new) + samples, mapOf("old" to 4_000L))
        assertEquals(listOf("old", "new", "mid"), ids(shown).take(3))
    }

    @Test
    fun `a part collected after moving goes above the moved one`() {
        // 「先頭へ」を押したあとに採集したパーツは、さらに上に来る（最後に触ったものが上）
        val newer = walk("newer", 5_000L)
        val shown = inventoryOrder(listOf(old, mid, new, newer) + samples, mapOf("old" to 4_000L))
        assertEquals(listOf("newer", "old", "new", "mid"), ids(shown).take(4))
    }

    @Test
    fun `a sample moved to the top comes above collected parts`() {
        val shown = inventoryOrder(listOf(old, new) + samples, mapOf(samples[2].id to 4_000L))
        assertEquals(listOf(samples[2].id, "new", "old"), ids(shown).take(3))
        // ほかの見本は決まった順のまま
        assertEquals(samples.map { it.id } - samples[2].id, ids(shown).drop(3))
    }

    @Test
    fun `collected parts without a date still come above samples`() {
        val noDate = walk("no-date", 0L)
        val shown = inventoryOrder(samples + noDate, emptyMap())
        assertEquals("no-date", shown.first().id)
    }
}
