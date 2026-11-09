package com.rein4ce.roadcollector.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.data.AppSettings
import kotlin.math.pow

/** 効果音の種類。音は tools/make_sfx.py で作った木琴の音（BGM と同じ音色・同じハ長調） */
enum class Sfx(val res: Int) {
    /** ボタンを押したとき（押した瞬間の振動と同じ所で鳴らす）。高めの短い「ぽこっ」 */
    PRESS(R.raw.sfx_press),

    /** 題字・画面名の積み木を押したとき。「ぴょん」。積み木ごとに高さを変える（[BlockNotes]）。最後の積み木も、押したときにこれを鳴らす */
    HOP(R.raw.sfx_hop),

    /** 最後の積み木が落ちてきて着いたとき。軽い「ことっ」 */
    LAND(R.raw.sfx_land),

    /** 記録を止めて、道を 1 本集めたとき。ド・ミ・ソ・ドのごほうび */
    DONE(R.raw.sfx_done),
}

/**
 * 積み木ごとの音の高さ。ハ長調の音階（ド・レ・ミ・ファ・ソ・ラ・シ・ド）を左から 1 音ずつ上がり、**最後の積み木がいつも上のド**になるように、
 * 音階を後ろからそろえる。「ミチツミ」はソ・ラ・シ・ド、「コレクション」はミ・ファ・ソ・ラ・シ・ド、「みんなのタワー」はレ・ミ・ファ・ソ・ラ・シ・ド。
 * 左から押すと、木琴を叩くように音階を上がって、ドで締まる（左のドから始めると、字の数しだいでファ・ラ・シで終わり、中途半端に聞こえた。B が決めた）。
 * 「ぴょん」の音はソ（G5）で作ってあるので、再生の速さを変えて高さを変える（速さ 2 倍で 1 オクターブ上）
 */
object BlockNotes {
    /** ソ（G5）から何半音か：ド・レ・ミ・ファ・ソ・ラ・シ・ド */
    private val SEMITONES = intArrayOf(-7, -5, -3, -2, 0, 2, 4, 5)

    /**
     * [count] 個並んだ積み木の、[index] 番目（左から 0）の再生の速さ。最後（[count] - 1 番目）が上のド。
     * 8 個より多いときは、左へ音階を 1 周さかのぼる（ド の左は 上のド）
     */
    fun rate(index: Int, count: Int): Float {
        val step = SEMITONES.size - count + index
        return 2f.pow(SEMITONES[Math.floorMod(step, SEMITONES.size)] / 12f)
    }
}

/**
 * 効果音を鳴らす係（アプリ全体で 1 つ。RoadCollectorApp）。
 * 短い音をすぐ鳴らすので、MediaPlayer ではなく SoundPool に、起動したときに全部読み込んでおく。
 * 設定の「効果音」がオフなら鳴らさない（鳴らす瞬間に読むので、ボタン 1 つずつが設定を見張らなくてよい。振動と同じ考え方）。
 *
 * 音の出口はメディアの音量（BGM と同じ）。BGM と違って、ほかのアプリの音楽を止めたり（音の順番の取り合い）はしない
 * （0.1〜1 秒の短い音なので、ゲームの効果音と同じく、重ねて鳴らす）
 */
class SoundEffects(context: Context, private val settings: AppSettings) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /** 読み込みを始めた音の番号（読み込みが終わる前に鳴らそうとしても、鳴らないだけで困らない） */
    private val ids: Map<Sfx, Int> = Sfx.entries.associateWith { pool.load(context, it.res, 1) }

    /**
     * [sfx] を鳴らす。[rate] は再生の速さ（音の高さ。1 がそのまま、0.5〜2）。設定でオフなら何もしない
     */
    fun play(sfx: Sfx, rate: Float = 1f) {
        if (!settings.sfxOn.value) return
        val id = ids[sfx] ?: return
        pool.play(id, 1f, 1f, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private companion object {
        /** 同時に鳴らせる数（積み木を続けて押したときに、前の音を切らない程度） */
        const val MAX_STREAMS = 6
    }
}
