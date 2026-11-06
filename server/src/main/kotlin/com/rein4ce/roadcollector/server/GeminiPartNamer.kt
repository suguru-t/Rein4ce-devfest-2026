package com.rein4ce.roadcollector.server

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingConfig
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
 * - 考える時間（thinking）はオフ。オンだと数秒かかり、3 秒の打ち切り（PartNamer.TIMEOUT_MS）を超える
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
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_LOCATION = "asia-northeast1"

        private val CONFIG: GenerateContentConfig = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .responseSchema(
                Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(mapOf("name" to Schema.builder().type(Type.Known.STRING).build()))
                    .required(listOf("name"))
                    .build()
            )
            .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
            .temperature(1.0f) // 毎回少し違う名前が出るように
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

/**
 * Gemini への指示文。手がかりは形・距離・日時だけ（位置情報は Part に入っていない）。
 * TODO(C): 手順 4 で、日時を「朝・昼・夕方・夜」と月に直すなどして作り込む。
 */
internal fun buildNamingPrompt(part: Part): String = """
    散歩で歩いた道の形から生まれた、積み木パーツに名前を付けてください。
    - 形：${part.kind.label}${if (part.closed) "（輪になっている）" else ""}
    - 歩いた距離：${part.distanceM} m
    ルール：日本語で ${PartNamer.MAX_NAME_LENGTH} 文字以内。地名・人名・実在の店名は入れない。
""".trimIndent()

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
