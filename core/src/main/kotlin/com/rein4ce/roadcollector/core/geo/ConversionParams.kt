package com.rein4ce.roadcollector.core.geo

/**
 * 軌跡→パーツ変換の調整パラメータ。
 * 「技術選定まとめ」6 章の初期値。実際に歩いてから調整する。
 */
data class ConversionParams(
    /** これ未満の距離はパーツにしない [m] */
    val minDistanceM: Double = 200.0,
    /** accuracy がこれより悪い点は捨てる [m] */
    val maxAccuracyM: Float = 30f,
    /** 前回の点からこれ未満しか動いていない点は無視 [m] */
    val minStepM: Double = 5.0,
    /** Douglas-Peucker の ε [m] */
    val epsilonM: Double = 15.0,
    /** 頂点の上限 */
    val maxVertices: Int = 12,
    /** 終点が始点からこの比率（長辺に対する）以内なら「輪」 */
    val closeRatio: Double = 0.10,
    /** 太さ（長辺に対する比率） */
    val thickness: Float = 0.15f,
) {
    companion object {
        val DEFAULT = ConversionParams()

        /** デモ・サンプル用：短い軌跡でもパーツにする */
        val LENIENT = ConversionParams(minDistanceM = 30.0)
    }
}
