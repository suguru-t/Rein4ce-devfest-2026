package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.NicknameHints
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
 * かぎ括弧の除去や空の判定はサーバ側（cleanName）でもやる。長さは nameSafely が見て、長すぎたら前の案を見せて 1 回だけ聞き直す（切り詰めない）。
 *
 * 言葉づかい（決定 91）：前は「意外性のある言葉選び」と頼んでいて、ほとんどの名前が「季節・時間帯＋形のたとえ」
 * （「春夜のループ」「霜柱の直線バー」「夏夜の水平線」）になり、AI が作った名前らしさが強かった。
 * 詩的な名前と、漢字をつなげた言葉（春夜・冬朝 など）はよい（C の判断）。カタカナの英語と、いつも「季節・時間帯＋形」になる型を
 * 避けるよう書く。季節と時間帯は入れなくてよいことにし、「意外性」は頼まない
 */

/** 名前の雰囲気。試し打ちで決めたら、ここを 1 行替える（docs/decisions.md の 89 番も直す） */
internal const val NAMING_TONE = "詩的でもよいが、ふだん使う言葉で。歩いた本人が散歩の道をメモに書いても照れないくらいの名前にする"

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

/**
 * @param tooLong 聞き直しのとき、前に返ってきた長すぎる名前。見せたうえで「短く」と頼む（同じ質問を繰り返すより短くなりやすい）
 */
internal fun buildNamingPrompt(part: Part, tooLong: String? = null): String {
    val facts = buildList {
        add("道の形：${describeShape(part)}")
        add("歩いた距離：${describeDistance(part.distanceM)}" + (distanceFeel(part.distanceM)?.let { "（$it）" } ?: ""))
        describeWhen(part.walkedAt)?.let { add("歩いた時間：$it") }
    }
    return buildString {
        appendLine("あなたは、散歩アプリで、歩いた道に名前を付ける係です。")
        appendLine("歩いた本人があとで一覧を見て「ああ、あの道か」と分かるような名前を 1 つ考えてください。")
        appendLine()
        facts.forEach { appendLine("- $it") }
        appendLine()
        appendLine("ルール：")
        // 上限ちょうどを頼むと、たまに 11〜12 文字で返ってくる（「夕暮れの七曲りブロック」）。目安を短めにして余裕を持たせる
        appendLine("- 日本語で ${PartNamer.TARGET_NAME_LENGTH} 文字くらい。長くても ${PartNamer.MAX_NAME_LENGTH} 文字まで（${PartNamer.MAX_NAME_LENGTH + 1} 文字以上は使えない）")
        appendLine("- $NAMING_TONE")
        appendLine("- 道の形や歩き方（まっすぐ・曲がった・行って戻った・一周した など）が伝わる名前にする")
        appendLine("- カタカナの英語（ループ・ターン・バー など）は使わない")
        appendLine("- 季節や時間帯は入れなくてよい。入れるなら「朝」「夜」くらいの一言にする")
        appendLine("- 「〜の道」「〜道」で終わらせない。「〇〇の〇〇」の形でなくてよく、歩いたことを短く言う言い方（〜まで・〜して戻る・〜を一周 など）でもよい")
        appendLine("- 距離の数字は名前に入れない（画面に別に出るため）")
        appendLine("- 地名・人名・店名・実在の作品名は入れない")
        appendLine("- 絵文字・記号・かぎ括弧は使わない")
        if (tooLong != null) {
            appendLine()
            appendLine("前に考えてもらった「$tooLong」は ${nameLength(tooLong)} 文字で長すぎました。${PartNamer.MAX_NAME_LENGTH} 文字以内の別の名前にしてください。")
        }
    }.trimEnd()
}

/**
 * ニックネームの指示文。手がかりは、コレクションのパーツの名前・本数・合計距離だけ（[NicknameHints]。場所は来ない）。
 * 「みんなのタワー」に作者名として出るので、人に見せて気持ちのよい名前にする。
 * パーツの名前は人が自由に打ったものも混ざるので、「名前の一覧」として渡し、雰囲気の手がかりにだけ使ってもらう
 *
 * @param hints 掃除済みの手がかり（[cleanNicknameHints]）
 * @param tooLong 聞き直しのとき、前に返ってきた長すぎる名前
 */
internal fun buildNicknamePrompt(hints: NicknameHints, tooLong: String? = null): String = buildString {
    appendLine("あなたは、歩いた道を集めて積み木のように積むアプリ「ミチツミ」で、遊ぶ人のニックネームを考える係です。")
    appendLine("次のような道を集めている人に似合うニックネームを 1 つ考えてください。")
    appendLine()
    if (hints.partCount <= 0) {
        appendLine("- まだ道を集めていない（これから歩き始める人）")
    } else {
        appendLine("- 集めた道：${hints.partCount} 本（合計 ${describeDistance(hints.totalDistanceM)}）")
        if (hints.partNames.isNotEmpty()) {
            appendLine("- 集めた道に付いている名前：${hints.partNames.joinToString("、") { "「$it」" }}")
        }
    }
    appendLine()
    appendLine("ルール：")
    appendLine("- 日本語で ${PartNamer.TARGET_NICKNAME_LENGTH} 文字くらい。長くても ${PartNamer.MAX_NICKNAME_LENGTH} 文字まで（${PartNamer.MAX_NICKNAME_LENGTH + 1} 文字以上は使えない）")
    appendLine("- 「みんなのタワー」に作者名として出るので、呼びやすく、親しみのある名前にする")
    appendLine("- 道の名前は雰囲気の手がかりにだけ使い、そのまま並べたり、そのまま使ったりしない")
    appendLine("- 距離や本数の数字は入れない")
    appendLine("- 実在の人名・有名人・キャラクター・作品名・地名・店名は使わない")
    appendLine("- 人を傷つける言葉や、下品な言葉は使わない")
    appendLine("- 絵文字・記号・かぎ括弧は使わない")
    appendLine("- ありがちな名前は避け、意外性のある言葉選びにする")
    if (tooLong != null) {
        appendLine()
        appendLine("前に考えてもらった「$tooLong」は ${nameLength(tooLong)} 文字で長すぎました。${PartNamer.MAX_NICKNAME_LENGTH} 文字以内の別の名前にしてください。")
    }
}.trimEnd()

/**
 * 受け取った手がかりの掃除：名前は前後の空白と改行を取り、空と重複を除いて、先頭から [NicknameHints.MAX_PART_NAMES] 個まで。
 * - 1 つの名前が [MAX_HINT_NAME_LENGTH] 文字を超えたら、その名前は使わない（指示文を長くしすぎないため）。
 *   空白をまとめる前に、元の長さでも先にはじく（大きな文字を送られても、そのぶん処理しないように。#81 のレビュー）
 * - かぎ括弧と引用符は取る（指示文では名前をかぎ括弧で囲んで渡すので、名前の中のかぎ括弧で指示を書き換えにくくする。#81 のレビュー）
 */
internal fun cleanNicknameHints(hints: NicknameHints): NicknameHints = hints.copy(
    partNames = hints.partNames
        .asSequence()
        .filter { it.length <= MAX_HINT_NAME_LENGTH * 4 }
        .map { it.replace(HINT_QUOTES, "").replace(WHITESPACE, " ").trim() }
        .filter { it.isNotEmpty() && nameLength(it) <= MAX_HINT_NAME_LENGTH }
        .distinct()
        .take(NicknameHints.MAX_PART_NAMES)
        .toList(),
)

/** 名前の中の空白・改行（1 つにまとめる）と、かぎ括弧・引用符（取る）。名前ごとに作り直さないよう 1 つずつ作っておく */
private val WHITESPACE = Regex("\\s+")
private val HINT_QUOTES = Regex("[「」『』\"“”'‘’]")

/** 手がかりに使うパーツの名前の長さの上限（アプリのパーツの名前は 10 文字まで。少し余裕を持たせる） */
internal const val MAX_HINT_NAME_LENGTH = 20

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

/**
 * 距離の感じ（決定 91）。数字だけだと、ふだんの言葉の名前（「ちょっとそこまで」など）につながりにくいので、言葉でも添える。
 * 分からない（0 以下）なら null
 */
internal fun distanceFeel(distanceM: Int): String? = when {
    distanceM <= 0 -> null
    distanceM < 300 -> "ほんの少し"
    distanceM < 1000 -> "ちょっとそこまで"
    distanceM < 3000 -> "けっこう歩いた"
    else -> "たくさん歩いた"
}

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
