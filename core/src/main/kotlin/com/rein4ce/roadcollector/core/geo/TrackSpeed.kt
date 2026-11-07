package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.TrackPoint

/**
 * 軌跡の速さ。歩き以外（車・自転車・走り）をパーツにしないための判定に使う。
 *
 * 速さは「時間の窓の始点から終点までの直線距離 ÷ 時間」で測る（[ConversionParams.walkSpeedWindowMs]、30 秒ほど）。
 * 点をたどった道のりで測ると、GPS の揺れ（粗いときは 1 点ごとに数 m）が全部足されて、歩きでも速く見えてしまうため。
 * 直線距離は、窓の中で曲がった分だけ短く出る（速さを少なめに見積もる）が、車や走りは 30 秒で歩きの数倍は進むので十分に分かる。
 * 時刻が分からない点（timeMs が 0 以下）が混ざっていれば判定しない（null）。
 */
object TrackSpeed {

    /**
     * いちばん速かった区間の速さ [m/s]。
     * 各点から数えて [windowMs] 以上あとの最初の点までを 1 つの窓にして、始点と終点の直線距離 ÷ 時間の最大を返す。
     * 軌跡全体が [windowMs] より短ければ、全体を 1 つの窓にする。
     * 点が 2 つ未満、時刻の分からない点がある、時間が進んでいないときは null
     */
    fun fastestWindowMps(points: List<TrackPoint>, windowMs: Long): Double? {
        if (points.size < 2 || points.any { it.timeMs <= 0L }) return null
        var best: Double? = null
        var j = 0
        for (i in points.indices) {
            if (j < i) j = i
            while (j < points.size && points[j].timeMs - points[i].timeMs < windowMs) j++
            if (j >= points.size) break
            val v = speed(points[i], points[j]) ?: continue
            if (best == null || v > best) best = v
        }
        return best ?: speed(points.first(), points.last())
    }

    /**
     * 最後の [windowMs] の速さ [m/s]（記録中の表示用）。
     * 最後の点から数えて [windowMs] 以上前の点が無ければ null（まだ短すぎて測れない）
     */
    fun recentMps(points: List<TrackPoint>, windowMs: Long): Double? {
        if (points.size < 2 || points.any { it.timeMs <= 0L }) return null
        val last = points.last()
        var i = points.lastIndex
        while (i > 0 && last.timeMs - points[i].timeMs < windowMs) i--
        if (last.timeMs - points[i].timeMs < windowMs) return null
        return speed(points[i], last)
    }

    /** 点 a から点 b までの直線距離 ÷ 時間 [m/s]。時間が進んでいなければ null */
    private fun speed(a: TrackPoint, b: TrackPoint): Double? {
        val dtSec = (b.timeMs - a.timeMs) / 1000.0
        if (dtSec <= 0.0) return null
        return Projection.distanceM(a, b) / dtSec
    }
}
