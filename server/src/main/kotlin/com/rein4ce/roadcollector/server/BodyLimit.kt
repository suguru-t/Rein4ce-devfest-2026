package com.rein4ce.roadcollector.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * 送られてくる本文の大きさの上限 [バイト]（決定 88）。
 * 前は本文を全部読んでから検査していたので、Cloud Run が通す 32 MB 近い本文をいくつか同時に送られると、
 * メモリ 256 MB のサーバが足りなくなって落ちることがあった。上限を超えるものは読み切らずに 413 で断る
 */

/**
 * タワー（POST /towers・POST /towers/challenge・PUT /towers/daily）。アプリが作る塔はパーツ 100 個（点は 1 つ 50 まで）・
 * 配置 500 個でも 250 KB ほど（TowerValidation.kt）。Firestore の 1 件の上限（1 MiB）にも、JSON と一覧用の項目を合わせて収まる
 */
const val MAX_TOWER_BODY_BYTES = 512L * 1024

/** パーツ 1 つ（POST /parts/name）。点は 50 個まで（[MAX_PART_POINTS]）なので、数 KB で足りる */
const val MAX_PART_BODY_BYTES = 16L * 1024

/**
 * 本文を [maxBytes] まで読んで文字列にする。大きすぎたら 413 を返して null。
 * Content-Length が上限を超えていれば読まずに断り、Content-Length の無い本文（chunked）も上限の 1 バイト先までしか読まない
 */
internal suspend fun ApplicationCall.receiveTextWithin(maxBytes: Long): String? {
    val declared = request.contentLength()
    if (declared != null && declared > maxBytes) {
        respond(HttpStatusCode.PayloadTooLarge, "body too large (> $maxBytes bytes)")
        return null
    }
    val bytes = receiveChannel().readRemaining(maxBytes + 1).readByteArray()
    if (bytes.size > maxBytes) {
        respond(HttpStatusCode.PayloadTooLarge, "body too large (> $maxBytes bytes)")
        return null
    }
    return bytes.decodeToString()
}
