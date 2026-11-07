package com.rein4ce.roadcollector.collect

import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.TrackFilter
import com.rein4ce.roadcollector.core.geo.TrackSpeed
import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 記録中の軌跡を保持する（サービスと画面の橋渡し）。
 * サービス（実 GPS）もデモ再生もここに点を流し込む。
 *
 * 点が来るたびに最近 30 秒の速さを見て、歩き・ジョギングの上限（[ConversionParams.walkMaxSpeedMps]）を超えた状態が
 * [STOP_AFTER_MS] 続いたら記録を自動で止める（[tooFast]）。画面を閉じて記録しているときにも効くように、画面ではなくここで判定する。
 * 超え始めた時刻の 30 秒前より後を切り落とした点列を [TooFast.keptPoints] に残すので、そこまでの歩きはパーツにできる。
 *
 * 「続いたら」にしているのは、GPS が 1 点だけ大きく飛んだときに止めないため（飛びのフィルタは通さないので、1 点の飛びでも
 * 最近 30 秒の速さは一瞬上限を超える。次の点で元に戻るので、続かない）。自転車や車は超え続けるので、10 秒遅れて止まる
 */
object TrackRecorder {

    /** 速すぎて自動で止めた理由。[speedMps] は止めたときの最近 30 秒の速さ、[keptPoints] は速くなる前までの点列 */
    data class TooFast(val speedMps: Double, val keptPoints: List<TrackPoint>)

    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())
    val points: StateFlow<List<TrackPoint>> = _points

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _tooFast = MutableStateFlow<TooFast?>(null)

    /** 上限を超え始めた時刻。超えている間だけ持つ（下回ったら null に戻す） */
    private var overSince: Long? = null

    /** 速すぎて自動で止めたとき、その理由。画面（CollectViewModel）が受け取って [consumeTooFast] で消す */
    val tooFast: StateFlow<TooFast?> = _tooFast

    fun start() {
        _points.value = emptyList()
        _tooFast.value = null
        overSince = null
        _isRecording.value = true
    }

    fun add(point: TrackPoint) {
        if (!_isRecording.value) return
        val pts = _points.updateAndGet { it + point }
        val speed = recentSpeedMps(pts)
        if (speed == null || speed <= ConversionParams.DEFAULT.walkMaxSpeedMps) {
            overSince = null
            return
        }
        val since = overSince ?: point.timeMs.also { overSince = it }
        if (point.timeMs - since < STOP_AFTER_MS) return
        // 超え始めた時刻の 30 秒前より後は、速かった分が混ざるので切り落とす
        val cutoff = since - ConversionParams.DEFAULT.walkSpeedWindowMs
        _isRecording.value = false
        _tooFast.value = TooFast(speed, pts.filter { it.timeMs < cutoff })
    }

    /** 記録を止めて、点列を返す。 */
    fun stop(): List<TrackPoint> {
        _isRecording.value = false
        return _points.value
    }

    /** 自動停止の理由を受け取ったら消す（同じ理由で 2 回動かないように） */
    fun consumeTooFast() {
        _tooFast.value = null
    }

    /**
     * 最近 30 秒の速さ [m/s]。まだ 30 秒に足りなければ null。
     * 飛びのフィルタ（5 m/s）は通さない。車のように速い点は飛びとして全部落とされてしまい、速さが測れなくなるため。
     * 精度の悪い点と、近すぎる点（立ち止まり中のふらつき）だけ落として測る
     */
    fun recentSpeedMps(pts: List<TrackPoint>): Double? {
        val p = ConversionParams.DEFAULT
        val usable = TrackFilter.filter(pts, p.maxAccuracyM, p.minStepM, maxSpeedMps = Double.MAX_VALUE)
        return TrackSpeed.recentMps(usable, p.walkSpeedWindowMs)
    }

    private inline fun <T> MutableStateFlow<T>.updateAndGet(f: (T) -> T): T {
        update(f)
        return value
    }

    /** 上限を超えた状態がこれだけ続いたら止める [ms]。1 点の GPS の飛びでは止めず、自転車や車は止める */
    const val STOP_AFTER_MS = 10_000L
}
