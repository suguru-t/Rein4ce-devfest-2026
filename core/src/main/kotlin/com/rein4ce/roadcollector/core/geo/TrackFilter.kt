package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.TrackPoint

/**
 * GPS の生データから、明らかなノイズを落とす。
 *
 * - 精度（accuracy）が悪い点を捨てる
 * - 前後どちらの点から見ても速すぎる移動になる点（孤立した GPS の飛び）を捨てる。時刻が分かる点だけ見る
 * - 前回採用した点から近すぎる点（立ち止まり中のふらつき）を捨てる
 *
 * 数値は試作で調整する前提の初期値。
 */
object TrackFilter {

    fun filter(
        points: List<TrackPoint>,
        maxAccuracyM: Float = ConversionParams.DEFAULT.maxAccuracyM,
        minStepM: Double = ConversionParams.DEFAULT.minStepM,
        maxSpeedMps: Double = ConversionParams.DEFAULT.maxSpeedMps,
    ): List<TrackPoint> {
        val accurate = points.filter { p -> p.accuracyM <= 0f || p.accuracyM <= maxAccuracyM }
        val steady = dropSpikes(accurate, maxSpeedMps)
        val out = ArrayList<TrackPoint>(steady.size)
        for (p in steady) {
            val last = out.lastOrNull()
            if (last != null && Projection.distanceM(last, p) < minStepM) continue
            out += p
        }
        return out
    }

    /**
     * 孤立した飛びを落とす。
     * 前の点からも次の点からも maxSpeedMps を超える速さでしか行けない点は、GPS が一瞬飛んだものとみなす。
     * 先頭・末尾は片側だけで判定する（記録開始直後に位置が定まらず飛ぶ点を落とすため）。
     * 飛んだ先に留まり続ける場合は次の点と矛盾しないので残る（本当に移動したときと区別できないため）。
     */
    fun dropSpikes(points: List<TrackPoint>, maxSpeedMps: Double): List<TrackPoint> {
        if (points.size < 2) return points
        // fast[i] = 点 i-1 → 点 i の移動が速すぎる（時刻が分からなければ false）
        val fast = BooleanArray(points.size)
        for (i in 1 until points.size) fast[i] = isTooFast(points[i - 1], points[i], maxSpeedMps)
        return points.filterIndexed { i, _ ->
            val fromPrev = i == 0 || fast[i]
            val toNext = i == points.size - 1 || fast[i + 1]
            !(fromPrev && toNext)
        }
    }

    private fun isTooFast(a: TrackPoint, b: TrackPoint, maxSpeedMps: Double): Boolean {
        if (a.timeMs <= 0L || b.timeMs <= 0L) return false
        val dtSec = (b.timeMs - a.timeMs) / 1000.0
        if (dtSec <= 0.0) return false
        return Projection.distanceM(a, b) / dtSec > maxSpeedMps
    }
}
