package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.weather.Sky
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames

/**
 * パーツのすべりやすさ。歩いたときの天気で決まり、晴れ → くもり → 雨 → 雪 の順に、積むと滑りやすい（摩擦が小さい）。
 * くもりは晴れとほんの少しだけ違い、雨や雪の日の道ははっきり滑る。
 *
 * 画面に出す天気を晴れ・くもり・雨・雪の 4 つにまとめたので（決定 73。[Sky.shown]）、すべりやすさも 4 つにした。
 * 前は霧（[CLOUDY] より少し滑る）と霧雨（[WET] より滑りにくい）にも別の値があったが、霧はくもり、霧雨は雨と同じにした
 * （同じ「雨の日の道」で滑り方や天気のボーナスが違うと分かりにくいため）。
 * 毎日 1 つずつ積むの塔のファイルには、前の名前（"FOGGY"・"DAMP"）で残っていることがあるので、読むときはそれぞれ
 * [CLOUDY]・[WET] として読む（[JsonNames]）
 *
 * パーツどうしが触れたときは、滑りやすい方の値を使う。台との間は天気によらず晴れと同じ（台は滑らない）（[StackWorld] の摩擦の混ぜ方）。
 *
 * @property friction パーツの面の摩擦
 * @property label 積む画面に出す一言。ほとんど変わらない（晴れ・くもり）なら null
 * @property bonusPercent 天気のボーナス（[WeatherBonus]。決定 68）：終わったときに台の上に残っていたら、模型を大きくする倍率に足す分
 *   [百分の一]。滑りやすいほど大きい（雨は 0.10、雪は 0.20）。ほとんど滑らないくもりは少しだけ
 */
@OptIn(ExperimentalSerializationApi::class)
enum class Grip(val friction: Double, val label: String?, val bonusPercent: Int) {
    /** 晴れ・天気が分からない：今までどおり */
    NORMAL(0.7, null, 0),

    /** くもり（霧も） */
    @JsonNames("FOGGY")
    CLOUDY(0.62, null, 1),

    /** 雨（霧雨・雷雨も） */
    @JsonNames("DAMP")
    WET(0.15, "すべりやすい", 10),

    /** 雪 */
    ICY(0.05, "とてもすべりやすい", 20),
    ;

    companion object {
        /** 歩いたときの天気（分からなければ null）から。画面に出す 4 つの天気（[Sky.shown]）で決める */
        fun of(sky: Sky?): Grip = when (sky?.shown) {
            Sky.SNOW -> ICY
            Sky.RAIN -> WET
            Sky.CLOUDY -> CLOUDY
            else -> NORMAL
        }
    }
}
