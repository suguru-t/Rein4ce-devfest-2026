package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.NicknameHints
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.sample.SampleParts
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * API の回数制限（429）と、CORS を入れていないこと。
 * 上限が小さい制限を渡して、少ない回数で確かめる（本番の上限は RateLimiterTest）
 */
class RateLimitRoutesTest {

    private val me = "a".repeat(64)
    private val other = "b".repeat(64)

    /** どの API も、端末ごと 2 回・合計 100 回までにした制限 */
    private fun tinyLimits() = RateLimits(
        towersPost = RateLimiter(perClient = 2, total = 100),
        towersGet = RateLimiter(perClient = 2, total = 100),
        partName = RateLimiter(perClient = 2, total = 100),
        nickname = RateLimiter(perClient = 2, total = 100),
    )

    /** いつも決まった名前を返す偽物 */
    private object FixedNamer : PartNamer {
        override suspend fun name(part: Part) = "稲妻の小道"
        override suspend fun nickname(hints: NicknameHints) = "夜道の旅人"
    }

    private fun towerJson(): String {
        val parts = SampleParts.all.take(2)
        val tower = Tower(
            parts = parts,
            placements = parts.mapIndexed { i, p -> Placement(p.id, x = 0f, y = 0.1f + i * 0.4f, angleRad = 0f) },
            height = 0.8f,
            createdAt = 0L,
        )
        return AppJson.encodeToString(Tower.serializer(), tower)
    }

    private val partJson = AppJson.encodeToString(
        Part.serializer(),
        Part(id = "p1", points = listOf(Pt(-0.3f, 0f), Pt(0f, 0.3f), Pt(0.3f, 0f)), kind = Kind.ZIGZAG, distanceM = 820, walkedAt = 0L),
    )

    private suspend fun HttpClient.postJson(path: String, body: String, owner: String? = null, forwardedFor: String? = null): HttpResponse =
        post(path) {
            contentType(ContentType.Application.Json)
            owner?.let { header(OWNER_HEADER, it) }
            forwardedFor?.let { header(HttpHeaders.XForwardedFor, it) }
            setBody(body)
        }

    @Test
    fun `投稿は端末ごとの上限を超えると 429 と Retry-After を返し、ほかの端末は通る`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer, limits = tinyLimits()) }

        repeat(2) { assertEquals(HttpStatusCode.Created, client.postJson("/towers", towerJson(), owner = me).status) }
        val over = client.postJson("/towers", towerJson(), owner = me)
        assertEquals(HttpStatusCode.TooManyRequests, over.status)
        val wait = assertNotNull(over.headers[HttpHeaders.RetryAfter]).toInt()
        assert(wait in 1..60) { "Retry-After は 1〜60 秒（$wait）" }

        assertEquals(HttpStatusCode.Created, client.postJson("/towers", towerJson(), owner = other).status)
    }

    @Test
    fun `一覧も数える`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer, limits = tinyLimits()) }

        repeat(2) { assertEquals(HttpStatusCode.OK, client.get("/towers") { header(OWNER_HEADER, me) }.status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/towers") { header(OWNER_HEADER, me) }.status)
    }

    @Test
    fun `AI の名前とニックネームを数える（ほかの API とは別の数え）`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = FixedNamer, limits = tinyLimits()) }

        repeat(2) { assertEquals(HttpStatusCode.OK, client.postJson("/parts/name", partJson, owner = me).status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.postJson("/parts/name", partJson, owner = me).status)

        val hints = AppJson.encodeToString(NicknameHints.serializer(), NicknameHints(partNames = listOf("夜道"), partCount = 1))
        repeat(2) { assertEquals(HttpStatusCode.OK, client.postJson("/nickname", hints, owner = me).status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.postJson("/nickname", hints, owner = me).status)
    }

    @Test
    fun `印が無いときは IP（X-Forwarded-For の最初）で数え、同じ IP でも印が違えば別に数える`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = FixedNamer, limits = tinyLimits()) }

        repeat(2) { client.postJson("/parts/name", partJson, forwardedFor = "203.0.113.5, 10.0.0.1") }
        assertEquals(HttpStatusCode.TooManyRequests, client.postJson("/parts/name", partJson, forwardedFor = "203.0.113.5").status)
        assertEquals(HttpStatusCode.OK, client.postJson("/parts/name", partJson, forwardedFor = "198.51.100.7").status)
        // 会場の Wi-Fi のように同じ IP でも、端末の印があればそちらで数える
        assertEquals(HttpStatusCode.OK, client.postJson("/parts/name", partJson, owner = me, forwardedFor = "203.0.113.5").status)
    }

    @Test
    fun `形の違う印は使わず、IP で数える（印を変えて上限をすり抜けにくくする）`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = FixedNamer, limits = tinyLimits()) }

        repeat(2) { client.postJson("/parts/name", partJson, owner = "bogus-$it", forwardedFor = "203.0.113.9") }
        assertEquals(HttpStatusCode.TooManyRequests, client.postJson("/parts/name", partJson, owner = "bogus-x", forwardedFor = "203.0.113.9").status)
    }

    @Test
    fun `OFF なら数えない`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = FixedNamer, limits = RateLimits.OFF) }

        repeat(30) { assertEquals(HttpStatusCode.OK, client.postJson("/parts/name", partJson, owner = me).status) }
    }

    @Test
    fun `CORS を入れていないので、ほかのサイトからのブラウザの呼び出しは許さない`() = testApplication {
        application { module(store = MemoryTowerStore(), namer = DisabledPartNamer, limits = RateLimits.OFF) }

        val get = client.get("/towers") { header(HttpHeaders.Origin, "https://evil.example") }
        assertEquals(HttpStatusCode.OK, get.status) // サーバは答えるが、ブラウザに読ませる許可の見出しは付けない
        assertNull(get.headers[HttpHeaders.AccessControlAllowOrigin])

        val preflight = client.options("/towers") {
            header(HttpHeaders.Origin, "https://evil.example")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertNull(preflight.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
