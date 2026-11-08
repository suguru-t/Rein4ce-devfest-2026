package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.weather.Sky

/**
 * パーツのすべりやすさ。歩いたときの天気で決まり、晴れ → くもり → 霧 → 霧雨 → 雨 → 雪 の順に、積むと滑りやすい（摩擦が小さい）。
 * くもりと霧は晴れとほんの少しだけ違い、雨や雪の日の道ははっきり滑る。
 *
 * パーツどうしが触れたときは、滑りやすい方の値を使う。台との間は天気によらず晴れと同じ（台は滑らない）（[StackWorld] の摩擦の混ぜ方）。
 *
 * @property friction パーツの面の摩擦
 * @property label 積む画面に出す一言。ほとんど変わらない（晴れ・くもり・霧）なら null
 */
enum class Grip(val friction: Double, val label: String?) {
    /** 晴れ・天気が分からない：今までどおり */
    NORMAL(0.7, null),

    /** くもり */
    CLOUDY(0.62, null),

    /** 霧 */
    FOGGY(0.55, null),

    /** 霧雨 */
    DAMP(0.35, "少しすべりやすい"),

    /** 雨・雷雨 */
    WET(0.15, "すべりやすい"),

    /** 雪 */
    ICY(0.05, "とてもすべりやすい"),
    ;

    companion object {
        /** 歩いたときの天気（分からなければ null）から */
        fun of(sky: Sky?): Grip = when (sky) {
            Sky.SNOW -> ICY
            Sky.RAIN, Sky.THUNDER -> WET
            Sky.DRIZZLE -> DAMP
            Sky.FOG -> FOGGY
            Sky.CLOUDY -> CLOUDY
            Sky.CLEAR, null -> NORMAL
        }
    }
}
