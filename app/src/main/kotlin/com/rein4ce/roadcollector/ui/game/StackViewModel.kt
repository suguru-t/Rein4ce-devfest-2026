package com.rein4ce.roadcollector.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * D 担当：積み上げゲームの進行。物理は :core の StackWorld、ここは「ターン」の管理。
 *
 * Placing  … 手持ちの 1 個をドラッグ・回転して「置く」
 * Settling … 落下中。全部が静止したら次へ（落ちたパーツは消える）
 * Finished … 置き切った。高さ確定
 */
class StackViewModel(
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
    private var completedTower: Tower? = null
    private var uploadJob: Job? = null

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

    /** 一覧の通知は開始要求ではない。初回だけ先頭5個で始める。 */
    fun updateInventory(inventory: List<Part>, isLoaded: Boolean, request: GameSession.StartRequest?) {
        if (!isLoaded) return
        if (request != null) {
            if (handledRequestId == request.id) return
            handledRequestId = request.id
            startResolved(request.partIds, inventory)
        } else if (runId == 0L && inventory.isNotEmpty()) {
            startResolved(inventory.take(GameSession.MAX_PARTS).map { it.id }, inventory)
        }
    }

    fun restart(inventory: List<Part>, isLoaded: Boolean) {
        if (!isLoaded) return
        startResolved(sessionParts.map { it.id }, inventory)
    }

    private fun startResolved(ids: List<String>, inventory: List<Part>) {
        val byId = inventory.associateBy { it.id }
        val parts = ids.distinct().take(GameSession.MAX_PARTS).mapNotNull { byId[it] }
        if (parts.isEmpty()) {
            startMessage = "選んだパーツはコレクションにありません。パーツを選び直してください。"
            return
        }
        start(parts)
    }

    fun dismissStartMessage() {
        startMessage = null
    }

    private fun start(parts: List<Part>) {
        runId++
        uploadJob?.cancel()
        uploadJob = null
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

    fun place() {
        val part = current ?: return
        if (phase != Phase.Placing) return
        world.place(part, ghostX.toDouble(), spawnY().toDouble(), Math.toRadians(ghostAngleDeg.toDouble()))
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
        uploadJob = viewModelScope.launch {
            try {
                val response = postTower(tower)
                if (runId == uploadingRunId) {
                    uploadedRunId = uploadingRunId
                    uploadMessage = "送信しました：${response.url}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (runId == uploadingRunId) uploadMessage = "送信できませんでした：${e.message}"
            } finally {
                if (runId == uploadingRunId) uploading = false
            }
        }
    }

    companion object {
        private const val MIN_SETTLE_SECONDS = 0.8
        private const val MAX_SETTLE_SECONDS = 6.0
    }
}
