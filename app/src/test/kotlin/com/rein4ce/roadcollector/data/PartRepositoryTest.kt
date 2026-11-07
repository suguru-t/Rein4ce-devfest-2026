package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.ui.inventory.inventoryOrder
import com.rein4ce.roadcollector.ui.inventory.moved
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 手持ちの保存（parts.json）のテスト。Android なしで動く。
 * 読み込み・保存はテスト用の時計（TestScope）で走らせ、advanceUntilIdle() で「ここまでの非同期処理を全部終わらせる」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val serializer = ListSerializer(Part.serializer())

    /** 採集したパーツの例（名前なし） */
    private val walk = Part(
        id = "walk-1",
        points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)),
        kind = Kind.BAR,
        distanceM = 341,
        walkedAt = 1_762_400_000_000L,
    )

    /** [walk] を歩いた場所の例 */
    private val place = PartPlace(lat = 35.7501, lng = 139.8055, sizeM = 341.0)

    private fun file(): File = File(tmp.root, "parts.json")

    private fun read(f: File): List<Part> = AppJson.decodeFromString(serializer, f.readText())

    private fun write(f: File, parts: List<Part>) = f.writeText(AppJson.encodeToString(serializer, parts))

    /** 失敗の記録は何もしない（android.util.Log はテストでは使えない） */
    private fun TestScope.repository(f: File) = PartRepository(f, this) { _, _ -> }

    @Test
    fun `first launch seeds the samples and saves them`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()

        assertEquals(SampleParts.all, repo.parts.value)
        assertEquals(SampleParts.all, read(f))
    }

    @Test
    fun `isLoaded turns true only after loading finishes`() = runTest {
        val f = file()
        write(f, SampleParts.all.take(2))

        val repo = repository(f)
        // 読み込みはまだ走っていない。手持ちは空に見えるが、画面はこれを「0 個」と扱ってはいけない
        assertFalse(repo.isLoaded.value)
        assertTrue(repo.parts.value.isEmpty())

        advanceUntilIdle()
        assertTrue(repo.isLoaded.value)
        assertEquals(SampleParts.all.take(2), repo.parts.value)
    }

    @Test
    fun `isLoaded turns true even when the saved file is broken`() = runTest {
        val f = file()
        f.writeText("{ これは JSON ではない")

        val repo = repository(f)
        advanceUntilIdle()

        // 読めなければ見本で始め直し、読み込みは終わったことになる（画面が「読み込み中…」のまま止まらない）
        assertTrue(repo.isLoaded.value)
        assertEquals(SampleParts.all, repo.parts.value)
    }

    @Test
    fun `a part added before loading finishes is kept with the saved ones`() = runTest {
        val f = file()
        val saved = SampleParts.all.take(2)
        write(f, saved)

        val repo = repository(f)
        repo.add(walk, place) // 起動直後、読み込みがまだ走っていないうちに採集した（#10 で直した競合）
        advanceUntilIdle()

        assertEquals(saved + walk, repo.parts.value)
        assertEquals(saved + walk, read(f))
        assertEquals(place, repository(f).also { advanceUntilIdle() }.placeOf(walk.id)) // 場所も残る
    }

    @Test
    fun `restoring samples brings back only the deleted ones`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        repo.remove(SampleParts.all[1].id)
        repo.restoreMissingSamples()
        advanceUntilIdle()

        // 採集したパーツは残り、消したサンプルだけが戻る
        assertEquals((SampleParts.all + walk).map { it.id }.toSet(), repo.parts.value.map { it.id }.toSet())
        assertEquals(SampleParts.all.size + 1, read(f).size)
    }

    @Test
    fun `rename saves the trimmed name and it survives a restart`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)

        assertTrue(repo.rename(walk.id, "  駅前の寄り道 "))
        advanceUntilIdle()
        assertEquals("駅前の寄り道", repo.parts.value.single { it.id == walk.id }.name)

        // アプリを開き直しても名前が残る
        val again = repository(f)
        advanceUntilIdle()
        assertEquals("駅前の寄り道", again.parts.value.single { it.id == walk.id }.displayName)
    }

    @Test
    fun `blank rename removes the name`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        repo.rename(walk.id, "駅前の寄り道")

        assertTrue(repo.rename(walk.id, "   "))
        advanceUntilIdle()

        val p = read(f).single { it.id == walk.id }
        assertNull(p.name)
        assertEquals("341 m の散歩", p.displayName)
    }

    @Test
    fun `rename of a deleted part does nothing`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        repo.remove(walk.id)
        advanceUntilIdle()
        val before = read(f)

        // 名前が届く前に削除されたパーツ
        assertFalse(repo.rename(walk.id, "駅前の寄り道"))
        advanceUntilIdle()
        assertEquals(before, repo.parts.value)
        assertEquals(before, read(f))
    }

    @Test
    fun `an arranged order is saved and survives a restart`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        val arranged = listOf(SampleParts.all[2].id, walk.id) + SampleParts.all.map { it.id }.filter { it != SampleParts.all[2].id }

        repo.setOrder(arranged)
        advanceUntilIdle()
        assertEquals(arranged, repo.order.value)

        // アプリを開き直しても残る（parts.json とは別のファイル）
        val again = repository(f)
        advanceUntilIdle()
        assertEquals(arranged, again.order.value)
        assertTrue(File(tmp.root, "parts_order.json").exists())
    }

    @Test
    fun `setOrder drops unknown ids and duplicates`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()

        repo.setOrder(listOf("nothing-here", SampleParts.all[1].id, SampleParts.all[1].id, SampleParts.all[0].id))
        assertEquals(listOf(SampleParts.all[1].id, SampleParts.all[0].id), repo.order.value)
    }

    @Test
    fun `the walked place is saved apart from the parts and survives a restart`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        advanceUntilIdle()
        assertEquals(place, repo.placeOf(walk.id))
        // 場所は parts.json（パーツの形式。タワーと一緒にサーバへ送られる）には入らない
        val partsJson = f.readText()
        assertFalse(partsJson.contains("35.7501") || partsJson.contains("\"lat\""))
        assertTrue(File(tmp.root, "parts_places.json").exists())

        val again = repository(f)
        advanceUntilIdle()
        assertEquals(place, again.placeOf(walk.id))
    }

    @Test
    fun `deleting a part removes its place too`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        repo.remove(walk.id)
        advanceUntilIdle()

        assertNull(repo.placeOf(walk.id))
        val again = repository(f)
        advanceUntilIdle()
        assertNull(again.placeOf(walk.id))
    }

    @Test
    fun `samples have places and older parts without a place have none`() = runTest {
        val f = file()
        write(f, SampleParts.all + walk) // 場所を残す前（#30 より前）に採集したパーツ
        val repo = repository(f)
        advanceUntilIdle()

        assertEquals(SampleParts.places[SampleParts.all.first().id], repo.placeOf(SampleParts.all.first().id))
        assertNull(repo.placeOf(walk.id))
    }

    @Test
    fun `places of parts that are gone are erased from the file too`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        advanceUntilIdle()

        f.writeText("{ broken") // parts.json が壊れて、見本から作り直しになった
        val again = repository(f)
        advanceUntilIdle()

        assertNull(again.placeOf(walk.id))
        // もう手持ちに無いパーツの、歩いた場所（緯度・経度）をスマホに残し続けない
        assertFalse(File(tmp.root, "parts_places.json").readText().contains("35.7501"))
    }

    @Test
    fun `deleting a part removes it from the arranged order`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk, place)
        repo.setOrder(listOf(walk.id) + SampleParts.all.map { it.id })
        repo.remove(walk.id)
        advanceUntilIdle()

        assertEquals(SampleParts.all.map { it.id }, repo.order.value)
        val again = repository(f)
        advanceUntilIdle()
        assertEquals(SampleParts.all.map { it.id }, again.order.value)
    }

    /**
     * ドラッグ中にパーツが足されたとき（デモ散歩がちょうど終わった等）。
     * 画面は指を離したときに「画面の並び（足されたパーツを知らない）で保存 → 最新の内容で並べ直す」ので、
     * 動かさずに離しても、動かして離しても、足されたパーツが先頭に出ることを確かめる。
     */
    @Test
    fun `a part added during a drag shows at the top once the drag ends`() = runTest {
        val repo = repository(file())
        advanceUntilIdle()
        val onScreen = inventoryOrder(repo.parts.value, repo.order.value).map { it.id }

        repo.add(walk, place)
        advanceUntilIdle()

        // 動かさずに離した（保存しない）
        assertEquals(listOf(walk.id) + onScreen, inventoryOrder(repo.parts.value, repo.order.value).map { it.id })

        // 1 枚目を 3 枚目へ動かして離した（足される前の並びで保存する）
        repo.setOrder(onScreen.moved(0, 2))
        assertEquals(listOf(walk.id) + onScreen.moved(0, 2), inventoryOrder(repo.parts.value, repo.order.value).map { it.id })
    }

    @Test
    fun `the old move-to-top file is ignored without breaking loading`() = runTest {
        val f = file()
        write(f, SampleParts.all)
        // #20 の形式（id → 時刻の表）。今は id の列なので読めないが、読み込みは終わって並べ替えなしから始まる
        File(tmp.root, "parts_order.json").writeText("""{"sample-ring":1793963153833}""")

        val repo = repository(f)
        advanceUntilIdle()
        assertTrue(repo.isLoaded.value)
        assertTrue(repo.order.value.isEmpty())
        assertEquals(SampleParts.all, repo.parts.value)
    }

    @Test
    fun `a part collected and named before loading finishes keeps its name`() = runTest {
        val f = file()
        write(f, SampleParts.all)

        val repo = repository(f)
        repo.add(walk, place)
        repo.rename(walk.id, "初夏を刺す直進") // 起動直後に採集して、すぐ名前が届いた
        advanceUntilIdle()

        assertEquals("初夏を刺す直進", read(f).single { it.id == walk.id }.name)
    }
}
