package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.sample.SampleParts
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
        repo.add(walk) // 起動直後、読み込みがまだ走っていないうちに採集した（#10 で直した競合）
        advanceUntilIdle()

        assertEquals(saved + walk, repo.parts.value)
        assertEquals(saved + walk, read(f))
    }

    @Test
    fun `restoring samples brings back only the deleted ones`() = runTest {
        val f = file()
        val repo = repository(f)
        advanceUntilIdle()
        repo.add(walk)
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
        repo.add(walk)

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
        repo.add(walk)
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
        repo.add(walk)
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
    fun `a part collected and named before loading finishes keeps its name`() = runTest {
        val f = file()
        write(f, SampleParts.all)

        val repo = repository(f)
        repo.add(walk)
        repo.rename(walk.id, "初夏を刺す直進") // 起動直後に採集して、すぐ名前が届いた
        advanceUntilIdle()

        assertEquals("初夏を刺す直進", read(f).single { it.id == walk.id }.name)
    }
}
