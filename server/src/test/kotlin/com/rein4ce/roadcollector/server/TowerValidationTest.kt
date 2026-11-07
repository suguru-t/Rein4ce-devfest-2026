package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.sample.SampleParts
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/** POST /towers で断るタワーと、通すタワー（TowerValidation.kt） */
class TowerValidationTest {

    private val parts = SampleParts.all.take(2)
    private val ok = Tower(
        parts = parts,
        placements = parts.mapIndexed { i, p -> Placement(p.id, x = 0f, y = 0.1f + i * 0.4f, angleRad = 0f) },
        height = 0.8f,
        createdAt = 0L,
        author = "あきやま",
    )

    private fun bad(tower: Tower, expected: String) {
        val reason = assertNotNull(tower.validationError(), "断るはず")
        assertContains(reason, expected)
    }

    @Test
    fun `アプリが作ったふつうのタワーは通る`() {
        assertNull(ok.validationError())
        // 見本 5 本全部でも通る。作者なし・名前付きのパーツも通る
        val five = SampleParts.all
        val tower = Tower(
            parts = five.map { it.copy(name = "夕暮れの七曲り") },
            placements = five.mapIndexed { i, p -> Placement(p.id, x = 0.1f * i, y = 0.3f * i, angleRad = 1.57f * i) },
            height = 2.5f,
            createdAt = 0L,
        )
        assertNull(tower.validationError())
    }

    @Test
    fun `上限ぴったりは通る`() {
        val many = (1..MAX_TOWER_PARTS).map { Part(id = "p$it", points = List(MAX_PART_POINTS) { i -> Pt(i * 0.01f, 0f) }) }
        val tower = ok.copy(
            parts = many,
            placements = many.map { Placement(it.id, x = MAX_COORDINATE, y = -MAX_COORDINATE, angleRad = 0f) },
            height = MAX_COORDINATE,
            author = "あ".repeat(MAX_AUTHOR_LENGTH),
        )
        assertNull(tower.validationError())
    }

    @Test
    fun `持ち主の印は 64 桁の 16 進だけ通す（無しも通す）`() {
        assertNull(ok.copy(ownerId = "0123456789abcdef".repeat(4)).validationError())
        assertNull(ok.copy(ownerId = null).validationError())
        bad(ok.copy(ownerId = "ABCDEF0123456789".repeat(4)), "ownerId")
        bad(ok.copy(ownerId = "abc"), "ownerId")
        bad(ok.copy(ownerId = ""), "ownerId")
    }

    @Test
    fun `空・多すぎは断る`() {
        bad(ok.copy(parts = emptyList()), "parts is empty")
        bad(ok.copy(placements = emptyList()), "placements is empty")
        val tooMany = (1..MAX_TOWER_PARTS + 1).map { Part(id = "p$it", points = listOf(Pt(0f, 0f))) }
        bad(ok.copy(parts = tooMany), "too many parts")
        bad(ok.copy(placements = List(MAX_TOWER_PARTS + 1) { ok.placements[0] }), "too many placements")
    }

    @Test
    fun `パーツの中身がおかしければ断る`() {
        bad(ok.copy(parts = listOf(parts[0].copy(id = " "), parts[1])), "part id is blank")
        bad(ok.copy(parts = listOf(parts[0], parts[0])), "duplicate part id")
        bad(ok.copy(parts = listOf(parts[0].copy(points = emptyList()), parts[1])), "points must have")
        bad(ok.copy(parts = listOf(parts[0].copy(points = List(MAX_PART_POINTS + 1) { Pt(0f, 0f) }), parts[1])), "points must have")
        bad(ok.copy(parts = listOf(parts[0].copy(points = listOf(Pt(Float.NaN, 0f))), parts[1])), "point out of range")
        bad(ok.copy(parts = listOf(parts[0].copy(points = listOf(Pt(1e9f, 0f))), parts[1])), "point out of range")
        bad(ok.copy(parts = listOf(parts[0].copy(thickness = 0f), parts[1])), "thickness out of range")
        bad(ok.copy(parts = listOf(parts[0].copy(thickness = Float.POSITIVE_INFINITY), parts[1])), "thickness out of range")
        bad(ok.copy(parts = listOf(parts[0].copy(distanceM = -1), parts[1])), "distanceM is negative")
        bad(ok.copy(parts = listOf(parts[0].copy(name = "あ".repeat(MAX_PART_NAME_LENGTH + 1)), parts[1])), "name too long")
    }

    @Test
    fun `置き方がおかしければ断る`() {
        bad(ok.copy(placements = listOf(Placement("nothere", 0f, 0f, 0f))), "unknown partId nothere")
        bad(ok.copy(placements = listOf(ok.placements[0].copy(x = Float.NaN))), "placement 0: out of range")
        bad(ok.copy(placements = listOf(ok.placements[0].copy(y = MAX_COORDINATE + 1))), "placement 0: out of range")
        bad(ok.copy(placements = listOf(ok.placements[0].copy(angleRad = Float.NEGATIVE_INFINITY))), "placement 0: out of range")
    }

    @Test
    fun `高さと作者名がおかしければ断る`() {
        bad(ok.copy(height = -0.1f), "height out of range")
        bad(ok.copy(height = Float.NaN), "height out of range")
        bad(ok.copy(height = MAX_COORDINATE + 1), "height out of range")
        bad(ok.copy(author = "あ".repeat(MAX_AUTHOR_LENGTH + 1)), "author too long")
        // 絵文字は 1 文字と数える
        assertNull(ok.copy(author = "🌙".repeat(MAX_AUTHOR_LENGTH)).validationError())
        assertEquals("author too long (> $MAX_AUTHOR_LENGTH)", ok.copy(author = "🌙".repeat(MAX_AUTHOR_LENGTH + 1)).validationError())
    }
}
