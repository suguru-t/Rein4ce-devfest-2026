package com.rein4ce.roadcollector.server

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.ThinkingLevel
import com.google.genai.types.Type
import com.rein4ce.roadcollector.core.model.Part
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Vertex AI の Gemini でパーツに名前を付ける。
 *
 * - 認証は Application Default Credentials（Cloud Run はサービスアカウント、手元は
 *   `gcloud auth application-default login`）。API キーは使わない（AI Studio はクレジット対象外）
 * - モデルは gemini-3.5-flash-lite を global で使う。2026-11-06 の試し打ちで、名前付けには十分な質で、
 *   12 回とも 1 秒以内だった。3.5-flash は時間帯によって 5〜8 秒かかり 429（混雑）も返した。
 *   2.5-flash はこのプロジェクトでは使えず（404）、3.8-flash は遅く失敗が混ざった
 *   （比較は docs/decisions.md の 10 番）。GEMINI_MODEL / GEMINI_LOCATION で替えられる
 * - 考える時間（thinking）は最小（MINIMAL）。長く考えさせると 3 秒の打ち切り（PartNamer.TIMEOUT_MS）を超える
 * - 返事は {"name": "..."} の JSON に固定して、読み取りを安定させる
 * - 長さの切り詰めや空の判定は nameSafely / cleanName がやるので、ここでは読み取るだけ
 */
class GeminiPartNamer(
    private val client: Client,
    private val model: String = DEFAULT_MODEL,
) : PartNamer {

    override suspend fun name(part: Part): String? {
        val response = client.async.models
            .generateContent(model, buildNamingPrompt(part), CONFIG)
            .await() // 時間切れで nameSafely がキャンセルすると、待っている future もキャンセルされる
        return parseNameResponse(response.text())
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        /** 3.5-flash-lite は東京（asia-northeast1）にはなく、global でだけ使える */
        const val DEFAULT_LOCATION = "global"

        private val CONFIG: GenerateContentConfig = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(
                Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(mapOf("name" to Schema.builder().type(Type.Known.STRING).build()))
                    .required(listOf("name"))
                    .build()
            )
            .thinkingConfig(ThinkingConfig.builder().thinkingLevel(ThinkingLevel.Known.MINIMAL).build())
            // 1.8 まで上げても名前のばらつきはあまり増えなかったので 1.0。ばらつきは指示文（「意外性」の 1 行）で出す
            .temperature(1.0f)
            .maxOutputTokens(64)
            .build()

        fun create(projectId: String, location: String = DEFAULT_LOCATION, model: String = DEFAULT_MODEL): GeminiPartNamer {
            val client = Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .build()
            return GeminiPartNamer(client, model)
        }

        /**
         * Vertex AI を呼ぶプロジェクト ID。
         * 環境変数 GOOGLE_CLOUD_PROJECT → Cloud Run のメタデータサーバ の順に探す。見つからなければ null。
         */
        fun resolveProjectId(env: (String) -> String? = System::getenv): String? {
            env("GOOGLE_CLOUD_PROJECT")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
            return runCatching {
                val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
                val req = HttpRequest.newBuilder(URI("http://metadata.google.internal/computeMetadata/v1/project/project-id"))
                    .header("Metadata-Flavor", "Google")
                    .timeout(Duration.ofSeconds(1))
                    .build()
                http.send(req, HttpResponse.BodyHandlers.ofString())
                    .takeIf { it.statusCode() == 200 }
                    ?.body()?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
    }
}

@Serializable
private data class NameJson(val name: String? = null)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** {"name": "..."} を読む。JSON でなければ、返ってきた文字をそのまま名前とみなす（掃除は cleanName がやる） */
internal fun parseNameResponse(text: String?): String? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return null
    return runCatching { lenientJson.decodeFromString(NameJson.serializer(), t).name }
        .getOrElse { if (t.startsWith("{")) null else t }
}
