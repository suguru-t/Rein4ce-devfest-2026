package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.sample.SampleParts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
/** 押し続けると繰り返す仕組み（回すボタン）と、ドラッグで動かすときの範囲 */
class HoldRepeaterTest {
    /** ドラッグで少しずつ動かす量 [m] */
    private val step = 0.05f

    @Test fun `long hold repeats at eighty ms and release stops it`() = runTest {
        val repeater = MoveRepeater(this)
        var steps = 0
        repeater.start { steps++ }
        assertEquals(1, steps)
        advanceTimeBy(240); runCurrent()
        assertEquals(4, steps)
        repeater.stop(); advanceTimeBy(1000)
        assertEquals(4, steps)
    }
    @Test fun `replacing a held direction cancels the old repeater`() = runTest {
        val repeater = MoveRepeater(this)
        var x = 0
        repeater.start { x++ }
        repeater.start { x-- }
        advanceTimeBy(80); runCurrent()
        assertEquals(-1, x)
        repeater.stop()
    }
    @Test fun `drag moves in meters and clamps at both edges`() {
        val part = SampleParts.all.first()
        val vm = StackViewModel()
        vm.updateInventory(listOf(part), true)
        vm.dragBy(.12f); vm.dragBy(step)
        assertEquals(.17f, vm.ghostX, .0001f)
        vm.dragBy(-.07f)
        assertEquals(.1f, vm.ghostX, .0001f)
        repeat(1000) { vm.dragBy(step) }
        assertEquals(vm.dragLimit, vm.ghostX, .0001f)
        repeat(1000) { vm.dragBy(-step) }
        assertEquals(-vm.dragLimit, vm.ghostX, .0001f)
    }
    @Test fun `falling and terminal states refuse further movement`() {
        val part = SampleParts.all.first()
        val vm = StackViewModel()
        vm.updateInventory(listOf(part), true)
        // 台の外に置く（通常モードは、台から落ちたら終わり）
        vm.dragBy(10f); vm.place()
        val fallingX = vm.ghostX
        vm.dragBy(-step)
        assertEquals(fallingX, vm.ghostX, 0f)
        repeat(20 * 60) {
            if (vm.phase == StackViewModel.Phase.Finished || vm.phase == StackViewModel.Phase.Unresolved) return@repeat
            vm.tick(1.0 / 60)
        }
        assertTrue(vm.phase == StackViewModel.Phase.Finished || vm.phase == StackViewModel.Phase.Unresolved)
        val finalX = vm.ghostX
        vm.dragBy(-step)
        assertEquals(finalX, vm.ghostX, 0f)
    }
}
