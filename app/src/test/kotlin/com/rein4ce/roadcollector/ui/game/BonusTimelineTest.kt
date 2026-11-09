package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.physics.WeatherBonus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 終わったときの天気のボーナスの見せ方の時間割（決定 68）：札を 1 つずつ出してから、ゆっくり大きくする */
class BonusTimelineTest {

    @Test
    fun `tags come one by one before the model grows`() {
        val timeline = BonusTimeline(listOf(20, 10, 10))
        assertEquals(0, timeline.tagsShown(0))
        assertEquals(0, timeline.tagsShown(BonusTimeline.TAGS_START_MS - 1))
        assertEquals(1, timeline.tagsShown(timeline.tagStartMs(0)))
        assertEquals(2, timeline.tagsShown(timeline.tagStartMs(1)))
        assertEquals(3, timeline.tagsShown(timeline.tagStartMs(2)))
        // 最後の札が出てから、少し待って大きくなり始める
        assertTrue(timeline.growStartMs > timeline.tagStartMs(2))
        assertEquals(1f, timeline.growth(timeline.growStartMs, 1.4), 0f)
        assertEquals(1.4f, timeline.growth(timeline.growEndMs, 1.4), 1e-6f)
    }

    @Test
    fun `the shown multiplier counts up with each tag`() {
        val timeline = BonusTimeline(listOf(20, 10, 10))
        assertEquals(1.0, timeline.shownMultiplier(0), 1e-9)
        assertEquals(1.2, timeline.shownMultiplier(timeline.tagStartMs(0)), 1e-9)
        assertEquals(1.3, timeline.shownMultiplier(timeline.tagStartMs(1)), 1e-9)
        assertEquals(1.4, timeline.shownMultiplier(timeline.totalMs), 1e-9)
    }

    @Test
    fun `the counted multiplier stops at the cap`() {
        val timeline = BonusTimeline(List(15) { 20 })
        assertEquals(WeatherBonus.MAX_MULTIPLIER, timeline.shownMultiplier(timeline.totalMs), 0.0)
    }

    @Test
    fun `growing is slow enough to follow`() {
        // 前は 0.7 秒で大きくなり、なぜ大きくなったのか分からなかった
        assertTrue(BonusTimeline.GROW_MS >= 1500)
        val timeline = BonusTimeline(listOf(10))
        assertTrue(timeline.growStartMs >= 1000) // 塔が落ち着いてから、札を見せる間がある
    }

    @Test
    fun `many tags come faster but the whole show stays short`() {
        val few = BonusTimeline(listOf(10, 10))
        val many = BonusTimeline(List(20) { 20 })
        assertEquals(BonusTimeline.MAX_TAG_MS, few.tagIntervalMs)
        assertEquals(BonusTimeline.MIN_TAG_MS, many.tagIntervalMs)
        assertTrue("total=${many.totalMs}", many.totalMs <= 7000)
    }

    @Test
    fun `tags pop in and fade out after growing`() {
        val timeline = BonusTimeline(listOf(20))
        val start = timeline.tagStartMs(0)
        assertEquals(0f, timeline.tagPop(0, start - 1), 0f)
        assertTrue(timeline.tagPop(0, start + BonusTimeline.TAG_POP_MS * 6 / 10) > 1f) // 少し大きくなってから
        assertEquals(1f, timeline.tagPop(0, start + BonusTimeline.TAG_POP_MS), 1e-6f) // 戻る
        assertEquals(1f, timeline.tagAlpha(timeline.growEndMs), 0f)
        assertEquals(0f, timeline.tagAlpha(timeline.totalMs), 0f)
    }
}
