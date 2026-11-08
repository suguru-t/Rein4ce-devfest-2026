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
    /**
     * パーツの大きさ（長辺 [m]）。歩いた距離で決まる（[com.rein4ce.roadcollector.core.physics.PartSize]）。
     * 描くときは、パーツの形をこの倍率で大きくしてから置く。大きさを入れる前のタワーには無いので 1
     */
    val scale: Float = 1f,
)

/**
 * 完成したタワー（作品）。共有・ギャラリー・ランキングの単位。
 *
 * @property author  作者名（アプリの設定のニックネーム）。見た目だけで、誰かを見分けるものではない
 * @property ownerId 持ち主の印。アプリが端末ごとに作る 64 桁の 16 進（端末の id を塩を付けてハッシュしたもの。元の id は分からない）。
 *   サーバはこれを保存して「あなたのタワーか」を答えるのに使い、**誰にも返さない**（一覧は [TowerSummary.mine] だけ、
 *   1 基の JSON では null にして返す）。古いアプリは送らないので null
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
    val ownerId: String? = null,
)

/**
 * ギャラリー一覧用の軽い形。
 * @property mine 一覧を取ったアプリ（X-Owner-Id ヘッダの持ち主）が送ったタワーなら true。持ち主の印そのものは入れない
 */
@Serializable
data class TowerSummary(
    val id: String,
    val height: Float,
    val partCount: Int,
    val createdAt: Long,
    val author: String? = null,
    val mine: Boolean = false,
)

/** POST /towers の応答。 */
@Serializable
data class TowerCreated(
    val id: String,
    val url: String,
)
