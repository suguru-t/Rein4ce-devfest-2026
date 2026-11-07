package com.rein4ce.roadcollector.net

import android.util.Log
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartNameResponse
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
import kotlinx.coroutines.CancellationException

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

    /**
     * 送るときの作者名（設定のニックネーム。空なら null で、「みんな」には「名無し」と出る）。
     * アプリの起動時に RoadCollectorApp が設定とつなぐ
     */
    var authorName: () -> String? = { null }

    /**
     * 送信できたタワーの id を受け取る係（自分のタワーの印。data/MyTowers）。
     * アプリの起動時に RoadCollectorApp がつなぐ
     */
    var onPosted: (TowerCreated) -> Unit = {}

    /** タワーを送る。作者名がまだ入っていなければ、設定のニックネームを入れて送る。送れたら [onPosted] に知らせる */
    suspend fun postTower(tower: Tower): TowerCreated =
        client.post("$baseUrl/towers") {
            contentType(ContentType.Application.Json)
            setBody(withAuthor(tower, authorName()))
        }.body<TowerCreated>().also(onPosted)

    /**
     * パーツの名前を AI（サーバの Gemini）に考えてもらう（POST /parts/name）。
     * 名前が付かなかった（サーバが 503 を返した）・通信できなかったときは null。そのときは今の表示のままにする（decisions.md の 7）
     */
    suspend fun namePart(part: Part): String? = try {
        client.post("$baseUrl/parts/name") {
            contentType(ContentType.Application.Json)
            setBody(namingRequest(part))
        }.body<PartNameResponse>().name
    } catch (e: CancellationException) {
        throw e // 画面を閉じて取りやめたときは、失敗として扱わない
    } catch (e: Exception) {
        Log.w("ApiClient", "naming failed", e)
        null
    }
}

/**
 * 命名 API に送るパーツ。名前の手がかりは形・距離・日時だけなので（decisions.md の 8）、今付いている名前は送らない
 * （自分で付けた名前を外に出さない。サーバの指示文も名前は使わない）
 */
internal fun namingRequest(part: Part): Part = part.copy(name = null)

/** 送るタワーに作者名を入れる。もう入っていればそのまま。[author] が空なら入れない（名無し） */
internal fun withAuthor(tower: Tower, author: String?): Tower =
    if (!tower.author.isNullOrBlank() || author.isNullOrBlank()) tower else tower.copy(author = author)
