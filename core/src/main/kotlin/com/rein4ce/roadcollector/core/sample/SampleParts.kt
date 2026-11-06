package com.rein4ce.roadcollector.core.sample

import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.TrackToPart
import com.rein4ce.roadcollector.core.model.Part

/**
 * 初回起動時に同梱する 5 種類のパーツ。
 * サンプル軌跡を実際の変換パイプラインに通して作る（＝変換が壊れるとここで気づける）。
 */
object SampleParts {

    val all: List<Part> by lazy {
        SampleTracks.all.map { track ->
            when (val r = TrackToPart.convert(track.points, ConversionParams.DEFAULT, id = track.id)) {
                is TrackToPart.Result.Success -> r.part.copy(name = track.name)
                else -> error("sample track ${track.id} could not be converted: $r")
            }
        }
    }

    fun byId(id: String): Part? = all.firstOrNull { it.id == id }
}
