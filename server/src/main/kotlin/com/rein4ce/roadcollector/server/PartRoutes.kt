package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartNameResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException

/** 頂点がこれより多いパーツは受け付けない（変換結果は 12 個程度。Gemini に大きなデータを送らないための歯止め） */
const val MAX_PART_POINTS = 50

/**
 * POST /parts/name  パーツに名前を付ける
 *
 *   リクエスト：Part（JSON）。名前の手がかりは形・距離・日時だけ
 *   200 { "name": "..." }   名前が付いた（10 文字以内）
 *   400                     Part として読めない、頂点が 0 個か多すぎる
 *   503                     名前を付けられなかった（時間切れ、Gemini の失敗、命名が無効）。アプリは今の表示のままにする
 */
fun Route.partRoutes(namer: PartNamer) {

    post("/parts/name") {
        val part = try {
            call.receive<Part>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "body must be a Part")
            return@post
        }
        if (part.points.isEmpty() || part.points.size > MAX_PART_POINTS) {
            call.respond(HttpStatusCode.BadRequest, "points must have 1..$MAX_PART_POINTS items")
            return@post
        }

        val name = namer.nameSafely(part)
        if (name == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, "naming unavailable")
            return@post
        }
        call.respond(PartNameResponse(name))
    }
}
