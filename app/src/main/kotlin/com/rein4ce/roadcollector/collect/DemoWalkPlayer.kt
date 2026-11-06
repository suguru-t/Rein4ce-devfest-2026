package com.rein4ce.roadcollector.collect

import com.rein4ce.roadcollector.core.sample.SampleTracks
import kotlinx.coroutines.delay

/**
 * 歩かなくてもデモできるように、サンプル軌跡を早回しで TrackRecorder に流す。
 * 審査員は歩けないので、これがデモの命綱。
 *
 * 実機で録った GPX / JSON を読み込む機能は、このクラスに足すのが自然。
 */
object DemoWalkPlayer {

    suspend fun play(track: SampleTracks.SampleTrack, durationMs: Long = 3_000L) {
        TrackRecorder.start()
        val perPoint = (durationMs / track.points.size.coerceAtLeast(1)).coerceAtLeast(1L)
        for (p in track.points) {
            TrackRecorder.add(p)
            delay(perPoint)
        }
    }
}
