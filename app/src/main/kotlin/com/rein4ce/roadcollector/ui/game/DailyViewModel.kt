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
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.DailyTowerStore
import java.time.LocalDate
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.launch

/**
 * 「毎日 1 つずつ積む」の進行（決定 61・#106）。
 *
 * - 開くと、保存した塔（[DailyTowerStore]）を、止まったときの位置・向きのまま組み直す（動かさずに置く）
 * - その日にまだ積んでいなければ、今日歩いた道から 1 つ選び（[choose]）、ふつうの積む画面と同じく横に動かし・回して「置く」
 * - 止まったら（または 6 秒たったら）、全部のパーツの位置を保存する。台から落ちたパーツは塔から外れる。
 *   今日のパーツが落ちたときは、今日はまだ積んでいないことになり、もう一度選べる
 * - 日の変わり目は端末の時刻の 0 時（[today]）。積まなかった日は何もしない（塔はそのまま）
 *
 * @param today 今日の日付（"2026-11-10"）。テストで差し替える
 */
class DailyViewModel(
    private val store: DailyTowerStore,
    private val today: () -> String = { LocalDate.now().toString() },
) : ViewModel() {

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

    /** さっき積んだ今日のパーツが、台から落ちて塔から外れた（画面に一言出す。今日の道をもう一度選べる） */
    var lastFell by mutableStateOf(false)
        private set

    /** 塔を保存できなかった（画面に一言出す） */
    var saveFailed by mutableStateOf(false)
        private set

    private var world = StackWorld()
    private var settleTimer = 0.0

    /** 今日もう積んだか（今日のパーツが塔に残っているか） */
    val placedToday: Boolean get() = tower.placedOn(day)

    init {
        viewModelScope.launch {
            tower = store.load()
            rebuild()
            phase = Phase.Viewing
        }
    }

    fun refreshDay() {
        day = today()
    }

    /** 保存した塔を、止まったときの位置・向きで組み直す。置いたまま止めておく（動き出して崩れないように） */
    private fun rebuild() {
        world = StackWorld()
        for (block in tower.blocks) {
            val p = block.placement
            world.place(block.part, p.x.toDouble(), p.y.toDouble(), p.angleRad.toDouble(), p.scale.toDouble(), block.grip).setAtRest(true)
        }
        shapes = world.renderShapes()
        height = world.height().toFloat()
        camera = overview()
    }

    /** 今日の道から 1 つ選んで、狙い始める */
    fun choose(part: Part, grip: Grip) {
        if (phase != Phase.Viewing || placedToday) return
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

    /** 止まった（か待ちの上限）。全部のパーツの位置を保存して、塔を見る画面に戻る */
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
        // 次に開いたときと同じ形で見せる（止まったまま置き直す）
        rebuild()
        viewModelScope.launch {
            saveFailed = runCatching { store.save(next) }.isFailure
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
        const val SPAWN_ABOVE_M = 1.5f
        const val DRAG_MARGIN_M = 0.8
        const val MAX_FRAME_SECONDS = 0.05
        const val SETTLE_MIN_SECONDS = 0.8
        const val SETTLE_MAX_SECONDS = 6.0
    }
}
