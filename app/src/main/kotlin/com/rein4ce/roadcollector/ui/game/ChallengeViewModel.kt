package com.rein4ce.roadcollector.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rein4ce.roadcollector.core.challenge.ChallengeRules
import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import com.rein4ce.roadcollector.core.challenge.ChallengeStyle
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.StackWorld
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 3分チャレンジの画面の中身のうち、操作・次の形・結果のときだけ変わるもの。
 * 毎コマ変わるもの（塔の図形 [ChallengeViewModel.shapes]・残り時間 [ChallengeViewModel.remainingSeconds]）は入れない
 * （入れると、毎コマ新しい中身になって、画面全体を毎コマ組み立て直すことになるため。#78 のレビュー）
 */
data class ChallengeFrame(
    val phase: ChallengeRun.Phase,
    val current: Part,
    val next: Part,
    val x: Double,
    val angle: Double,
    val spawnY: Double,
    val cameraTop: Double,
    val towerHeight: Double,
    val score: ChallengeScore?,
) {
    val terminal get() = phase == ChallengeRun.Phase.FINISHED || phase == ChallengeRun.Phase.UNRESOLVED
}

/**
 * 3分チャレンジの進行と、端末内の記録の保存。
 *
 * 記録の保存：
 * - 結果が出たら、画面が無くても ViewModel が保存する（1 回の結果は 1 回だけ）
 * - 保存に失敗したら「再試行」できる。次の回にも進める（その回の記録は残らない、と画面に出す）。
 *   前は保存できるまで「もう一度」が押せず、ファイルが壊れていると先に進めなかった（#78 のレビュー）
 * - 開いたときの読み込みに失敗しても、新しい結果は保存しにいく（前は読み込みの失敗が残って、自動では保存しなかった）
 */
class ChallengeViewModel(
    private val updateRecords: suspend (ChallengeScore?) -> List<ChallengeScore>,
    private val newRun: (ChallengeStyle) -> ChallengeRun = { ChallengeRun(Random.nextLong(), style = it) },
    /** [ChallengeStyle] ごとの記録を読む（決まりの ID）。渡さなければ、自分で落とすの記録（テスト） */
    private val loadRecords: suspend (String) -> List<ChallengeScore> = { updateRecords(null) },
) : ViewModel() {
    private var run: ChallengeRun? = null

    /** 新しい回を始めた・やめたときに増える（画面のフレームのループを始め直す目印） */
    var generation by mutableIntStateOf(0)
        private set

    /** 今の回（始めていなければ null） */
    var frame by mutableStateOf<ChallengeFrame?>(null)
        private set

    /** 塔の図形。毎コマ変わるので、キャンバスの描画の中だけで読む（組み立て直しを起こさない） */
    var shapes by mutableStateOf<List<StackWorld.RenderShape>>(emptyList())
        private set

    /** 残り時間 [秒]（切り上げ）。秒が変わったときだけ変わる */
    var remainingSeconds by mutableIntStateOf(FULL_SECONDS)
        private set

    /**
     * 遊び方（自分で落とす・自動で落ちる。#45・決定 65）。始める前の画面で選ぶ。記録の一覧もこれで分ける
     */
    var style by mutableStateOf(ChallengeStyle.MANUAL)
        private set

    /**
     * いま狙っている形の高さ [m]。自動で落ちるときは毎コマ下がるので、フレーム（[frame]）には入れず、キャンバスの描画の中だけで読む
     */
    var ghostY by mutableFloatStateOf(0f)
        private set

    var ranking by mutableStateOf<List<ChallengeScore>>(emptyList())
        private set

    /** 結果を保存しているところ */
    var saving by mutableStateOf(false)
        private set

    /** 記録を読み書きできなかった（「再試行」を出す） */
    var saveError by mutableStateOf(false)
        private set

    private var savedRunId: String? = null
    private var pendingRunId: String? = null

    /** 保存に失敗した回。自動では保存し直さない（「再試行」で保存し直す） */
    private var failedRunId: String? by mutableStateOf(null)
    private var operation = 0

    /** 今の回の結果を保存できなかった（次の回に進むと、その記録は残らない） */
    val resultUnsaved: Boolean get() = frame?.score?.runId?.let { it == failedRunId } == true

    /** 次の回を始められるか。結果を保存している間だけ待つ（保存できたか、失敗したら進める） */
    val canStart: Boolean
        get() {
            val score = run?.score ?: return true
            return score.runId == savedRunId || score.runId == failedRunId
        }

    init {
        refreshRecords()
    }

    /** 遊び方を選ぶ（始める前だけ）。記録の一覧を、その遊び方のものに読み直す */
    fun selectStyle(next: ChallengeStyle) {
        if (frame != null || next == style) return
        style = next
        ranking = emptyList()
        load()
    }

    fun start() {
        if (!canStart) return
        run?.pause()
        run = newRun(style)
        generation++
        publishFrame()
    }

    /** 「やり直す」：今の回を記録に残さずに捨てて、新しい回を始める */
    fun restart() {
        if (frame?.terminal == true) return
        run?.pause()
        run = null
        start()
    }

    /** 「中断」：今の回を記録に残さずに捨てて、始める前の画面（説明とランキング）に戻る */
    fun abandon() {
        if (frame?.terminal == true) return
        run?.pause()
        run = null
        frame = null
        shapes = emptyList()
        remainingSeconds = FULL_SECONDS
        generation++
    }

    fun resume() {
        run?.resume()
        publishFrame()
    }

    fun pause() {
        run?.pause()
        publishFrame()
    }

    fun tick() {
        run?.tick()
        publishFrame()
    }

    fun move(dx: Double) {
        run?.move(dx)
        publishFrame()
    }

    fun rotate(degrees: Double) {
        run?.rotate(degrees)
        publishFrame()
    }

    fun place() {
        run?.place()
        publishFrame()
    }

    /**
     * 今の回の様子を画面に渡す。フレームは中身が同じなら変わらない（mutableStateOf は同じ値では知らせない）ので、
     * 操作も次の形も無いコマでは、塔の図形と（秒が変わったときの）残り時間だけが変わる
     */
    private fun publishFrame() {
        val r = run ?: return
        shapes = r.shapes
        ghostY = r.ghostY.toFloat()
        remainingSeconds = ((r.remainingNs + NS_PER_SECOND - 1) / NS_PER_SECOND).toInt()
        frame = ChallengeFrame(r.phase, r.current, r.next, r.x, r.angle, r.spawnY, r.cameraTop, r.towerHeight, r.score)
        val score = r.score
        if (score != null && score.runId != savedRunId && score.runId != pendingRunId && score.runId != failedRunId) save(score)
    }

    /** 「再試行」：今の結果を保存できていなければ保存し直し、ランキングを読み直す */
    fun refreshRecords() {
        val score = run?.score?.takeIf { it.runId != savedRunId }
        if (score != null) save(score) else load()
    }

    private fun load() {
        val request = ++operation
        saveError = false
        viewModelScope.launch {
            try {
                val records = loadRecords(style.ruleId)
                if (request == operation) ranking = records
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (request == operation) saveError = true
            }
        }
    }

    private fun save(score: ChallengeScore) {
        if (pendingRunId == score.runId) return
        val request = ++operation
        pendingRunId = score.runId
        failedRunId = null
        saving = true
        saveError = false
        // 画面を離れて ViewModel の仕事が止められる前に、保存の処理に入っておく
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val records = updateRecords(score)
                savedRunId = score.runId
                if (request == operation) ranking = records
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failedRunId = score.runId
                if (request == operation) saveError = true
            } finally {
                if (pendingRunId == score.runId) pendingRunId = null
                if (request == operation) saving = false
            }
        }
    }

    companion object {
        private const val NS_PER_SECOND = 1_000_000_000L

        /** 始める前の残り時間の表示 [秒]（3:00） */
        val FULL_SECONDS = (ChallengeRules.DURATION_NS / NS_PER_SECOND).toInt()
    }
}
