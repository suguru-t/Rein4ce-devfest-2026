package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 「みんなのタワー」の一覧の取り方：起動時の先読みと、開いたとき・「再読み込み」の取り直し */
@OptIn(ExperimentalCoroutinesApi::class)
class TowerGalleryTest {

    private fun summary(id: String, height: Float) = TowerSummary(id = id, height = height, partCount = 1, createdAt = 0L, author = null)

    /** 一覧の応答を手で返せるようにした係。何回取りに来たかも数える */
    private class FakeServer {
        val responses = ArrayList<CompletableDeferred<List<TowerSummary>>>()
        val calls: Int get() = responses.size
        suspend fun list(): List<TowerSummary> {
            val d = CompletableDeferred<List<TowerSummary>>()
            responses += d
            return d.await()
        }
    }

    private fun loaded(g: TowerGallery) = (g.state as TowerGallery.State.Loaded).towers.map { it.id }

    @Test
    fun `先読みは何度呼んでも 1 回しか取らない`() = runTest {
        val server = FakeServer()
        val g = TowerGallery(listTowers = { server.list() }, getTower = { error("unused") }, scope = this)
        g.prefetch()
        g.prefetch() // 画面の回転などでもう一度
        runCurrent()
        assertEquals(1, server.calls)
        assertTrue(g.refreshing)

        server.responses[0].complete(listOf(summary("a", 1f), summary("b", 2f)))
        runCurrent()
        assertEquals(listOf("b", "a"), loaded(g)) // 高い順
        assertFalse(g.refreshing)
        g.prefetch() // 取れたあとは取り直さない
        runCurrent()
        assertEquals(1, server.calls)
    }

    @Test
    fun `先読みの最中に開いても止めずにその結果を出し、そのあと 1 回だけ取り直す`() = runTest {
        val server = FakeServer()
        val g = TowerGallery(listTowers = { server.list() }, getTower = { error("unused") }, scope = this)
        g.prefetch()
        runCurrent()
        g.refresh() // 「みんな」を開いた
        g.refresh() // タブを行き来してもう一度開いた
        runCurrent()
        assertEquals(1, server.calls) // 先読みはそのまま、新しく取りには行かない

        server.responses[0].complete(listOf(summary("a", 1f)))
        runCurrent()
        assertEquals(listOf("a"), loaded(g)) // 先読みの結果がすぐ出る
        assertEquals(2, server.calls) // そのあと 1 回だけ取り直す（2 回開いても 1 回）
        assertTrue(g.refreshing)

        server.responses[1].complete(listOf(summary("a", 1f), summary("new", 3f)))
        runCurrent()
        assertEquals(listOf("new", "a"), loaded(g))
        assertFalse(g.refreshing)
        assertEquals(2, server.calls)
    }

    @Test
    fun `先読みが失敗していたら、開いたときに取り直す`() = runTest {
        val server = FakeServer()
        val g = TowerGallery(listTowers = { server.list() }, getTower = { error("unused") }, scope = this)
        g.prefetch()
        runCurrent()
        server.responses[0].completeExceptionally(IOException("offline"))
        runCurrent()
        assertTrue(g.state is TowerGallery.State.Error)
        g.prefetch() // 回転しても勝手には取り直さない
        runCurrent()
        assertEquals(1, server.calls)

        g.refresh() // 「みんな」を開いた
        runCurrent()
        assertEquals(2, server.calls)
        assertEquals(TowerGallery.State.Loading, g.state)
        server.responses[1].complete(emptyList())
        runCurrent()
        assertEquals(emptyList<String>(), loaded(g))
    }

    @Test
    fun `先読みの最中に開いて、先読みが失敗したときは続けて取り直さない`() = runTest {
        val server = FakeServer()
        val g = TowerGallery(listTowers = { server.list() }, getTower = { error("unused") }, scope = this)
        g.prefetch()
        runCurrent()
        g.refresh()
        server.responses[0].completeExceptionally(IOException("offline"))
        runCurrent()
        assertTrue(g.state is TowerGallery.State.Error)
        assertFalse(g.refreshing)
        assertEquals(1, server.calls) // 10 秒待ってだめだったあと、さらに 10 秒待たせない。「再読み込み」で取る
    }

    @Test
    fun `一覧が出ているときに取り直しに失敗しても、一覧は残る`() = runTest {
        val server = FakeServer()
        val g = TowerGallery(listTowers = { server.list() }, getTower = { error("unused") }, scope = this)
        g.refresh()
        runCurrent()
        server.responses[0].complete(listOf(summary("a", 1f)))
        runCurrent()

        g.refresh()
        runCurrent()
        assertTrue(g.refreshing)
        assertEquals(listOf("a"), loaded(g)) // 取り直し中も一覧は消えない
        server.responses[1].completeExceptionally(IOException("offline"))
        runCurrent()
        assertEquals(listOf("a"), loaded(g))
        assertTrue(g.refreshFailed)
        assertFalse(g.refreshing)

        g.refresh()
        runCurrent()
        assertFalse(g.refreshFailed) // 取り直し始めたら赤い印は消す
        server.responses[2].complete(listOf(summary("a", 1f), summary("b", 2f)))
        runCurrent()
        assertEquals(listOf("b", "a"), loaded(g))
    }

    @Test
    fun `絵の中身は 1 基 1 回しか取らず、取れなかったものは再読み込みまで取り直さない`() = runTest {
        val fetched = ArrayList<String>()
        val g = TowerGallery(
            listTowers = { emptyList() },
            getTower = { id ->
                fetched += id
                if (id == "bad") throw IOException("broken") else Tower(parts = emptyList(), placements = emptyList(), height = 1f, createdAt = 0L)
            },
            scope = this,
        )
        g.loadSketch("ok")
        g.loadSketch("ok")
        g.loadSketch("bad")
        runCurrent()
        assertEquals(listOf("ok", "bad"), fetched)
        assertTrue("ok" in g.sketches)
        assertNull(g.sketches["bad"])

        g.loadSketch("bad") // スクロールでまた見えた：取り直さない
        runCurrent()
        assertEquals(2, fetched.size)

        g.refresh()
        runCurrent()
        g.loadSketch("bad") // 「再読み込み」のあとは取り直す
        g.loadSketch("ok") // 取れたものは取り直さない
        runCurrent()
        assertEquals(listOf("ok", "bad", "bad"), fetched)
    }

    @Test
    fun `遊び方ごとに別の一覧を取り、見ている遊び方の一覧を出す（決定 77）`() = runTest {
        val asked = mutableListOf<TowerMode>()
        val g = TowerGallery(
            listTowers = { mode -> asked += mode; listOf(summary("${mode.wire}-1", 1f)) },
            getTower = { error("unused") },
            scope = this,
        )
        g.refresh(TowerMode.NORMAL)
        g.refresh(TowerMode.DAILY)
        runCurrent()
        assertEquals(listOf(TowerMode.NORMAL, TowerMode.DAILY), asked)
        assertEquals(listOf("normal-1"), loaded(g))
        g.mode = TowerMode.DAILY
        assertEquals(listOf("daily-1"), loaded(g))
        g.mode = TowerMode.CHALLENGE
        assertTrue("まだ取っていない遊び方は読み込み中", g.state is TowerGallery.State.Loading)
        // 先読みは通常だけ
        g.prefetch()
        runCurrent()
        assertEquals(2, asked.size)
    }

    @Test
    fun `毎日の塔の絵は、一覧を取り直すと前の絵を出したまま取り直す。取っている最中なら、取れたあとにもう一度取る（決定 88）`() = runTest {
        var version = 1f
        val fetched = ArrayList<CompletableDeferred<Tower>>()
        val g = TowerGallery(
            listTowers = { listOf(summary("d1", version).copy(mode = TowerMode.DAILY)) },
            getTower = { _ -> CompletableDeferred<Tower>().also { fetched += it }.await() },
            scope = this,
        )
        fun tower(h: Float) = Tower(parts = emptyList(), placements = emptyList(), height = h, createdAt = 0L)

        g.refresh(TowerMode.DAILY)
        runCurrent()
        g.loadSketch("d1")
        runCurrent()
        fetched[0].complete(tower(1f))
        runCurrent()
        assertEquals(1f, g.sketches["d1"]!!.height)

        // 再読み込み（一覧は前と同じ）：前の絵は消さずに取り直す。前は消えて、画面からは取りに行かないので点線のままだった
        g.refresh(TowerMode.DAILY)
        runCurrent()
        assertEquals("取れるまでは前の絵", 1f, g.sketches["d1"]!!.height)
        assertEquals(2, fetched.size)
        fetched[1].complete(tower(2f))
        runCurrent()
        assertEquals(2f, g.sketches["d1"]!!.height)

        // 取っている最中に一覧が取り直された：古い形が先に届いても、そのあともう一度取る
        version = 3f
        g.refresh(TowerMode.DAILY)
        runCurrent()
        assertEquals(3, fetched.size)
        g.refresh(TowerMode.DAILY)
        runCurrent()
        assertEquals("取っている最中は重ねて取りに行かない", 3, fetched.size)
        fetched[2].complete(tower(2f))
        runCurrent()
        assertEquals(4, fetched.size)
        fetched[3].complete(tower(3f))
        runCurrent()
        assertEquals(3f, g.sketches["d1"]!!.height)
    }
}
