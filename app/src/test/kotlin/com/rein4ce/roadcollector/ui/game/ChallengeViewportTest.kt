package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.challenge.ChallengeBounds
import org.junit.Assert.*
import org.junit.Test

class ChallengeViewportTest {
    @Test fun `all four bounds fit including low overhang and narrow screens`() {
        for (width in listOf(320f, 720f, 1080f)) for (height in listOf(80f, 200f, 800f)) {
            for (bounds in listOf(ChallengeBounds(2.5, 2.2, -.4), ChallengeBounds(4.0, 20.0, -1.5))) {
                val scale = bounds.pixelsPerMeter(width, height)
                assertTrue(scale > 0)
                assertTrue(width / 2 - bounds.halfWidth * scale >= -.001)
                assertTrue(width / 2 + bounds.halfWidth * scale <= width + .001)
                assertTrue(height * .78 - bounds.top * scale >= -.001)
                assertTrue(height * .78 - bounds.bottom * scale <= height + .001)
            }
        }
    }
    @Test fun `zero size before measurement has a safe scale`() {
        assertEquals(0f, ChallengeBounds(2.5, 2.2, -.4).pixelsPerMeter(320f, 0f))
    }
}
