package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.TowerSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「みんなのタワー」の並び順（高い順・新しい順・パーツ数）と、5 基ずつのページ分け */
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
    fun `新しい順は作った日時の降順`() {
        assertEquals(listOf("low-new", "mid", "tall-old"), sortTowers(towers, TowerSort.NEWEST).map { it.id })
    }

    @Test
    fun `パーツ数は多い順、同じなら高いほうが上`() {
        assertEquals(listOf("tall-old", "mid", "low-new"), sortTowers(towers, TowerSort.PARTS).map { it.id })
    }

    @Test
    fun `1 番のタワーの札は並びごとに変わる`() {
        assertEquals("最長", TowerSort.HEIGHT.topLabel)
        assertEquals("最新", TowerSort.NEWEST.topLabel)
        assertEquals("最多", TowerSort.PARTS.topLabel)
    }

    @Test
    fun `5 基ずつに分け、端のページは残りだけ`() {
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
}
