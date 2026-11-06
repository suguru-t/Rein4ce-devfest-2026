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
    private val sampleIds = samples.map { it.id }

    private fun ids(list: List<Part>) = list.map { it.id }

    @Test
    fun `without arranging, collected parts come first newest first, then samples in their fixed order`() {
        // 保存の順番はばらばらでも、並びは同じになる
        val shown = inventoryOrder(listOf(samples[3], old, samples[0], new) + samples.drop(4) + listOf(mid) + samples.subList(1, 3), emptyList())
        assertEquals(listOf("new", "mid", "old") + sampleIds, ids(shown))
    }

    @Test
    fun `an arranged order is kept as is, samples included`() {
        val order = listOf(sampleIds[2], "old", "new", sampleIds[0], "mid") + sampleIds.drop(3) + sampleIds[1]
        assertEquals(order, ids(inventoryOrder(listOf(old, mid, new) + samples, order)))
    }

    @Test
    fun `a part collected after arranging comes on top`() {
        val order = listOf("old", "new", "mid") + sampleIds
        val newer = walk("newer", 5_000L)
        assertEquals(listOf("newer") + order, ids(inventoryOrder(listOf(old, mid, new, newer) + samples, order)))
    }

    @Test
    fun `a restored sample that is not in the arranged order goes to the bottom`() {
        // 見本 0 を消して並べ替えたあと、「見本を戻す」で戻した
        val order = listOf("new", "old") + sampleIds.drop(1)
        val shown = inventoryOrder(listOf(old, new) + samples, order)
        assertEquals(order + sampleIds[0], ids(shown))
    }

    @Test
    fun `ids that are no longer in the inventory and duplicates are ignored`() {
        val order = listOf("gone", "old", "old", "new")
        assertEquals(listOf("old", "new") + sampleIds, ids(inventoryOrder(listOf(old, new) + samples, order)))
    }

    @Test
    fun `moved moves one item and leaves out-of-range moves alone`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), list.moved(2, 0))
        assertEquals(listOf("b", "c", "a", "d"), list.moved(0, 2))
        assertEquals(list, list.moved(1, 1))
        assertEquals(list, list.moved(0, 9))
    }
}
