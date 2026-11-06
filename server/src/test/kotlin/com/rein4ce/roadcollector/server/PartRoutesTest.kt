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
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * POST /parts/name の約束事（200 { name } / 失敗は 503 / 変な入力は 400）を確かめる。
 * 本物の Gemini は呼ばず、決まった答えを返す偽物（FakePartNamer）を渡す。
 */
class PartRoutesTest {

    /** 決まった動きをする偽物。受け取ったパーツを覚えておく */
    private class FakePartNamer(private val answer: suspend (Part) -> String?) : PartNamer {
        var received: Part? = null
        override suspend fun name(part: Part): String? {
            received = part
            return answer(part)
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
        application { module(namer = namer) }

        val res = client.postName(zigzag.json())

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(PartNameResponse("稲妻の小道"), AppJson.decodeFromString(PartNameResponse.serializer(), res.bodyAsText()))
        assertEquals(zigzag, namer.received)
    }

    @Test
    fun `命名が null なら 503`() = testApplication {
        application { module(namer = FakePartNamer { null }) }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
    }

    @Test
    fun `命名が例外を投げても 503 で、サーバは落ちない`() = testApplication {
        application { module(namer = FakePartNamer { error("Gemini が落ちた") }) }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
        // 続けて呼んでも普通に応答する
        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
    }

    @Test
    fun `命名なし（手元の既定）なら 503`() = testApplication {
        application { module(namer = DisabledPartNamer) }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postName(zigzag.json()).status)
    }

    @Test
    fun `長すぎる名前は 10 文字に切り詰めて返す`() = testApplication {
        application { module(namer = FakePartNamer { "とてもとても長い名前の道でございます" }) }

        val res = client.postName(zigzag.json())

        assertEquals(HttpStatusCode.OK, res.status)
        val name = AppJson.decodeFromString(PartNameResponse.serializer(), res.bodyAsText()).name
        assertEquals("とてもとても長い名前", name)
    }

    @Test
    fun `読めない JSON や頂点の数がおかしいパーツは 400 で、命名は呼ばない`() = testApplication {
        val namer = FakePartNamer { "呼ばれないはず" }
        application { module(namer = namer) }

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
    fun `絵文字を途中で切らない`() {
        // 絵文字（サロゲートペア）を含めて 11 文字 → 10 文字に切っても壊れない
        val cut = cleanName("🌙月夜の長い長い小道です")!!
        assertEquals(10, cut.codePointCount(0, cut.length))
        assertEquals("🌙月夜の長い長い小道", cut)
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
