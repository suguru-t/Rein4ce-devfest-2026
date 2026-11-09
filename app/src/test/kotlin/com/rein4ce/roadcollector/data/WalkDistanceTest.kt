package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Test

/** 完成カードの「使った道」：本当に歩いた道だけを数える（walkDistance） */
class WalkDistanceTest {

    private val sample = SampleParts.all.first()

    @Test
    fun `歩いて記録したパーツだけを数え、同じパーツは 1 回だけ`() {
        val a = sample.copy(id = "a", distanceM = 500)
        val b = sample.copy(id = "b", distanceM = 300)
        val demo = sample.copy(id = "demo", distanceM = 900)
        val file = sample.copy(id = "file", distanceM = 700)
        val origins = mapOf("a" to PartOrigin.WALK, "b" to PartOrigin.WALK, "demo" to PartOrigin.DEMO, "file" to PartOrigin.FILE)

        assertEquals(
            WalkDistance(meters = 800, walks = 2, others = 2, unknown = 0),
            walkDistance(listOf(a, b, a, demo, file), origins),
        )
    }

    @Test
    fun `見本は出どころの表に無くても、分からないパーツには数えない`() {
        val old = sample.copy(id = "made-before-origins", distanceM = 400)
        assertEquals(
            WalkDistance(meters = 0, walks = 0, others = 2, unknown = 1),
            walkDistance(listOf(sample, old), emptyMap()),
        )
    }

    @Test
    fun `長い道をいくつ足しても溢れない`() {
        val a = sample.copy(id = "a", distanceM = Int.MAX_VALUE)
        val b = sample.copy(id = "b", distanceM = Int.MAX_VALUE)
        val origins = mapOf("a" to PartOrigin.WALK, "b" to PartOrigin.WALK)
        assertEquals(2L * Int.MAX_VALUE, walkDistance(listOf(a, b), origins).meters)
    }
}
