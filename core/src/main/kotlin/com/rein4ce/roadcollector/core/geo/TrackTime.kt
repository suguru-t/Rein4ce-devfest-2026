package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.TrackPoint

/**
 * 軌跡の時刻をずらす。
 * デモ再生（サンプル軌跡や過去に録ったファイル）を「今歩いた」ことにするために使う。
 * `walkedAt` は最後の点の時刻になるので、最後の点が [endMs] になるように全体を平行移動する。
 */
object TrackTime {

    /** 時刻が無い（0）ファイル用に、点の間隔として仮定する秒数（時速 4.8 km、4 m 刻み相当） */
    const val DEFAULT_STEP_MS = 3_000L

    fun shiftToEndAt(points: List<TrackPoint>, endMs: Long, stepMs: Long = DEFAULT_STEP_MS): List<TrackPoint> {
        if (points.isEmpty()) return points
        val last = points.last().timeMs
        if (last <= 0L) {
            // 時刻の無いファイル：等間隔に振り直す
            val n = points.size
            return points.mapIndexed { i, p -> p.copy(timeMs = endMs - (n - 1 - i) * stepMs) }
        }
        val offset = endMs - last
        return points.map { p -> if (p.timeMs <= 0L) p else p.copy(timeMs = p.timeMs + offset) }
    }
}
