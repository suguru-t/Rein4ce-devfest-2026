package com.rein4ce.roadcollector.ui.collect

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.collect.DemoWalkPlayer
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.geo.TrackToPart
import com.rein4ce.roadcollector.core.io.TrackFileParser
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleTracks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 採集画面の状態。
 * 実 GPS（サービス）とデモ再生のどちらも TrackRecorder に点が流れてくるので、画面はそれだけ見ればよい。
 */
class CollectViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoadCollectorApp.from(app).partRepository

    val points: StateFlow<List<TrackPoint>> = TrackRecorder.points
    val isRecording: StateFlow<Boolean> = TrackRecorder.isRecording

    /** 停止後の結果（ダイアログ表示用） */
    sealed class Outcome {
        data class Created(val part: Part, val stats: TrackToPart.Stats) : Outcome()
        data class Rejected(val reason: String) : Outcome()
    }

    var outcome: Outcome? by mutableStateOf(null)
        private set

    var isDemoPlaying: Boolean by mutableStateOf(false)
        private set

    private var demoJob: Job? = null

    /** 距離 [m]（表示用。フィルタ前の生データなので目安） */
    fun distanceM(pts: List<TrackPoint>): Double =
        Projection.pathLength(Projection.toMeters(pts))

    fun startRecording() {
        val ctx = getApplication<Application>()
        if (!LocationRecordingService.hasLocationPermission(ctx)) return
        TrackRecorder.start()
        LocationRecordingService.start(ctx)
    }

    /** 記録を止めてパーツに変換する。 */
    fun stopAndConvert() {
        val ctx = getApplication<Application>()
        demoJob?.cancel()
        isDemoPlaying = false
        LocationRecordingService.stop(ctx)
        val pts = TrackRecorder.stop()
        convert(pts)
    }

    fun playDemo(track: SampleTracks.SampleTrack) = playPoints { track.points }

    /** 実機で録った GPX / JSON ファイルを読んで、デモ散歩と同じように流す。 */
    fun playFile(uri: Uri) = playPoints {
        val ctx = getApplication<Application>()
        val text = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } ?: throw IllegalArgumentException("ファイルを開けません")
        TrackFileParser.parse(text)
    }

    private fun playPoints(load: suspend () -> List<TrackPoint>) {
        if (isRecording.value) return
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            isDemoPlaying = true
            try {
                val points = try {
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    outcome = Outcome.Rejected("読み込めませんでした：${e.message ?: e.javaClass.simpleName}")
                    return@launch
                }
                DemoWalkPlayer.play(points)
                val pts = TrackRecorder.stop()
                convert(pts)
            } finally {
                isDemoPlaying = false
            }
        }
    }

    private fun convert(pts: List<TrackPoint>) {
        when (val r = TrackToPart.convert(pts, ConversionParams.DEFAULT)) {
            is TrackToPart.Result.Success -> {
                repository.add(r.part)
                outcome = Outcome.Created(r.part, r.stats)
            }
            is TrackToPart.Result.TooShort ->
                outcome = Outcome.Rejected("短すぎます（%.0f m、最低 %.0f m）".format(r.distanceM, r.minDistanceM))
            is TrackToPart.Result.TooFewPoints ->
                outcome = Outcome.Rejected("使える位置情報が足りません（${r.pointCount} 点）")
        }
    }

    fun dismissOutcome() {
        outcome = null
    }
}
