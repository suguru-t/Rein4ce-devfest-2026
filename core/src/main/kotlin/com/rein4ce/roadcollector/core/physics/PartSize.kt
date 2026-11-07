package com.rein4ce.roadcollector.core.physics

import kotlin.math.ln

/**
 * 積むときのパーツの大きさ（長辺 [m]。今までは全部 1）。長く歩いた道ほど大きい。
 *
 * [SHORT_M] 以下で [MIN] 倍、[LONG_M] 以上で [MAX] 倍。そのあいだは距離の対数で決める
 * （そのままの距離に比例させると、数百 m の散歩どうしがほとんど同じ大きさになるため）。400 m ほどでちょうど 1 倍。
 * 例：170 m → 0.82 倍、400 m → 1.02 倍、800 m → 1.19 倍、2 km → 1.4 倍
 */
object PartSize {
    const val MIN = 0.7
    const val MAX = 1.4

    /** これより短い道は、いちばん小さい [MIN] 倍 */
    const val SHORT_M = 100.0

    /** これより長い道は、いちばん大きい [MAX] 倍 */
    const val LONG_M = 2000.0

    fun of(distanceM: Int): Double {
        val d = distanceM.toDouble().coerceIn(SHORT_M, LONG_M)
        return MIN + (MAX - MIN) * ln(d / SHORT_M) / ln(LONG_M / SHORT_M)
    }
}
