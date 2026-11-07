package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * GPS 軌跡 → Part への変換パイプライン。
 *
 * 1. フィルタ（精度・最小移動・最大速度）
 * 2. メートル座標へ投影
 * 3. 距離チェック
 * 3'. 速さチェック（歩き・ジョギングより速い区間があれば、車・自転車とみなしてパーツにしない）
 * 4. Douglas-Peucker で簡略化（頂点上限あり）
 * 5. 輪の判定
 * 6. 正規化（中心を原点、長辺 1.0）
 * 7. 分類（棒 / L / U / 輪 / ジグザグ / その他）
 */
object TrackToPart {

    sealed class Result {
        /**
         * @param place 歩いた場所（形の中心と長辺の長さ）。**Part とは別に、スマホの中だけに保存する**（PartPlace のコメント）
         */
        data class Success(val part: Part, val stats: Stats, val place: PartPlace) : Result()
        data class TooShort(val distanceM: Double, val minDistanceM: Double) : Result()
        /** 歩き・ジョギングより速い区間があった（車・自転車）。[fastestMps] は 30 秒の窓で測った最高速度、[limitMps] は上限 */
        data class TooFast(val fastestMps: Double, val limitMps: Double) : Result()
        data class TooFewPoints(val pointCount: Int) : Result()
    }

    data class Stats(
        val rawPoints: Int,
        val filteredPoints: Int,
        val simplifiedPoints: Int,
        val distanceM: Double,
        val longSideM: Double,
        /** 簡略化に実際に使った ε [m]（GPS の精度に合わせて大きくなることがある） */
        val epsilonM: Double,
        /** 30 秒の窓で測った最高速度 [m/s]（TrackSpeed）。時刻の分からない点があれば null */
        val fastestMps: Double? = null,
    )

    fun convert(
        points: List<TrackPoint>,
        params: ConversionParams = ConversionParams.DEFAULT,
        id: String = newId(),
        walkedAt: Long = points.lastOrNull()?.timeMs ?: 0L,
    ): Result {
        val filtered = TrackFilter.filter(points, params.maxAccuracyM, params.minStepM, params.maxSpeedMps)
        if (filtered.size < 2) return tooFewOrTooFast(points, filtered.size, params)

        val xy = Projection.toMeters(filtered)
        val distance = Projection.pathLength(xy)
        if (distance < params.minDistanceM) return Result.TooShort(distance, params.minDistanceM)

        // 自分の足で動いた軌跡だけをパーツにする。速さは GPS の揺れでばらつくので、30 秒の窓の直線距離 ÷ 時間の最高で見る
        val fastest = TrackSpeed.fastestWindowMps(filtered, params.walkSpeedWindowMs)
        if (fastest != null && fastest > params.walkMaxSpeedMps) return Result.TooFast(fastest, params.walkMaxSpeedMps)

        val epsilon = effectiveEpsilon(filtered, params)
        val simplified = Simplify.rdpWithLimit(xy, epsilon, params.maxVertices)
        if (simplified.size < 2) return Result.TooFewPoints(simplified.size)

        val bbox = BBox.of(simplified)
        val longSide = max(bbox.width, bbox.height).coerceAtLeast(1e-6)

        val closed = simplified.size >= 4 &&
            simplified.first().dist(simplified.last()) < params.closeRatio * longSide

        // 輪のときは重複する終点を落とす（closed=true で始点とつなぐ）
        val ring = if (closed) simplified.dropLast(1) else simplified

        val normalized = ring.map { p ->
            Pt(
                x = ((p.x - bbox.centerX) / longSide).toFloat(),
                y = ((p.y - bbox.centerY) / longSide).toFloat(),
            )
        }

        // 分類は粗い形で判定する（形を細かく残しても、名札と色が変わらないように）。
        // GPS が粗くて ε を大きくしたときは、分類もそれより細かくは見ない（揺れを曲がり角と数えないように）
        val coarse = Simplify.rdpWithLimit(xy, max(params.classifyEpsilonM, epsilon), params.classifyMaxVertices)
        val kind = classify(coarse.map { Pt(it.x.toFloat(), it.y.toFloat()) }, closed)

        val part = Part(
            id = id,
            points = normalized,
            thickness = params.thickness,
            closed = closed,
            kind = kind,
            distanceM = distance.toInt(),
            walkedAt = walkedAt,
        )
        val stats = Stats(
            rawPoints = points.size,
            filteredPoints = filtered.size,
            simplifiedPoints = simplified.size,
            distanceM = distance,
            longSideM = longSide,
            epsilonM = epsilon,
            fastestMps = fastest,
        )
        // 歩いた場所：形の中心（バウンディングボックスの中心）を緯度・経度に戻す。長辺の長さはパーツの 1.0 にあたる
        val (centerLat, centerLng) = Projection.toLatLng(filtered.first(), XY(bbox.centerX, bbox.centerY))
        val place = PartPlace(lat = centerLat, lng = centerLng, sizeM = longSide)
        return Result.Success(part, stats, place)
    }

    /**
     * 使える点が 2 つ未満だったときの理由。
     * 車や電車でずっと速い（飛びの上限 [ConversionParams.maxSpeedMps] を超え続ける）と、速度フィルタが点をほぼ全部落として
     * 「位置情報が足りない」に見えてしまう。そのときは飛びの判定を外した点列で速さを測り、上限より速ければ「速すぎ」と返す
     */
    private fun tooFewOrTooFast(points: List<TrackPoint>, count: Int, params: ConversionParams): Result {
        val unfiltered = TrackFilter.filter(points, params.maxAccuracyM, params.minStepM, maxSpeedMps = Double.MAX_VALUE)
        val fastest = if (unfiltered.size >= 2) TrackSpeed.fastestWindowMps(unfiltered, params.walkSpeedWindowMs) else null
        if (fastest != null && fastest > params.walkMaxSpeedMps) return Result.TooFast(fastest, params.walkMaxSpeedMps)
        return Result.TooFewPoints(count)
    }

    /**
     * 簡略化に使う ε [m]。
     * [ConversionParams.epsilonFollowsAccuracy] なら、使う点の精度の中央値と [ConversionParams.epsilonM] の大きい方。
     * 精度が分からない点（0）は数えず、全部分からなければ epsilonM のまま。
     */
    fun effectiveEpsilon(filtered: List<TrackPoint>, params: ConversionParams): Double {
        if (!params.epsilonFollowsAccuracy) return params.epsilonM
        val known = filtered.map { it.accuracyM.toDouble() }.filter { it > 0.0 }.sorted()
        if (known.isEmpty()) return params.epsilonM
        return max(params.epsilonM, known[known.size / 2])
    }

    /**
     * 表示用の分類。曲がり角（50° 以上）の数と向きで決める。
     * 厳密さは不要。ルールには使わない。角度しか見ないので、点の単位（m でも正規化後でも）は問わない。
     */
    fun classify(points: List<Pt>, closed: Boolean): Kind {
        if (closed) return Kind.RING
        if (points.size < 3) return Kind.BAR

        val turns = ArrayList<Double>() // 符号付きの曲がり角 [rad]
        for (i in 1 until points.size - 1) {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            val ang1 = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
            val ang2 = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble())
            var d = ang2 - ang1
            while (d > Math.PI) d -= 2 * Math.PI
            while (d < -Math.PI) d += 2 * Math.PI
            if (abs(d) > Math.toRadians(50.0)) turns += d
        }

        return when (turns.size) {
            0 -> Kind.BAR
            1 -> Kind.L
            2 -> if (turns[0] * turns[1] > 0) Kind.U else Kind.ZIGZAG
            else -> {
                var alternating = true
                for (i in 1 until turns.size) if (turns[i] * turns[i - 1] > 0) alternating = false
                if (alternating) Kind.ZIGZAG else Kind.OTHER
            }
        }
    }

    private fun newId(): String = java.util.UUID.randomUUID().toString()
}

/** 軸平行のバウンディングボックス。 */
data class BBox(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width get() = maxX - minX
    val height get() = maxY - minY
    val centerX get() = (minX + maxX) / 2
    val centerY get() = (minY + maxY) / 2

    companion object {
        fun of(points: List<XY>): BBox {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            return BBox(minX, minY, maxX, maxY)
        }
    }
}
