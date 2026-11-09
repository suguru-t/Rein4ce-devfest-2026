package com.rein4ce.roadcollector.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** 3 分チャレンジのドラッグを、前の ◀▶ と同じ 10 cm きざみの動きに直す（決定 67） */
class AimGesturesTest {

    @Test
    fun `1 きざみに届くまでは動かず、届いたら動き、端数は持ち越す`() {
        val steps = DragSteps(0.1)
        assertEquals(0, steps.add(0.06))
        assertEquals(1, steps.add(0.06)) // 0.12 → 1 きざみ、0.02 を持ち越す
        assertEquals(0.02, steps.remainder, 1e-9)
        assertEquals(3, steps.add(0.29)) // 0.31 → 3 きざみ
        assertEquals(0.01, steps.remainder, 1e-9)
    }

    @Test
    fun `左へのドラッグは負のきざみで、向きを戻すと端数から引く`() {
        val steps = DragSteps(0.1)
        assertEquals(-2, steps.add(-0.25))
        assertEquals(-0.05, steps.remainder, 1e-9)
        assertEquals(0, steps.add(0.04)) // まだ 0.1 に届かない
        assertEquals(1, steps.add(0.12)) // -0.01 + 0.12 = 0.11 → 右へ 1 きざみ
    }

    @Test
    fun `指を離したら端数を捨てる`() {
        val steps = DragSteps(0.1)
        steps.add(0.09)
        steps.reset()
        assertEquals(0, steps.add(0.05))
        assertEquals(0.05, steps.remainder, 1e-9)
    }
}
