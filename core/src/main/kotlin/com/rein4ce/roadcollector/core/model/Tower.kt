package com.rein4ce.roadcollector.core.model

import kotlinx.serialization.Serializable

/**
 * タワー内でのパーツ 1 個の最終配置（物理の再現ではなく、静止した結果を保存する）。
 * 座標はワールド座標 [m]。角度はラジアン。
 */
@Serializable
data class Placement(
    val partId: String,
    val x: Float,
    val y: Float,
    val angleRad: Float,
)

/**
 * 完成したタワー（作品）。共有・ギャラリー・ランキングの単位。
 */
@Serializable
data class Tower(
    val id: String = "",
    val parts: List<Part>,
    val placements: List<Placement>,
    /** 台の上面からの高さ [m] */
    val height: Float,
    val createdAt: Long,
    val author: String? = null,
)

/** ギャラリー一覧用の軽い形。 */
@Serializable
data class TowerSummary(
    val id: String,
    val height: Float,
    val partCount: Int,
    val createdAt: Long,
    val author: String? = null,
)

/** POST /towers の応答。 */
@Serializable
data class TowerCreated(
    val id: String,
    val url: String,
)
