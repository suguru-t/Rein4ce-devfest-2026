package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.core.sample.SampleParts
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * API の振る舞いのテスト。アプリと共有ページが頼っている約束事を確かめる。
 * テストごとに module() を起動し直すので、保存データはテストをまたがない。
 *
 * 本物の Firestore にはつながないよう、module() にはメモリ版の保存先を渡す。
 */
class RoutesTest {

    private fun sampleTower(): Tower {
        val parts = SampleParts.all.take(2)
        return Tower(
            parts = parts,
            placements = parts.mapIndexed { i, p -> Placement(p.id, x = 0f, y = 0.1f + i * 0.4f, angleRad = 0f) },
            height = 0.8f,
            createdAt = 0L,
        )
    }

    private suspend fun HttpClient.postTower(tower: Tower, forwardedProto: String? = null): HttpResponse =
        post("/towers") {
            contentType(ContentType.Application.Json)
            forwardedProto?.let { header("X-Forwarded-Proto", it) }
            setBody(AppJson.encodeToString(Tower.serializer(), tower))
        }

    @Test
    fun `動作確認とヘルスチェックが応答する`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        val root = client.get("/")
        assertEquals(HttpStatusCode.OK, root.status)
        assertContains(root.bodyAsText(), "OK (0 towers)")

        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals("ok", health.bodyAsText())
    }

    @Test
    fun `保存したタワーが一覧・JSON・共有ページ・画像で取れる`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        val res = client.postTower(sampleTower())
        assertEquals(HttpStatusCode.Created, res.status)
        val created = AppJson.decodeFromString(TowerCreated.serializer(), res.bodyAsText())
        assertTrue(created.url.endsWith("/towers/${created.id}"), "共有 URL: ${created.url}")

        // 一覧
        val list = AppJson.decodeFromString(ListSerializer(TowerSummary.serializer()), client.get("/towers").bodyAsText())
        assertEquals(1, list.size)
        assertEquals(created.id, list[0].id)
        assertEquals(2, list[0].partCount)
        assertTrue(list[0].createdAt > 0, "createdAt はサーバで埋める")

        // JSON（Accept: application/json）
        val json = client.get("/towers/${created.id}") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(HttpStatusCode.OK, json.status)
        val tower = AppJson.decodeFromString(Tower.serializer(), json.bodyAsText())
        assertEquals(created.id, tower.id)
        assertEquals(2, tower.placements.size)

        // 共有ページ（HTML、OGP 付き）
        val page = client.get("/towers/${created.id}")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.contentType()?.match(ContentType.Text.Html) == true)
        val html = page.bodyAsText()
        assertContains(html, "og:title")
        // og:image は PNG（LINE / X は SVG を読まない）。ページの中の絵はインライン SVG
        assertContains(html, """<meta property="og:image" content="http://localhost/towers/${created.id}/image.png">""")
        assertContains(html, "<svg")

        // 画像
        val svg = client.get("/towers/${created.id}/image.svg")
        assertEquals(HttpStatusCode.OK, svg.status)
        assertTrue(svg.contentType()?.match(ContentType.Image.SVG) == true)
        assertContains(svg.bodyAsText(), "<svg")

        val png = client.get("/towers/${created.id}/image.png")
        assertEquals(HttpStatusCode.OK, png.status)
        assertTrue(png.contentType()?.match(ContentType.Image.PNG) == true)
        assertContains(png.headers[HttpHeaders.CacheControl].orEmpty(), "max-age")
    }

    @Test
    fun `持ち主の印を送ると自分のタワーに mine が付き、印そのものは返らない`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }
        val me = "a".repeat(64)
        val other = "b".repeat(64)

        val mineRes = client.postTower(sampleTower().copy(ownerId = me))
        assertEquals(HttpStatusCode.Created, mineRes.status)
        val mine = AppJson.decodeFromString(TowerCreated.serializer(), mineRes.bodyAsText())
        client.postTower(sampleTower()) // 古いアプリから（印なし）

        suspend fun summaries(owner: String?): List<TowerSummary> {
            val body = client.get("/towers") { owner?.let { header(OWNER_HEADER, it) } }.bodyAsText()
            return AppJson.decodeFromString(ListSerializer(TowerSummary.serializer()), body)
        }
        // 自分の印：自分のだけ true
        assertEquals(mapOf(mine.id to true), summaries(me).filter { it.mine }.associate { it.id to it.mine })
        // 他人の印・印なし・変な印：全部 false
        assertTrue(summaries(other).none { it.mine })
        assertTrue(summaries(null).none { it.mine })
        assertTrue(summaries("not-a-hash").none { it.mine })
        // 一覧にも 1 基の JSON にも、印そのものは出ない
        val listRes = client.get("/towers") { header(OWNER_HEADER, me) }
        val listBody = listRes.bodyAsText()
        assertFalse(me in listBody, "一覧に印が出ている")
        // 人によって答えが違うので、途中のキャッシュに共有させない
        assertContains(listRes.headers[HttpHeaders.CacheControl].orEmpty(), "private")
        val json = client.get("/towers/${mine.id}") { header(HttpHeaders.Accept, "application/json") }.bodyAsText()
        assertFalse(me in json, "1 基の JSON に印が出ている")
        assertEquals(null, AppJson.decodeFromString(Tower.serializer(), json).ownerId)
    }

    @Test
    fun `共有ページに作者名が出る（無ければ出ない）`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        val named = AppJson.decodeFromString(TowerCreated.serializer(), client.postTower(sampleTower().copy(author = "C <3")).bodyAsText())
        val namedHtml = client.get("/towers/${named.id}").bodyAsText()
        // 説明（og:description とページの本文）に名前。HTML の特殊文字はエスケープされる
        assertContains(namedHtml, "C &lt;3 さんが 2 個の散歩パーツで積みました")

        val anon = AppJson.decodeFromString(TowerCreated.serializer(), client.postTower(sampleTower()).bodyAsText())
        val anonHtml = client.get("/towers/${anon.id}").bodyAsText()
        assertContains(anonHtml, "content=\"2 個の散歩パーツで積みました")

        // 「〜さん」で終わる名前に「さん」を重ねない。向きを変える字（U+202E）や制御文字は落とす
        val san = AppJson.decodeFromString(TowerCreated.serializer(), client.postTower(sampleTower().copy(author = "みち‮さん\u0007")).bodyAsText())
        val sanHtml = client.get("/towers/${san.id}").bodyAsText()
        assertContains(sanHtml, "みちさん が 2 個の散歩パーツで積みました")
        assertFalse("‮" in sanHtml)
        assertEquals("みちさん", SharePage.cleanAuthor("  みち‮さん\u0007 "))
    }

    @Test
    fun `Cloud Run 経由（X-Forwarded-Proto が https）なら共有 URL も https になる`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        val res = client.postTower(sampleTower(), forwardedProto = "https")
        val created = AppJson.decodeFromString(TowerCreated.serializer(), res.bodyAsText())
        assertTrue(created.url.startsWith("https://"), "共有 URL: ${created.url}")
    }

    @Test
    fun `壊れたタワーは 400 で断り、保存しない`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        val empty = sampleTower().copy(parts = emptyList(), placements = emptyList())
        val res = client.postTower(empty)
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals("parts is empty", res.bodyAsText())

        // 知らない partId（細かい検査は TowerValidationTest。ここでは理由が本文に出ることだけ）
        val unknown = sampleTower().let { it.copy(placements = listOf(it.placements[0].copy(partId = "nothere"))) }
        assertEquals("placement 0: unknown partId nothere", client.postTower(unknown).bodyAsText())

        assertEquals("[]", client.get("/towers").bodyAsText())
    }

    @Test
    fun `読めない JSON は 400（NaN や型違いも）`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        suspend fun post(body: String): HttpResponse = client.post("/towers") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.BadRequest, post("""{"parts":""").status)
        assertEquals(HttpStatusCode.BadRequest, post("""{"parts":[],"placements":[],"height":"high","createdAt":0}""").status)
        // JSON に NaN は書けない（kotlinx.serialization の既定で読めない）ので、読めない JSON として 400
        val nan = AppJson.encodeToString(Tower.serializer(), sampleTower()).replace(""""height":0.8""", """"height":NaN""")
        assertEquals(HttpStatusCode.BadRequest, post(nan).status)
        assertEquals("[]", client.get("/towers").bodyAsText())
    }

    @Test
    fun `存在しない ID は 404`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        assertEquals(HttpStatusCode.NotFound, client.get("/towers/nothere1").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/towers/nothere1/image.svg").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/towers/nothere1/image.png").status)
    }
}
