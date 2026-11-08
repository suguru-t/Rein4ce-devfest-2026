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
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * D 担当：積み上げゲームの進行。物理は :core の StackWorld、ここは「ターン」の管理。
 *
 * Placing  … 手持ちの 1 個をドラッグ・回転して「置く」
 * Settling … 落下中。全部が静止したら次へ（落ちたパーツは消える）
 * Finished … 置き切った。高さ確定
 */
class StackViewModel(
    /** 送信に失敗したとき、画面に出す接続先。デモ端末で接続先の設定ミスを見分けるため */
    private val serverUrl: () -> String = { ApiClient.baseUrl },
    private val postTower: suspend (Tower) -> TowerCreated = { ApiClient.postTower(it) },
) : ViewModel() {

    enum class Phase { Idle, Placing, Settling, Finished }

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
    private var completedTower: Tower? = null

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
    val uploaded: Boolean get() = completedTower != null && uploadedRunId == runId

    private var settleTimer = 0.0

    val platformWidth: Double get() = world.platformWidth

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
        completedTower = null
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

    private fun nextPart() {
        current = queue.firstOrNull()
        queue = queue.drop(1)
        remaining = queue.size
        ghostX = 0f
        ghostAngleDeg = 0f
        if (current == null) {
            phase = Phase.Finished
            completedTower = Tower(
                parts = world.placedParts,
                placements = world.placements(),
                height = height,
                createdAt = System.currentTimeMillis(),
            )
        } else {
            phase = Phase.Placing
        }
    }

    fun dragBy(dxMeters: Float) {
        if (phase != Phase.Placing) return
        val limit = (platformWidth / 2 + 0.8).toFloat()
        ghostX = (ghostX + dxMeters).coerceIn(-limit, limit)
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

    /** 毎フレーム呼ぶ。 */
    fun tick(dtSeconds: Double) {
        if (phase == Phase.Idle) return
        world.step(dtSeconds)

        if (phase == Phase.Settling) {
            settleTimer += dtSeconds
            val settled = settleTimer > MIN_SETTLE_SECONDS && world.isSettled()
            if (settled || settleTimer > MAX_SETTLE_SECONDS) {
                val fallen = world.removeFallen()
                fallenCount += fallen.size
                height = world.height().toFloat()
                nextPart()
            }
        }
        shapes = world.renderShapes()
        if (phase != Phase.Settling) height = world.height().toFloat()
    }

    fun upload() {
        val tower = completedTower ?: return
        if (phase != Phase.Finished || uploading || uploaded) return
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
        private const val MIN_SETTLE_SECONDS = 0.8
        private const val MAX_SETTLE_SECONDS = 6.0

        const val MESSAGE_SELECTION_MISSING = "選んだパーツはコレクションにありません。パーツを選び直してください。"
        const val MESSAGE_RETRY_MISSING = "このゲームのパーツは、すべてコレクションから削除されています。パーツを選び直してください。"
        const val MESSAGE_COLLECTION_EMPTY = "コレクションにパーツがありません。"
    }
}
