package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * GPS 軌跡 → Part への変換パイプライン。
 *
 * 1. フィルタ（精度・最小移動）
 * 2. メートル座標へ投影
 * 3. 距離チェック
 * 4. Douglas-Peucker で簡略化（頂点上限あり）
 * 5. 輪の判定
 * 6. 正規化（中心を原点、長辺 1.0）
 * 7. 分類（棒 / L / U / 輪 / ジグザグ / その他）
 */
object TrackToPart {

    sealed class Result {
        data class Success(val part: Part, val stats: Stats) : Result()
        data class TooShort(val distanceM: Double, val minDistanceM: Double) : Result()
        data class TooFewPoints(val pointCount: Int) : Result()
    }

    data class Stats(
        val rawPoints: Int,
        val filteredPoints: Int,
        val simplifiedPoints: Int,
        val distanceM: Double,
        val longSideM: Double,
    )

    fun convert(
        points: List<TrackPoint>,
        params: ConversionParams = ConversionParams.DEFAULT,
        id: String = newId(),
        walkedAt: Long = points.lastOrNull()?.timeMs ?: 0L,
    ): Result {
        val filtered = TrackFilter.filter(points, params.maxAccuracyM, params.minStepM)
        if (filtered.size < 2) return Result.TooFewPoints(filtered.size)

        val xy = Projection.toMeters(filtered)
        val distance = Projection.pathLength(xy)
        if (distance < params.minDistanceM) return Result.TooShort(distance, params.minDistanceM)

        val simplified = Simplify.rdpWithLimit(xy, params.epsilonM, params.maxVertices)
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

        // 分類は粗い形で判定する（形を細かく残しても、名札と色が変わらないように）
        val coarse = Simplify.rdpWithLimit(xy, params.classifyEpsilonM, params.classifyMaxVertices)
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
        )
        return Result.Success(part, stats)
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
