package com.rein4ce.roadcollector.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.WalkDistance
import com.rein4ce.roadcollector.data.walkDistance
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * D 担当：積み上げゲームの進行。物理は :core の StackWorld、ここは「ターン」の管理。
 *
 * Placing    … 手持ちの 1 個をドラッグ・回転して「置く」
 * Settling   … 落下中。塔全体が止まったら次へ。落ちたパーツは、落ちた時点で取り除いて数える
 * Finished   … 置き切って、塔全体が止まったのを確かめた。結果（[result]）を固定し、物理も止める
 * Unresolved … 最後のパーツのあと、上限の時間までに塔が止まらなかった（判定できない）。結果は見せるが送信はできない
 */
class StackViewModel(
    /** 送信に失敗したとき、画面に出す接続先。デモ端末で接続先の設定ミスを見分けるため */
    private val serverUrl: () -> String = { ApiClient.baseUrl },
    /** 落下待ちの時間。テストで短くできるように差し替えられる */
    private val settleTiming: SettleTiming = SettleTiming(),
    private val postTower: suspend (Tower) -> TowerCreated = { ApiClient.postTower(it) },
) : ViewModel() {

    enum class Phase { Idle, Placing, Settling, Finished, Unresolved }

    /**
     * 落下待ちの決まり。[minSeconds] より前は止まっていても次へ進まない（置いた直後の一瞬の静止を拾わないため）。
     * [maxSeconds] までに止まらなければ時間切れ：途中のパーツならそのまま次へ（最後の判定で塔全体をもう一度確かめる）、
     * 最後のパーツなら [Phase.Unresolved]
     */
    data class SettleTiming(val minSeconds: Double = 0.8, val maxSeconds: Double = 6.0)

    /**
     * ゲームの結果。置き切った瞬間に 1 つにまとめて固定し、完成の表示と送信の両方がこれを使う（以後の tick では変わらない）。
     *
     * @property usedCount   このゲームで置いたパーツの数
     * @property fallenCount 落ちたパーツの数（置いたあとに崩れて落ちたものも含む）
     * @property settled     最後のパーツのあと、塔全体が止まったのを確かめられたか
     */
    data class StackResult(val tower: Tower, val usedCount: Int, val fallenCount: Int, val settled: Boolean, val walked: WalkDistance = WalkDistance(0, 0)) {
        /** 台の上に残ったパーツの数 */
        val remainingCount: Int get() = tower.parts.size

        /** サーバに送れるか。止まったのを確かめられなかった結果と、パーツが 1 つも残らなかった結果は送らない（空のタワーはサーバが 400 で断る：#58） */
        val canUpload: Boolean get() = settled && tower.parts.isNotEmpty()
    }

    private var world = StackWorld()

    /** このゲームで使うパーツ（開始時のスナップショット） */
    var sessionParts: List<Part> = emptyList()
        private set
    private var queue: List<Part> = emptyList()

    var runId: Long by mutableStateOf(0L)
        private set
    private var handledRequestId: Long? = null

    /** いまのゲームが、選択なし（コレクションの先頭から）で始めたものか */
    private var implicitRun = false

    /** 選んだ直後の要求が解決できなかった。別のパーツで勝手に始めず、選び直しを待つ */
    private var waitingForReselect = false

    /** 置き切ったときに固定した結果（[Phase.Finished] / [Phase.Unresolved] のときだけ入っている） */
    var result: StackResult? by mutableStateOf(null)
        private set

    var startMessage: String? by mutableStateOf(null)
        private set

    var phase: Phase by mutableStateOf(Phase.Idle)
        private set
    var current: Part? by mutableStateOf(null)
        private set
    var remaining: Int by mutableStateOf(0)
        private set
    var shapes: List<StackWorld.RenderShape> by mutableStateOf(emptyList())
        private set
    var height: Float by mutableStateOf(0f)
        private set
    var fallenCount: Int by mutableStateOf(0)
        private set

    /** ゴースト（置く前のパーツ）の位置と角度。x [m]、角度は度（反時計回り正） */
    var ghostX: Float by mutableStateOf(0f)
        private set
    var ghostAngleDeg: Float by mutableStateOf(0f)
        private set

    /** 送信結果の表示用 */
    var uploadMessage: String? by mutableStateOf(null)
        private set
    var uploading: Boolean by mutableStateOf(false)
        private set

    /** #39の一回送信をrun単位で管理し、古い送信結果を次のゲームへ持ち越さない。 */
    private var uploadedRunId: Long? by mutableStateOf(null)

    /** いま完成しているタワーが送信済みか。送信済みならボタンを押せなくする */
    val uploaded: Boolean get() = result != null && uploadedRunId == runId

    /** いまの結果をサーバに送れるか（完成して、塔が止まったのを確かめられ、パーツが残っている） */
    val canUpload: Boolean get() = phase == Phase.Finished && result?.canUpload == true

    /** 落下を待ち始めてから進めた時間 [s]。1 フレームの上限（[MAX_FRAME_SECONDS]）をかけた時間を足すので、物理の進みと合う */
    private var settleTimer = 0.0

    val platformWidth: Double get() = world.platformWidth

    /** ゴーストを左右に動かせる範囲 [m]（台の端から [DRAG_MARGIN_M] 外まで） */
    val dragLimit: Float get() = (platformWidth / 2 + DRAG_MARGIN_M).toFloat()

    /**
     * カメラの狙い（[CameraTarget]）。パーツが止まったときと始めたときだけ変わる（[nextPart]）。
     * 狙っている間（ドラッグ・回転）と落下中は変えないので、指で動かしている最中にカメラが動かない
     */
    var camera: CameraTarget by mutableStateOf(CameraTarget.Follow(0f))
        private set

    /** ゴーストを出す高さ [m]（タワーの上 1.5 m） */
    fun spawnY(): Float = height + 1.5f

    /**
     * コレクションの通知を受け取る。一覧が変わっただけではゲームを始め直さない。
     *
     * - 新しい開始要求：IDを今のコレクションで解決して始める（要求ごとに1回だけ）
     * - 要求がない、または前に遊んだ要求のパーツがすべて消えた：まだ一度も始めていなければ、先頭の最大5個で始める
     */
    fun updateInventory(inventory: List<Part>, isLoaded: Boolean, request: GameSession.StartRequest?) {
        if (!isLoaded) return
        if (request != null && request.id != handledRequestId) {
            handledRequestId = request.id
            val parts = resolve(request.partIds, inventory)
            if (parts.isNotEmpty()) {
                start(parts, implicit = false)
                GameSession.markStarted(request.id)
                return
            }
            if (!request.started) {
                // 選んだ直後の要求：別のパーツで代わりに始めない（#9）
                waitingForReselect = true
                startMessage = MESSAGE_SELECTION_MISSING
                return
            }
            // 前に遊んだ要求（積む画面が作り直された）で、パーツがすべて消えていた：選択なしと同じに扱う
        }
        if (runId == 0L && !waitingForReselect && inventory.isNotEmpty()) {
            start(inventory.take(GameSession.MAX_PARTS), implicit = true)
        }
    }

    /**
     * 「やり直す」「もう一度」。選んで始めたゲームは同じパーツの最新の値で、
     * 選択なしで始めたゲームは今のコレクションの先頭の最大5個で始め直す。
     * 使えるパーツがなければ、いまのゲームはそのままにして案内を出す。
     */
    fun restart(inventory: List<Part>) {
        if (phase == Phase.Idle) return
        val ids = if (implicitRun) inventory.map { it.id } else sessionParts.map { it.id }
        val parts = resolve(ids, inventory)
        if (parts.isEmpty()) {
            startMessage = if (implicitRun) MESSAGE_COLLECTION_EMPTY else MESSAGE_RETRY_MISSING
            return
        }
        start(parts, implicitRun)
    }

    /** IDを今のコレクションのPartに置き換える。消えたものは除き、重複を除いて最大5個、元の順番のまま */
    private fun resolve(ids: List<String>, inventory: List<Part>): List<Part> {
        val byId = inventory.associateBy { it.id }
        return ids.distinct().mapNotNull { byId[it] }.take(GameSession.MAX_PARTS)
    }

    fun dismissStartMessage() {
        startMessage = null
    }

    private fun start(parts: List<Part>, implicit: Boolean) {
        runId++
        implicitRun = implicit
        waitingForReselect = false
        // 前のゲームの送信は打ち切らない（サーバ側の保存と食い違わないように）。
        // 結果は runId で照合するので、次のゲームの表示には混ざらない
        uploading = false
        result = null
        settleTimer = 0.0
        startMessage = null
        world = StackWorld()
        sessionParts = parts.toList()
        queue = sessionParts
        height = 0f
        fallenCount = 0
        uploadMessage = null
        shapes = world.renderShapes()
        nextPart()
    }

    /**
     * 次のパーツへ進む。もう無ければ、この瞬間の結果を固定して終わる。
     * @param settled 直前のパーツのあと、塔全体が止まったのを確かめられたか（時間切れなら false）
     */
    private fun nextPart(settled: Boolean = true) {
        current = queue.firstOrNull()
        queue = queue.drop(1)
        remaining = queue.size
        ghostX = 0f
        ghostAngleDeg = 0f
        if (current == null) {
            result = StackResult(
                tower = Tower(
                    parts = world.placedParts,
                    placements = world.placements(),
                    height = height,
                    createdAt = System.currentTimeMillis(),
                ),
                usedCount = sessionParts.size,
                fallenCount = fallenCount,
                settled = settled,
                walked = walkDistance(sessionParts),
            )
            phase = if (settled) Phase.Finished else Phase.Unresolved
        } else {
            phase = Phase.Placing
        }
        camera = computeCamera()
    }

    /**
     * カメラの狙いを決める。パーツが止まったとき（と始めたとき）だけ計算し直す（[nextPart] から呼ぶ）。
     *
     * - 次のパーツを狙うとき：大きさは変えず、出てくる範囲のいちばん上（出てくる高さ＋どう回しても入る半径）が入るところまで
     *   カメラを上げる。縮めると、同じ指の動きでパーツが大きく動き、細かく狙いたい高い塔ほど狙いにくくなるため（#70 のレビュー）
     * - 置き切ったとき：台と、台の上に残っているパーツが全部入るように引く。全体が台の下面より下にあるパーツ
     *   （落ちている途中のもの）は入れない（落ちていくパーツを入れようとして小さくなりすぎないように）
     */
    private fun computeCamera(): CameraTarget {
        val part = current
        if (phase == Phase.Placing && part != null) return CameraTarget.Follow(top = spawnY() + reachOf(part))
        val halfWidth = (world.platformWidth / 2).toFloat()
        val bottom = -world.platformThickness.toFloat()
        var focus = WorldRect(-halfWidth, halfWidth, bottom, 0f)
        world.renderShapes().filter { it.part != null }.groupBy { it.part }.values.forEach { shapesOfPart ->
            val bounds = shapesOfPart.map { it.bounds() }.reduce(WorldRect::union)
            if (bounds.maxY >= bottom) focus = focus.union(bounds)
        }
        return CameraTarget.Overview(focus)
    }

    /**
     * パーツの原点から、形のいちばん遠い点までの距離 [m]。どう回しても、この半径の円の中に収まる。
     * 置くときと同じ大きさ（[PartSize]。長く歩いた道ほど大きい：#71）で測る
     */
    private fun reachOf(part: Part): Float {
        var reach = 0.0
        for (poly in PartGeometry.shapes(part, PartSize.of(part.distanceM))) {
            for (v in poly.points) reach = max(reach, hypot(v.x, v.y))
        }
        return reach.toFloat()
    }

    fun dragBy(dxMeters: Float) {
        if (phase != Phase.Placing) return
        ghostX = (ghostX + dxMeters).coerceIn(-dragLimit, dragLimit)
    }

    fun rotate(deltaDeg: Float) {
        if (phase != Phase.Placing) return
        ghostAngleDeg = (ghostAngleDeg + deltaDeg + 360f) % 360f
    }

    /**
     * いまのパーツを置く。大きさは歩いた距離で決まる（[PartSize]。長く歩いた道ほど大きい）。
     *
     * @param grip いま置くパーツのすべりやすさ（歩いたときの天気。天気は画面がコレクションから引いて渡す）
     */
    fun place(grip: Grip = Grip.NORMAL) {
        val part = current ?: return
        if (phase != Phase.Placing) return
        world.place(
            part,
            ghostX.toDouble(),
            spawnY().toDouble(),
            Math.toRadians(ghostAngleDeg.toDouble()),
            scale = PartSize.of(part.distanceM),
            grip = grip,
        )
        phase = Phase.Settling
        settleTimer = 0.0
    }

    /**
     * 毎フレーム呼ぶ。[dtSeconds] は前のフレームからの時間。
     *
     * - 1 回に進める時間は [MAX_FRAME_SECONDS] まで。アプリを裏から戻したときなどに長い dt が来ても、
     *   物理がほとんど進まないまま待ち時間だけが進んで、判定が飛ぶことはない
     * - 落ちたパーツは、落ちた時点で取り除いて数える（すでに置いたパーツが崩れて落ちた場合も）。
     *   落ち続けるパーツは止まらないので、残しておくと塔全体が止まったことにならず、上限まで待つことになる
     * - 置き切ったあと（[Phase.Finished] / [Phase.Unresolved]）は物理を進めない。結果と画面の塔が変わらない
     */
    fun tick(dtSeconds: Double) {
        if (phase == Phase.Idle || phase == Phase.Finished || phase == Phase.Unresolved) return
        val dt = dtSeconds.coerceIn(0.0, MAX_FRAME_SECONDS)
        world.step(dt)
        val fallen = world.removeFallen()
        if (fallen.isNotEmpty()) fallenCount += fallen.size

        if (phase == Phase.Settling) {
            settleTimer += dt
            val settled = settleTimer >= settleTiming.minSeconds && world.isSettled()
            if (settled || settleTimer >= settleTiming.maxSeconds) {
                height = world.height().toFloat()
                nextPart(settled)
            }
        }
        shapes = world.renderShapes()
        if (phase == Phase.Placing) height = world.height().toFloat()
    }

    fun upload() {
        val tower = result?.tower ?: return
        if (!canUpload || uploading || uploaded) return
        val uploadingRunId = runId
        uploading = true
        viewModelScope.launch {
            try {
                val response = postTower(tower)
                if (runId == uploadingRunId) {
                    uploadedRunId = uploadingRunId
                    uploadMessage = "送信しました：${response.url}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (runId == uploadingRunId) uploadMessage = "送信できませんでした（${serverUrl()}）：${e.message}"
            } finally {
                if (runId == uploadingRunId) uploading = false
            }
        }
    }

    companion object {
        /**
         * 1 フレームで進める時間の上限 [s]。物理は 1/60 秒刻みで、1 回の呼び出しで 5 歩（約 0.083 秒）までしか進まない。
         * 0.05 秒（3 歩ぶん）に抑えて、いつも全部進められるようにし、待ち時間と物理の進みがずれないようにする
         */
        const val MAX_FRAME_SECONDS = 0.05

        /** ゴーストを台の端からどこまで外へ動かせるか [m] */
        const val DRAG_MARGIN_M = 0.8

        const val MESSAGE_SELECTION_MISSING = "選んだパーツはコレクションにありません。パーツを選び直してください。"
        const val MESSAGE_RETRY_MISSING = "このゲームのパーツは、すべてコレクションから削除されています。パーツを選び直してください。"
        const val MESSAGE_COLLECTION_EMPTY = "コレクションにパーツがありません。"
    }
}

/** 描く図形 1 つを囲む長方形 [m]（ワールド座標） */
private fun StackWorld.RenderShape.bounds(): WorldRect = when (this) {
    is StackWorld.RenderShape.Poly -> WorldRect(xs.min().toFloat(), xs.max().toFloat(), ys.min().toFloat(), ys.max().toFloat())
    is StackWorld.RenderShape.Disc -> WorldRect((cx - r).toFloat(), (cx + r).toFloat(), (cy - r).toFloat(), (cy + r).toFloat())
}
