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
import com.rein4ce.roadcollector.core.geo.TrackFilter
import com.rein4ce.roadcollector.core.geo.TrackSpeed
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

    /** 再生中のものの呼び名（「デモ散歩」「ファイル」）。再生していなければ null */
    var playingLabel: String? by mutableStateOf(null)
        private set

    val isDemoPlaying: Boolean get() = playingLabel != null

    private var demoJob: Job? = null

    /**
     * 距離 [m]（表示用）。パーツの distanceM と同じく TrackFilter を通してから測るので、
     * 見出しの「L=」と結果ダイアログの「L=」が同じ値になる。
     */
    fun distanceM(pts: List<TrackPoint>): Double {
        val p = ConversionParams.DEFAULT
        return Projection.pathLength(Projection.toMeters(TrackFilter.filter(pts, p.maxAccuracyM, p.minStepM, p.maxSpeedMps)))
    }

    /**
     * 最近 30 秒の速さ [m/s]（記録中の表示用）。まだ 30 秒に足りなければ null。
     * パーツ化の判定（TrackToPart）と同じく、TrackFilter を通した点で測る
     */
    fun recentSpeedMps(pts: List<TrackPoint>): Double? {
        val p = ConversionParams.DEFAULT
        return TrackSpeed.recentMps(TrackFilter.filter(pts, p.maxAccuracyM, p.minStepM, p.maxSpeedMps), p.walkSpeedWindowMs)
    }

    /** 歩きとみなす速さの上限 [m/s]。これを超える区間がある散歩はパーツにならない */
    val walkMaxSpeedMps: Double = ConversionParams.DEFAULT.walkMaxSpeedMps

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
        playingLabel = null
        LocationRecordingService.stop(ctx)
        val pts = TrackRecorder.stop()
        convert(pts)
    }

    fun playDemo(track: SampleTracks.SampleTrack) = playPoints("デモ散歩") { track.points }

    /** 実機で録った GPX / JSON ファイルを読んで、デモ散歩と同じように流す。 */
    fun playFile(uri: Uri) = playPoints("ファイル") {
        val ctx = getApplication<Application>()
        val text = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } ?: throw IllegalArgumentException("ファイルを開けません")
        TrackFileParser.parse(text)
    }

    private fun playPoints(label: String, load: suspend () -> List<TrackPoint>) {
        if (isRecording.value) return
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            playingLabel = label
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
                playingLabel = null
            }
        }
    }

    private fun convert(pts: List<TrackPoint>) {
        when (val r = TrackToPart.convert(pts, ConversionParams.DEFAULT)) {
            is TrackToPart.Result.Success -> {
                // 歩いた場所はパーツとは別に、スマホの中だけに保存する（コレクションの拡大表示の背景の地図に使う。B）
                repository.add(r.part, r.place)
                outcome = Outcome.Created(r.part, r.stats)
            }
            is TrackToPart.Result.TooShort ->
                outcome = Outcome.Rejected("短すぎます（%.0f m、最低 %.0f m）".format(r.distanceM, r.minDistanceM))
            is TrackToPart.Result.TooFewPoints ->
                outcome = Outcome.Rejected("使える位置情報が足りません（${r.pointCount} 点）")
            is TrackToPart.Result.TooFast ->
                outcome = Outcome.Rejected(
                    "歩くより速い移動がありました（30 秒の間で最高 ${kmh(r.fastestMps)}）。" +
                        "車・自転車・走りはパーツになりません。歩き（${kmh(r.limitMps)} まで）で採集してください",
                )
        }
    }

    fun dismissOutcome() {
        outcome = null
    }

    companion object {
        /** 速さの表示（「時速 8 km」）。画面もダイアログも同じ書き方にする */
        fun kmh(mps: Double): String = "時速 %.0f km".format(mps * 3.6)
    }
}
