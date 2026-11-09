package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.core.sample.SampleParts
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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

    private suspend fun HttpClient.summariesOf(mode: String?, owner: String? = null): List<TowerSummary> {
        val body = get(if (mode == null) "/towers" else "/towers?mode=$mode") { owner?.let { header(OWNER_HEADER, it) } }.bodyAsText()
        return AppJson.decodeFromString(ListSerializer(TowerSummary.serializer()), body)
    }

    @Test
    fun `遊び方ごとに別の URL で受け、一覧も遊び方ごとに分ける（決定 77）`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }
        client.postTower(sampleTower())
        val challengeRes = client.post("/towers/challenge") {
            contentType(ContentType.Application.Json)
            // 本文の mode は見ない（URL で決める）
            setBody(AppJson.encodeToString(Tower.serializer(), sampleTower().copy(rule = "manual-180-v6")))
        }
        assertEquals(HttpStatusCode.Created, challengeRes.status)
        val challenge = AppJson.decodeFromString(TowerCreated.serializer(), challengeRes.bodyAsText())

        assertEquals(1, client.summariesOf(null).size, "mode なし（前のアプリ）は通常だけ")
        assertEquals(TowerMode.NORMAL, client.summariesOf("normal").single().mode)
        val c = client.summariesOf("challenge").single()
        assertEquals(challenge.id, c.id)
        assertEquals(TowerMode.CHALLENGE, c.mode)
        assertEquals("manual-180-v6", c.rule)
        assertTrue(client.summariesOf("daily").isEmpty())
        assertEquals(HttpStatusCode.BadRequest, client.get("/towers?mode=weekly").status)
        // 共有ページと JSON は、どの遊び方のタワーでも id で取れる
        val json = client.get("/towers/${challenge.id}") { header(HttpHeaders.Accept, "application/json") }.bodyAsText()
        assertEquals(TowerMode.CHALLENGE, AppJson.decodeFromString(Tower.serializer(), json).mode)
        assertEquals(HttpStatusCode.OK, client.get("/towers/${challenge.id}").status)
        // 決まりの ID が無い 3 分チャレンジ・通常の URL に送った 3 分チャレンジは断る
        val noRule = client.post("/towers/challenge") {
            contentType(ContentType.Application.Json)
            setBody(AppJson.encodeToString(Tower.serializer(), sampleTower()))
        }
        assertEquals(HttpStatusCode.BadRequest, noRule.status)
        assertEquals(HttpStatusCode.BadRequest, client.postTower(sampleTower().copy(mode = TowerMode.CHALLENGE, rule = "manual-180-v6")).status)
        assertEquals(1, client.summariesOf("normal").size)
    }

    @Test
    fun `毎日 1 つずつ積むは持ち主ごとに 1 基を新しくする（決定 77）`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }
        val me = "a".repeat(64)
        suspend fun put(tower: Tower) = client.put("/towers/daily") {
            contentType(ContentType.Application.Json)
            setBody(AppJson.encodeToString(Tower.serializer(), tower))
        }
        val day1 = sampleTower().copy(ownerId = me, lastDay = "2026-11-09", streakDays = 1)
        val first = put(day1)
        assertEquals(HttpStatusCode.OK, first.status)
        val id = AppJson.decodeFromString(TowerCreated.serializer(), first.bodyAsText()).id
        val second = put(day1.copy(height = 1.6f, lastDay = "2026-11-10", streakDays = 2))
        assertEquals(id, AppJson.decodeFromString(TowerCreated.serializer(), second.bodyAsText()).id, "同じ持ち主は同じ id")
        put(sampleTower().copy(ownerId = "b".repeat(64), lastDay = "2026-11-10", streakDays = 1))

        val list = client.summariesOf("daily", owner = me)
        assertEquals(2, list.size)
        val mine = list.single { it.mine }
        assertEquals(id, mine.id)
        assertEquals(1.6f, mine.height)
        assertEquals(2, mine.streakDays)
        assertEquals("2026-11-10", mine.lastDay)
        assertTrue(client.summariesOf("normal").isEmpty(), "通常のランキングには混ざらない")
        // 持ち主の印が無い毎日の塔は、誰のか分からないので断る
        assertEquals(HttpStatusCode.BadRequest, put(day1.copy(ownerId = null)).status)
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

    @Test
    fun `終わりなく積んだ塔：一覧のパーツ数は置いた数、台の幅は JSON と絵に残る（決定 66）`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer) }

        // 同じパーツを 12 回置いた、広い台の塔（10 個より多いので、上限を上げる前は 400 だった）
        val part = SampleParts.all.first()
        val endless = Tower(
            parts = listOf(part),
            placements = List(12) { i -> Placement(part.id, x = -1.2f + 0.2f * i, y = 0.1f, angleRad = 0f) },
            height = 0.4f,
            createdAt = 0L,
            platformWidth = 2.8f,
        )
        val res = client.postTower(endless)
        assertEquals(HttpStatusCode.Created, res.status)
        val id = AppJson.decodeFromString(TowerCreated.serializer(), res.bodyAsText()).id

        val list = AppJson.decodeFromString(ListSerializer(TowerSummary.serializer()), client.get("/towers").bodyAsText())
        assertEquals(12, list.single().partCount, "parts は 1 つでも、置いた数で数える")

        val json = client.get("/towers/$id") { header(HttpHeaders.Accept, "application/json") }
        assertEquals(2.8f, AppJson.decodeFromString(Tower.serializer(), json.bodyAsText()).platformWidth)
        assertContains(client.get("/towers/$id").bodyAsText(), "12 個の散歩パーツで積みました")
        // 共有ページの絵の台は、塔の台の幅（2.8 m）で描く
        assertEquals(HttpStatusCode.OK, client.get("/towers/$id/image.png").status)
    }

    @Test
    fun `台の幅を持たない前のタワーは、今までの台の幅で読む`() {
        val old = AppJson.encodeToString(Tower.serializer(), sampleTower()).replace(Regex(""",?"platformWidth":[0-9.]+"""), "")
        assertTrue(!old.contains("platformWidth"))
        assertEquals(1.4f, AppJson.decodeFromString(Tower.serializer(), old).platformWidth)
    }
}
