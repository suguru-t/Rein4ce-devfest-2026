package com.rein4ce.roadcollector.audio

import com.rein4ce.roadcollector.data.AppSettings

/**
 * BGM を流すか止めるかの決まり。音を出すところ（MediaPlayer と、音の出番＝オーディオフォーカス）は [Output] にまかせる
 * （Android に触らないので、ユニットテストで決まりを確かめられる。[BgmPlayer] が本物の [Output] をつなぐ）。
 *
 * - 流すのは、アプリが前に出ていて（[setForeground]）、設定の BGM がオンのときだけ
 * - 音量は、端末のメディアの音量に、設定の音量（1〜10）の割合を掛ける。耳の感じ方に合わせ、割合は 2 乗で小さくする
 * - ほかのアプリが音楽を流しているときは、始めない（その人の音楽を止めない）
 * - 電話やカーナビの案内などで一時的に出番を取られたら止めて、戻ってきたら続きから流す
 * - 電話の最中にアプリを開いたとき（出番を「あとで渡す」と言われたとき）は、電話が終わって出番が来てから流す
 * - ほかのアプリが音楽を流し始めたら（出番をずっと取られたら）止めて、次にアプリを開くか、設定でオンにし直すまで流さない
 *   （音量を動かしただけでは始めない）
 */
internal class BgmControl(private val out: Output) {

    /** 音を出すところ */
    interface Output {
        /** 曲が鳴っているか */
        val isPlaying: Boolean

        /** ほかのアプリが音楽を流しているか */
        val othersPlayingMusic: Boolean

        /** 音の出番をもらう */
        fun requestFocus(): FocusResult

        /** 音の出番を返す */
        fun abandonFocus()

        /** 曲を（止めたところから）この大きさで流す。曲を読み込めなければ false */
        fun play(gain: Float): Boolean

        fun pause()

        fun setGain(gain: Float)
    }

    /** 音の出番を頼んだ答え */
    enum class FocusResult {
        /** もらえた */
        GRANTED,

        /** 今は渡せないが、あとで渡す（電話の最中など。渡せるようになったら [onFocusGained] が来る） */
        DELAYED,

        /** もらえなかった */
        FAILED,
    }

    private var foreground = false
    private var enabled = true

    /** 鳴らす大きさ（0〜1） */
    private var gain = gainOf(AppSettings.DEFAULT_BGM_VOLUME)

    /** 音の出番を持っているか */
    private var hasFocus = false

    /** 出番を「あとで渡す」と言われて待っているか（出番が来たら流す。頼み直さない） */
    private var waitingForFocus = false

    /** 一時的に出番を取られて止めているか（出番を持ったまま待ち、戻ってきたら続きから流す） */
    private var pausedForOthers = false

    /** ほかのアプリが音楽を流し始めたので止めたか（次にアプリを開くか、設定でオンにし直すまで流さない） */
    private var yieldedToOthers = false

    /**
     * ほかの音に出番を譲っている間か（電話・地図アプリの道案内の声などで一時的に止めている、電話の最中で出番を待っている）。
     * この間は効果音も鳴らさない（audio/SoundEffects.kt。#93 の A のレビュー）。
     * ほかのアプリが音楽を流し始めて譲った（[yieldedToOthers]）ときは含めない（一時的ではなく、効果音は短いので）
     */
    val givingWay: Boolean get() = pausedForOthers || waitingForFocus

    /** アプリが前に出たか（MainActivity の onStart・onStop） */
    fun setForeground(on: Boolean) {
        if (foreground == on) return
        foreground = on
        update()
    }

    /** 設定の BGM のオン・オフと音量（1〜10） */
    fun setSettings(on: Boolean, volume: Int) {
        enabled = on
        gain = gainOf(volume)
        out.setGain(gain)
        update()
    }

    /** 出番が来た（一時的に取られていたのが戻ってきた・あとで渡すと言われていたのが来た） */
    fun onFocusGained() {
        hasFocus = true
        val resume = pausedForOthers || waitingForFocus
        pausedForOthers = false
        waitingForFocus = false
        if (resume) update()
    }

    /** 一時的に出番を取られた（電話・カーナビの案内など） */
    fun onFocusLostForAWhile() {
        pausedForOthers = true
        pause()
    }

    /** ほかのアプリが音楽を流し始めた */
    fun onFocusLost() {
        pausedForOthers = false
        yieldedToOthers = true
        pause()
        releaseFocus()
    }

    private fun update() {
        if (foreground && enabled) start() else stop()
    }

    private fun start() {
        if (out.isPlaying || pausedForOthers || yieldedToOthers || waitingForFocus) return
        if (!hasFocus) {
            // ほかのアプリが音楽を流しているときは、その音楽を止めないよう、こちらは流さない
            if (out.othersPlayingMusic) return
            when (out.requestFocus()) {
                FocusResult.GRANTED -> hasFocus = true
                // 電話の最中など：出番が来たら onFocusGained から流す（電話を切ったら流れ始める。#86 の C のレビュー）
                FocusResult.DELAYED -> {
                    waitingForFocus = true
                    return
                }
                FocusResult.FAILED -> return
            }
        }
        if (!out.play(gain)) releaseFocus()
    }

    /** 自分で止める（アプリが裏に回った・設定でオフにした） */
    private fun stop() {
        pause()
        releaseFocus()
        // 出番を返したので、戻ってきた知らせはもう来ない。待つのをやめて、次に流すときは出番をもらい直す
        // （一時的に取られている間にオフにすると、オンに戻しても流れなかった。#86 の D のレビュー）
        pausedForOthers = false
        yieldedToOthers = false
    }

    private fun pause() {
        if (out.isPlaying) out.pause()
    }

    /** 出番を返す。「あとで渡す」と言われて待っている頼みも取り下げる */
    private fun releaseFocus() {
        if (hasFocus || waitingForFocus) {
            out.abandonFocus()
            hasFocus = false
            waitingForFocus = false
        }
    }

    companion object {
        /** 設定の音量（1〜10）を、鳴らす大きさ（0〜1）に。耳の感じ方に合わせて 2 乗にする（5 で 1/4） */
        fun gainOf(volume: Int): Float {
            val v = volume.coerceIn(1, AppSettings.MAX_BGM_VOLUME) / AppSettings.MAX_BGM_VOLUME.toFloat()
            return v * v
        }
    }
}
