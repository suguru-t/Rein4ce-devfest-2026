package com.rein4ce.roadcollector.core.geo

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * パーツの道を、歩いた順に途中までなぞる（コレクションの拡大表示の「もう一度歩く」で使う）。
 *
 * パーツの点は歩いた順に並んでいる（始めの点が歩き始め。輪は最後の点から始めの点へ戻って閉じる）。
 * 保存してあるのは角を残して簡単にした形だけなので、点と点のあいだは同じ速さで歩いたものとしてなぞる
 */
object PartTrace {

    /** なぞった途中の形と、いま歩いているところ */
    data class Progress(
        /** 歩き始めから [Progress.head] までの道。輪でも閉じない（なぞり終わったときだけ、元のパーツのまま） */
        val walked: Part,
        /** いま歩いているところ（パーツの座標） */
        val head: Pt,
    )

    /**
     * 道の長さの [fraction]（0〜1）まで歩いたところ。0 なら歩き始めの点だけ、1 なら元のパーツのまま
     */
    fun at(part: Part, fraction: Float): Progress {
        val pts = if (part.closed && part.points.size > 2) part.points + part.points.first() else part.points
        if (pts.isEmpty()) return Progress(part, Pt(0f, 0f))
        if (fraction >= 1f) return Progress(part, pts.last())
        val total = length(pts)
        if (total <= 0f) return Progress(part.copy(points = listOf(pts.first()), closed = false), pts.first())

        var left = fraction.coerceAtLeast(0f) * total
        val walked = ArrayList<Pt>(pts.size)
        walked += pts.first()
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val seg = hypot(b.x - a.x, b.y - a.y)
            if (left <= seg) {
                val t = if (seg > 0f) left / seg else 0f
                val head = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                if (t > 0f) walked += head
                return Progress(part.copy(points = walked, closed = false), head)
            }
            left -= seg
            walked += b
        }
        return Progress(part, pts.last())
    }

    /**
     * 道の長さの [fraction] のところで歩いている向き [度]（北 = 上が 0、時計回り）。
     * 角でいきなり向きが変わらないよう、少し手前（[window]）から少し先までを見た向きにする（角に近づくと曲がり始める）
     */
    fun headingAt(part: Part, fraction: Float, window: Float = 0.04f): Float {
        val from = at(part, (fraction - window).coerceIn(0f, 1f)).head
        val to = at(part, (fraction + window).coerceIn(0f, 1f)).head
        val dx = to.x - from.x
        val dy = to.y - from.y
        if (dx == 0f && dy == 0f) return 0f
        return Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
    }

    private fun length(pts: List<Pt>): Float {
        var total = 0f
        for (i in 1 until pts.size) total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return total
    }
}
