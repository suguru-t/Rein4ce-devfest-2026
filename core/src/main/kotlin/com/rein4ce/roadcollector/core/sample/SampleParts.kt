package com.rein4ce.roadcollector.core.sample

import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.TrackToPart
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
import com.rein4ce.roadcollector.core.weather.WalkWeather

/**
 * 初回起動時に同梱する 5 種類のパーツ。
 * サンプル軌跡を実際の変換パイプラインに通して作る（＝変換が壊れるとここで気づける）。
 */
object SampleParts {

    private val converted: List<TrackToPart.Result.Success> by lazy {
        SampleTracks.all.map { track ->
            when (val r = TrackToPart.convert(track.points, ConversionParams.DEFAULT, id = track.id)) {
                is TrackToPart.Result.Success -> r.copy(part = r.part.copy(name = track.name))
                else -> error("sample track ${track.id} could not be converted: $r")
            }
        }
    }

    val all: List<Part> by lazy { converted.map { it.part } }

    /** 見本を歩いた場所（千住キャンパス付近）。拡大表示の背景の地図に使う */
    val places: Map<String, PartPlace> by lazy { converted.associate { r -> r.part.id to r.place } }

    /**
     * 見本を歩いたときの天気。見本は実際には歩いていないので、道の色と背景の雨・雪の見本になるように決めてある
     * （棒：快晴、L 字：くもり、U 字：雨、輪：晴れ、ジグザグ：雪）
     */
    val weather: Map<String, WalkWeather> = mapOf(
        "sample-bar" to WalkWeather(code = 0),
        "sample-l" to WalkWeather(code = 3),
        "sample-u" to WalkWeather(code = 63, precipitationMm = 3.0),
        "sample-ring" to WalkWeather(code = 1),
        "sample-zigzag" to WalkWeather(code = 73, precipitationMm = 1.0),
    )

    fun byId(id: String): Part? = all.firstOrNull { it.id == id }
}
