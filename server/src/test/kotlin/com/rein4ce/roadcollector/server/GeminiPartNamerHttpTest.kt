package com.rein4ce.roadcollector.server

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.genai.Client
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Gemini SDK の HTTP の設定（期限・再試行なし）が効いているかを、Vertex AI の代わりの偽サーバで確かめる。
 * 本物の Gemini にはつながない。認証は期限内の偽トークンを渡すので、ADC も要らない。
 */
class GeminiPartNamerHttpTest {

    /** Vertex AI のふりをする HTTP サーバ。来た数を数え、どのパスでも [handler] で応える */
    private class FakeVertex(private val handler: suspend (ApplicationCall) -> Unit) : AutoCloseable {
        val requests = AtomicInteger()
        private val server = embeddedServer(Netty, port = 0) {
            routing {
                route("{...}") {
                    handle {
                        requests.incrementAndGet()
                        handler(call)
                    }
                }
            }
        }.start(wait = false)
        val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
        override fun close() = server.stop(0, 0)
    }

    private fun namer(port: Int, httpTimeoutMs: Long) = GeminiPartNamer(
        Client.builder()
            .vertexAI(true)
            .project("test-project")
            .location("global")
            .credentials(GoogleCredentials.create(AccessToken("test-token", Date(System.currentTimeMillis() + 3_600_000))))
            .httpOptions(GeminiPartNamer.httpOptions(httpTimeoutMs).baseUrl("http://127.0.0.1:$port").build())
            .build()
    )

    private val zigzag = Part(
        id = "p1",
        points = listOf(Pt(-0.3f, 0f), Pt(0f, 0.3f), Pt(0.3f, 0f)),
        kind = Kind.ZIGZAG,
        distanceM = 820,
        walkedAt = 1_793_900_000_000L,
    )

    private inline fun measureMs(block: () -> Unit): Long {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000
    }

    @Test
    fun `正常な応答なら名前が返る（偽サーバに届いている）`() = runBlocking {
        FakeVertex { call ->
            call.respondText(
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"{\"name\":\"稲妻の小道\"}"}]}}]}""",
                ContentType.Application.Json,
            )
        }.use { vertex ->
            assertEquals("稲妻の小道", namer(vertex.port, httpTimeoutMs = 3_000).nameSafely(zigzag, timeoutMs = 3_000))
            assertEquals(1, vertex.requests.get())
        }
    }

    @Test
    fun `応答が遅いと HTTP の期限で切れ、裏で再試行しない`() = runBlocking {
        FakeVertex { call ->
            delay(5_000)
            call.respondText("{}", ContentType.Application.Json)
        }.use { vertex ->
            var name: String? = "unset"
            val ms = measureMs { name = namer(vertex.port, httpTimeoutMs = 500).nameSafely(zigzag, timeoutMs = 3_000) }
            assertNull(name)
            assertTrue(ms < 2_500, "HTTP の期限（500 ms）で切れていない: $ms ms")
            delay(1_500) // 再試行があるならこの間に 2 回目が来る
            assertEquals(1, vertex.requests.get(), "SDK が再試行している")
        }
    }

    @Test
    fun `429 でも再試行せず、待ち時間も入れない`() = runBlocking {
        FakeVertex { call ->
            call.respondText(
                """{"error":{"code":429,"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED"}}""",
                ContentType.Application.Json,
                HttpStatusCode.TooManyRequests,
            )
        }.use { vertex ->
            var name: String? = "unset"
            val ms = measureMs { name = namer(vertex.port, httpTimeoutMs = 3_000).nameSafely(zigzag, timeoutMs = 3_000) }
            assertNull(name)
            assertTrue(ms < 1_000, "429 のあとに待ち時間（バックオフ）が入っている: $ms ms")
            delay(1_500)
            assertEquals(1, vertex.requests.get(), "SDK が再試行している")
        }
    }
}
