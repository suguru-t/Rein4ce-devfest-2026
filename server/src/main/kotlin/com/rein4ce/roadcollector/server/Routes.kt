package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.svg.TowerSvg
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.accept
import io.ktor.server.request.header
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * API 一覧（技術選定まとめ 4 章）
 *
 *   GET  /                     動作確認
 *   GET  /health               ヘルスチェック（/healthz は Cloud Run の予約パスで届かないため使わない）
 *   GET  /towers               一覧（新しい順）。X-Owner-Id ヘッダ（アプリの持ち主の印）を送ると、自分が送ったタワーに mine: true が付く。
 *                              ?mode=normal|challenge|daily で遊び方を選ぶ（無ければ通常。決定 77）。知らない値は 400
 *   POST /towers               通常のタワーを保存 → 201 { id, url }。壊れたタワー（空・多すぎ・NaN・知らない partId など）は 400（TowerValidation.kt）。
 *                              ownerId（持ち主の印）は保存するが、どの応答にも入れない。通常でないタワー（mode が challenge・daily）は 400
 *   POST /towers/challenge     3 分チャレンジのタワーを保存 → 201 { id, url }（決定 77）。決まりの ID（rule）が要る
 *   PUT  /towers/daily         毎日 1 つずつ積むの塔を、持ち主ごとに 1 基だけ持つ → 200 { id, url }（決定 77）。同じ持ち主の塔があれば、その id のまま
 *                              新しい形にする。持ち主の印（ownerId）・最後に積んだ日（lastDay）・連続日数（streakDays）が要る。
 *                              通常と URL を分けたのは、遊び方を知らない前のサーバに送っても 404 になり、通常のランキングに混ざらないようにするため
 *   GET  /towers/{id}          共有ページ（HTML）。Accept: application/json なら JSON（ownerId は null にして返す）。どの遊び方のタワーでも
 *   GET  /towers/{id}/image.svg  タワーの絵（SVG）。共有ページの中に描く
 *   GET  /towers/{id}/image.png  タワーの絵（PNG、1200×630）。共有ページの og:image（LINE / X のプレビュー）
 *
 *   POST /parts/name           パーツに名前を付ける（Gemini）→ { name }。失敗は 503。PartRoutes.kt
 *   POST /nickname             ニックネームを考える（Gemini）→ { name }。失敗は 503。PartRoutes.kt
 *
 *   一覧・投稿・名前・ニックネーム・タワー 1 基・絵は、1 分あたりの回数を数え、超えたら 429（Retry-After 付き）。RateLimit.kt
 *   本文は大きさの上限を超えたら読まずに 413（BodyLimit.kt。決定 88）
 *   TODO(C): 対戦方式が決まったら /challenges
 */
fun Route.towerRoutes(store: TowerStore, limits: RateLimits = RateLimits.OFF) {

    get("/") {
        call.respondText("Road Collector server OK (${store.count()} towers)")
    }

    get("/health") {
        call.respondText("ok")
    }

    get("/towers") {
        if (call.overLimit(limits.towersGet)) return@get
        // 遊び方（決定 77）。無ければ通常（前のアプリは付けずに取りに来る）
        val modeParam = call.request.queryParameters["mode"]
        val mode = if (modeParam == null) TowerMode.NORMAL else TowerMode.of(modeParam)
        if (mode == null) {
            call.respond(HttpStatusCode.BadRequest, "mode must be normal, challenge or daily")
            return@get
        }
        // アプリは自分の持ち主の印を X-Owner-Id で送る。形が違うもの（他人の印を探る試しなど）は無いものとして扱う
        val owner = call.request.header(OWNER_HEADER)?.takeIf { OWNER_ID_PATTERN.matches(it) }
        // 答えが人（印）によって違うので、途中のキャッシュ（CDN など）に共有させない
        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        call.respond(store.summaries(owner, mode))
    }

    post("/towers") {
        // 回数を数えてから読む（壊れたものを送り続けられても、1 分の上限で止まる。RateLimit.kt）
        if (call.overLimit(limits.towersPost)) return@post
        val tower = call.receiveValidTower() ?: return@post
        // 3 分チャレンジと毎日 1 つずつ積むは、それぞれの URL へ（ここで受けると通常のランキングに混ざる）
        if (tower.mode != TowerMode.NORMAL) {
            call.respond(HttpStatusCode.BadRequest, "challenge and daily towers go to POST /towers/challenge and PUT /towers/daily")
            return@post
        }
        val saved = store.add(tower)
        call.respond(HttpStatusCode.Created, TowerCreated(saved.id, "${publicBaseUrl(call)}/towers/${saved.id}"))
    }

    post("/towers/challenge") {
        if (call.overLimit(limits.towersPost)) return@post
        val tower = call.receiveValidTower(TowerMode.CHALLENGE) ?: return@post
        val saved = store.add(tower)
        call.respond(HttpStatusCode.Created, TowerCreated(saved.id, "${publicBaseUrl(call)}/towers/${saved.id}"))
    }

    put("/towers/daily") {
        if (call.overLimit(limits.towersPost)) return@put
        val tower = call.receiveValidTower(TowerMode.DAILY) ?: return@put
        val saved = store.putDaily(tower)
        call.respond(HttpStatusCode.OK, TowerCreated(saved.id, "${publicBaseUrl(call)}/towers/${saved.id}"))
    }

    get("/towers/{id}") {
        if (call.overLimit(limits.towerView)) return@get
        val id = call.parameters["id"].orEmpty()
        val tower = store.get(id)
        if (tower == null) {
            call.respond(HttpStatusCode.NotFound, "tower not found")
            return@get
        }
        val wantsJson = call.request.accept()?.contains("application/json") == true
        if (wantsJson) {
            // 持ち主の印は誰にも返さない（返すと、同じ人のタワーをたどれてしまう）
            call.respond(tower.copy(ownerId = null))
        } else {
            call.respondText(SharePage.render(tower, publicBaseUrl(call)), ContentType.Text.Html)
        }
    }

    get("/towers/{id}/image.svg") {
        if (call.overLimit(limits.towerView)) return@get
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
        // その場で描くので重い。LINE / X のプレビューが取りに来るくらいの回数で足りる
        if (call.overLimit(limits.towerImage)) return@get
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
 * 本文のタワーを読んで検査する。誰でも叩ける URL なので、壊れたタワーは保存前に 400 で断る（TowerValidation.kt）。
 * 大きすぎる本文は読み切らずに 413（BodyLimit.kt）。読めない JSON（NaN や型違いも）は Ktor の既定に任せず、ここで 400 にする。
 * その遊び方で使わない項目は捨てる（[withoutUnusedFields]。決定 88）。断ったら null（応答は済んでいる）
 *
 * @param mode 遊び方をこれにしてから検査する（3 分チャレンジ・毎日 1 つずつ積むの URL。本文の mode は見ない）。null なら本文のまま
 */
private suspend fun ApplicationCall.receiveValidTower(mode: TowerMode? = null): Tower? {
    val text = receiveTextWithin(MAX_TOWER_BODY_BYTES) ?: return null
    val received = try {
        AppJson.decodeFromString(Tower.serializer(), text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        respond(HttpStatusCode.BadRequest, "body must be a Tower")
        return null
    }
    val tower = if (mode == null) received else received.copy(mode = mode)
    tower.validationError()?.let { reason ->
        respond(HttpStatusCode.BadRequest, reason)
        return null
    }
    return tower.withoutUnusedFields()
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
