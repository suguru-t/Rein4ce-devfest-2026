package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/** 平面上の点 [m]。 */
data class XY(val x: Double, val y: Double) {
    fun dist(o: XY): Double = hypot(x - o.x, y - o.y)
}

/**
 * 緯度経度 → 開始点基準のメートル座標（等距円筒近似）。
 * 散歩の範囲（数 km）なら誤差は無視できる。y は北向き（＝上向き）。
 */
object Projection {
    /** 緯度 1 度あたりのメートル。緯度・経度とメートルを行き来するところ（サンプル軌跡、拡大表示の地図、テスト）は、すべてこの値を使う */
    const val M_PER_DEG_LAT = 110_574.0

    /** 赤道での経度 1 度あたりのメートル。緯度が上がると cos の分だけ短くなる（[metersPerDegLng]） */
    const val M_PER_DEG_LNG_AT_EQUATOR = 111_320.0

    /** 緯度 [lat] での経度 1 度あたりのメートル */
    fun metersPerDegLng(lat: Double): Double = M_PER_DEG_LNG_AT_EQUATOR * cos(Math.toRadians(lat))

    fun toMeters(points: List<TrackPoint>): List<XY> {
        if (points.isEmpty()) return emptyList()
        val lat0 = points.first().lat
        val lng0 = points.first().lng
        val mPerDegLng = metersPerDegLng(lat0)
        return points.map { p ->
            XY((p.lng - lng0) * mPerDegLng, (p.lat - lat0) * M_PER_DEG_LAT)
        }
    }

    /**
     * [toMeters] の逆：メートルの座標を緯度・経度に戻す。
     * @param origin [toMeters] に渡した点列の最初の点（そこが原点）
     * @return 緯度と経度
     */
    fun toLatLng(origin: TrackPoint, p: XY): Pair<Double, Double> {
        val mPerDegLng = metersPerDegLng(origin.lat)
        return Pair(origin.lat + p.y / M_PER_DEG_LAT, origin.lng + p.x / mPerDegLng)
    }

    fun pathLength(points: List<XY>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += points[i - 1].dist(points[i])
        return total
    }

    /** 2 点間の距離 [m]（Haversine）。 */
    fun distanceM(a: TrackPoint, b: TrackPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val s = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return 2 * r * kotlin.math.atan2(sqrt(s), sqrt(1 - s))
    }
}
