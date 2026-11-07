package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.svg.TowerSvg
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.accept
import io.ktor.server.request.header
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * API 一覧（技術選定まとめ 4 章）
 *
 *   GET  /                     動作確認
 *   GET  /health               ヘルスチェック（/healthz は Cloud Run の予約パスで届かないため使わない）
 *   GET  /towers               一覧（新しい順）
 *   POST /towers               保存 → { id, url }
 *   GET  /towers/{id}          共有ページ（HTML）。Accept: application/json なら JSON
 *   GET  /towers/{id}/image.svg  タワーの絵（SVG）。共有ページの中に描く
 *   GET  /towers/{id}/image.png  タワーの絵（PNG、1200×630）。共有ページの og:image（LINE / X のプレビュー）
 *
 *   TODO(C): POST /parts/name  Vertex AI（Gemini）でパーツ命名
 *   TODO(C): 対戦方式が決まったら /challenges
 */
fun Route.towerRoutes(store: TowerStore) {

    get("/") {
        call.respondText("Road Collector server OK (${store.count()} towers)")
    }

    get("/health") {
        call.respondText("ok")
    }

    get("/towers") {
        call.respond(store.summaries())
    }

    post("/towers") {
        val tower = call.receive<Tower>()
        if (tower.parts.isEmpty() || tower.placements.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "parts and placements are required")
            return@post
        }
        val saved = store.add(tower)
        call.respond(HttpStatusCode.Created, TowerCreated(saved.id, "${publicBaseUrl(call)}/towers/${saved.id}"))
    }

    get("/towers/{id}") {
        val id = call.parameters["id"].orEmpty()
        val tower = store.get(id)
        if (tower == null) {
            call.respond(HttpStatusCode.NotFound, "tower not found")
            return@get
        }
        val wantsJson = call.request.accept()?.contains("application/json") == true
        if (wantsJson) {
            call.respond(tower)
        } else {
            call.respondText(SharePage.render(tower, publicBaseUrl(call)), ContentType.Text.Html)
        }
    }

    get("/towers/{id}/image.svg") {
        val id = call.parameters["id"].orEmpty()
        val tower = store.get(id)
        if (tower == null) {
            call.respond(HttpStatusCode.NotFound, "tower not found")
            return@get
        }
        call.respondText(TowerSvg.render(tower), ContentType.Image.SVG)
    }

    // 共有ページの og:image 用。LINE / X は SVG を読まないので PNG。タワーは保存後に変わらないのでキャッシュさせる。
    // GET だけで HEAD には応えない（AutoHeadResponse は入れていない）ので、確認は `curl -I` ではなく GET で行う
    get("/towers/{id}/image.png") {
        val id = call.parameters["id"].orEmpty()
        val tower = store.get(id)
        if (tower == null) {
            call.respond(HttpStatusCode.NotFound, "tower not found")
            return@get
        }
        val png = withContext(Dispatchers.Default) { TowerPng.render(tower) }
        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
        call.respondBytes(png, ContentType.Image.PNG)
    }
}

/**
 * 共有 URL に使う自分の公開 URL。
 * Cloud Run では X-Forwarded-Proto: https が付くので、それを見る。環境変数 PUBLIC_BASE_URL で上書き可。
 */
fun publicBaseUrl(call: ApplicationCall): String {
    System.getenv("PUBLIC_BASE_URL")?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
    val proto = call.request.header("X-Forwarded-Proto") ?: "http"
    val host = call.request.host()
    val port = call.request.port()
    val portPart = if ((proto == "http" && port == 80) || (proto == "https" && port == 443) || proto == "https") "" else ":$port"
    return "$proto://$host$portPart"
}
