package com.rein4ce.roadcollector.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.data.BgmTrack

/**
 * アプリの BGM（res/raw/bgm・bgm_chip・bgm_ukulele・bgm_guitar。tools/make_bgm.py で作った、つなぎ目なく繰り返す曲）。全部の画面で同じ曲を流す。
 * 曲は設定で選ぶ（[setTrack]。決定 48）。
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

    /** 流す曲（設定の「BGM の曲」） */
    private var track: BgmTrack = BgmTrack.USUAL

    /** いま流している大きさ（曲を切り替えたときに、同じ大きさで流し始めるため） */
    private var gain: Float = 1f

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        // 「小さくすれば鳴らしてよい」と言われたとき（カーナビの案内など）も、ほかと同じく止める。
        // これが無いと、端末が勝手に小さくするだけで知らせが来ないことがある（#86 の D のレビュー）
        .setWillPauseWhenDucked(true)
        // 電話の最中に頼んだときは「あとで渡す」と答えてもらい、電話が終わったら出番をもらって流す（#86 の C のレビュー）
        .setAcceptsDelayedFocusGain(true)
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

        override fun requestFocus(): BgmControl.FocusResult = when (audio.requestAudioFocus(focusRequest)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> BgmControl.FocusResult.GRANTED
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> BgmControl.FocusResult.DELAYED
            else -> BgmControl.FocusResult.FAILED
        }

        override fun abandonFocus() {
            audio.abandonAudioFocusRequest(focusRequest)
        }

        override fun play(gain: Float): Boolean {
            val p = player ?: create() ?: return false
            this@BgmPlayer.gain = gain
            p.setVolume(gain, gain)
            p.start()
            return true
        }

        override fun pause() {
            player?.pause()
        }

        override fun setGain(gain: Float) {
            this@BgmPlayer.gain = gain
            player?.setVolume(gain, gain)
        }
    })

    /** アプリが前に出たか（MainActivity の onStart・onStop） */
    fun setForeground(on: Boolean) = control.setForeground(on)

    /** 設定の BGM のオン・オフと音量（1〜10） */
    fun setSettings(on: Boolean, volume: Int) = control.setSettings(on, volume)

    /**
     * 設定の「BGM の曲」。変わったら、今の曲を片付ける。流している途中なら、すぐに新しい曲を頭から同じ大きさで流す
     * （音の出番はそのまま持っているので、頼み直さない）。止めている・一時的に譲っているときは、次に流すときに新しい曲を読む。
     * 流す・止めるの決まり（[BgmControl]）は、曲によらないので変えない
     */
    fun setTrack(next: BgmTrack) {
        if (next == track) return
        track = next
        val p = player ?: return
        val wasPlaying = p.isPlaying
        p.release()
        player = null
        if (wasPlaying) {
            create()?.apply {
                setVolume(gain, gain)
                start()
            }
        }
    }

    private fun create(): MediaPlayer? = runCatching {
        MediaPlayer.create(context, track.res, attributes, audio.generateAudioSessionId())?.apply {
            isLooping = true
        }
    }.onFailure { Log.w(TAG, "BGM could not be loaded", it) }.getOrNull().also { player = it }

    /** 曲のファイル（tools/make_bgm.py の SONGS と同じ名前） */
    private val BgmTrack.res: Int
        get() = when (this) {
            BgmTrack.USUAL -> R.raw.bgm
            BgmTrack.CHIP -> R.raw.bgm_chip
            BgmTrack.UKULELE -> R.raw.bgm_ukulele
            BgmTrack.GUITAR -> R.raw.bgm_guitar
        }

    private companion object {
        const val TAG = "BgmPlayer"
    }
}
