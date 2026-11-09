package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.TowerSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「みんなのタワー」の並び順（高い順・多い順）、断面図で比べる 2 基、ランキングの 5 個ずつのページ分け */
class GalleryOrderTest {

    private fun tower(id: String, height: Float, parts: Int = 1, at: Long = 0L) =
        TowerSummary(id = id, height = height, partCount = parts, createdAt = at, author = null)

    private val towers = listOf(
        tower("low-new", height = 1f, parts = 2, at = 300L),
        tower("tall-old", height = 3f, parts = 5, at = 100L),
        tower("mid", height = 2f, parts = 5, at = 200L),
    )

    @Test
    fun `高い順は高さの降順、同じ高さなら新しいほうが上`() {
        val same = listOf(tower("a", 2f, at = 1L), tower("b", 2f, at = 2L), tower("c", 1f, at = 9L))
        assertEquals(listOf("b", "a", "c"), sortTowers(same, TowerSort.HEIGHT).map { it.id })
    }

    @Test
    fun `多い順はパーツの多い順、同じなら高いほうが上`() {
        assertEquals(listOf("tall-old", "mid", "low-new"), sortTowers(towers, TowerSort.PARTS).map { it.id })
    }

    @Test
    fun `新しい順は作った日時の降順（あなたのタワーだけで使う）`() {
        assertEquals(listOf("low-new", "mid", "tall-old"), sortTowers(towers, TowerSort.NEWEST).map { it.id })
        assertEquals(listOf(TowerSort.HEIGHT, TowerSort.PARTS), TowerSort.COMPARABLE)
    }

    @Test
    fun `物差しごとの値と字`() {
        assertEquals("3.00", TowerSort.HEIGHT.format(TowerSort.HEIGHT.valueOf(towers[1])))
        assertEquals("5", TowerSort.PARTS.format(TowerSort.PARTS.valueOf(towers[1])))
        assertEquals("最長", TowerSort.HEIGHT.topLabel)
        assertEquals("最多", TowerSort.PARTS.topLabel)
    }

    @Test
    fun `断面図は 1 番と自分のいちばん良いタワーを比べる`() {
        val c = comparison(towers, TowerSort.HEIGHT, isMine = { it.id == "low-new" })
        assertEquals("tall-old", c.top?.id)
        assertEquals("low-new", c.mine?.id)
        assertFalse(c.mineIsTop)
    }

    @Test
    fun `自分が 1 番なら、相手は他人で 1 番のタワーで、その順位も分かる`() {
        val c = comparison(towers, TowerSort.HEIGHT, isMine = { it.id == "tall-old" })
        assertEquals("tall-old", c.top?.id)
        assertEquals("mid", c.mine?.id)
        assertTrue(c.mineIsTop)
        assertEquals(2, c.otherRank)
        // 自分のタワーが 1 番と 2 番なら、相手は 3 番
        val two = comparison(towers, TowerSort.HEIGHT, isMine = { it.id == "tall-old" || it.id == "mid" })
        assertEquals("low-new", two.mine?.id)
        assertEquals(3, two.otherRank)
    }

    @Test
    fun `値が同じなら自分を 1 番と見なす（あと 0 にしない）`() {
        // パーツ数が同じ 5 本で、他人（tall-old）が先に並んでいる
        val c = comparison(towers, TowerSort.PARTS, isMine = { it.id == "mid" })
        assertTrue(c.mineIsTop)
        assertEquals("mid", c.top?.id)
        assertEquals("tall-old", c.mine?.id)
        // 自分を 1 番と見なすので、先に並んでいた相手は 2 番目（「1 番」が 2 つにならない）
        assertEquals(2, c.otherRank)
    }

    @Test
    fun `自分のタワーが無ければ相手は空、タワーが無ければ両方空`() {
        val none = comparison(towers, TowerSort.PARTS, isMine = { false })
        assertEquals("tall-old", none.top?.id)
        assertNull(none.mine)
        val empty = comparison(emptyList(), TowerSort.HEIGHT, isMine = { true })
        assertNull(empty.top)
        assertNull(empty.mine)
    }

    @Test
    fun `5 個ずつに分け、端のページは残りだけ`() {
        val many = (1..12).map { tower("t$it", it.toFloat()) }
        assertEquals(3, pageCount(many.size))
        assertEquals(0, pageCount(0))
        assertEquals(1, pageCount(5))
        assertEquals(2, pageCount(6))
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), pageOf(many, 0).map { it.id })
        assertEquals(listOf("t11", "t12"), pageOf(many, 2).map { it.id })
        assertEquals(emptyList<TowerSummary>(), pageOf(many, 3))
        assertEquals(emptyList<TowerSummary>(), pageOf(many, -1))
    }

    @Test
    fun `標尺の刻みは、目盛りの間が空く最小のものを選ぶ`() {
        assertEquals(1, tickStepM(pxPerM = 40f, minGapPx = 16f)) // ふつうのタワー
        assertEquals(2, tickStepM(pxPerM = 10f, minGapPx = 16f))
        assertEquals(5, tickStepM(pxPerM = 4f, minGapPx = 16f))
        assertEquals(10, tickStepM(pxPerM = 2f, minGapPx = 16f)) // 100 m のタワーが来たとき
        assertEquals(100, tickStepM(pxPerM = 0.01f, minGapPx = 16f))
    }

    @Test
    fun `断面図のどのスロットを押したか`() {
        // 幅 100、標尺 10、2 基：スロットは 45 ずつ
        assertEquals(-1, slotAt(x = 5f, width = 100f, count = 2, staffWidthPx = 10f))
        assertEquals(0, slotAt(x = 10f, width = 100f, count = 2, staffWidthPx = 10f))
        assertEquals(1, slotAt(x = 60f, width = 100f, count = 2, staffWidthPx = 10f))
        assertEquals(1, slotAt(x = 99f, width = 100f, count = 2, staffWidthPx = 10f))
        assertEquals(-1, slotAt(x = 50f, width = 100f, count = 0))
    }
}
