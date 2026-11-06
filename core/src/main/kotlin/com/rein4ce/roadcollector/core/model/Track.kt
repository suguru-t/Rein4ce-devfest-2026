package com.rein4ce.roadcollector.core.model

import kotlinx.serialization.Serializable

/**
 * GPS の生の 1 点。Part には含めない（位置情報を外に出さないため）。
 * デバッグ用にアプリ内へ別保存する想定。
 */
@Serializable
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    /** 水平精度 [m]。不明なら 0。 */
    val accuracyM: Float = 0f,
    val timeMs: Long = 0L,
)

/**
 * 1 回の記録（開始〜停止）。
 */
@Serializable
data class Track(
    val id: String,
    val points: List<TrackPoint>,
    val startedAt: Long,
    val endedAt: Long,
)
