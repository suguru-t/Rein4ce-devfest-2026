package com.rein4ce.roadcollector.net

import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** タワーを送るときの作者名（設定のニックネーム） */
class TowerAuthorTest {

    private val part = SampleParts.byId("sample-bar")!!
    private val tower = Tower(parts = listOf(part), placements = listOf(Placement(part.id, 0f, 0.3f, 0f)), height = 0.3f, createdAt = 0L)

    @Test
    fun `the nickname goes in as the author`() {
        assertEquals("みちつみ", withAuthor(tower, "みちつみ").author)
    }

    @Test
    fun `no nickname leaves it nameless`() {
        assertNull(withAuthor(tower, null).author)
        assertNull(withAuthor(tower, "  ").author)
    }

    @Test
    fun `an author already set is kept`() {
        assertEquals("先に入れた名前", withAuthor(tower.copy(author = "先に入れた名前"), "みちつみ").author)
    }

    @Test
    fun `the owner mark goes in, and nothing goes in without one`() {
        val mark = "c".repeat(64)
        assertEquals(mark, withOwner(tower, mark).ownerId)
        assertNull(withOwner(tower, null).ownerId)
        assertNull(withOwner(tower, "").ownerId)
    }
}
