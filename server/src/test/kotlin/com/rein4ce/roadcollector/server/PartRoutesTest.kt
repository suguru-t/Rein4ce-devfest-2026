package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartNameResponse
import com.rein4ce.roadcollector.core.model.Pt
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * POST /parts/name の約束事（200 { name } / 失敗は 503 / 変な入力は 400）を確かめる。
 * 本物の Gemini は呼ばず、決まった答えを返す偽物（FakePartNamer）を渡す。
 */
class PartRoutesTest {

    /** 決まった動きをする偽物。受け取ったパーツと、聞き直されたときの前の案を覚えておく */
    private class FakePartNamer(private val answer: suspend (Part) -> String?) : PartNamer {
        var received: Part? = null
        var calls = 0
        var retriedWith: String? = null
        override suspend fun name(part: Part): String? {
            received = part
            calls++
            return answer(part)
        }

        override suspend fun retryName(part: Part, tooLong: String): String? {
            retriedWith = tooLong
            return name(part)
        }
    }

    private val zigzag = Part(
        id = "p1",
        points = listOf(Pt(-0.3f, 0f), Pt(0f, 0.3f), Pt(0.3f, 0f)),
        kind = Kind.ZIGZAG,
        distanceM = 820,
        walkedAt = 1_793_900_000_000L,
    )

    private suspend fun HttpClient.postName(body: String): HttpResponse =
        post("/parts/name") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun Part.json() = AppJson.encodeToString(Part.serializer(), this)

    // --- ルート ---

    @Test
    fun `名前が付けば 200 で name を返し、パーツの中身が命名に渡る`() = testApplication {
        val namer = FakePartNamer { "稲妻の小道" }
        application { module(store = MemoryTowerStore(), namer = namer) }

        val res = client.postName(zigzag.json())

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(PartNameResponse("稲妻の小道"), AppJson.decodeFromString(PartNameResponse.serializer(), res.bodyAsText()))
        assertEquals(zigzag, namer.received)
    }

    @Test
    fun `命名が null なら 503`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = FakePartNamer { null }) }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
    }

    @Test
    fun `命名が例外を投げても 503 で、サーバは落ちない`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = FakePartNamer { error("Gemini が落ちた") }) }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
        // 続けて呼んでも普通に応答する
        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
    }

    @Test
    fun `命名なし（手元の既定）なら 503`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
    }

    @Test
    fun `長すぎる名前は切り詰めずに 1 回だけ聞き直し、短くなれば 200`() = testApplication {
        val answers = ArrayDeque(listOf("夕暮れの七曲りブロック", "夕暮れの七曲り"))
        val namer = FakePartNamer { answers.removeFirst() }
        application { module(store = MemoryTowerStore(), namer = namer) }

        val res = client.postName(zigzag.json())

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("夕暮れの七曲り", AppJson.decodeFromString(PartNameResponse.serializer(), res.bodyAsText()).name)
        assertEquals(2, namer.calls)
        assertEquals("夕暮れの七曲りブロック", namer.retriedWith) // 前の案を見せて聞き直す
    }

    @Test
    fun `聞き直しても長ければ、途中で切った名前は返さず 503`() = testApplication {
        val namer = FakePartNamer { "とてもとても長い名前の道でございます" }
        application { module(store = MemoryTowerStore(), namer = namer) }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
        assertEquals(2, namer.calls) // 聞き直しは 1 回だけ
    }

    @Test
    fun `ちょうど 10 文字なら聞き直さない`() = testApplication {
        val namer = FakePartNamer { "ちょうど十文字の名前" }
        application { module(store = MemoryTowerStore(), namer = namer) }

        val res = client.postName(zigzag.json())

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("ちょうど十文字の名前", AppJson.decodeFromString(PartNameResponse.serializer(), res.bodyAsText()).name)
        assertEquals(1, namer.calls)
    }

    @Test
    fun `読めない JSON や頂点の数がおかしいパーツは 400 で、命名は呼ばない`() = testApplication {
        val namer = FakePartNamer { "呼ばれないはず" }
        application { module(store = MemoryTowerStore(), namer = namer) }

        assertEquals(HttpStatusCode.BadRequest, client.postName("""{"id":""").status)
        assertEquals(HttpStatusCode.BadRequest, client.postName(zigzag.copy(points = emptyList()).json()).status)
        val tooMany = zigzag.copy(points = List(MAX_PART_POINTS + 1) { Pt(it.toFloat(), 0f) })
        assertEquals(HttpStatusCode.BadRequest, client.postName(tooMany.json()).status)
        // ちょうど上限なら通る
        val atLimit = zigzag.copy(points = List(MAX_PART_POINTS) { Pt(it.toFloat(), 0f) })
        assertEquals(HttpStatusCode.OK, client.postName(atLimit.json()).status)

        assertEquals(atLimit, namer.received)
    }

    // --- 安全策（nameSafely / cleanName） ---

    @Test
    fun `時間切れなら null`() = runBlocking {
        val slow = FakePartNamer { delay(1_000); "遅すぎた名前" }

        assertNull(slow.nameSafely(zigzag, timeoutMs = 50))
    }

    @Test
    fun `名前の前後の空白・かぎ括弧・引用符を取り、空なら null`() {
        assertEquals("夕暮れの稲妻", cleanName("  「夕暮れの稲妻」 \n"))
        assertEquals("朝の一本道", cleanName("\"朝の一本道\""))
        assertEquals("最初の行", cleanName("\n最初の行\n二行目"))
        assertNull(cleanName(""))
        assertNull(cleanName("   "))
        assertNull(cleanName("「」"))
    }

    @Test
    fun `文字数は絵文字も 1 文字と数える`() {
        // 絵文字（サロゲートペア）は Java の文字（char）では 2 つぶんだが、1 文字として数える（アプリの数え方と同じ）
        assertEquals(10, nameLength("🌙月夜の長い長い小道"))
        assertFalse(isTooLong("🌙月夜の長い長い小道"))
        assertTrue(isTooLong("🌙月夜の長い長い小道です"))
        // 長くても cleanName は切らない（聞き直すか 503 にするのは nameSafely）
        assertEquals("🌙月夜の長い長い小道です", cleanName("🌙月夜の長い長い小道です"))
    }
}

/** どの命名の部品を使うかの切り替え。本物の Gemini は作らない（認証のない CI でも通るように） */
class PartNamerSelectionTest {

    private fun envOf(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test
    fun `手元の既定と PART_NAMER=off は命名なし`() {
        kotlin.test.assertSame(DisabledPartNamer, PartNamer.fromEnvironment(envOf()))
        kotlin.test.assertSame(DisabledPartNamer, PartNamer.fromEnvironment(envOf("PART_NAMER" to "off", "K_SERVICE" to "x")))
    }

    @Test
    fun `Gemini を使う設定でもプロジェクト ID が分からなければ、落ちずに命名なしで動く`() {
        // GOOGLE_CLOUD_PROJECT なし。手元・CI にはメタデータサーバもない
        kotlin.test.assertSame(DisabledPartNamer, PartNamer.fromEnvironment(envOf("PART_NAMER" to "gemini")))
    }

    @Test(expected = IllegalStateException::class)
    fun `PART_NAMER の打ち間違いは起動時に気づけるようにする`() {
        PartNamer.fromEnvironment(envOf("PART_NAMER" to "gemni"))
    }
}
