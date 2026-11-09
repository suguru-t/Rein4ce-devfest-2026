package com.rein4ce.roadcollector.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rein4ce.roadcollector.core.daily.DailyBlock
import com.rein4ce.roadcollector.core.daily.DailyTower
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.DailyTowerStore
import java.time.LocalDate
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 「毎日 1 つずつ積む」の進行（決定 61・#106）。
 *
 * - 開くと、保存した塔（[DailyTowerStore]）を、止まったときの位置・向きのまま組み直し、**固める**（決定 70。
 *   今日のパーツが当たっても、昨日までの塔は動かず、落ちもしない。StackWorld.freeze）
 * - その日にまだ積んでいなければ、今日歩いた道から 1 つ選び（[choose]）、ふつうの積む画面と同じく横に動かし・回して「置く」
 * - 止まったら（または 6 秒たったら）、全部のパーツの位置を保存する。
 *   今日のパーツが台から落ちたときは、同じパーツで、そのまま狙い直す（置き直し。位置と向きもそのまま）。
 *   置き直しは 1 日 [DailyTower.MAX_RETRIES] 回まで（落ちた回数は塔と一緒に保存する）。使い切ったら、その日はもう積めない
 * - 日の変わり目は端末の時刻の 0 時（[today]）。積まなかった日は何もしない（塔はそのまま）
 * - 台は通常と同じ 2.8 m（決定 70。3 つのモードでそろえる。前は 1.4 m）
 *
 * @param today 今日の日付（"2026-11-10"）。テストで差し替える
 */
class DailyViewModel(
    private val store: DailyTowerStore,
    private val today: () -> String = { LocalDate.now().toString() },
    /**
     * 塔を「みんな」の毎日 1 つずつ積むのランキングに送る（決定 73。画面からは ApiClient.putDailyTower。サーバは持ち主ごとに 1 基を新しくする）。
     * 積んで止まったら自動で送る。前に送れなかった形があれば、開いたときにも送る。null なら送らない（テスト）
     */
    private val sendTower: (suspend (Tower) -> TowerCreated)? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** 「みんな」に送ったようす（決定 73） */
    enum class SendState { IDLE, SENDING, SENT, FAILED }

    var sendState by mutableStateOf(SendState.IDLE)
        private set

    enum class Phase { Loading, Viewing, Aiming, Settling }

    var phase by mutableStateOf(Phase.Loading)
        private set

    var tower by mutableStateOf(DailyTower())
        private set

    /** 今日の日付。画面に戻るたびに見直す（[refreshDay]。0 時をまたいで開いたままでも、次の日の 1 つを積めるように） */
    var day by mutableStateOf(today())
        private set

    /** 狙っているパーツ（選んでいなければ null） */
    var current by mutableStateOf<Part?>(null)
        private set
    private var currentGrip = Grip.NORMAL

    var ghostX by mutableFloatStateOf(0f)
        private set
    var ghostAngleDeg by mutableFloatStateOf(0f)
        private set

    /** 塔の図形。キャンバスの描画の中だけで読む */
    var shapes by mutableStateOf<List<StackWorld.RenderShape>>(emptyList())
        private set

    /** 今の塔の高さ [m] */
    var height by mutableFloatStateOf(0f)
        private set

    var camera by mutableStateOf<CameraTarget>(CameraTarget.Follow(0f))
        private set

    /** さっき積んだ今日のパーツが、台から落ちた（画面に一言出す。置き直しが残っていれば、同じパーツで狙い直している） */
    var lastFell by mutableStateOf(false)
        private set

    /** 塔を保存できなかった（画面に一言出す） */
    var saveFailed by mutableStateOf(false)
        private set

    private var world = newWorld()
    private var settleTimer = 0.0

    /** 今日もう積んだか（今日のパーツが塔に残っているか） */
    val placedToday: Boolean get() = tower.placedOn(day)

    /** 今日まだ積めるか（まだ積んでいなくて、置き直しを使い切っていない。決定 70） */
    val canPlaceToday: Boolean get() = tower.canPlaceOn(day)

    /** 今日のパーツが台から落ちた回数（置き直している間は、今が何回目の置き直しか） */
    val fallsToday: Int get() = tower.fallsOn(day)

    init {
        viewModelScope.launch {
            tower = store.load()
            rebuild()
            phase = Phase.Viewing
            // 前に送れなかった形（送る前にアプリを閉じた・つながらなかった）があれば送る
            sendIfNeeded()
        }
    }

    /**
     * まだ「みんな」に送っていない形があれば送る（決定 73）。送れたら、送った日（[DailyTower.sentDay]）を塔と一緒に保存する。
     * 送れなかったら [SendState.FAILED]（画面の「もう一度送る」か、次に開いたときに送り直す）
     */
    fun sendIfNeeded() {
        val upload = sendTower ?: return
        val current = tower
        if (!current.needsSend || sendState == SendState.SENDING) return
        val day = current.lastDay ?: return
        sendState = SendState.SENDING
        viewModelScope.launch {
            val ok = try {
                upload(current.toTower(platformWidth = StackViewModel.PLATFORM_WIDTH_M, createdAt = now()))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) {
                // 送っている間に塔が変わっていても、送れた日だけを書き足す（次に積んだ形は、その日がまた違うので送り直す）
                val sent = tower.copy(sentDay = day)
                tower = sent
                runCatching { store.save(sent) }
            }
            sendState = if (ok) SendState.SENT else SendState.FAILED
            if (ok && tower.needsSend) sendIfNeeded()
        }
    }

    fun refreshDay() {
        day = today()
    }

    /**
     * 保存した塔を、止まったときの位置・向きで組み直し、固める（決定 70）。今日のパーツが当たっても動かず、落ちもしない
     * （前は止めておくだけで、当たると動き出して、昨日までのパーツが落ちることがあった）
     */
    private fun rebuild() {
        world = newWorld()
        for (block in tower.blocks) {
            val p = block.placement
            world.freeze(world.place(block.part, p.x.toDouble(), p.y.toDouble(), p.angleRad.toDouble(), p.scale.toDouble(), block.grip))
        }
        shapes = world.renderShapes()
        height = world.height().toFloat()
        camera = overview()
    }

    /** 今日の道から 1 つ選んで、狙い始める */
    fun choose(part: Part, grip: Grip) {
        if (phase != Phase.Viewing || !canPlaceToday) return
        current = part
        lastFell = false
        currentGrip = grip
        ghostX = 0f
        ghostAngleDeg = 0f
        phase = Phase.Aiming
        camera = CameraTarget.Follow(top = spawnY() + reachOf(part))
    }

    /** 選び直す（狙うのをやめて、今日の道の一覧に戻る） */
    fun cancelChoice() {
        if (phase != Phase.Aiming) return
        current = null
        phase = Phase.Viewing
        camera = overview()
    }

    fun dragBy(dxMeters: Float) {
        if (phase != Phase.Aiming) return
        // 台の端より少し外まで（ふつうの積む画面と同じ）
        val limit = (world.platformWidth / 2 + DRAG_MARGIN_M).toFloat()
        ghostX = (ghostX + dxMeters).coerceIn(-limit, limit)
    }

    fun rotate(deltaDeg: Float) {
        if (phase != Phase.Aiming) return
        ghostAngleDeg = (ghostAngleDeg + deltaDeg + 360f) % 360f
    }

    fun spawnY(): Float = height + SPAWN_ABOVE_M

    fun place() {
        val part = current ?: return
        if (phase != Phase.Aiming) return
        world.place(
            part,
            ghostX.toDouble(),
            spawnY().toDouble(),
            Math.toRadians(ghostAngleDeg.toDouble()),
            scale = PartSize.of(part.distanceM),
            grip = currentGrip,
        )
        phase = Phase.Settling
        settleTimer = 0.0
    }

    /** 毎フレーム呼ぶ（落ちている間だけ物理を進める） */
    fun tick(dtSeconds: Double) {
        if (phase != Phase.Settling) return
        val dt = dtSeconds.coerceIn(0.0, MAX_FRAME_SECONDS)
        world.step(dt)
        world.removeFallen()
        settleTimer += dt
        shapes = world.renderShapes()
        if ((settleTimer >= SETTLE_MIN_SECONDS && world.isSettled()) || settleTimer >= SETTLE_MAX_SECONDS) finish()
    }

    /**
     * 止まった（か待ちの上限）。全部のパーツの位置を保存して、塔を見る画面に戻る。
     * 今日のパーツが落ちて、置き直しが残っていれば、同じパーツで狙い直す（位置と向きはそのまま。決定 70）
     */
    private fun finish() {
        val part = current ?: return
        val placements = world.placements()
        val added = DailyBlock(
            part = part,
            placement = placements.firstOrNull { it.partId == part.id }
                ?: Placement(part.id, ghostX, spawnY(), Math.toRadians(ghostAngleDeg.toDouble()).toFloat(), PartSize.of(part.distanceM).toFloat()),
            grip = currentGrip,
            day = day,
        )
        height = world.height().toFloat()
        val next = tower.settledWith(added, placements, height.toDouble())
        lastFell = placements.none { it.partId == part.id }
        tower = next
        current = null
        phase = Phase.Viewing
        // 次に開いたときと同じ形で見せる（止まった形のまま固める）
        rebuild()
        if (lastFell && next.canPlaceOn(day)) {
            // 置き直し：同じパーツ・同じすべりやすさで、もう一度狙う（ghostX と ghostAngleDeg はそのまま）
            current = part
            phase = Phase.Aiming
            camera = CameraTarget.Follow(top = spawnY() + reachOf(part))
        }
        viewModelScope.launch {
            saveFailed = runCatching { store.save(next) }.isFailure
            // 今日のパーツが残ったら、新しい形を「みんな」に送る（決定 73）
            if (!lastFell) sendIfNeeded()
        }
    }

    /** 塔全体と台が入るように引いたカメラ（ふつうの積む画面の、置き切ったときと同じ） */
    private fun overview(): CameraTarget {
        val halfWidth = (world.platformWidth / 2).toFloat()
        val bottom = -world.platformThickness.toFloat()
        var focus = WorldRect(-halfWidth, halfWidth, bottom, 0f)
        for (shape in world.renderShapes()) {
            val bounds = when (shape) {
                is StackWorld.RenderShape.Poly -> WorldRect(shape.xs.min().toFloat(), shape.xs.max().toFloat(), shape.ys.min().toFloat(), shape.ys.max().toFloat())
                is StackWorld.RenderShape.Disc -> WorldRect((shape.cx - shape.r).toFloat(), (shape.cx + shape.r).toFloat(), (shape.cy - shape.r).toFloat(), (shape.cy + shape.r).toFloat())
            }
            if (shape.part != null && bounds.maxY >= bottom) focus = focus.union(bounds)
        }
        return CameraTarget.Overview(focus)
    }

    /** どう回しても入る半径 [m]（置くときと同じ大きさで測る） */
    private fun reachOf(part: Part): Float {
        var reach = 0.0
        for (poly in PartGeometry.shapes(part, PartSize.of(part.distanceM))) {
            for (v in poly.points) reach = max(reach, hypot(v.x, v.y))
        }
        return reach.toFloat()
    }

    companion object {
        /** 台は通常（[StackViewModel.PLATFORM_WIDTH_M]）と同じ 2.8 m（決定 70。前は StackWorld の既定の 1.4 m） */
        private fun newWorld() = StackWorld(platformWidth = StackViewModel.PLATFORM_WIDTH_M)

        const val SPAWN_ABOVE_M = 1.5f
        const val DRAG_MARGIN_M = 0.8
        const val MAX_FRAME_SECONDS = 0.05
        const val SETTLE_MIN_SECONDS = 0.8
        const val SETTLE_MAX_SECONDS = 6.0
    }
}
