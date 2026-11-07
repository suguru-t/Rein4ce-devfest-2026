package com.rein4ce.roadcollector.net

import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.model.TowerSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Cloud Run 上のサーバ（:server モジュール）を呼ぶ。
 * URL は local.properties の SERVER_BASE_URL（既定は本番の Cloud Run。ローカルサーバなら http://10.0.2.2:8080）。
 */
object ApiClient {

    val baseUrl: String = BuildConfig.SERVER_BASE_URL.trimEnd('/')

    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(AppJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    suspend fun ping(): String = client.get("$baseUrl/").body()

    suspend fun listTowers(): List<TowerSummary> = client.get("$baseUrl/towers").body()

    /**
     * タワー 1 基の中身（パーツと置き方）。
     * 同じ URL は Accept に application/json がないと共有ページ（HTML）を返す。ContentNegotiation が Accept を付けてくれるが、
     * この呼び出しはそれに頼っていることが分かるよう明示しておく
     */
    suspend fun getTower(id: String): Tower =
        client.get("$baseUrl/towers/$id") { accept(ContentType.Application.Json) }.body()

    suspend fun postTower(tower: Tower): TowerCreated =
        client.post("$baseUrl/towers") {
            contentType(ContentType.Application.Json)
            setBody(tower)
        }.body()
}
