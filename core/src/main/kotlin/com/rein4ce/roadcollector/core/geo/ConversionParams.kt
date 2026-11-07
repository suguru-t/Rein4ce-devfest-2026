package com.rein4ce.roadcollector.core.geo

/**
 * 軌跡→パーツ変換の調整パラメータ。
 * 「技術選定まとめ」6 章の初期値。実際に歩いてから調整する。
 */
data class ConversionParams(
    /**
     * これ未満の距離はパーツにしない [m]。
     * 2026-11-06 に 200 → 150（iPhone で歩いた 6 本のうち、きれいな L 字・ジグザグの 2 本が 157 m・178 m で弾かれていた）
     */
    val minDistanceM: Double = 150.0,
    /** accuracy がこれより悪い点は捨てる [m] */
    val maxAccuracyM: Float = 30f,
    /** 前回の点からこれ未満しか動いていない点は無視 [m] */
    val minStepM: Double = 5.0,
    /**
     * 前回採用した点からの移動速度がこれを超える点は捨てる [m/s]（時刻が分かる点だけ）。
     * 歩きは 1.5 m/s くらいなので、5 m/s（時速 18 km）を超える移動は GPS の飛び。
     * 記録開始直後に位置が定まらずぐるぐる動く点や、建物の間で一瞬 30 m 飛ぶ点を落とす。
     * 精度（accuracy）が分からない GPX でも効く。2026-11-06 追加
     */
    val maxSpeedMps: Double = 5.0,
    /**
     * 自分の足で動いているとみなす速さの上限 [m/s]。これより速い区間がある軌跡は、車・自転車とみなしてパーツにしない。
     * 歩きは時速 4〜6.5 km、ジョギングは 8〜12 km くらいなので、ジョギングまで通す 3.9 m/s（時速 14 km）。
     * 自転車は巡航で時速 15 km 以上、車はもっと速いので弾ける（ゆっくりの自転車は通ってしまう）。
     * 点と点の間の速さは GPS の揺れでばらつくので、[walkSpeedWindowMs] の窓の始点から終点までの直線距離 ÷ 時間で測る（TrackSpeed）。
     * 時刻の分からない点（GPX の一部など）が混ざった軌跡は判定しない。
     * 2026-11-07 追加（最初は歩きだけの 2.2 m/s。A が自分のジョギングの記録 2 本（時速 8 km・12 km）を通したいと決めて上げた）
     */
    val walkMaxSpeedMps: Double = 3.9,
    /** 速さを平均する時間の窓 [ms]。信号待ちや横断の小走りで弾かないよう、30 秒 */
    val walkSpeedWindowMs: Long = 30_000L,
    /**
     * Douglas-Peucker の ε [m]。線からこれ以上離れた曲がりは残す。
     * 小さいほど歩いた形の細かい曲がりが残る（小さすぎると GPS の揺れまで形になる）。
     * 2026-11-06 に 15 → 8（歩いた形をなるべくそのままパーツにする方針）
     * 2026-11-06 に 8 → 10（iPhone で歩いた 6 本で確認。8 だとまっすぐな区間に小さなコブが 1 つ出た記録があり、10 なら出ない。角は 10 でも全部残る）
     */
    val epsilonM: Double = 10.0,
    /**
     * GPS の精度に合わせて ε を大きくするか。
     * true なら、アプリが使う点（精度 maxAccuracyM 以内）の精度の中央値と epsilonM の大きい方を ε にする。
     * 精度が良い記録は epsilonM のまま細かく残し、粗い記録だけ大まかにまとめる（GPS の揺れで形が絡まらないように）。
     * 精度が分からない点（accuracyM = 0。GPX など）は数えない。全部分からなければ epsilonM のまま。
     * 2026-11-06 追加（iPhone で歩いた 6 本で確認。精度が良い 5 本は変わらず、粗い 1 本は頂点 16 → 9 個）
     */
    val epsilonFollowsAccuracy: Boolean = true,
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
