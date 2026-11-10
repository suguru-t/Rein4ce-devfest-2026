package com.rein4ce.roadcollector.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.Test

/** 一覧を覚えておく入れ物（決定 88） */
class TtlCacheTest {

    @Test
    fun `期限の間は覚えた値を返し、切れたら取り直す`() = runTest {
        var now = 0L
        var loads = 0
        val cache = TtlCache<String, Int>(ttlMs = 1_000, now = { now })
        assertEquals(1, cache.get("normal") { ++loads })
        now = 999
        assertEquals(1, cache.get("normal") { ++loads })
        assertEquals(1, loads)
        now = 1_000
        assertEquals(2, cache.get("normal") { ++loads })
        // 鍵ごとに別
        assertEquals(3, cache.get("daily") { ++loads })
    }

    @Test
    fun `invalidate したら次で取り直す`() = runTest {
        var loads = 0
        val cache = TtlCache<String, Int>(ttlMs = 60_000, now = { 0L })
        cache.get("normal") { ++loads }
        cache.invalidate("normal")
        assertEquals(2, cache.get("normal") { ++loads })
    }

    @Test
    fun `同時に頼まれても取るのは 1 回で、ほかはその結果を待つ`() = runTest {
        var loads = 0
        val gate = CompletableDeferred<Unit>()
        val cache = TtlCache<String, Int>(ttlMs = 60_000, now = { 0L })
        val a = async { cache.get("normal") { loads++; gate.await(); 7 } }
        val b = async { cache.get("normal") { loads++; gate.await(); 8 } }
        runCurrent()
        gate.complete(Unit)
        assertEquals(7, a.await())
        assertEquals(7, b.await())
        assertEquals(1, loads)
    }

    @Test
    fun `取っている最中に invalidate されたら、その結果は覚えない（書き込む前の一覧を覚えない）`() = runTest {
        var loads = 0
        val gate = CompletableDeferred<Unit>()
        val cache = TtlCache<String, Int>(ttlMs = 60_000, now = { 0L })
        val a = async { cache.get("normal") { loads++; gate.await(); 1 } }
        runCurrent()
        cache.invalidate("normal") // 取っている間に、このサーバで書き込んだ
        gate.complete(Unit)
        assertEquals(1, a.await(), "頼んだ人には返す")
        assertEquals(2, cache.get("normal") { ++loads }, "次は取り直す")
    }
}
