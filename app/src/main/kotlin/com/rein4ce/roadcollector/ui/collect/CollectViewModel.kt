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
import com.rein4ce.roadcollector.core.geo.TrackToPart
import com.rein4ce.roadcollector.core.io.TrackFileParser
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleTracks
import com.rein4ce.roadcollector.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 採集画面の状態。
 * 実 GPS（サービス）とデモ再生のどちらも TrackRecorder に点が流れてくるので、画面はそれだけ見ればよい。
 */
class CollectViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RoadCollectorApp.from(app).partRepository
    private val settings = RoadCollectorApp.from(app).settings

    val points: StateFlow<List<TrackPoint>> = TrackRecorder.points
    val isRecording: StateFlow<Boolean> = TrackRecorder.isRecording

    /** 停止後の結果（ダイアログ表示用） */
    sealed class Outcome {
        /**
         * @param note 自動で止めたときの説明（「速すぎたので記録を止めました…」）。ふつうは null
         * @param naming AI に名前を考えてもらっているところか、その結果
         */
        data class Created(
            val part: Part,
            val stats: TrackToPart.Stats,
            val note: String? = null,
            val naming: Naming = Naming.ASKING,
        ) : Outcome()
        data class Rejected(val reason: String) : Outcome()
    }

    /** 採集したパーツの名前（AI に考えてもらう。decisions.md の 5） */
    enum class Naming {
        /** 考えてもらっているところ（「名前を考え中…」） */
        ASKING,

        /** 名前が付いた */
        NAMED,

        /** 付かなかった（通信できない・サーバが考えられなかった）。「○ m の散歩」のまま。コレクションからあとで付けられる */
        FAILED,

        /** 設定で AI の名前付けをオフにしている。聞かずに「○ m の散歩」のまま */
        OFF,
    }

    var outcome: Outcome? by mutableStateOf(null)
        private set

    /** 再生中のものの呼び名（「デモ散歩」「ファイル」）。再生していなければ null */
    var playingLabel: String? by mutableStateOf(null)
        private set

    val isDemoPlaying: Boolean get() = playingLabel != null

    private var demoJob: Job? = null

    init {
        // 速すぎて TrackRecorder が自動で止めたら、速くなる前までの点列でパーツ化を試す。
        // 画面を閉じている間に止まった場合も、次に開いたときにここで拾う（tooFast は StateFlow なので値が残っている）
        viewModelScope.launch {
            TrackRecorder.tooFast.collect { stop -> if (stop != null) onStoppedForSpeed(stop) }
        }
    }

    /**
     * 距離 [m]（表示用）。パーツの distanceM と同じく TrackFilter を通してから測るので、
     * 見出しの「L=」と結果ダイアログの「L=」が同じ値になる。
     */
    fun distanceM(pts: List<TrackPoint>): Double {
        val p = ConversionParams.DEFAULT
        return Projection.pathLength(Projection.toMeters(TrackFilter.filter(pts, p.maxAccuracyM, p.minStepM, p.maxSpeedMps)))
    }

    /** 最近 30 秒の速さ [m/s]（記録中の表示用）。まだ 30 秒に足りなければ null。自動停止の判定と同じ測り方 */
    fun recentSpeedMps(pts: List<TrackPoint>): Double? = TrackRecorder.recentSpeedMps(pts)

    /** 歩き・ジョギングとみなす速さの上限 [m/s]。記録中にこれを超えると自動で止まる */
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
                // 速すぎて途中で自動停止したなら、tooFast の購読側（onStoppedForSpeed）が処理する
                if (!TrackRecorder.isRecording.value) return@launch
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
                keep(r)
            }
            is TrackToPart.Result.TooShort ->
                outcome = Outcome.Rejected("短すぎます（%.0f m、最低 %.0f m）".format(r.distanceM, r.minDistanceM))
            is TrackToPart.Result.TooFewPoints ->
                outcome = Outcome.Rejected("使える位置情報が足りません（${r.pointCount} 点）")
            is TrackToPart.Result.TooFast ->
                outcome = Outcome.Rejected(
                    "歩くより速い移動がありました（30 秒の間で最高 ${kmh(r.fastestMps, RoundingMode.CEILING)}）。" +
                        "車・自転車はパーツになりません。歩きかジョギング（${kmh(r.limitMps, RoundingMode.FLOOR)} まで）で採集してください",
                )
        }
    }

    /**
     * できたパーツを保存して、結果のダイアログに出す。名前は AI（命名 API）に考えてもらい、届いたら差し替える
     * （decisions.md の 5。パーツはすぐ出し、届くまでは「名前を考え中…」）。
     * 名前の手がかりは形・距離・日時だけで、歩いた場所は送らない（8）。付かなければ「○ m の散歩」のまま（7）
     */
    private fun keep(r: TrackToPart.Result.Success, note: String? = null) {
        // 歩いた場所はパーツとは別に、スマホの中だけに保存する（コレクションの拡大表示の背景の地図に使う）
        repository.add(r.part, r.place)
        // 設定（歯車）で AI の名前付けをオフにしていたら、聞かない（形・距離・日時もサーバに送らない）
        if (!settings.aiNaming.value) {
            outcome = Outcome.Created(r.part, r.stats, note = note, naming = Naming.OFF)
            return
        }
        outcome = Outcome.Created(r.part, r.stats, note = note)
        // ダイアログを閉じても、名前は考え続けて保存する
        viewModelScope.launch {
            val suggested = ApiClient.namePart(r.part)
            // 届くまでのあいだに自分で名前を付けていたら、そちらを残す（消していたら何もしない）
            suggested?.let { repository.nameIfUnnamed(r.part.id, it) }
            val shown = outcome as? Outcome.Created ?: return@launch
            if (shown.part.id != r.part.id) return@launch // 別の結果に変わっていたら、そのまま
            val latest = repository.parts.value.firstOrNull { it.id == r.part.id } ?: shown.part
            outcome = shown.copy(part = latest, naming = if (suggested != null) Naming.NAMED else Naming.FAILED)
        }
    }

    /**
     * 速すぎて記録が自動で止まったとき。サービスとデモ再生を止め、速くなる前までの点列でパーツ化を試す。
     * パーツにできる長さなら作り、ダイアログに「止めた」ことを添える。短ければその旨を出す
     */
    private fun onStoppedForSpeed(stop: TrackRecorder.TooFast) {
        TrackRecorder.consumeTooFast()
        LocationRecordingService.stop(getApplication())
        demoJob?.cancel()
        playingLabel = null
        val stopped = "速すぎたので記録を止めました（${kmh(stop.speedMps, RoundingMode.CEILING)}）"
        val limitNote = "歩きかジョギング（${kmh(walkMaxSpeedMps, RoundingMode.FLOOR)} まで）だけ記録できます"
        when (val r = TrackToPart.convert(stop.keptPoints, ConversionParams.DEFAULT)) {
            is TrackToPart.Result.Success -> keep(r, note = "$stopped。止める前までをパーツにしました")
            is TrackToPart.Result.TooShort ->
                outcome = Outcome.Rejected(
                    "$stopped。止める前までの %.0f m は短すぎてパーツになりません（最低 %.0f m）。$limitNote".format(r.distanceM, r.minDistanceM),
                )
            is TrackToPart.Result.TooFewPoints ->
                outcome = Outcome.Rejected(
                    if (r.pointCount < 2) "$stopped。速くなる前の記録が無いので、パーツはできません。$limitNote"
                    else "$stopped。止める前までの位置情報が足りません（${r.pointCount} 点）。$limitNote",
                )
            is TrackToPart.Result.TooFast ->
                outcome = Outcome.Rejected("$stopped。止める前にも速い区間があり、パーツになりません。$limitNote")
        }
    }

    fun dismissOutcome() {
        outcome = null
    }

    companion object {
        /**
         * 速さの表示（「時速 8.1 km」。小数 1 桁）。画面もダイアログも同じ書き方にする。
         * 弾いたときの文では、弾かれた速さは切り上げ（CEILING）、上限は切り捨て（FLOOR）で出す。
         * 上限（2.2 m/s = 時速 7.92 km）のすぐ上（7.93 km）で弾かれたとき、どちらも四捨五入だと「最高 時速 7.9 km」と
         * 「時速 7.9 km まで」になって矛盾して見えるため。小数点は言語の設定によらず「.」（BigDecimal）
         */
        fun kmh(mps: Double, rounding: RoundingMode = RoundingMode.HALF_UP): String =
            "時速 " + BigDecimal(mps * 3.6).setScale(1, rounding).toPlainString() + " km"

        /** 時速の整数だけ（「14」）。状態行のように短く書きたいところ用 */
        fun kmhInt(mps: Double): String = BigDecimal(mps * 3.6).setScale(0, RoundingMode.HALF_UP).toPlainString()
    }
}
