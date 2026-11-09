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

    /** なぞった途中の形と、いま歩いているところと、歩いている向き（[frame] が 1 回で出す） */
    data class Frame(val progress: Progress, val heading: Float)

    /**
     * 道の長さの [fraction] まで歩いたところ（[at]）と、そこで歩いている向き（[headingAt]）を 1 回で出す。
     * 拡大表示の「もう一度歩く」は 1 秒に 60 回これを呼ぶので、道の長さは 1 回だけ数え、向きのための前後の点は
     * 途中の形を作らずに求める（#94 の A のレビュー）
     */
    fun frame(part: Part, fraction: Float, window: Float = HEADING_WINDOW): Frame {
        val progress = at(part, fraction)
        val pts = if (part.closed && part.points.size > 2) part.points + part.points.first() else part.points
        val total = length(pts)
        if (pts.size < 2 || total <= 0f) return Frame(progress, 0f)
        fun pointAt(f: Float): Pt {
            var left = f.coerceIn(0f, 1f) * total
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val seg = hypot(b.x - a.x, b.y - a.y)
                if (left <= seg) {
                    val t = if (seg > 0f) left / seg else 0f
                    return Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
                left -= seg
            }
            return pts.last()
        }
        val from = pointAt(fraction - window)
        val to = pointAt(fraction + window)
        // 行って同じ道を戻る道（A → B → A）では、ちょうど折り返す所で手前の点と先の点が重なる。
        // そのときは来た向き（手前の点 → 今いる所）のまま、それも決まらなければ行く向き（今いる所 → 先の点）にする
        // （北を向いてしまわないように。#94 の A のレビュー）
        val heading = bearing(from, to) ?: bearing(from, progress.head) ?: bearing(progress.head, to) ?: 0f
        return Frame(progress, heading)
    }

    /**
     * 道の長さの [fraction] のところで歩いている向き [度]（北 = 上が 0、時計回り）。
     * 角でいきなり向きが変わらないよう、少し手前（[window]）から少し先までを見た向きにする（角に近づくと曲がり始める）
     */
    fun headingAt(part: Part, fraction: Float, window: Float = HEADING_WINDOW): Float = frame(part, fraction, window).heading

    /** [a] から [b] への向き [度]（北 = 上が 0、時計回り）。2 点がほとんど同じ所なら null */
    private fun bearing(a: Pt, b: Pt): Float? {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (hypot(dx, dy) < SAME_POINT) return null
        return Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
    }

    /** 向きを見る、手前・先までの長さ（道の長さに対する割合） */
    private const val HEADING_WINDOW = 0.04f

    /** これより近い 2 点は同じ所とみなす（パーツの座標。長辺が 1） */
    private const val SAME_POINT = 1e-4f

    private fun length(pts: List<Pt>): Float {
        var total = 0f
        for (i in 1 until pts.size) total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return total
    }
}
