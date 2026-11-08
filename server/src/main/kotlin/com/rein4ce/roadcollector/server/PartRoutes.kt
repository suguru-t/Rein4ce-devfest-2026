package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.NicknameHints
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartNameResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
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

    /**
     * POST /nickname  ニックネームを考える
     *
     *   リクエスト：NicknameHints（JSON）。手がかりはパーツの名前・本数・合計距離だけ
     *   200 { "name": "..." }   考えられた（12 文字以内）
     *   400                     NicknameHints として読めない、数が変（負の数・多すぎる名前）
     *   413                     大きすぎる（16 KB を超える）
     *   503                     考えられなかった（時間切れ、Gemini の失敗、命名が無効）。アプリは入力欄をそのままにする
     */
    post("/nickname") {
        // 受け取る大きさの上限（名前 50 個ぶんでも数 KB。それより大きいものは読まない。#81 のレビュー）
        val size = call.request.contentLength()
        if (size != null && size > MAX_NICKNAME_BODY_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge, "body too large")
            return@post
        }
        val hints = try {
            call.receive<NicknameHints>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "body must be NicknameHints")
            return@post
        }
        if (hints.partCount !in 0..MAX_HINT_PART_COUNT || hints.totalDistanceM !in 0..MAX_HINT_DISTANCE_M ||
            hints.partNames.size > MAX_HINT_NAMES
        ) {
            call.respond(HttpStatusCode.BadRequest, "partCount, totalDistanceM or partNames out of range")
            return@post
        }

        val name = namer.nicknameSafely(cleanNicknameHints(hints))
        if (name == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, "nickname unavailable")
            return@post
        }
        call.respond(PartNameResponse(name))
    }
}

/** POST /nickname で受け付ける大きさの上限 [バイト] */
const val MAX_NICKNAME_BODY_BYTES = 16_384L

/** POST /nickname で受け付ける数の上限（いたずらで大きなデータを送られて、Gemini への指示文が長くならないように） */
const val MAX_HINT_NAMES = 50
const val MAX_HINT_PART_COUNT = 100_000
const val MAX_HINT_DISTANCE_M = 100_000_000
