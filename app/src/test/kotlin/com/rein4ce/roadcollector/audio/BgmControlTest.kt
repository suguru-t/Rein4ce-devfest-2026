package com.rein4ce.roadcollector.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmControlTest {

    /** 本物の MediaPlayer・音の出番の代わり。呼ばれたことを覚えておく */
    private class FakeOutput : BgmControl.Output {
        override var isPlaying = false
            private set
        override var othersPlayingMusic = false
        var focusResult = BgmControl.FocusResult.GRANTED
        var canLoad = true
        var hasFocus = false
            private set
        var focusRequests = 0
            private set
        var abandons = 0
            private set
        var gain = -1f
            private set

        override fun requestFocus(): BgmControl.FocusResult {
            focusRequests++
            hasFocus = focusResult == BgmControl.FocusResult.GRANTED
            return focusResult
        }

        override fun abandonFocus() {
            abandons++
            hasFocus = false
        }

        override fun play(gain: Float): Boolean {
            if (!canLoad) return false
            this.gain = gain
            isPlaying = true
            return true
        }

        override fun pause() {
            isPlaying = false
        }

        override fun setGain(gain: Float) {
            this.gain = gain
        }
    }

    private val out = FakeOutput()
    private val bgm = BgmControl(out)

    /** MainActivity と同じ順：設定を渡してから、アプリが前に出る */
    private fun open(on: Boolean = true, volume: Int = 5) {
        bgm.setSettings(on, volume)
        bgm.setForeground(true)
    }

    @Test
    fun `前に出ていて、オンのときだけ流す`() {
        bgm.setSettings(true, 5)
        assertFalse(out.isPlaying)
        assertFalse(out.hasFocus)

        bgm.setForeground(true)
        assertTrue(out.isPlaying)
        assertTrue(out.hasFocus)

        bgm.setForeground(false)
        assertFalse(out.isPlaying)
        assertFalse(out.hasFocus)
    }

    @Test
    fun `オフなら前に出ても流さず、オンにしたら流す`() {
        open(on = false)
        assertFalse(out.isPlaying)
        assertEquals(0, out.focusRequests)

        bgm.setSettings(true, 5)
        assertTrue(out.isPlaying)
    }

    @Test
    fun `ほかのアプリが音楽を流しているときは、出番も取りに行かない`() {
        out.othersPlayingMusic = true
        open()
        assertFalse(out.isPlaying)
        assertEquals(0, out.focusRequests)
    }

    @Test
    fun `出番をもらえない・曲を読み込めないときは流さず、出番を持ったままにしない`() {
        out.focusResult = BgmControl.FocusResult.FAILED
        open()
        assertFalse(out.isPlaying)

        out.focusResult = BgmControl.FocusResult.GRANTED
        out.canLoad = false
        bgm.setSettings(true, 6)
        assertFalse(out.isPlaying)
        assertFalse(out.hasFocus)
    }

    @Test
    fun `一時的に出番を取られたら止めて、戻ったら続きから流す`() {
        open()
        bgm.onFocusLostForAWhile()
        assertFalse(out.isPlaying)
        // 戻ってくるのを待つので、出番は返さない
        assertTrue(out.hasFocus)

        // 待っている間に音量を動かしても、まだ流さない
        bgm.setSettings(true, 7)
        assertFalse(out.isPlaying)

        bgm.onFocusGained()
        assertTrue(out.isPlaying)
        assertEquals(1, out.focusRequests)
    }

    @Test
    fun `一時的に出番を取られている間にオフにしても、オンに戻せば出番をもらい直して流す`() {
        // #86 の D のレビュー：再生 → 一時的に取られる → オフ → 相手が終わる → オン
        open()
        bgm.onFocusLostForAWhile()
        bgm.setSettings(false, 5)
        assertFalse(out.hasFocus)

        bgm.setSettings(true, 5)
        assertEquals(2, out.focusRequests)
        assertTrue(out.isPlaying)
    }

    @Test
    fun `ほかのアプリが音楽を流し始めたら止めて、音量を動かしただけでは流さない`() {
        open()
        bgm.onFocusLost()
        assertFalse(out.isPlaying)
        assertFalse(out.hasFocus)

        // その音楽が止まったあとでも、音量を動かしただけでは始めない（#86 の C のレビュー）
        bgm.setSettings(true, 8)
        assertFalse(out.isPlaying)
        assertEquals(1, out.focusRequests)

        // 設定でオンにし直したら流す
        bgm.setSettings(false, 8)
        bgm.setSettings(true, 8)
        assertTrue(out.isPlaying)
    }

    @Test
    fun `ほかのアプリに音楽を譲ったあと、アプリを開き直したら流す`() {
        open()
        bgm.onFocusLost()
        bgm.setForeground(false)
        bgm.setForeground(true)
        assertTrue(out.isPlaying)
    }

    @Test
    fun `電話の最中に開いたら、電話が終わって出番が来てから流す`() {
        // #86 の C のレビュー：出番を「あとで渡す」と言われたら、頼み直さずに待つ
        out.focusResult = BgmControl.FocusResult.DELAYED
        open()
        assertFalse(out.isPlaying)

        // 待っている間に音量を動かしても、頼み直さない
        bgm.setSettings(true, 7)
        assertEquals(1, out.focusRequests)
        assertFalse(out.isPlaying)

        bgm.onFocusGained()
        assertTrue(out.isPlaying)
        assertEquals(1, out.focusRequests)
    }

    @Test
    fun `出番を待っている間に裏に回ったら、頼みを取り下げ、開き直したら頼み直す`() {
        out.focusResult = BgmControl.FocusResult.DELAYED
        open()
        bgm.setForeground(false)
        assertEquals(1, out.abandons)

        out.focusResult = BgmControl.FocusResult.GRANTED
        bgm.setForeground(true)
        assertEquals(2, out.focusRequests)
        assertTrue(out.isPlaying)
    }

    @Test
    fun `出番を待っている間にオフにしたら、頼みを取り下げる`() {
        out.focusResult = BgmControl.FocusResult.DELAYED
        open()
        bgm.setSettings(false, 5)
        assertEquals(1, out.abandons)
        assertFalse(out.isPlaying)
    }

    @Test
    fun `音量は 2 乗で効かせ、1〜10 に収める`() {
        open(volume = 5)
        assertEquals(0.25f, out.gain, 1e-6f)

        bgm.setSettings(true, 10)
        assertEquals(1f, out.gain, 1e-6f)

        bgm.setSettings(true, 0)
        assertEquals(0.01f, out.gain, 1e-6f)

        bgm.setSettings(true, 99)
        assertEquals(1f, out.gain, 1e-6f)
    }
}
