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
import com.rein4ce.roadcollector.data.PartOrigin
import com.rein4ce.roadcollector.data.WalkDistance
import com.rein4ce.roadcollector.data.walkDistance
import com.rein4ce.roadcollector.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/**
 * D 担当：積み上げゲーム（通常モード）の進行。物理は :core の StackWorld、ここは「ターン」の管理。
 *
 * 通常モード（決定 62・#108）：コレクションの全部のパーツから、ランダムに 1 つずつ出す（同じパーツが何度出てもよい）。
 * 置いたら次が出て、終わりなく続く。パーツが 1 つでも台から落ちたら終わりで、塔が落ち着くのを待ってから、そのときの高さで結果を固定する。
 *
 * Placing    … 出てきた 1 個をドラッグ・回転して「置く」
 * Settling   … 落下中。塔全体が止まったら次へ（落ちたパーツがあれば終わり）。落ちたパーツは、落ちた時点で取り除いて数える
 * Finished   … パーツが落ちて終わり、塔全体が止まったのを確かめた。結果（[result]）を固定し、物理も止める
 * Unresolved … 終わったあと、上限の時間までに塔が止まらなかった（判定できない）。結果は見せるが送信はできない
 */
class StackViewModel(
    /** 送信に失敗したとき、画面に出す接続先。デモ端末で接続先の設定ミスを見分けるため */
    private val serverUrl: () -> String = { ApiClient.baseUrl },
    /** 落下待ちの時間。テストで短くできるように差し替えられる */
    private val settleTiming: SettleTiming = SettleTiming(),
    /** 次に出すパーツを選ぶ乱数。テストで出る順を決められるように差し替えられる */
    private val random: Random = Random.Default,
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
     * ゲームの結果。終わって塔が落ち着いた瞬間に 1 つにまとめて固定し、終わりの表示と送信の両方がこれを使う（以後の tick では変わらない）。
     *
     * [tower] の parts は同じパーツを 1 つにまとめ、placements は置いた数だけ並ぶ（同じパーツが何度も出るため。サーバは同じ id の
     * パーツが 2 つあると断る）。
     *
     * @property usedCount   このゲームで置いたパーツの数
     * @property fallenCount 落ちたパーツの数（置いたあとに崩れて落ちたものも含む）
     * @property settled     終わったあと、塔全体が止まったのを確かめられたか
     * @property walked      置いたパーツ（落ちたものも含む。同じパーツは 1 回）のうち、本当に歩いた道の距離
     */
    data class StackResult(
        val tower: Tower,
        val usedCount: Int,
        val fallenCount: Int,
        val settled: Boolean,
        val walked: WalkDistance = WalkDistance.NONE,
    ) {
        /** 台の上に残ったパーツの数（同じパーツを何度も置いたら、その数だけ） */
        val remainingCount: Int get() = tower.placements.size

        /** サーバに送れるか。止まったのを確かめられなかった結果と、パーツが 1 つも残らなかった結果は送らない（空のタワーはサーバが 400 で断る：#58） */
        val canUpload: Boolean get() = settled && tower.placements.isNotEmpty()
    }

    private var world = newWorld()

    /**
     * このゲームで出すパーツ（始めたときのコレクションの全部。スナップショット）。ここからランダムに 1 つずつ出す
     * （途中でコレクションのパーツを消したり名前を変えたりしても、始めたゲームには入れない。「やり直す」で取り直す）
     */
    var pool: List<Part> = emptyList()
        private set

    /** このゲームで置いたパーツ（置いた順。同じパーツが何度も入る） */
    private val placedSequence = mutableListOf<Part>()

    /** 始めたときのパーツの出どころ（「使った道」を数えるため。終わったときに、置いたパーツだけで数える） */
    private var sessionOrigins: Map<String, PartOrigin> = emptyMap()

    /** パーツが台から落ちて、このゲームは終わった（塔が落ち着くのを待っている、または終わった） */
    private var gameOver = false

    var runId: Long by mutableStateOf(0L)
        private set

    /** 置き切ったときに固定した結果（[Phase.Finished] / [Phase.Unresolved] のときだけ入っている） */
    var result: StackResult? by mutableStateOf(null)
        private set

    var startMessage: String? by mutableStateOf(null)
        private set

    var phase: Phase by mutableStateOf(Phase.Idle)
        private set
    var current: Part? by mutableStateOf(null)
        private set

    /** このゲームで置いたパーツの数 */
    var placedCount: Int by mutableStateOf(0)
        private set

    /** このゲームで、あと何回チェンジ（狙っているパーツを別のパーツに替える）できるか（[CHANGES_PER_GAME] 回から） */
    var changesLeft: Int by mutableStateOf(CHANGES_PER_GAME)
        private set

    /** いまチェンジできるか（狙っている間で、残りの回数があり、ほかに替えられるパーツがある） */
    val canChange: Boolean get() = phase == Phase.Placing && current != null && changesLeft > 0 && pool.size > 1
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
     * コレクションの通知を受け取る。読み込みが済んでいて、まだ一度も始めていなければ、コレクションの全部のパーツで始める。
     * 一覧が変わっただけではゲームを始め直さない（始めたゲームのパーツは [pool] のまま。「やり直す」で取り直す）。
     *
     * @param inventory コレクションに見えているパーツ（見本は、設定の「見本のパーツを出す」に合わせて入る）
     * @param origins   パーツの出どころ（PartRepository.origins）。終わったときに「使った道」を数えるのに使う
     */
    fun updateInventory(
        inventory: List<Part>,
        isLoaded: Boolean,
        origins: Map<String, PartOrigin> = emptyMap(),
    ) {
        if (!isLoaded) return
        if (runId == 0L && inventory.isNotEmpty()) start(inventory, origins)
    }

    /**
     * 「やり直す」「もう一度」。今のコレクションの全部のパーツで始め直す。
     * コレクションが空なら、いまのゲームはそのままにして案内を出す。
     * @param origins パーツの出どころ（[updateInventory] と同じ）
     */
    fun restart(inventory: List<Part>, origins: Map<String, PartOrigin> = emptyMap()) {
        if (phase == Phase.Idle) return
        if (inventory.isEmpty()) {
            startMessage = MESSAGE_COLLECTION_EMPTY
            return
        }
        start(inventory, origins)
    }

    fun dismissStartMessage() {
        startMessage = null
    }

    private fun start(parts: List<Part>, origins: Map<String, PartOrigin>) {
        runId++
        // 前のゲームの送信は打ち切らない（サーバ側の保存と食い違わないように）。
        // 結果は runId で照合するので、次のゲームの表示には混ざらない
        uploading = false
        result = null
        settleTimer = 0.0
        startMessage = null
        world = newWorld()
        pool = parts.distinctBy { it.id }
        sessionOrigins = origins
        placedSequence.clear()
        placedCount = 0
        changesLeft = CHANGES_PER_GAME
        gameOver = false
        height = 0f
        fallenCount = 0
        uploadMessage = null
        shapes = world.renderShapes()
        nextPart()
    }

    /**
     * 次のパーツへ進む。パーツが落ちて終わっていれば、この瞬間の結果を固定して終わる。
     * @param settled 直前のパーツのあと、塔全体が止まったのを確かめられたか（時間切れなら false）
     */
    private fun nextPart(settled: Boolean = true) {
        ghostX = 0f
        ghostAngleDeg = 0f
        if (gameOver) {
            current = null
            val used = placedSequence.distinctBy { it.id }
            result = StackResult(
                tower = Tower(
                    // 同じパーツは 1 つにまとめる（配置は置いた数だけ）。サーバは同じ id のパーツが 2 つあると断る
                    parts = world.placedParts.distinctBy { it.id },
                    placements = world.placements(),
                    height = height,
                    // みんなの絵と共有画像が、この台の幅で描く（決定 66）
                    platformWidth = PLATFORM_WIDTH_M.toFloat(),
                    createdAt = System.currentTimeMillis(),
                ),
                usedCount = placedCount,
                fallenCount = fallenCount,
                settled = settled,
                walked = walkDistance(used, sessionOrigins),
            )
            phase = if (settled) Phase.Finished else Phase.Unresolved
        } else {
            current = pool.random(random)
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

    /**
     * チェンジ：狙っているパーツを、ほかのパーツ（いまと違うもの）にランダムに替える（決定 62）。都合の悪い形が出たときのため。
     * 1 ゲームに [CHANGES_PER_GAME] 回まで（何度でも替えられると、合わない形を全部飛ばせて、積み方を考える楽しさが減るため）。
     * 狙っている位置はそのまま、向きは 0° に戻す。出てくる高さは同じだが、大きさが違うので、カメラは新しいパーツが入る所まで上げ直す
     */
    fun change() {
        val part = current ?: return
        if (!canChange) return
        current = pool.filter { it.id != part.id }.random(random)
        changesLeft--
        ghostAngleDeg = 0f
        camera = computeCamera()
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
        placedSequence += part
        placedCount++
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
     * - パーツが 1 つでも落ちたら終わり（[gameOver]）。狙っている間に塔が崩れて落ちたときも、狙っていたパーツは置かずに終わる。
     *   どちらも、塔全体が止まるのを待ってから結果を固定する（止まらなければ時間切れで [Phase.Unresolved]）
     * - 終わったあと（[Phase.Finished] / [Phase.Unresolved]）は物理を進めない。結果と画面の塔が変わらない
     */
    fun tick(dtSeconds: Double) {
        if (phase == Phase.Idle || phase == Phase.Finished || phase == Phase.Unresolved) return
        val dt = dtSeconds.coerceIn(0.0, MAX_FRAME_SECONDS)
        world.step(dt)
        val fallen = world.removeFallen()
        if (fallen.isNotEmpty()) {
            fallenCount += fallen.size
            if (!gameOver) {
                gameOver = true
                if (phase == Phase.Placing) {
                    // 狙っている間に塔が崩れて落ちた：狙っていたパーツは置かず、塔が落ち着くのを待って終わる
                    current = null
                    phase = Phase.Settling
                    settleTimer = 0.0
                }
            }
        }

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

        const val MESSAGE_COLLECTION_EMPTY = "コレクションにパーツがありません。"

        /** 1 ゲームでチェンジできる回数（[change]） */
        const val CHANGES_PER_GAME = 3

        /** 通常モードの台の幅 [m]。たくさん積めるように、1 本ずつ選んで積んでいたころ（1.4 m）の 2 倍（決定 62・#108）。画面に映る横 5 m に収まる */
        const val PLATFORM_WIDTH_M = 2.8

        private fun newWorld() = StackWorld(platformWidth = PLATFORM_WIDTH_M)
    }
}

/** 描く図形 1 つを囲む長方形 [m]（ワールド座標） */
private fun StackWorld.RenderShape.bounds(): WorldRect = when (this) {
    is StackWorld.RenderShape.Poly -> WorldRect(xs.min().toFloat(), xs.max().toFloat(), ys.min().toFloat(), ys.max().toFloat())
    is StackWorld.RenderShape.Disc -> WorldRect((cx - r).toFloat(), (cx + r).toFloat(), (cy - r).toFloat(), (cy + r).toFloat())
}
