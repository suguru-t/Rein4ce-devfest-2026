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
        application { module(MemoryTowerStore()) }

        val root = client.get("/")
        assertEquals(HttpStatusCode.OK, root.status)
        assertContains(root.bodyAsText(), "OK (0 towers)")

        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals("ok", health.bodyAsText())
    }

    @Test
    fun `保存したタワーが一覧・JSON・共有ページ・画像で取れる`() = testApplication {
        application { module(MemoryTowerStore()) }

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
        assertContains(html, "/towers/${created.id}/image.svg")

        // 画像
        val svg = client.get("/towers/${created.id}/image.svg")
        assertEquals(HttpStatusCode.OK, svg.status)
        assertTrue(svg.contentType()?.match(ContentType.Image.SVG) == true)
        assertContains(svg.bodyAsText(), "<svg")
    }

    @Test
    fun `Cloud Run 経由（X-Forwarded-Proto が https）なら共有 URL も https になる`() = testApplication {
        application { module(MemoryTowerStore()) }

        val res = client.postTower(sampleTower(), forwardedProto = "https")
        val created = AppJson.decodeFromString(TowerCreated.serializer(), res.bodyAsText())
        assertTrue(created.url.startsWith("https://"), "共有 URL: ${created.url}")
    }

    @Test
    fun `パーツや配置が空のタワーは 400 で断る`() = testApplication {
        application { module(MemoryTowerStore()) }

        val empty = sampleTower().copy(parts = emptyList(), placements = emptyList())
        assertEquals(HttpStatusCode.BadRequest, client.postTower(empty).status)
        assertEquals("[]", client.get("/towers").bodyAsText())
    }

    @Test
    fun `存在しない ID は 404`() = testApplication {
        application { module(MemoryTowerStore()) }

        assertEquals(HttpStatusCode.NotFound, client.get("/towers/nothere1").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/towers/nothere1/image.svg").status)
    }
}
