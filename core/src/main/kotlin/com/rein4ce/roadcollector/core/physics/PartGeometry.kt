package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.model.Part
import kotlin.math.abs
import kotlin.math.hypot

/**
 * パーツの「見た目の形」を、凸多角形の集まりとして表す。
 * 物理ボディ（dyn4j の fixture）と描画（Compose Canvas / SVG / PNG）の両方がこれを使うので、
 * 見た目と当たり判定が必ず一致する。
 *
 * 形は角ばらせている（丸い継ぎ目・丸い端より、置いたときに少し安定するため。`docs/decisions.md` の 18）：
 * - 線分ごとの長方形。輪でないパーツの両端は、太さの半分だけ伸ばして四角く切る
 * - 曲がり角の継ぎ目。外側の角はとがらせ、とがりすぎる角（[MITER_LIMIT] を超える）は斜めに切る
 *
 * 多角形どうしは重なっている。描くときは、全部を 1 つのパスにまとめて塗る（継ぎ目に線が出ず、半透明でも重なりが濃くならない）。
 * 線を太さ・四角い端・とがった角（miter、上限 [MITER_LIMIT]）で描いたときと同じ形になる。
 *
 * 座標はパーツのローカル座標（長辺 = scale、中心が原点、y 上向き）。多角形の頂点は反時計回り。
 */
object PartGeometry {

    /** 点（パーツのローカル座標） */
    data class Vec(val x: Double, val y: Double)

    /** 凸多角形。頂点は反時計回り（y 上向き） */
    data class Polygon(val points: List<Vec>)

    /**
     * 角をとがらせる上限。とがった先が、曲がり角の点から太さの半分の何倍までなら、とがらせるか。
     * 2 なら、内側の角度が 60° より鋭い角は斜めに切る（線の描画の miter limit と同じ意味の値）
     */
    const val MITER_LIMIT = 2.0

    /**
     * @param scale パーツの長辺を何 [m]（描くときは何 px）にするか（ゲーム内では 1.0）
     */
    fun shapes(part: Part, scale: Double = 1.0): List<Polygon> {
        val h = part.thickness * scale / 2.0
        // 同じ点が続くと線分の向きが決まらないので、1 つにまとめる
        val pts = ArrayList<Vec>()
        for (p in part.points) {
            val v = Vec(p.x * scale, p.y * scale)
            if (pts.isEmpty() || dist(pts.last(), v) > EPS * scale) pts += v
        }
        // 輪で、最後の点が最初の点と同じなら、最後の点は要らない（最後から最初へは輪としてつなぐ）
        if (part.closed && pts.size >= 2 && dist(pts.first(), pts.last()) <= EPS * scale) pts.removeAt(pts.lastIndex)
        val closed = part.closed && pts.size >= 3
        if (pts.isEmpty() || h <= 0.0) return emptyList()
        if (pts.size == 1) return listOf(square(pts[0], h))

        val out = ArrayList<Polygon>()
        val n = pts.size
        val segCount = if (closed) n else n - 1
        for (i in 0 until segCount) {
            var a = pts[i]
            var b = pts[(i + 1) % n]
            val d = unit(a, b)
            // 端は四角く切る：太さの半分だけ伸ばす
            if (!closed && i == 0) a = Vec(a.x - d.x * h, a.y - d.y * h)
            if (!closed && i == segCount - 1) b = Vec(b.x + d.x * h, b.y + d.y * h)
            val nx = -d.y * h
            val ny = d.x * h
            out += Polygon(listOf(Vec(a.x - nx, a.y - ny), Vec(b.x - nx, b.y - ny), Vec(b.x + nx, b.y + ny), Vec(a.x + nx, a.y + ny)))
        }
        val corners = if (closed) 0 until n else 1 until n - 1
        for (i in corners) corner(pts[(i - 1 + n) % n], pts[i], pts[(i + 1) % n], h)?.let { out += it }
        return out
    }

    /**
     * 曲がり角 [p] の継ぎ目。前後の長方形の端の 4 点と、外側のとがった先（上限以内なら）を囲む凸多角形。
     * まっすぐ・折り返し（継ぎ目が要らない）のときは null
     */
    private fun corner(prev: Vec, p: Vec, next: Vec, h: Double): Polygon? {
        val d1 = unit(prev, p)
        val d2 = unit(p, next)
        val n1 = Vec(-d1.y * h, d1.x * h)
        val n2 = Vec(-d2.y * h, d2.x * h)
        val cand = arrayListOf(
            Vec(p.x + n1.x, p.y + n1.y), Vec(p.x - n1.x, p.y - n1.y),
            Vec(p.x + n2.x, p.y + n2.y), Vec(p.x - n2.x, p.y - n2.y),
        )
        val cross = d1.x * d2.y - d1.y * d2.x
        if (abs(cross) > 1e-9) {
            // 外側は曲がる向きの反対。外側の 2 本の辺を延ばした交点が、とがった先
            val s = if (cross > 0) -1.0 else 1.0
            val o1 = Vec(p.x + s * n1.x, p.y + s * n1.y)
            val o2 = Vec(p.x + s * n2.x, p.y + s * n2.y)
            val det = -cross // o1 + d1·u = o2 + d2·v を解く
            val u = ((o2.x - o1.x) * -d2.y + (o2.y - o1.y) * d2.x) / det
            val tip = Vec(o1.x + d1.x * u, o1.y + d1.y * u)
            if (dist(tip, p) <= MITER_LIMIT * h + 1e-9 * h) cand += tip
        }
        val hull = convexHull(cand, eps = 1e-9 * h * h)
        // まっすぐ・折り返しでは細い線か点になる（前後の長方形だけで形ができている）
        return if (hull.size >= 3 && area(hull) > 1e-6 * h * h) Polygon(hull) else null
    }

    /** 点が 1 つしかないときの形（ありえないが、何も描かないよりよい）：太さを 1 辺とする正方形 */
    private fun square(c: Vec, h: Double) =
        Polygon(listOf(Vec(c.x - h, c.y - h), Vec(c.x + h, c.y - h), Vec(c.x + h, c.y + h), Vec(c.x - h, c.y + h)))

    /** 凸包（反時計回り。一直線に並ぶ点は除く） */
    private fun convexHull(ps: List<Vec>, eps: Double): List<Vec> {
        val p = ps.sortedWith(compareBy({ it.x }, { it.y }))
        fun cr(o: Vec, a: Vec, b: Vec) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Vec>()
        for (q in p) {
            while (lower.size >= 2 && cr(lower[lower.size - 2], lower.last(), q) <= eps) lower.removeAt(lower.lastIndex)
            lower += q
        }
        val upper = ArrayList<Vec>()
        for (q in p.asReversed()) {
            while (upper.size >= 2 && cr(upper[upper.size - 2], upper.last(), q) <= eps) upper.removeAt(upper.lastIndex)
            upper += q
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** 多角形の面積（反時計回りなら正） */
    internal fun area(ps: List<Vec>): Double {
        var s = 0.0
        for (i in ps.indices) {
            val a = ps[i]
            val b = ps[(i + 1) % ps.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2.0
    }

    private fun unit(a: Vec, b: Vec): Vec {
        val len = dist(a, b)
        return Vec((b.x - a.x) / len, (b.y - a.y) / len)
    }

    private fun dist(a: Vec, b: Vec) = hypot(b.x - a.x, b.y - a.y)

    /** 同じ点とみなす距離（長辺 1.0 に対して） */
    private const val EPS = 1e-6
}
