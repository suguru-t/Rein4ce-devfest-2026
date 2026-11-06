package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.TrackPoint

/**
 * GPS の生データから、明らかなノイズを落とす。
 *
 * - 精度（accuracy）が悪い点を捨てる
 * - 前回採用した点から近すぎる点（立ち止まり中のふらつき）を捨てる
 *
 * 数値は試作で調整する前提の初期値。
 */
object TrackFilter {

    fun filter(
        points: List<TrackPoint>,
        maxAccuracyM: Float = ConversionParams.DEFAULT.maxAccuracyM,
        minStepM: Double = ConversionParams.DEFAULT.minStepM,
    ): List<TrackPoint> {
        val out = ArrayList<TrackPoint>(points.size)
        for (p in points) {
            if (p.accuracyM > 0f && p.accuracyM > maxAccuracyM) continue
            val last = out.lastOrNull()
            if (last != null && Projection.distanceM(last, p) < minStepM) continue
            out += p
        }
        return out
    }
}
