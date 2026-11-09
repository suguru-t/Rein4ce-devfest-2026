package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 回転のボタンを押し続けると回り続ける（#114） */
@OptIn(ExperimentalCoroutinesApi::class)
class RotateButtonsTest {

    @Test
    fun `押し続けると 120 ms ごとに 15° 回り、離すと止まる`() = runTest {
        val repeater = MoveRepeater(this, intervalMs = ROTATE_REPEAT_MS)
        var angle = 0f
        repeater.start { angle += RotateDirection.LEFT.sign * ROTATE_STEP_DEG }
        assertEquals("回り始めにすぐ 1 回", 15f, angle, 0f)
        advanceTimeBy(360); runCurrent()
        assertEquals("0.36 秒で 4 回（1 秒に約 125°）", 60f, angle, 0f)
        repeater.stop(); advanceTimeBy(1000)
        assertEquals("離したら止まる", 60f, angle, 0f)
    }

    @Test
    fun `回し続けても 15° きざみのまま（離したあとにそろえ直さない）`() {
        // 3 分チャレンジの回し方で確かめる（積む画面の進め方（StackViewModel）の作り直し（#119）に左右されないように）
        var now = 0L
        val run = ChallengeRun(1, clock = { now })
        run.resume()
        repeat(7) { run.rotate((RotateDirection.RIGHT.sign * ROTATE_STEP_DEG).toDouble()) }
        assertEquals(255.0, run.angle, 0.0001)
        assertEquals(0.0, run.angle % ROTATE_STEP_DEG, 0.0001)
    }

    @Test
    fun `ボタンの字と読み上げ`() {
        assertEquals("⟲ 15°", RotateDirection.LEFT.text)
        assertEquals("⟳ 15°", RotateDirection.RIGHT.text)
        assertEquals("左に 15° 回す", RotateDirection.LEFT.label)
        assertEquals("右に 15° 回す", RotateDirection.RIGHT.label)
    }
}
