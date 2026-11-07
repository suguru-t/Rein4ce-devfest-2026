package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.physics.PartBodyFactory
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.PartGeometry.Vec
import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** パーツの形（角ばった形。物理と描画の両方がこれを使う） */
class PartGeometryTest {

    private val t = 0.1f
    private val h = t / 2.0

    private fun part(vararg p: Pair<Float, Float>, closed: Boolean = false) =
        Part(id = "t", points = p.map { Pt(it.first, it.second) }, thickness = t, closed = closed)

    private fun vertices(polys: List<PartGeometry.Polygon>) = polys.flatMap { it.points }

    /** 点 [c] のまわり（太さの 3 倍以内）だけでできている多角形＝曲がり角の継ぎ目 */
    private fun cornerAt(polys: List<PartGeometry.Polygon>, c: Vec) =
        polys.single { poly -> poly.points.all { hypot(it.x - c.x, it.y - c.y) < 3 * h } }

    @Test
    fun `a bar is one rectangle whose square ends reach half the thickness past each end`() {
        val polys = PartGeometry.shapes(part(0f to -0.5f, 0f to 0.5f))
        val ps = polys.single().points
        assertEquals(4, ps.size)
        assertEquals(-0.5 - h, ps.minOf { it.y }, 1e-6)
        assertEquals(0.5 + h, ps.maxOf { it.y }, 1e-6)
        assertEquals(-h, ps.minOf { it.x }, 1e-6)
        assertEquals(h, ps.maxOf { it.x }, 1e-6)
    }

    @Test
    fun `a right-angle corner is sharp on the outside`() {
        // 東へ進んで、(0.5, 0) で北へ曲がる。外側（右下）の角は (0.5 + h, -h) にとがる
        val polys = PartGeometry.shapes(part(-0.5f to 0f, 0.5f to 0f, 0.5f to 1f))
        assertEquals(3, polys.size) // 長方形 2 つ＋曲がり角の継ぎ目
        val corner = cornerAt(polys, Vec(0.5, 0.0))
        assertTrue(corner.points.any { abs(it.x - (0.5 + h)) < 1e-6 && abs(it.y + h) < 1e-6 })
        // 内側（左上）には、はみ出さない
        assertFalse(vertices(polys).any { it.x < 0.5 - h - 1e-6 && it.y > h + 1e-6 })
    }

    @Test
    fun `a very sharp corner is cut instead of sticking out`() {
        // ほぼ折り返す（内側の角度 約 21°）。とがらせると先が太さの数倍まで伸びるので、斜めに切る
        val polys = PartGeometry.shapes(part(-0.5f to 0f, 0.5f to 0f, -0.4f to 0.35f))
        val corner = cornerAt(polys, Vec(0.5, 0.0))
        assertTrue(corner.points.all { hypot(it.x - 0.5, it.y) <= h + 1e-6 })
    }

    @Test
    fun `a corner within the limit is sharp up to the limit`() {
        // 内側の角度 70°。とがった先は角の点から h / sin(35°) ≒ 1.74h（上限 2h 以内）
        val rad = Math.toRadians(180.0 - 70.0)
        val next = (0.5 + 0.5 * kotlin.math.cos(rad)).toFloat() to (0.5 * kotlin.math.sin(rad)).toFloat()
        val polys = PartGeometry.shapes(part(-0.5f to 0f, 0.5f to 0f, next))
        val far = cornerAt(polys, Vec(0.5, 0.0)).points.maxOf { hypot(it.x - 0.5, it.y) }
        assertEquals(h / kotlin.math.sin(Math.toRadians(35.0)), far, 1e-4)
        assertTrue(far <= PartGeometry.MITER_LIMIT * h)
    }

    @Test
    fun `a ring has no square ends and a sharp joint at every point`() {
        val polys = PartGeometry.shapes(part(-0.5f to -0.5f, 0.5f to -0.5f, 0.5f to 0.5f, -0.5f to 0.5f, closed = true))
        assertEquals(8, polys.size) // 長方形 4 つ＋継ぎ目 4 つ
        val vs = vertices(polys)
        // 四隅はとがるが、端を伸ばした分は無い
        assertEquals(0.5 + h, vs.maxOf { it.x }, 1e-6)
        assertEquals(-0.5 - h, vs.minOf { it.y }, 1e-6)
        assertTrue(vs.any { abs(it.x - (0.5 + h)) < 1e-6 && abs(it.y - (0.5 + h)) < 1e-6 })
    }

    @Test
    fun `repeated and straight points do not break the shape`() {
        val polys = PartGeometry.shapes(part(0f to -0.5f, 0f to -0.5f, 0f to 0f, 0f to 0.5f))
        assertTrue(vertices(polys).none { it.x.isNaN() || it.y.isNaN() })
        // まっすぐ続く点には継ぎ目は要らない（長方形 2 つだけ）
        assertEquals(2, polys.size)
    }

    @Test
    fun `every polygon is convex and counterclockwise, and the physics accepts all of them`() {
        val shapes = SampleParts.all + listOf(
            part(-0.5f to 0f, 0.5f to 0f, -0.4f to 0.35f),
            part(-0.5f to -0.5f, 0.5f to -0.5f, 0.5f to 0.5f, -0.5f to 0.5f, closed = true),
            part(-0.5f to 0f, 0.5f to 0f, -0.5f to 0.001f), // ほぼ折り返す
        )
        for (p in shapes) {
            val polys = PartGeometry.shapes(p)
            for (poly in polys) {
                val ps = poly.points
                assertTrue("${p.id}: at least 3 points", ps.size >= 3)
                for (i in ps.indices) {
                    val a = ps[i]
                    val b = ps[(i + 1) % ps.size]
                    val c = ps[(i + 2) % ps.size]
                    val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                    assertTrue("${p.id}: convex and counterclockwise", cross > 0)
                }
            }
            assertEquals(p.id, polys.size, PartBodyFactory.create(p).fixtureCount)
        }
    }

    @Test
    fun `no corner sticks out farther than the limit from the walked path`() {
        // 角ばらせても、全体の大きさはほとんど変わらない（端は丸から四角へ、角はとがるだけ）。
        // どの頂点も、歩いた線（いずれかの線分）から、太さの半分 × 上限（MITER_LIMIT）以内にある
        for (p in SampleParts.all) {
            val pts = p.points.map { Vec(it.x.toDouble(), it.y.toDouble()) }
            val segs = (0 until pts.size - 1).map { pts[it] to pts[it + 1] } +
                if (p.closed) listOf(pts.last() to pts.first()) else emptyList()
            val hp = p.thickness / 2.0
            for (v in vertices(PartGeometry.shapes(p))) {
                val d = segs.minOf { (a, b) -> distToSegment(v, a, b) }
                assertTrue("${p.id}: vertex $v is $d from the path", d <= hp * PartGeometry.MITER_LIMIT + 1e-6)
            }
        }
    }

    private fun distToSegment(p: Vec, a: Vec, b: Vec): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val u = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + u * dx), p.y - (a.y + u * dy))
    }
}
