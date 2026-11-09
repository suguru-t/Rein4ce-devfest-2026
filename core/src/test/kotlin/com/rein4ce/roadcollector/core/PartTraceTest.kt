package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.geo.PartTrace
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class PartTraceTest {

    // L 字：右へ 0.6、上へ 0.4（長さ 1.0）
    private val l = Part(id = "l", points = listOf(Pt(-0.3f, -0.2f), Pt(0.3f, -0.2f), Pt(0.3f, 0.2f)), kind = Kind.L)

    // 輪：1 辺 0.5 の正方形（長さ 2.0。最後の点から始めの点へ戻る）
    private val ring = Part(
        id = "r",
        points = listOf(Pt(-0.25f, -0.25f), Pt(0.25f, -0.25f), Pt(0.25f, 0.25f), Pt(-0.25f, 0.25f)),
        closed = true,
        kind = Kind.RING,
    )

    private fun assertPt(expected: Pt, actual: Pt) {
        assertEquals(expected.x, actual.x, 1e-5f)
        assertEquals(expected.y, actual.y, 1e-5f)
    }

    @Test
    fun `start is only the first point`() {
        val p = PartTrace.at(l, 0f)
        assertEquals(listOf(Pt(-0.3f, -0.2f)), p.walked.points)
        assertPt(Pt(-0.3f, -0.2f), p.head)
    }

    @Test
    fun `walks along the segments in order at the same speed`() {
        // 0.3 進むと、1 本目のまん中
        val half = PartTrace.at(l, 0.3f)
        assertEquals(2, half.walked.points.size)
        assertPt(Pt(0f, -0.2f), half.head)
        // 0.8 進むと、角を曲がって 2 本目のまん中
        val turned = PartTrace.at(l, 0.8f)
        assertEquals(3, turned.walked.points.size)
        assertPt(Pt(0.3f, -0.2f), turned.walked.points[1])
        assertPt(Pt(0.3f, 0f), turned.head)
    }

    @Test
    fun `end gives back the whole part`() {
        assertSame(l, PartTrace.at(l, 1f).walked)
        assertPt(Pt(0.3f, 0.2f), PartTrace.at(l, 1f).head)
    }

    @Test
    fun `ring goes back to the start and closes only at the end`() {
        // 3/4 では、3 辺を歩いて開いたまま
        val most = PartTrace.at(ring, 0.75f)
        assertFalse(most.walked.closed)
        assertPt(Pt(-0.25f, 0.25f), most.head)
        // 7/8 では、最後の点から始めの点へ戻る辺の途中
        val back = PartTrace.at(ring, 0.875f)
        assertFalse(back.walked.closed)
        assertPt(Pt(-0.25f, 0f), back.head)
        // 終わると、元の閉じた輪のまま
        assertSame(ring, PartTrace.at(ring, 1f).walked)
        assertPt(Pt(-0.25f, -0.25f), PartTrace.at(ring, 1f).head)
    }

    @Test
    fun `heading follows the road and turns around the corner`() {
        // 1 本目（右へ）は 90 度、2 本目（上へ）は 0 度。角のところでは、そのあいだ
        assertEquals(90f, PartTrace.headingAt(l, 0.2f), 1e-3f)
        assertEquals(0f, PartTrace.headingAt(l, 0.9f), 1e-3f)
        val corner = PartTrace.headingAt(l, 0.6f)
        assertEquals(45f, corner, 1e-3f)
    }

    @Test
    fun `going out and coming back the same way does not face north at the turn`() {
        // 東へ行って、同じ道を西へ戻る（A → B → A）。折り返す所（ちょうどまん中）で、手前と先の点が重なる
        val back = Part(id = "back", points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f), Pt(-0.5f, 0f)))
        // 行きは東（90°）、帰りは西（-90°）
        assertEquals(90f, PartTrace.headingAt(back, 0.3f), 1e-3f)
        assertEquals(-90f, PartTrace.headingAt(back, 0.7f), 1e-3f)
        // 折り返す所では、北（0°）ではなく、来た向き（東）のまま
        assertEquals(90f, PartTrace.headingAt(back, 0.5f), 1e-3f)
    }

    @Test
    fun `frame gives the same place and heading as at and headingAt`() {
        for (f in listOf(0f, 0.2f, 0.6f, 0.9f, 1f)) {
            val frame = PartTrace.frame(l, f)
            val at = PartTrace.at(l, f)
            assertPt(at.head, frame.progress.head)
            assertEquals(at.walked.points.size, frame.progress.walked.points.size)
        }
        assertEquals(90f, PartTrace.frame(l, 0.2f).heading, 1e-3f)
    }
}
