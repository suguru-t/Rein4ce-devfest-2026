package com.rein4ce.roadcollector.core.weather

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI

/**
 * 月の満ち欠け。晴れの夜の背景で、満月の夜だけ満月を出すのに使う（ふだんは分かりやすい三日月。通信はしない。日付から計算するだけ）。
 *
 * 新月から次の新月までの平均の長さ（朔望月、約 29.53 日）と、基準にする新月の時刻から、何割進んだかを出す。
 * 本当の月の動きは少し速くなったり遅くなったりするので、1 日ほどずれることがあるが、背景の絵としては十分
 */
object Moon {
    /** 朔望月の長さ [日] */
    const val SYNODIC_DAYS = 29.530588853

    /** 基準にする新月（2000-01-06 18:14 UTC）[epoch ミリ秒] */
    private const val REFERENCE_NEW_MOON_MS = 947_182_440_000L

    /**
     * [timeMs] の月の位相（0〜1）。0 が新月、0.25 が上弦、0.5 が満月、0.75 が下弦。
     * 0〜0.5 は満ちていく（右から光る）、0.5〜1 は欠けていく（左だけ光る）
     */
    fun phase(timeMs: Long): Double {
        val days = (timeMs - REFERENCE_NEW_MOON_MS) / 86_400_000.0
        val p = (days / SYNODIC_DAYS) % 1.0
        return if (p < 0) p + 1.0 else p
    }

    /** 満月の夜か（満月の前後 1 日まで。上の計算は 1 日ほどずれることがあるので、少し幅を持たせる） */
    fun isFull(phase: Double): Boolean = abs(phase - 0.5) <= 1.0 / SYNODIC_DAYS

    /** 光って見える割合（0〜1）。新月で 0、満月で 1 */
    fun illumination(phase: Double): Double = (1 - cos(2 * PI * phase)) / 2
}
