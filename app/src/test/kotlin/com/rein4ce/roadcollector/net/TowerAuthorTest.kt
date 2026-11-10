package com.rein4ce.roadcollector.net

import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.sample.SampleParts
import io.ktor.client.request.HttpRequestBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `the owner mark also goes in the header, so the server counts uploads per phone and not per Wi-Fi`() {
        // サーバの回数制限はヘッダで端末を見分ける。無いと IP で数え、同じ Wi-Fi の全員で上限を分け合ってしまう（決定 57・88）
        val mark = "d".repeat(64)
        assertEquals(mark, HttpRequestBuilder().apply { withOwnerHeader(mark) }.headers[OWNER_HEADER])
        assertNull(HttpRequestBuilder().apply { withOwnerHeader(null) }.headers[OWNER_HEADER])
        assertNull(HttpRequestBuilder().apply { withOwnerHeader("") }.headers[OWNER_HEADER])
    }

    @Test
    fun `通常と毎日の塔にはパーツの色を入れ、90 秒チャレンジには入れない（決定 95）`() {
        val c = com.rein4ce.roadcollector.core.model.PartColors("#5C95E0", "#2E5A99")
        assertEquals(mapOf(part.id to c), withPieceColors(tower, { c }).colors)
        assertEquals(mapOf(part.id to c), withPieceColors(tower.copy(mode = com.rein4ce.roadcollector.core.model.TowerMode.DAILY), { c }).colors)
        assertTrue(withPieceColors(tower.copy(mode = com.rein4ce.roadcollector.core.model.TowerMode.CHALLENGE), { c }).colors.isEmpty())
        // 色を返さないパーツには入れない。もう入っている色はそのまま
        assertTrue(withPieceColors(tower, { null }).colors.isEmpty())
        val before = com.rein4ce.roadcollector.core.model.PartColors("#111111", "#000000")
        assertEquals(before, withPieceColors(tower.copy(colors = mapOf(part.id to before)), { c }).colors[part.id])
    }
}
