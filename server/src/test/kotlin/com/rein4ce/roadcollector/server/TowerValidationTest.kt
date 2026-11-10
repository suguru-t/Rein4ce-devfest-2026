package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.physics.enlarged
import com.rein4ce.roadcollector.core.sample.SampleParts
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate
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
            height = 1.2f,
            createdAt = 0L,
        )
        assertNull(tower.validationError())
    }

    @Test
    fun `上限ぴったりは通る`() {
        val many = (1..MAX_TOWER_PARTS).map { Part(id = "p$it", points = List(MAX_PART_POINTS) { i -> Pt(i * 0.01f, 0f) }) }
        val tower = ok.copy(
            parts = many,
            placements = many.map { Placement(it.id, x = MAX_COORDINATE, y = MAX_COORDINATE, angleRad = 0f) },
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
        bad(ok.copy(placements = List(MAX_TOWER_PLACEMENTS + 1) { ok.placements[0] }), "too many placements")
    }

    @Test
    fun `配置はパーツの種類より多くてよい（500 個まで。決定 77）`() {
        // 通常で 100 個を超えて積むと、前は「保存」が断られていた
        assertNull(ok.copy(placements = List(MAX_TOWER_PLACEMENTS) { ok.placements[it % 2] }).validationError())
        assertEquals(500, MAX_TOWER_PLACEMENTS)
    }

    @Test
    fun `3 分チャレンジは決まりの ID が要る（決定 77）`() {
        val challenge = ok.copy(mode = TowerMode.CHALLENGE, rule = "manual-180-v6")
        assertNull(challenge.validationError())
        bad(challenge.copy(rule = null), "needs rule")
        bad(challenge.copy(rule = " "), "needs rule")
        bad(challenge.copy(rule = "x".repeat(MAX_RULE_LENGTH + 1)), "needs rule")
    }

    @Test
    fun `毎日 1 つずつ積むは持ち主の印・最後に積んだ日・連続日数が要る（決定 77）`() {
        val daily = ok.copy(mode = TowerMode.DAILY, ownerId = "a".repeat(64), lastDay = "2026-11-10", streakDays = 2)
        assertNull(daily.validationError())
        bad(daily.copy(ownerId = null), "needs ownerId")
        bad(daily.copy(lastDay = null), "needs lastDay")
        bad(daily.copy(lastDay = "11/10"), "needs lastDay")
        bad(daily.copy(streakDays = 0), "streakDays")
        bad(daily.copy(streakDays = MAX_STREAK_DAYS + 1), "streakDays")
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
        // 大きさ（歩いた距離で 0.7〜1.4 倍）。0・NaN・極端な値は断る
        bad(ok.copy(placements = listOf(ok.placements[0].copy(scale = 0f))), "placement 0: scale out of range")
        bad(ok.copy(placements = listOf(ok.placements[0].copy(scale = Float.NaN))), "placement 0: scale out of range")
        bad(ok.copy(placements = listOf(ok.placements[0], ok.placements[1].copy(scale = MAX_PLACEMENT_SCALE + 1))), "placement 1: scale out of range")
    }

    @Test
    fun `大きさを変えたパーツも通る`() {
        val sized = ok.copy(placements = listOf(ok.placements[0].copy(scale = 0.7f), ok.placements[1].copy(scale = 1.4f)))
        assertNull(sized.validationError())
        assertNull(ok.copy(placements = listOf(ok.placements[0].copy(scale = MIN_PLACEMENT_SCALE)), height = 0.1f).validationError())
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

    @Test
    fun `終わりなく積んだ塔：同じパーツを何度も置いた 100 個までは通し、台の幅は決まった範囲だけ通す（決定 66）`() {
        val part = parts[0]
        val endless = ok.copy(
            parts = listOf(part),
            placements = List(MAX_TOWER_PARTS) { i -> Placement(part.id, x = 0f, y = 0.1f * i, angleRad = 0f) },
            platformWidth = 2.8f,
        )
        assertNull(endless.validationError())
        assertEquals(100, MAX_TOWER_PARTS)
        assertNull(ok.copy(platformWidth = MIN_PLATFORM_WIDTH).validationError())
        assertNull(ok.copy(platformWidth = MAX_PLATFORM_WIDTH).validationError())
        bad(ok.copy(platformWidth = 0f), "platformWidth")
        bad(ok.copy(platformWidth = 11f), "platformWidth")
        bad(ok.copy(platformWidth = Float.NaN), "platformWidth")
    }

    /** 物理で実際に積んで止まった塔（アプリが送るのと同じ作り方。見本のパーツを、形と向きを変えながら落とす） */
    private fun settledTower(): Tower {
        val world = StackWorld()
        val used = SampleParts.all
        for ((i, part) in used.withIndex()) {
            // 形・大きさ・少しの傾きを変えながら、塔の上に落とす
            world.place(part, x = (i % 3 - 1) * 0.05, y = world.height() + 0.8, angle = (i % 2) * 0.2, scale = 0.7 + 0.1 * i)
            var t = 0.0
            while (t < 10.0) {
                world.step(1.0 / 60)
                world.removeFallen()
                t += 1.0 / 60
                if (t > 1.0 && world.isSettled()) break
            }
        }
        val placements = world.placements()
        return Tower(
            parts = used.filter { p -> placements.any { it.partId == p.id } },
            placements = placements,
            height = world.height().toFloat(),
            createdAt = 0L,
        )
    }

    @Test
    fun `物理で積んだ塔と、天気のボーナスで大きくした塔は通る（決定 68・88）`() {
        val tower = settledTower()
        assertTrue(tower.placements.size >= 2, "台に残った塔で確かめる")
        assertNull(tower.validationError())
        assertNull(tower.enlarged(3.0).validationError())
        // 置き方から出したいちばん上は、物理の高さとほぼ同じ
        assertEquals(tower.height.toDouble(), tower.reachableTop(), 0.01)
    }

    @Test
    fun `置いたパーツが届かない高さは断る（決定 88）`() {
        bad(ok.copy(height = 5f), "taller than the placed parts can reach")
        bad(settledTower().let { it.copy(height = it.height * 1.1f + 0.15f) }, "taller than the placed parts can reach")
    }

    @Test
    fun `毎日の塔：未来の日付・配置より長い連続日数・始めた日より前からの連続日数は断る（決定 88）`() {
        val today = LocalDate.parse("2026-11-10")
        val daily = ok.copy(mode = TowerMode.DAILY, ownerId = "a".repeat(64), lastDay = "2026-11-10", streakDays = 2)
        assertNull(daily.validationError(today))
        // 時差で 1 日先までは通す
        assertNull(daily.copy(lastDay = "2026-11-11").validationError(today))
        assertEquals("lastDay is in the future", daily.copy(lastDay = "2026-11-12").validationError(today))
        assertEquals("lastDay is in the future", daily.copy(lastDay = "2099-01-01", streakDays = 1).validationError(today))
        bad(daily.copy(lastDay = "2026-02-30"), "needs lastDay")
        bad(daily.copy(streakDays = 3), "more than the placed parts")
        val many = daily.copy(placements = List(20) { ok.placements[it % 2] }, lastDay = "2026-11-02")
        assertNull(many.copy(streakDays = 2).validationError(today))
        assertEquals("streakDays is longer than the daily mode has existed", many.copy(streakDays = 3).validationError(today))
    }

    @Test
    fun `パーツの id が長すぎれば断る（決定 88）`() {
        val longId = "x".repeat(MAX_PART_ID_LENGTH + 1)
        bad(ok.copy(parts = listOf(parts[0].copy(id = longId), parts[1]), placements = listOf(ok.placements[0].copy(partId = longId), ok.placements[1])), "part id too long")
    }

    @Test
    fun `その遊び方で使わない項目は捨てる（決定 88）`() {
        val big = "x".repeat(100_000)
        val normal = ok.copy(rule = big, lastDay = big, streakDays = 9).withoutUnusedFields()
        assertNull(normal.rule)
        assertNull(normal.lastDay)
        assertEquals(0, normal.streakDays)
        val challenge = ok.copy(mode = TowerMode.CHALLENGE, rule = "manual-90-v1", lastDay = big, streakDays = 9).withoutUnusedFields()
        assertEquals("manual-90-v1", challenge.rule)
        assertNull(challenge.lastDay)
        val daily = ok.copy(mode = TowerMode.DAILY, rule = big, lastDay = "2026-11-10", streakDays = 2).withoutUnusedFields()
        assertNull(daily.rule)
        assertEquals("2026-11-10", daily.lastDay)
    }

    @Test
    fun `毎日の塔は 100 日を超えても送れる（パーツは 500 個まで。決定 88）`() {
        val days = (1..MAX_DAILY_TOWER_PARTS).map { Part(id = "d$it", points = listOf(Pt(-0.3f, 0f), Pt(0.3f, 0f))) }
        val daily = ok.copy(
            mode = TowerMode.DAILY, ownerId = "a".repeat(64), lastDay = "2026-11-10", streakDays = 1,
            parts = days,
            placements = days.mapIndexed { i, p -> Placement(p.id, x = 0f, y = 0.1f * i, angleRad = 0f) },
            height = 0.1f * (days.size - 1),
        )
        assertNull(daily.validationError(LocalDate.parse("2026-11-10")))
        bad(daily.copy(parts = days + Part(id = "extra", points = listOf(Pt(0f, 0f)))), "too many parts")
        // 通常は今までどおり 100 まで
        bad(daily.copy(mode = TowerMode.NORMAL), "too many parts")
    }
}
