package com.rein4ce.roadcollector.collect

import com.rein4ce.roadcollector.core.geo.TrackTime
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleTracks
import com.rein4ce.roadcollector.data.PartOrigin
import kotlinx.coroutines.delay

/**
 * 歩かなくてもデモできるように、軌跡を早回しで TrackRecorder に流す。
 * 審査員は歩けないので、これがデモの命綱。
 *
 * 流す軌跡はサンプル（[SampleTracks]）か、実機で録った GPX / JSON ファイル
 * （[com.rein4ce.roadcollector.core.io.TrackFileParser] で読んだ点列）。
 * どちらも時刻を「最後の点 = 今」にずらして流すので、できたパーツの採集日時は今になる。
 * 出どころ（デモ散歩かファイルか）は、流す側が [origin] で渡す（完成カードの「使った道」には数えない）
 */
object DemoWalkPlayer {

    suspend fun play(track: SampleTracks.SampleTrack, durationMs: Long = 3_000L) =
        play(track.points, PartOrigin.DEMO, durationMs)

    suspend fun play(points: List<TrackPoint>, origin: PartOrigin, durationMs: Long = 3_000L) {
        val shifted = TrackTime.shiftToEndAt(points, endMs = System.currentTimeMillis())
        TrackRecorder.start(origin)
        val perPoint = (durationMs / shifted.size.coerceAtLeast(1)).coerceAtLeast(1L)
        for (p in shifted) {
            if (!TrackRecorder.isRecording.value) break // 速すぎて自動で止まった
            TrackRecorder.add(p)
            delay(perPoint)
        }
    }
}
