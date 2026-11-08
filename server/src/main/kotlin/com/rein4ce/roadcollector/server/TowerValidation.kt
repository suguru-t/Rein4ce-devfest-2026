package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import kotlin.math.abs

/**
 * POST /towers で受け付けるタワーの検査。
 *
 * この URL は認証なしで誰でも叩けるので、壊れたデータを Firestore に入れない歯止め。
 * 壊れたタワーが 1 基入ると、「みんな」の一覧の絵や共有ページの PNG がそのタワーで落ちる（NaN や巨大な座標）。
 * 上限はゲームのルールより余裕を見て、アプリが正しく作ったタワーを弾かないようにする。
 */

/** タワー 1 基のパーツ数・配置数の上限。ゲームは 5 本（GameSession.MAX_PARTS）だが、ルールが変わっても弾かないよう余裕を見る */
const val MAX_TOWER_PARTS = 10

/** 作者名の上限（文字）。みんなの一覧のカードに収まる長さ */
const val MAX_AUTHOR_LENGTH = 20

/** パーツの名前の上限（文字）。アプリは 10 文字までだが、余裕を見る */
const val MAX_PART_NAME_LENGTH = 30

/** 持ち主の印の形：SHA-256 の 16 進（64 桁、小文字）。アプリの OwnerId が作る形。GET /towers の X-Owner-Id も同じ */
val OWNER_ID_PATTERN = Regex("^[0-9a-f]{64}$")

/** 一覧を取るときに持ち主の印を送るヘッダ */
const val OWNER_HEADER = "X-Owner-Id"

/** 座標・高さ・太さの上限。台の幅は 1.4 m、5 本積んでも数 m。これを超える値は壊れたデータ */
const val MAX_COORDINATE = 100f

/**
 * パーツの大きさ（Placement.scale）の範囲。アプリは歩いた距離で 0.7〜1.4 倍（PartSize）にするが、
 * 決め方が変わっても弾かないよう余裕を見る。0 や巨大な値は、絵が消えたり画面を埋めたりする
 */
const val MIN_PLACEMENT_SCALE = 0.2f
const val MAX_PLACEMENT_SCALE = 5f

/**
 * 受け付けられない理由（英語の短い文。400 の本文にそのまま出す）。問題なければ null。
 * 最初に見つかった 1 つだけ返す。
 */
internal fun Tower.validationError(): String? {
    if (parts.isEmpty()) return "parts is empty"
    if (parts.size > MAX_TOWER_PARTS) return "too many parts (${parts.size} > $MAX_TOWER_PARTS)"
    if (placements.isEmpty()) return "placements is empty"
    if (placements.size > MAX_TOWER_PARTS) return "too many placements (${placements.size} > $MAX_TOWER_PARTS)"

    val ids = HashSet<String>()
    for (p in parts) {
        if (p.id.isBlank()) return "part id is blank"
        if (!ids.add(p.id)) return "duplicate part id: ${p.id}"
        if (p.points.isEmpty() || p.points.size > MAX_PART_POINTS) return "part ${p.id}: points must have 1..$MAX_PART_POINTS items"
        if (p.points.any { !inRange(it.x) || !inRange(it.y) }) return "part ${p.id}: point out of range"
        if (!p.thickness.isFinite() || p.thickness <= 0f || p.thickness > MAX_COORDINATE) return "part ${p.id}: thickness out of range"
        if (p.distanceM < 0) return "part ${p.id}: distanceM is negative"
        val name = p.name
        if (name != null && name.codePointCount(0, name.length) > MAX_PART_NAME_LENGTH) return "part ${p.id}: name too long (> $MAX_PART_NAME_LENGTH)"
    }
    for ((i, pl) in placements.withIndex()) {
        if (pl.partId !in ids) return "placement $i: unknown partId ${pl.partId}"
        if (!inRange(pl.x) || !inRange(pl.y) || !pl.angleRad.isFinite()) return "placement $i: out of range"
        if (!pl.scale.isFinite() || pl.scale < MIN_PLACEMENT_SCALE || pl.scale > MAX_PLACEMENT_SCALE) return "placement $i: scale out of range"
    }
    if (!inRange(height) || height < 0f) return "height out of range"
    val a = author
    if (a != null && a.codePointCount(0, a.length) > MAX_AUTHOR_LENGTH) return "author too long (> $MAX_AUTHOR_LENGTH)"
    val o = ownerId
    if (o != null && !OWNER_ID_PATTERN.matches(o)) return "ownerId must be 64 hex chars"
    return null
}

/** 有限で、絶対値が [MAX_COORDINATE] 以下 */
private fun inRange(v: Float): Boolean = v.isFinite() && abs(v) <= MAX_COORDINATE
