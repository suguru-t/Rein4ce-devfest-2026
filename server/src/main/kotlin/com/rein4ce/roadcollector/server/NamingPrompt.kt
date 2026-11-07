package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Gemini への指示文（パーツの命名）。
 *
 * 手がかりは形・距離・日時だけ。Part には位置情報が入っていないので、地名につながる情報は渡しようがない。
 * 数字のままでは Gemini が想像しにくいので、人が読める言葉に直してから渡す（例：walkedAt → 「晩秋の夕方」）。
 *
 * 名前のお手本は書かない：書くと、お手本とそっくりな名前ばかり返ってくるため。ルールで方向だけ伝える。
 * 長さの切り詰めやかぎ括弧の除去はサーバ側（cleanName）でもやるので、Gemini がルールを破っても大丈夫。
 */

/** 名前の雰囲気。試し打ちで決めたら、ここを 1 行替える（docs/decisions.md の 10 番も直す） */
internal const val NAMING_TONE = "雰囲気は自由。詩的でも、ゆるくかわいくても、図鑑風でもよい"

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

/**
 * @param tooLong 聞き直しのとき、前に返ってきた長すぎる名前。見せたうえで「短く」と頼む（同じ質問を繰り返すより短くなりやすい）
 */
internal fun buildNamingPrompt(part: Part, tooLong: String? = null): String {
    val facts = buildList {
        add("道の形：${describeShape(part)}")
        add("歩いた距離：${describeDistance(part.distanceM)}")
        describeWhen(part.walkedAt)?.let { add("歩いた時間：$it") }
    }
    return buildString {
        appendLine("あなたは、散歩の道から生まれた積み木パーツに名前を付ける係です。")
        appendLine("次の散歩の特徴から、パーツの名前を 1 つ考えてください。")
        appendLine()
        facts.forEach { appendLine("- $it") }
        appendLine()
        appendLine("ルール：")
        // 上限ちょうどを頼むと、たまに 11〜12 文字で返ってくる（「夕暮れの七曲りブロック」）。目安を短めにして余裕を持たせる
        appendLine("- 日本語で ${PartNamer.TARGET_NAME_LENGTH} 文字くらい。長くても ${PartNamer.MAX_NAME_LENGTH} 文字まで（${PartNamer.MAX_NAME_LENGTH + 1} 文字以上は使えない）")
        appendLine("- 形・距離・時間のどれかが伝わる名前にする")
        appendLine("- 距離の数字は名前に入れない（画面に別に出るため）")
        appendLine("- 地名・人名・店名・実在の作品名は入れない")
        appendLine("- 絵文字・記号・かぎ括弧は使わない")
        appendLine("- 「〜のパーツ」「〜の道」で終わる名前ばかりにしない")
        appendLine("- ありがちな名前は避け、意外性のある言葉選びにする")
        appendLine("- $NAMING_TONE")
        if (tooLong != null) {
            appendLine()
            appendLine("前に考えてもらった「$tooLong」は ${nameLength(tooLong)} 文字で長すぎました。${PartNamer.MAX_NAME_LENGTH} 文字以内の別の名前にしてください。")
        }
    }.trimEnd()
}

/** 例：「何度も折れ曲がった道（ジグザグ）。横に長い。5 回曲がった」 */
internal fun describeShape(part: Part): String {
    val base = when (part.kind) {
        Kind.BAR -> "まっすぐな道（棒）"
        Kind.L -> "一度大きく曲がった道（L字）"
        Kind.U -> "行って折り返してきた道（U字）"
        Kind.RING -> "ぐるっと一周して戻ってきた道（輪）"
        Kind.ZIGZAG -> "何度も折れ曲がった道（ジグザグ）"
        Kind.OTHER -> "自由気ままな形の道"
    }
    val details = buildList {
        describeAspect(part)?.let { add(it) }
        val turns = countTurns(part)
        if (turns > 0) add("$turns 回曲がった")
    }
    return (listOf(base) + details).joinToString("。")
}

/** 縦長・横長。座標は正規化済み（長辺 1.0）なので、外接矩形の縦横比を見る */
internal fun describeAspect(part: Part): String? {
    if (part.points.size < 2) return null
    val w = part.points.maxOf { it.x } - part.points.minOf { it.x }
    val h = part.points.maxOf { it.y } - part.points.minOf { it.y }
    return when {
        w <= 0f && h <= 0f -> null
        h <= 0f || w / h >= 1.5f -> "横に長い"
        w <= 0f || h / w >= 1.5f -> "縦に長い"
        else -> "縦横がほぼ同じ"
    }
}

/** 曲がり角の数。簡略化済みの頂点なので、端点を除いた頂点が曲がり角。輪は全頂点が曲がり角 */
internal fun countTurns(part: Part): Int =
    if (part.closed) part.points.size else (part.points.size - 2).coerceAtLeast(0)

/** 1 km 未満は m、以上は小数 1 桁の km */
internal fun describeDistance(distanceM: Int): String = when {
    distanceM <= 0 -> "不明"
    distanceM < 1000 -> "$distanceM m"
    else -> String.format(Locale.ROOT, "%.1f km", distanceM / 1000.0)
}

/** 例：「晩秋の夕方」。walkedAt が入っていなければ null（その行は渡さない） */
internal fun describeWhen(walkedAt: Long, zone: ZoneId = JST): String? {
    if (walkedAt <= 0L) return null
    val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(walkedAt), zone)
    return "${season(t.monthValue)}の${timeOfDay(t.hour)}"
}

internal fun season(month: Int): String = when (month) {
    3 -> "早春"; 4 -> "春"; 5 -> "晩春"
    6 -> "初夏"; 7 -> "夏"; 8 -> "晩夏"
    9 -> "初秋"; 10 -> "秋"; 11 -> "晩秋"
    12 -> "初冬"; 1 -> "冬"; 2 -> "晩冬"
    else -> "ある季節"
}

internal fun timeOfDay(hour: Int): String = when (hour) {
    in 4..9 -> "朝"
    in 10..15 -> "昼"
    in 16..18 -> "夕方"
    in 19..23 -> "夜"
    else -> "深夜"
}
