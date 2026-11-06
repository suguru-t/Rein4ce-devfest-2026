package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlin.math.roundToLong

/**
 * 軌跡の時刻をずらす。
 * デモ再生（サンプル軌跡や過去に録ったファイル）を「今歩いた」ことにするために使う。
 * `walkedAt` は最後の点の時刻になるので、最後の点が [endMs] になるように全体を平行移動する。
 */
object TrackTime {

    /** 時刻の無いファイルに時刻を振るときに仮定する歩く速さ [m/s]（時速 5 km） */
    const val WALKING_SPEED_MPS = 1.4

    /** 時刻の無いファイルで、点の間に最低限あける時間 [ms]（同じ場所の点が同時刻にならないように） */
    const val MIN_STEP_MS = 1_000L

    fun shiftToEndAt(points: List<TrackPoint>, endMs: Long): List<TrackPoint> {
        if (points.isEmpty()) return points
        val last = points.last().timeMs
        if (last <= 0L) return assignWalkingTimes(points, endMs)
        val offset = endMs - last
        return points.map { p -> if (p.timeMs <= 0L) p else p.copy(timeMs = p.timeMs + offset) }
    }

    /**
     * 時刻の無いファイル（GPX の `<rtept>` や time 無しの `<trkpt>`）に、点の間の距離を歩く速さで割った時刻を振る。
     * 一律の間隔にしないのは、角だけの経路（点の間が 100 m 以上）に 3 秒おきの時刻を振ると
     * 速度フィルタ（[ConversionParams.maxSpeedMps]）に「飛び」とみなされて全部落ちてしまうため。
     */
    private fun assignWalkingTimes(points: List<TrackPoint>, endMs: Long): List<TrackPoint> {
        val gaps = LongArray(points.size) // gaps[i] = 点 i-1 → 点 i にかかる時間 [ms]
        for (i in 1 until points.size) {
            val d = Projection.distanceM(points[i - 1], points[i])
            gaps[i] = ((d / WALKING_SPEED_MPS) * 1000.0).roundToLong().coerceAtLeast(MIN_STEP_MS)
        }
        val total = gaps.sum()
        var t = endMs - total
        return points.mapIndexed { i, p ->
            t += gaps[i]
            p.copy(timeMs = t)
        }
    }
}
