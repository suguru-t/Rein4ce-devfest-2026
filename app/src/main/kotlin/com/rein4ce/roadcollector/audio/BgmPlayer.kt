package com.rein4ce.roadcollector.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.data.AppSettings

/**
 * アプリの BGM（res/raw/bgm。tools/make_bgm.py で作った、つなぎ目なく繰り返す曲）。全部の画面で同じ曲を流す。
 *
 * 流すのは、アプリが前に出ていて（[setForeground]）、設定の BGM がオンのときだけ
 * （記録中にポケットに入れているとき・ほかのアプリを見ているときは止める）。
 * - 音量は、端末のメディアの音量に、設定の音量（1〜10）の割合を掛ける。耳の感じ方に合わせ、割合は 2 乗で小さくする
 * - ほかのアプリが音楽を流しているときは、始めない（その人の音楽を止めない）
 * - 電話や、ほかのアプリが音を出し始めたとき（音の出番を取られたとき）は止める。一時的に取られただけなら、戻ったら続きから流す
 *
 * アプリ全体で 1 つ（RoadCollectorApp）。MainActivity の開始・停止と、設定の変わり目から呼ぶ
 */
class BgmPlayer(context: Context) {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var player: MediaPlayer? = null
    private var foreground = false
    private var enabled = true
    private var gain = gainOf(AppSettings.DEFAULT_BGM_VOLUME)

    /** 音の出番を持っているか */
    private var hasFocus = false

    /** 一時的に音の出番を取られて止めているか（戻ってきたら続きから流す） */
    private var pausedForOthers = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasFocus = true
                    if (pausedForOthers) {
                        pausedForOthers = false
                        update()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    pausedForOthers = true
                    player?.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // ほかのアプリが音楽を流し始めた：止めて、次にアプリを開くまで流さない
                    pausedForOthers = false
                    player?.pause()
                    releaseFocus()
                }
            }
        }
        .build()

    /** アプリが前に出たか（MainActivity の onStart・onStop） */
    fun setForeground(on: Boolean) {
        if (foreground == on) return
        foreground = on
        if (!on) pausedForOthers = false
        update()
    }

    /** 設定の BGM のオン・オフと音量（1〜10） */
    fun setSettings(on: Boolean, volume: Int) {
        enabled = on
        gain = gainOf(volume)
        player?.setVolume(gain, gain)
        update()
    }

    private fun update() {
        if (foreground && enabled) start() else stop()
    }

    private fun start() {
        val p = player ?: create() ?: return
        if (p.isPlaying || pausedForOthers) return
        if (!hasFocus) {
            // ほかのアプリが音楽を流しているときは、その音楽を止めないよう、こちらは流さない
            if (audio.isMusicActive) return
            if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            hasFocus = true
        }
        p.setVolume(gain, gain)
        p.start()
    }

    private fun stop() {
        player?.takeIf { it.isPlaying }?.pause()
        releaseFocus()
    }

    private fun releaseFocus() {
        if (hasFocus) {
            audio.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
    }

    private fun create(): MediaPlayer? = runCatching {
        MediaPlayer.create(context, R.raw.bgm, attributes, audio.generateAudioSessionId())?.apply {
            isLooping = true
        }
    }.onFailure { Log.w(TAG, "BGM could not be loaded", it) }.getOrNull().also { player = it }

    private companion object {
        const val TAG = "BgmPlayer"

        /** 設定の音量（1〜10）を、鳴らす大きさ（0〜1）に。耳の感じ方に合わせて 2 乗にする（5 で 1/4） */
        fun gainOf(volume: Int): Float {
            val v = volume.coerceIn(1, AppSettings.MAX_BGM_VOLUME) / AppSettings.MAX_BGM_VOLUME.toFloat()
            return v * v
        }
    }
}
