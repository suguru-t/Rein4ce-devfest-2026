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
     * 1. 内側の点で、前の点からも次の点からも maxSpeedMps を超える速さでしか行けない点は、GPS が一瞬飛んだものとみなして落とす
     * 2. 残った列で、先頭・末尾が隣の点と矛盾する間は削る（記録開始直後に位置が定まらず飛ぶ点を落とす）
     *
     * 1 を先にやるので、2 点目だけが飛んだときに正しい 1 点目を道連れにしない。
     * 飛んだ先に留まり続ける場合は次の点と矛盾しないので残る（本当に移動したときと区別できないため）。
     */
    fun dropSpikes(points: List<TrackPoint>, maxSpeedMps: Double): List<TrackPoint> {
        val n = points.size
        if (n < 3) return points // 2 点だけでは、どちらが飛んだか決められない
        // fast[i] = 点 i-1 → 点 i の移動が速すぎる（時刻が分からなければ false）
        val fast = BooleanArray(n)
        for (i in 1 until n) fast[i] = isTooFast(points[i - 1], points[i], maxSpeedMps)
        val inner = points.filterIndexed { i, _ -> i == 0 || i == n - 1 || !(fast[i] && fast[i + 1]) }

        var from = 0
        var to = inner.size // exclusive
        while (to - from >= 2 && isTooFast(inner[from], inner[from + 1], maxSpeedMps)) from++
        while (to - from >= 2 && isTooFast(inner[to - 2], inner[to - 1], maxSpeedMps)) to--
        return inner.subList(from, to)
    }

    private fun isTooFast(a: TrackPoint, b: TrackPoint, maxSpeedMps: Double): Boolean {
        if (a.timeMs <= 0L || b.timeMs <= 0L) return false
        val dtSec = (b.timeMs - a.timeMs) / 1000.0
        if (dtSec <= 0.0) return false
        return Projection.distanceM(a, b) / dtSec > maxSpeedMps
    }
}
