package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.sample.SampleParts
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test

/** 共有ページの見た目をアプリにそろえる（決定 94） */
class SharePageTest {

    private val parts = SampleParts.all.take(2)
    private val tower = Tower(
        id = "abc12345",
        parts = parts,
        placements = parts.mapIndexed { i, p -> Placement(p.id, x = 0f, y = 0.1f + i * 0.4f, angleRad = 0f) },
        height = 0.8f,
        createdAt = 0L,
        author = "みちつみ",
    )

    @Test
    fun `アプリと同じ字体と、遊び方の名前の札と色が入る`() {
        val html = SharePage.render(tower, "https://example.test")
        assertContains(html, "family=M+PLUS+Rounded+1c")
        assertContains(html, "font-family: \"M PLUS Rounded 1c\"")
        assertContains(html, """<span class="tag">通常</span>""")
        assertContains(html, "--face: #A15D36")
        val challenge = SharePage.render(tower.copy(mode = TowerMode.CHALLENGE, rule = "manual-90-v1"), "https://example.test")
        assertContains(challenge, """<span class="tag">90 秒チャレンジ</span>""")
        assertContains(challenge, "--face: #3F7970")
    }

    @Test
    fun `本文の一言にはアプリの名前を付けず、プレビューの説明には付ける`() {
        val html = SharePage.render(tower, "https://example.test")
        assertContains(html, """<p class="desc">みちつみ さんが 2 個の散歩パーツで積みました</p>""")
        assertContains(html, """<meta property="og:description" content="みちつみ さんが 2 個の散歩パーツで積みました — ミチツミ">""")
    }

    @Test
    fun `毎日 1 つずつ積むは、何日まで何日続けたかを出す`() {
        val daily = tower.copy(mode = TowerMode.DAILY, lastDay = "2026-11-10", streakDays = 3)
        assertEquals("11/10 まで 3 日続けて積みました", SharePage.streakLine(daily))
        assertContains(SharePage.render(daily, "https://example.test"), "11/10 まで 3 日続けて積みました")
        assertNull(SharePage.streakLine(tower))
        assertNull(SharePage.streakLine(daily.copy(lastDay = "きのう")))
        assertFalse(SharePage.render(tower, "https://example.test").contains("class=\"streak\""))
    }

    @Test
    fun `積み木の側面は面の色を 3 割黒に寄せる（アプリの Pop の side と同じ）`() {
        assertEquals("#9C551D", SharePage.side("E07A2A"))
        assertEquals("#000000", SharePage.side("#000000"))
    }
}
