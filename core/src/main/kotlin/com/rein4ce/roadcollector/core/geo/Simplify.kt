package com.rein4ce.roadcollector.core.geo

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ramer–Douglas–Peucker による折れ線の簡略化。
 * ε [m] より線から離れていない中間点を捨てる。
 */
object Simplify {

    fun rdp(points: List<XY>, epsilon: Double): List<XY> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        rdpRec(points, 0, points.size - 1, epsilon, keep)
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun rdpRec(pts: List<XY>, first: Int, last: Int, eps: Double, keep: BooleanArray) {
        if (last <= first + 1) return
        var maxDist = -1.0
        var index = -1
        for (i in first + 1 until last) {
            val d = perpendicularDistance(pts[i], pts[first], pts[last])
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        if (maxDist > eps) {
            keep[index] = true
            rdpRec(pts, first, index, eps, keep)
            rdpRec(pts, index, last, eps, keep)
        }
    }

    /** 点 p から線分 ab への距離。 */
    fun perpendicularDistance(p: XY, a: XY, b: XY): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < 1e-9) return p.dist(a)
        // 線分の内側に射影が落ちるかを見る
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (len * len)
        return when {
            t <= 0 -> p.dist(a)
            t >= 1 -> p.dist(b)
            else -> abs(dx * (a.y - p.y) - dy * (a.x - p.x)) / len
        }
    }

    /**
     * 頂点数が maxVertices 以下になるまで ε を 1.5 倍しながら簡略化する。
     */
    fun rdpWithLimit(points: List<XY>, epsilon: Double, maxVertices: Int): List<XY> {
        var eps = epsilon
        var result = rdp(points, eps)
        var guard = 0
        while (result.size > maxVertices && guard < 20) {
            eps *= 1.5
            result = rdp(points, eps)
            guard++
        }
        return result
    }
}
