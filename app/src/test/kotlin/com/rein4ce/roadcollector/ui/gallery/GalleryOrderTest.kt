package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.TowerMode
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

    @Test
    fun `連続日数は、最後に積んだ日が今日か昨日のときだけ数える（決定 77）`() {
        val today = java.time.LocalDate.parse("2026-11-10")
        fun daily(last: String?, streak: Int) = tower("d", 1f).copy(mode = TowerMode.DAILY, lastDay = last, streakDays = streak)
        assertEquals(4, daily("2026-11-10", 4).currentStreak(today))
        assertEquals(4, daily("2026-11-09", 4).currentStreak(today))
        assertEquals("2 日あいたら 0", 0, daily("2026-11-08", 4).currentStreak(today))
        assertEquals(0, daily(null, 4).currentStreak(today))
        assertEquals(0, daily("きのう", 4).currentStreak(today))
    }

    @Test
    fun `遊び方ごとの物差し：通常と 3 分チャレンジは高い順・多い順、毎日は高い順・連続日数順（決定 77）`() {
        assertEquals(listOf(TowerSort.HEIGHT, TowerSort.PARTS), TowerSort.comparableFor(TowerMode.NORMAL))
        assertEquals(listOf(TowerSort.HEIGHT, TowerSort.PARTS), TowerSort.comparableFor(TowerMode.CHALLENGE))
        assertEquals(listOf(TowerSort.HEIGHT, TowerSort.STREAK), TowerSort.comparableFor(TowerMode.DAILY))
        // 連続日数順：今も続いている日数で並べ、途切れた塔は高くても下
        val today = java.time.LocalDate.now()
        val towers = listOf(
            tower("tall-broken", 9f).copy(mode = TowerMode.DAILY, lastDay = today.minusDays(5).toString(), streakDays = 9),
            tower("short-going", 1f).copy(mode = TowerMode.DAILY, lastDay = today.toString(), streakDays = 3),
            tower("mid-going", 2f).copy(mode = TowerMode.DAILY, lastDay = today.minusDays(1).toString(), streakDays = 2),
        )
        assertEquals(listOf("short-going", "mid-going", "tall-broken"), sortTowers(towers, TowerSort.STREAK).map { it.id })
        assertEquals("3", TowerSort.STREAK.format(TowerSort.STREAK.valueOf(towers[1])))
    }

    @Test
    fun `メダルの順位：カードの文と同じ高い順の並びで、自分のいちばん良い順位。無ければ null（決定 82）`() {
        val loaded = TowerGallery.State.Loaded(listOf(tower("a", 3f), tower("b", 2f), tower("c", 1f), tower("d", 2.5f)))
        val order = heightOrder(loaded, TowerMode.NORMAL)
        assertEquals(listOf("a", "d", "b", "c"), order.map { it.id })
        assertEquals(3, myRank(order) { it.id == "b" })
        assertEquals("自分のタワーが 2 つあれば、良いほう", 2, myRank(order) { it.id == "b" || it.id == "d" })
        assertEquals(null, myRank(order) { false })
        assertEquals("読み込めていなければ並びは空", emptyList<TowerSummary>(), heightOrder(TowerGallery.State.Loading, TowerMode.NORMAL))
        // カードの文も同じ並びの順位
        assertEquals("あなたの最高 2.00 m・3 位", homeRecord(loaded, TowerMode.NORMAL, order) { it.id == "b" })
    }

    @Test
    fun `ホームの行のようす：自分のがあれば最高と順位、無ければ 1 番、3 分チャレンジは今の決まりだけ（決定 77）`() {
        val loaded = TowerGallery.State.Loaded(listOf(tower("a", 3f), tower("b", 2f), tower("c", 1f)))
        assertEquals("あなたの最高 2.00 m・2 位", homeRecord(loaded, TowerMode.NORMAL) { it.id == "b" })
        assertEquals("1 位 3.00 m・全部で 3 個", homeRecord(loaded, TowerMode.NORMAL) { false })
        assertEquals("まだタワーがありません", homeRecord(TowerGallery.State.Loaded(emptyList()), TowerMode.NORMAL) { false })
        assertEquals("読み込み中…", homeRecord(TowerGallery.State.Loading, TowerMode.DAILY) { false })
        // 3 分チャレンジ：前の決まりの記録は数えない
        val challenge = TowerGallery.State.Loaded(
            listOf(
                tower("old", 9f).copy(mode = TowerMode.CHALLENGE, rule = "manual-180-v5"),
                tower("now", 4f).copy(mode = TowerMode.CHALLENGE, rule = com.rein4ce.roadcollector.core.challenge.ChallengeRules.ID),
            ),
        )
        assertEquals("1 位 4.00 m・全部で 1 個", homeRecord(challenge, TowerMode.CHALLENGE) { false })
        // 毎日：自分の塔と今も続いている日数
        val today = java.time.LocalDate.now().toString()
        val daily = TowerGallery.State.Loaded(listOf(tower("d", 1.5f).copy(mode = TowerMode.DAILY, lastDay = today, streakDays = 4)))
        assertEquals("あなたの塔 1.50 m・連続 4 日", homeRecord(daily, TowerMode.DAILY) { true })
    }
}
