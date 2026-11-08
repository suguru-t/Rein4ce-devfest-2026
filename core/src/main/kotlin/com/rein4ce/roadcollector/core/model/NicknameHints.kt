package com.rein4ce.roadcollector.core.model

import kotlinx.serialization.Serializable

/**
 * POST /nickname のリクエスト：ニックネームを AI（サーバの Gemini）に考えてもらうときの手がかり。
 * 応答は [PartNameResponse]（{ "name": "..." }）。考えられなかったときサーバは 503 を返す。
 *
 * 手がかりは、コレクションの自分のパーツ（見本は除く）の名前・本数・合計距離だけ。歩いた場所は送らない
 * （パーツの名前付けで形・距離・日時だけを送るのと同じ考え方。docs/decisions.md の 8・40）
 *
 * @property partNames 自分で付けた名前と AI が付けた名前（新しい順に [MAX_PART_NAMES] 個まで。名前のないパーツは入れない）
 * @property partCount 集めた道の本数
 * @property totalDistanceM 集めた道の合計の長さ [m]
 */
@Serializable
data class NicknameHints(
    val partNames: List<String> = emptyList(),
    val partCount: Int = 0,
    val totalDistanceM: Int = 0,
) {
    companion object {
        /** 送るパーツの名前の数の上限（新しい順） */
        const val MAX_PART_NAMES = 12

        /** ニックネームの長さの上限（アプリの入力欄と同じ。「みんな」の一覧の 1 行に収まる長さ） */
        const val MAX_NICKNAME_LENGTH = 12
    }
}
