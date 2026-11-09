package com.rein4ce.roadcollector.ui.collect

import com.rein4ce.roadcollector.core.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 今いる場所の人型の札：人を右向き・左向きのどちらにするか */
class HereMarkerTest {

    @Test
    fun `east-ish faces right and west-ish faces left`() {
        assertEquals(true, facingRight(90f, previous = false))
        assertEquals(true, facingRight(45f, previous = false))
        assertEquals(false, facingRight(270f, previous = true))
        assertEquals(false, facingRight(-120f, previous = true))
        // 何周してもよい（方位はつながった値で来る）
        assertEquals(true, facingRight(450f, previous = false))
    }

    @Test
    fun `near north or south keeps the previous direction`() {
        assertEquals(true, facingRight(5f, previous = true))
        assertEquals(false, facingRight(5f, previous = false))
        assertEquals(false, facingRight(178f, previous = false))
        assertEquals(true, facingRight(-8f, previous = true))
    }

    @Test
    fun `walking direction decides when the phone direction is unknown`() {
        fun pt(lat: Double, lng: Double) = TrackPoint(lat = lat, lng = lng)
        // 東へ 50 m
        assertEquals(true, movingEast(listOf(pt(35.75, 139.8), pt(35.75, 139.80055))))
        // 西へ 50 m
        assertEquals(false, movingEast(listOf(pt(35.75, 139.8), pt(35.75, 139.79945))))
        // ほぼ真北へ 100 m は決めない
        assertNull(movingEast(listOf(pt(35.75, 139.8), pt(35.7509, 139.8))))
        // 5 m しか動いていなければ決めない
        assertNull(movingEast(listOf(pt(35.75, 139.8), pt(35.75, 139.80005))))
    }
}
