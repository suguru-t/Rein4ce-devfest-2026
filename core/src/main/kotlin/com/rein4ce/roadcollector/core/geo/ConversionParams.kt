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
    /**
     * Douglas-Peucker の ε [m]。線からこれ以上離れた曲がりは残す。
     * 小さいほど歩いた形の細かい曲がりが残る（小さすぎると GPS の揺れまで形になる）。
     * 2026-11-06 に 15 → 8（歩いた形をなるべくそのままパーツにする方針）
     */
    val epsilonM: Double = 8.0,
    /**
     * 頂点の上限。曲がり角の多い散歩でも角を残せるように多めにしている。
     * 1 頂点ごとに物理の部品（矩形 1 つ＋関節の円 1 つ）が増える。
     * 2026-11-06 に 12 → 24
     */
    val maxVertices: Int = 24,
    /**
     * 分類（表示用の名札と色）は、形の細かさに左右されないよう、粗く簡略化した形で判定する。
     * 値は以前の ε・頂点上限と同じ。
     */
    val classifyEpsilonM: Double = 15.0,
    val classifyMaxVertices: Int = 12,
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
