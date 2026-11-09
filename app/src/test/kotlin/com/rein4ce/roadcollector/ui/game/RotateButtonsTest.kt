package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import org.junit.Assert.assertEquals
import org.junit.Test

/** 回転のボタンは、押している間なめらかに回る（決定 67） */
class RotateButtonsTest {

    @Test
    fun `押し始めのコマは回さず、そのあとはコマの時間 × 90°毎秒だけ回す`() {
        val spin = RotateSpin(RotateDirection.LEFT.sign)
        assertEquals(0f, spin.step(1_000_000_000L), 0f)
        assertEquals("1/60 秒で 1.5°", 1.5f, spin.step(1_000_000_000L + 16_666_667L), 1e-3f)
        var angle = 0f
        var t = 1_000_000_000L + 16_666_667L
        repeat(59) { t += 16_666_667L; angle += spin.step(t) }
        assertEquals("1 秒押すと（はじめのコマを除いて）約 90°", 88.5f, angle, 0.01f)
    }

    @Test
    fun `右は反対の向きに回す`() {
        val spin = RotateSpin(RotateDirection.RIGHT.sign)
        spin.step(0L)
        assertEquals(-ROTATE_SPEED_DEG_S * 0.02f, spin.step(20_000_000L), 1e-4f)
    }

    @Test
    fun `長いコマ（裏から戻ったときなど）でも、1 コマで大きく回らない`() {
        val spin = RotateSpin(RotateDirection.LEFT.sign)
        spin.step(0L)
        assertEquals(ROTATE_SPEED_DEG_S * ROTATE_MAX_FRAME_S, spin.step(2_000_000_000L), 1e-4f)
        assertEquals("時刻が戻っても回さない", 0f, spin.step(1_000_000_000L), 0f)
    }

    @Test
    fun `15° きざみにそろえず、離した角度のまま`() {
        var now = 0L
        val run = ChallengeRun(1, clock = { now })
        run.resume()
        repeat(7) { run.rotate(RotateDirection.LEFT.sign * 1.5) }
        assertEquals(10.5, run.angle, 1e-6)
    }

    @Test
    fun `ボタンの字は記号だけで、読み上げは向き`() {
        assertEquals("⟲", RotateDirection.LEFT.text)
        assertEquals("⟳", RotateDirection.RIGHT.text)
        assertEquals("左に回す", RotateDirection.LEFT.label)
        assertEquals("右に回す", RotateDirection.RIGHT.label)
    }
}
