package com.rein4ce.roadcollector.core.model

import kotlinx.serialization.Serializable

/**
 * 2D 座標。単位はパーツ座標系（長辺 = 1.0、y は上向き）。
 */
@Serializable
data class Pt(val x: Float, val y: Float)

/**
 * 表示用の形の分類。ゲームのルールはこれに依存しない（物理は points から作る）。
 */
@Serializable
enum class Kind(val label: String) {
    BAR("棒"),
    L("L字"),
    U("U字"),
    RING("輪"),
    ZIGZAG("ジグザグ"),
    OTHER("その他");
}

/**
 * 採集したパーツ。チーム共通のデータ形式。
 *
 * - 採集側（GPS → 変換）はこれを **作る**
 * - ゲーム側はこれから物理ボディを **作る**
 * - サーバはこれを JSON で **受け取る**
 *
 * フィールドを変えるときは必ず全員に周知すること。
 *
 * @property points     正規化済みの頂点列。バウンディングボックスの中心が原点、長辺が 1.0。最大 24 個（ConversionParams.maxVertices）。
 * @property thickness  太さ。長辺に対する比率（初期値 0.15）。
 * @property closed     輪なら true（最後の点と最初の点をつなぐ）。
 * @property kind       表示用の分類。
 * @property distanceM  歩いた距離 [m]。
 * @property walkedAt   採集日時（epoch millis）。
 * @property name       Gemini などで付けた名前（任意、後から入る）。
 */
@Serializable
data class Part(
    val id: String,
    val points: List<Pt>,
    val thickness: Float = DEFAULT_THICKNESS,
    val closed: Boolean = false,
    val kind: Kind = Kind.OTHER,
    val distanceM: Int = 0,
    val walkedAt: Long = 0L,
    val name: String? = null,
) {
    val displayName: String get() = name ?: kind.label

    companion object {
        const val DEFAULT_THICKNESS = 0.15f
    }
}
