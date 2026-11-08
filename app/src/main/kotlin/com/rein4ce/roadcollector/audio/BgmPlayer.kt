package com.rein4ce.roadcollector.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.rein4ce.roadcollector.R

/**
 * アプリの BGM（res/raw/bgm。tools/make_bgm.py で作った、つなぎ目なく繰り返す曲）。全部の画面で同じ曲を流す。
 *
 * 流すか止めるかの決まりは [BgmControl]（アプリが前に出ていて、設定でオンのときだけ。ほかのアプリの音楽を止めない、など）。
 * ここは、その決まりを MediaPlayer と音の出番（オーディオフォーカス）につなぐ。
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

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        // 「小さくすれば鳴らしてよい」と言われたとき（カーナビの案内など）も、ほかと同じく止める。
        // これが無いと、端末が勝手に小さくするだけで知らせが来ないことがある（#86 の D のレビュー）
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> control.onFocusGained()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> control.onFocusLostForAWhile()
                AudioManager.AUDIOFOCUS_LOSS -> control.onFocusLost()
            }
        }
        .build()

    private val control: BgmControl = BgmControl(object : BgmControl.Output {
        override val isPlaying: Boolean get() = player?.isPlaying == true
        override val othersPlayingMusic: Boolean get() = audio.isMusicActive

        override fun requestFocus(): Boolean = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        override fun abandonFocus() {
            audio.abandonAudioFocusRequest(focusRequest)
        }

        override fun play(gain: Float): Boolean {
            val p = player ?: create() ?: return false
            p.setVolume(gain, gain)
            p.start()
            return true
        }

        override fun pause() {
            player?.pause()
        }

        override fun setGain(gain: Float) {
            player?.setVolume(gain, gain)
        }
    })

    /** アプリが前に出たか（MainActivity の onStart・onStop） */
    fun setForeground(on: Boolean) = control.setForeground(on)

    /** 設定の BGM のオン・オフと音量（1〜10） */
    fun setSettings(on: Boolean, volume: Int) = control.setSettings(on, volume)

    private fun create(): MediaPlayer? = runCatching {
        MediaPlayer.create(context, R.raw.bgm, attributes, audio.generateAudioSessionId())?.apply {
            isLooping = true
        }
    }.onFailure { Log.w(TAG, "BGM could not be loaded", it) }.getOrNull().also { player = it }

    private companion object {
        const val TAG = "BgmPlayer"
    }
}
