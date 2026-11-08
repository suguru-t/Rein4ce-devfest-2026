package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.weather.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** コレクションと「パーツを選ぶ」の窓の並びと絞り込み（PartFilter.kt） */
class PartFilterTest {
    private val base = SampleParts.all.first()

    // いつもの順：a, b, c, d（歩いた日時と長さはばらばら。b と c は同じ日時・同じ長さ）
    private val a = base.copy(id = "a", walkedAt = 3_000L, distanceM = 300)
    private val b = base.copy(id = "b", walkedAt = 1_000L, distanceM = 800)
    private val c = base.copy(id = "c", walkedAt = 1_000L, distanceM = 800)
    private val d = base.copy(id = "d", walkedAt = 5_000L, distanceM = 150)
    private val arranged = listOf(a, b, c, d)
    private val skies = mapOf("a" to Sky.RAIN, "b" to Sky.CLEAR, "c" to Sky.RAIN)

    private fun ids(filter: PartFilter, favorites: Set<String> = emptySet()) =
        applyPartFilter(arranged, filter, { skies[it.id] }, favorites).map { it.id }

    @Test
    fun `each sort orders the parts and keeps the usual order for ties`() {
        assertEquals(listOf("a", "b", "c", "d"), ids(PartFilter()))
        assertEquals(listOf("d", "a", "b", "c"), ids(PartFilter(sort = PartSort.NEWEST)))
        assertEquals(listOf("b", "c", "a", "d"), ids(PartFilter(sort = PartSort.OLDEST)))
        assertEquals(listOf("b", "c", "a", "d"), ids(PartFilter(sort = PartSort.LONGEST)))
        assertEquals(listOf("d", "a", "b", "c"), ids(PartFilter(sort = PartSort.SHORTEST)))
    }

    @Test
    fun `weather and favorites narrow the parts and can be combined with a sort`() {
        // 天気が分からないパーツ（d）は、天気で絞り込むと出ない
        assertEquals(listOf("a", "c"), ids(PartFilter(sky = Sky.RAIN)))
        assertEquals(listOf("b"), ids(PartFilter(sky = Sky.CLEAR)))
        assertEquals(emptyList<String>(), ids(PartFilter(sky = Sky.SNOW)))
        assertEquals(listOf("b", "d"), ids(PartFilter(favoritesOnly = true), favorites = setOf("b", "d")))
        assertEquals(listOf("c", "a"), ids(PartFilter(sort = PartSort.LONGEST, sky = Sky.RAIN)))
        assertEquals(listOf("c"), ids(PartFilter(sky = Sky.RAIN, favoritesOnly = true), favorites = setOf("b", "c")))
    }

    @Test
    fun `only the usual order without narrowing allows reordering by hand`() {
        assertTrue(PartFilter().isDefault)
        assertFalse(PartFilter(sort = PartSort.OLDEST).isDefault)
        assertFalse(PartFilter(sky = Sky.SNOW).isDefault)
        assertFalse(PartFilter(favoritesOnly = true).isDefault)
        assertFalse(PartFilter(sort = PartSort.LONGEST).narrows)
        assertTrue(PartFilter(favoritesOnly = true).narrows)
    }
}
