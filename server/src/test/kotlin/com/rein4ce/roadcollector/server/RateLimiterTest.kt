package com.rein4ce.roadcollector.server

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/** 回数制限の数え方（端末ごと・合計・1 分ごとの数え直し・待つ秒数）。時刻は偽物で進める */
class RateLimiterTest {

    private var now = 1_800_000_000_000L // 分の切れ目ちょうど（1_800_000_000_000 は 60_000 で割り切れる）

    private fun limiter(perClient: Int, total: Int) = RateLimiter(perClient = perClient, total = total, now = { now })

    @Test
    fun `端末ごとの上限までは通し、超えたら止める`() {
        val l = limiter(perClient = 3, total = 100)
        repeat(3) { assertNull(l.acquire("owner:a"), "${it + 1} 回目") }
        assertNotNull(l.acquire("owner:a"))
        // ほかの端末は数えが別
        assertNull(l.acquire("owner:b"))
    }

    @Test
    fun `合計の上限を超えたら、どの端末も止める（印を変えながら叩かれても頭打ちになる）`() {
        val l = limiter(perClient = 100, total = 5)
        repeat(5) { assertNull(l.acquire("owner:$it")) }
        assertNotNull(l.acquire("owner:new"))
    }

    @Test
    fun `次の分になれば数え直す`() {
        val l = limiter(perClient = 1, total = 100)
        assertNull(l.acquire("ip:1.2.3.4"))
        assertNotNull(l.acquire("ip:1.2.3.4"))
        now += 59_999
        assertNotNull(l.acquire("ip:1.2.3.4"), "同じ分のうちは止めたまま")
        now += 1
        assertNull(l.acquire("ip:1.2.3.4"), "次の分で通る")
    }

    @Test
    fun `止めたときは、次の分までの秒数を返す（1 秒以上）`() {
        val l = limiter(perClient = 1, total = 100)
        now += 15_000 // 分の切れ目から 15 秒
        l.acquire("owner:a")
        assertEquals(45L, l.acquire("owner:a"))
        now += 44_900 // 次の分まであと 0.1 秒
        assertEquals(1L, l.acquire("owner:a"))
    }

    @Test
    fun `止めた回は数えない（止められている間に叩き続けても、次の分には通る）`() {
        val l = limiter(perClient = 1, total = 2)
        l.acquire("owner:a")
        repeat(10) { l.acquire("owner:a") }
        assertNull(l.acquire("owner:b"), "a が止められた回は合計に入らないので、b はまだ通る")
    }

    @Test
    fun `本番の上限と、切ったときの説明`() {
        val s = RateLimits.standard()
        assertEquals(10, s.towersPost?.perClient)
        assertEquals(60, s.towersGet?.perClient)
        assertEquals(20, s.partName?.perClient)
        assertEquals(120, s.partName?.total)
        assertEquals(10, s.nickname?.perClient)
        assertEquals(60, s.nickname?.total)
        assertEquals("off", RateLimits.OFF.description)
    }
}
