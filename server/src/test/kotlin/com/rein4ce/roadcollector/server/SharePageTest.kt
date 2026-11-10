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
        // 字体はこのサーバから配る（決定 95。前は Google Fonts から読んでいた）
        assertContains(html, "src: url(\"${SharePage.FONT_PATH}\") format(\"woff\")")
        assertContains(html, "font-family: \"MichitsumiRounded\"")
        assertFalse(html.contains("googleapis"))
        assertFalse(html.contains("gstatic"))
        assertContains(html, """<span class="tag">通常</span>""")
        assertContains(html, "--face: #A15D36")
        val challenge = SharePage.render(tower.copy(mode = TowerMode.CHALLENGE, rule = "manual-90-v1"), "https://example.test")
        assertContains(challenge, """<span class="tag">90 秒チャレンジ</span>""")
        assertContains(challenge, "--face: #3F7970")
    }

    @Test
    fun `本文の一言にはアプリの名前を付けず、プレビューの説明には付ける`() {
        val html = SharePage.render(tower, "https://example.test")
        // 作者名だけ端末の字で出す（切り出した字体に入っていない字で、名前の中で字体が混ざらないように）
        assertContains(html, """<p class="desc"><span class="name">みちつみ</span> さんが 2 個の散歩パーツで積みました</p>""")
        val noName = SharePage.render(tower.copy(author = null), "https://example.test")
        assertContains(noName, """<p class="desc">2 個の散歩パーツで積みました</p>""")
        val san = SharePage.render(tower.copy(author = "みちさん"), "https://example.test")
        assertContains(san, """<span class="name">みちさん</span> が 2 個の散歩パーツで積みました""")
        assertContains(html, """<meta property="og:description" content="みちつみ さんが 2 個の散歩パーツで積みました — ミチツミ">""")
    }

    @Test
    fun `毎日 1 つずつ積むは、何日まで何日続けたかを出す`() {
        val daily = tower.copy(mode = TowerMode.DAILY, lastDay = "2026-11-10", streakDays = 3)
        assertEquals("11/10 まで 3 日続けて積みました", SharePage.streakLine(daily))
        assertContains(SharePage.render(daily, "https://example.test"), "11/10 まで 3 日続けて積みました")
        // 1 日だけのときは「1 日続けて」にしない（決定 95）
        assertEquals("11/10 に積みました", SharePage.streakLine(daily.copy(streakDays = 1)))
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
