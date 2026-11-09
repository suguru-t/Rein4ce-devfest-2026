package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「回す」は、押している間なめらかに回る（決定 71。前は 15° ずつ。#114） */
class RotateButtonsTest {
    private val frame = 16_666_667L // 60 fps の 1 コマ [ns]

    @Test
    fun `押している間、1 秒に 120° の速さで回り、押し始めのコマでは回さない`() {
        val clock = TurnClock()
        clock.start(0L)
        var angle = 0f
        for (i in 1..30) angle += clock.advance(i * frame) // 0.5 秒
        assertEquals(60f, angle, 0.01f)
    }

    @Test
    fun `軽く触って離せば少しだけ回り、角度にきざみは無い`() {
        val clock = TurnClock()
        clock.start(0L)
        var angle = 0f
        for (i in 1..5) angle += clock.advance(i * frame) // 約 0.08 秒
        assertEquals(10f, angle, 0.01f)
        assertTrue("15° のきざみではない", angle % 15f != 0f)
    }

    @Test
    fun `コマが飛んでも、一度に大きく回らない`() {
        val clock = TurnClock()
        clock.start(0L)
        assertEquals("0.05 秒ぶんまで", 6f, clock.advance(2_000_000_000L), 0.001f)
    }

    @Test
    fun `45° 回るごとに 1 回振動する`() {
        val clock = TurnClock()
        clock.start(0L)
        var ticks = 0
        for (i in 1..60) { // 1 秒で 120°
            clock.advance(i * frame)
            if (clock.tick()) ticks++
        }
        assertEquals(2, ticks)
        assertFalse(clock.tick())
    }

    @Test
    fun `3 分チャレンジでも、回した角度のまま（15° にそろえない）`() {
        var now = 0L
        val run = ChallengeRun(1, clock = { now })
        run.resume()
        repeat(7) { run.rotate((RotateDirection.RIGHT.sign * 2.5f).toDouble()) }
        assertEquals(342.5, run.angle, 0.0001)
    }

    @Test
    fun `ボタンの字と読み上げ`() {
        assertEquals("⟲", RotateDirection.LEFT.text)
        assertEquals("⟳", RotateDirection.RIGHT.text)
        assertEquals("左に回す", RotateDirection.LEFT.label)
        assertEquals("右に回す", RotateDirection.RIGHT.label)
        assertEquals("時計回りに回す。押している間、回り続ける", TURN_BUTTON_LABEL)
    }
}
