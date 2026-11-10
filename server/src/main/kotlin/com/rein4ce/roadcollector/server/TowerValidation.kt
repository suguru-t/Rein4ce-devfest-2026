package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.physics.PartGeometry
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * POST /towers・POST /towers/challenge・PUT /towers/daily で受け付けるタワーの検査。
 *
 * この URL は認証なしで誰でも叩けるので、壊れたデータを Firestore に入れない歯止め。
 * 壊れたタワーが 1 基入ると、「みんな」の一覧の絵や共有ページの PNG がそのタワーで落ちる（NaN や巨大な座標）。
 * 上限はゲームのルールより余裕を見て、アプリが正しく作ったタワーを弾かないようにする。
 */

/**
 * タワー 1 基のパーツ（種類）の数の上限。積む画面の通常は、台から落ちるまで終わりなく積む（#108・決定 62）ので、
 * 10 から 100 に上げた（決定 66）。100 個（点は 1 つ 50 まで）でも JSON は 200 KB ほどで、Firestore の 1 件の上限（1 MiB）に収まる
 */
const val MAX_TOWER_PARTS = 100

/**
 * 毎日 1 つずつ積むの塔のパーツ（種類）の上限（決定 88）。1 日に 1 つ、その日に歩いた違うパーツを積むので、
 * [MAX_TOWER_PARTS]（100）のままだと、101 日目から送れなくなっていた。配置の上限（[MAX_TOWER_PLACEMENTS]）と同じにする。
 * ふつうのパーツ（点 12 個ほど）なら 500 個でも 300 KB ほどで、本文の上限（BodyLimit.kt）に収まる
 */
const val MAX_DAILY_TOWER_PARTS = 500

/**
 * 配置（台に残った数）の上限。同じパーツを何度も置けるので（通常は全部のパーツから出し、3 分チャレンジは 5 種類の形をくり返す）、
 * パーツの種類より多くなる。前はパーツと同じ 100 で、通常で 100 個を超えて積むと「保存」が 400 で断られていた（決定 77）。
 * 配置は 1 つ 100 バイトほどなので、500 個でも 50 KB ほど
 */
const val MAX_TOWER_PLACEMENTS = 500

/** 3 分チャレンジの決まりの ID の上限（文字。`manual-180-v6` など） */
const val MAX_RULE_LENGTH = 40

/** 毎日 1 つずつ積むの連続日数の上限（壊れた値を弾くだけ。100 年ぶん） */
const val MAX_STREAK_DAYS = 36_500

/** 毎日 1 つずつ積むの日付の形（"2026-11-10"） */
private val DAY_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/**
 * 毎日 1 つずつ積むを始めた日（決定 61）。これより前から続いている連続日数はありえないので断る（決定 88）。
 * 端末の日付のずれも見て、月の初めにしてある
 */
val DAILY_LAUNCH: LocalDate = LocalDate.of(2026, 11, 1)

/** パーツの id の上限（文字）。アプリの id は UUID（36 字）か、見本・90 秒チャレンジの短い名前 */
const val MAX_PART_ID_LENGTH = 64

/**
 * 高さの検査の余裕（決定 88）。置いたパーツから見積もった「届きうるいちばん上」に、この割合と長さ [m] を足した高さまで通す
 * （見積もりと物理の計算のずれ・小数の丸めで、アプリが正しく作った塔を断らないように）
 */
private const val HEIGHT_SLACK_RATIO = 1.02
private const val HEIGHT_SLACK_M = 0.1

/** 作者名の上限（文字）。みんなの一覧のカードに収まる長さ */
const val MAX_AUTHOR_LENGTH = 20

/** パーツの名前の上限（文字）。アプリは 10 文字までだが、余裕を見る */
const val MAX_PART_NAME_LENGTH = 30

/** 持ち主の印の形：SHA-256 の 16 進（64 桁、小文字）。アプリの OwnerId が作る形。GET /towers の X-Owner-Id も同じ */
val OWNER_ID_PATTERN = Regex("^[0-9a-f]{64}$")

/** 一覧を取るときに持ち主の印を送るヘッダ */
const val OWNER_HEADER = "X-Owner-Id"

/** 台の幅の範囲 [m]。今までの台は 1.4 m、通常の広い台は 2.8 m（決定 66）。0 や巨大な値は、絵が消えたり画面を埋めたりする */
const val MIN_PLATFORM_WIDTH = 0.5f
const val MAX_PLATFORM_WIDTH = 10f

/**
 * 座標・高さ・太さの上限 [m]。これを超える値は壊れたデータ。
 * 通常は終わりなく積み（決定 62）、終わったときに天気のボーナスで塔ごと最大 3 倍に大きくする（決定 68）ので、
 * 前の 100 m では、元の塔が 33 m を超えると送れなかった。300 m にして、元の塔が 100 m まで送れるようにした（#125 の C のレビュー）
 */
const val MAX_COORDINATE = 300f

/**
 * パーツの大きさ（Placement.scale）の範囲。アプリは歩いた距離で 0.7〜1.4 倍（PartSize）にするが、
 * 決め方が変わっても弾かないよう余裕を見る。0 や巨大な値は、絵が消えたり画面を埋めたりする
 */
const val MIN_PLACEMENT_SCALE = 0.2f
const val MAX_PLACEMENT_SCALE = 5f

/**
 * 受け付けられない理由（英語の短い文。400 の本文にそのまま出す）。問題なければ null。
 * 最初に見つかった 1 つだけ返す。
 *
 * 高さ・連続日数・最後に積んだ日はアプリが計算して送るので、作った値で 1 位になれないよう、ありうる範囲かも見る（決定 88）：
 * 高さは置いたパーツが届く高さまで、最後に積んだ日は次の日まで、連続日数は台に残ったパーツの数と、始めた日からの日数まで
 *
 * @param today 最後に積んだ日が未来でないかを見る日（UTC。端末の日付は時差で 1 日先まであるので、次の日までは通す）
 */
internal fun Tower.validationError(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): String? {
    if (parts.isEmpty()) return "parts is empty"
    val maxParts = if (mode == TowerMode.DAILY) MAX_DAILY_TOWER_PARTS else MAX_TOWER_PARTS
    if (parts.size > maxParts) return "too many parts (${parts.size} > $maxParts)"
    if (placements.isEmpty()) return "placements is empty"
    if (placements.size > MAX_TOWER_PLACEMENTS) return "too many placements (${placements.size} > $MAX_TOWER_PLACEMENTS)"

    val ids = HashSet<String>()
    for (p in parts) {
        if (p.id.isBlank()) return "part id is blank"
        if (p.id.length > MAX_PART_ID_LENGTH) return "part id too long (> $MAX_PART_ID_LENGTH)"
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
    if (height > reachableTop() * HEIGHT_SLACK_RATIO + HEIGHT_SLACK_M) return "height is taller than the placed parts can reach"
    if (!platformWidth.isFinite() || platformWidth < MIN_PLATFORM_WIDTH || platformWidth > MAX_PLATFORM_WIDTH) return "platformWidth out of range"
    val a = author
    if (a != null && a.codePointCount(0, a.length) > MAX_AUTHOR_LENGTH) return "author too long (> $MAX_AUTHOR_LENGTH)"
    val o = ownerId
    if (o != null && !OWNER_ID_PATTERN.matches(o)) return "ownerId must be 64 hex chars"
    // 遊び方ごとに要るもの（決定 77）
    when (mode) {
        TowerMode.NORMAL -> Unit
        TowerMode.CHALLENGE -> {
            val r = rule
            if (r.isNullOrBlank() || r.length > MAX_RULE_LENGTH) return "challenge tower needs rule (1..$MAX_RULE_LENGTH chars)"
        }
        TowerMode.DAILY -> {
            // 持ち主ごとに 1 基を新しくするので、持ち主の印が無いと誰の塔か分からない
            if (o == null) return "daily tower needs ownerId"
            val last = lastDay?.takeIf { DAY_PATTERN.matches(it) }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return "daily tower needs lastDay (yyyy-mm-dd)"
            if (last.isAfter(today.plusDays(1))) return "lastDay is in the future"
            if (streakDays < 1 || streakDays > MAX_STREAK_DAYS) return "streakDays out of range"
            // 1 日に 1 つ積み、続いている日のパーツは台に残っているので、連続日数は配置の数を超えない
            if (streakDays > placements.size) return "streakDays is more than the placed parts"
            if (streakDays > ChronoUnit.DAYS.between(DAILY_LAUNCH, last) + 1) return "streakDays is longer than the daily mode has existed"
        }
    }
    return null
}

/** 有限で、絶対値が [MAX_COORDINATE] 以下 */
private fun inRange(v: Float): Boolean = v.isFinite() && abs(v) <= MAX_COORDINATE

/**
 * 置いたパーツのいちばん上 [m]（台の上面から）。物理と同じ当たり判定の形（[PartGeometry.collisionShapes]）を、
 * 置いた向きに回して位置に動かし（世界の点 = 回転 × 形の点 + 位置。StackWorld.place と同じ）、いちばん高い頂点を取る。
 * 塔の高さ（StackWorld.height）も同じ形のいちばん上なので、アプリが正しく作った塔ならこれと同じになる。
 * 天気のボーナスで大きくした塔も、位置と大きさに同じ倍率をかけてあるので同じように出せる（決定 68）。partId が正しいことを確かめてから呼ぶ
 */
internal fun Tower.reachableTop(): Double {
    val byId = parts.associateBy { it.id }
    val shapes = HashMap<Pair<String, Float>, List<PartGeometry.Vec>>()
    return placements.maxOf { pl ->
        val points = shapes.getOrPut(pl.partId to pl.scale) {
            PartGeometry.collisionShapes(byId.getValue(pl.partId), pl.scale.toDouble()).flatMap { it.points }
        }
        val sin = sin(pl.angleRad.toDouble())
        val cos = cos(pl.angleRad.toDouble())
        pl.y + (points.maxOfOrNull { p -> sin * p.x + cos * p.y } ?: 0.0)
    }
}

/**
 * その遊び方で使わない項目を捨てる（決定 88）。前は通常の塔の rule・lastDay なども長さを見ずに保存していて、
 * 一覧（GET /towers）の各行にそのまま入るので、大きな文字列を入れた塔を何基か送られると、一覧が何十 MB にもなった
 */
internal fun Tower.withoutUnusedFields(): Tower = when (mode) {
    TowerMode.NORMAL -> copy(rule = null, streakDays = 0, lastDay = null)
    TowerMode.CHALLENGE -> copy(streakDays = 0, lastDay = null)
    TowerMode.DAILY -> copy(rule = null)
}
