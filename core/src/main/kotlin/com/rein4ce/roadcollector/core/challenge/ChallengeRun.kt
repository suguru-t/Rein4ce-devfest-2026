package com.rein4ce.roadcollector.core.challenge

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.StackWorld
import kotlinx.serialization.Serializable
import java.util.Collections
import java.util.Random
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max

/**
 * 3分チャレンジの決まり。形・摩擦・時間の決まりを変えたら [ID] も変える（違う決まりの記録が同じランキングに混ざらないように）。
 *
 * - v2：途中の揺れ待ちは 6 秒まで
 * - v3：途中の揺れ待ちを 3 秒までにした（塔が揺れていても、すぐ次の形を積めるように。#78 のユーザーの依頼）
 */
object ChallengeRules {
    const val ID = "manual-180-v3"
    const val DURATION_NS = 180_000_000_000L

    /** 置いてから、止まっていても次へ進まない時間 [s]（置いた直後の一瞬の静止を拾わないため） */
    const val MIN_SETTLE_S = 0.8

    /** 途中の揺れ待ちの上限 [s]。揺れていても、これだけ待ったら次の形へ進む */
    const val SETTLE_WAIT_S = 3.0

    /** 締切のあと、塔が止まるのを待つ上限 [s]。止まらなければ記録に残さない（浮いている高さを記録しない。#41） */
    const val FINAL_WAIT_S = 6.0

    val parts = listOf(
        Part("bar", listOf(Pt(-.5f, 0f), Pt(.5f, 0f)), kind = Kind.BAR, name = "棒"),
        Part("l", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f)), kind = Kind.L, name = "L字"),
        Part("u", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f), Pt(.5f, .5f)), kind = Kind.U, name = "U字"),
        Part("ring", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f), Pt(.5f, .5f)), closed = true, kind = Kind.RING, name = "輪"),
        Part("zigzag", listOf(Pt(-.5f, .5f), Pt(.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f)), kind = Kind.ZIGZAG, name = "ジグザグ"),
    )
}

/** 5 種類を 1 回ずつ入れた袋から順に出す。種が同じなら同じ順番になる（java.util.Random） */
class ChallengePieces(seed: Long) {
    private val random = Random(seed)
    private val bag = ArrayDeque<Part>()
    private var index = 0

    fun next(): Part {
        if (bag.isEmpty()) {
            val batch = ChallengeRules.parts.toMutableList()
            Collections.shuffle(batch, random)
            bag.addAll(batch)
        }
        val part = bag.removeFirst()
        return part.copy(id = "${part.id}-${index++}")
    }
}

/**
 * 1 回の記録。[rule] は必ず書き出す（既定値にすると、保存のときに省かれ、読むときに今の決まりに化けてしまうため。#78 のレビュー）
 */
@Serializable
data class ChallengeScore(
    val runId: String,
    val seed: Long,
    val height: Double,
    val createdAt: Long,
    val rule: String,
)

/**
 * 3分チャレンジの 1 回。時計はアプリが前に出ている間の経過時間（単調時計）で、端末の時刻や物理の時間の上限とは別に数える。
 *
 * カメラ：見せたいいちばん上（[cameraTop]、次の形が出てくる高さ＋どう回しても入る半径）が入るところまで、
 * 縮尺は変えずに視点を上げる（画面は通常モードと同じ StackViewport.follow）。次の形に進むときだけ変わるので、
 * 狙っている間と落下中はカメラが動かない
 */
class ChallengeRun(
    val seed: Long,
    val runId: String = UUID.randomUUID().toString(),
    private val clock: () -> Long = System::nanoTime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val world: StackWorld = StackWorld(),
) {
    enum class Phase { AIMING, FALLING, FINALIZING, FINISHED, UNRESOLVED }

    private val pieces = ChallengePieces(seed)
    var phase = Phase.AIMING; private set
    var current = pieces.next(); private set
    var next = pieces.next(); private set
    var x = 0.0; private set
    var angle = 0.0; private set
    var remainingNs = ChallengeRules.DURATION_NS; private set
    var score: ChallengeScore? = null; private set
    var shapes = world.renderShapes(); private set

    /** 次の形が出てくる高さ [m]（塔の上 [SPAWN_ABOVE_M]） */
    var spawnY = SPAWN_ABOVE_M; private set

    /** 止まった塔の高さ [m]。次の形に進むときと、終わったときに測り直す（画面の「模型の高さ」） */
    var towerHeight = 0.0; private set

    /** 画面に入れたいいちばん上 [m]。次の形に進むときだけ変わる */
    var cameraTop = spawnY + reachOf(current); private set

    private var active = false
    private var last = 0L
    private var settling = 0.0
    private var settleWait = 0.0
    val terminal get() = phase == Phase.FINISHED || phase == Phase.UNRESOLVED

    fun resume() {
        if (!active && !terminal) {
            active = true
            last = clock()
        }
    }

    fun pause() {
        if (active) tick()
        active = false
    }

    fun tick() {
        if (!active || terminal) return
        val now = clock()
        val elapsed = (now - last).coerceAtLeast(0)
        last = now
        val wasFinalizing = phase == Phase.FINALIZING
        val beforeDeadline = remainingNs
        remainingNs = (remainingNs - elapsed).coerceAtLeast(0)
        val crossedDeadline = remainingNs == 0L && !wasFinalizing
        if (crossedDeadline) {
            phase = Phase.FINALIZING
            settling = 0.0
            settleWait = 0.0
        }
        val dt = (elapsed / 1e9).coerceAtMost(MAX_FRAME_S)
        world.step(dt)
        world.removeFallen()
        shapes = world.renderShapes()
        if (phase == Phase.FALLING || phase == Phase.FINALIZING) {
            val waitDelta = if (crossedDeadline) (elapsed - beforeDeadline) / 1e9 else elapsed / 1e9
            settling += if (crossedDeadline) minOf(dt, waitDelta) else dt
            settleWait += waitDelta
            if (settling >= ChallengeRules.MIN_SETTLE_S && world.isSettled()) {
                if (phase == Phase.FINALIZING) {
                    towerHeight = world.height()
                    score = ChallengeScore(runId, seed, towerHeight, wallClock(), ChallengeRules.ID)
                    phase = Phase.FINISHED
                } else {
                    nextPiece()
                }
            } else if (phase == Phase.FINALIZING && settleWait >= ChallengeRules.FINAL_WAIT_S) {
                // 記録に残すのは、締切のあとに止まった塔の高さだけ。浮いている途中の高さは記録しない（#41）
                towerHeight = world.height()
                phase = Phase.UNRESOLVED
            } else if (phase == Phase.FALLING && settleWait >= ChallengeRules.SETTLE_WAIT_S) {
                // 途中は、塔が揺れていても 3 秒で次の形へ（揺れが収まるのを待ち続けて、3 分を無駄にしないように）
                nextPiece()
            }
        }
    }

    private fun nextPiece() {
        current = next
        next = pieces.next()
        x = 0.0
        angle = 0.0
        towerHeight = world.height()
        spawnY = towerHeight + SPAWN_ABOVE_M
        cameraTop = spawnY + reachOf(current)
        phase = Phase.AIMING
    }

    private fun canAct(): Boolean {
        tick()
        return active && phase == Phase.AIMING && remainingNs > 0
    }

    fun move(dx: Double) {
        if (canAct()) x = (x + dx).coerceIn(-MOVE_LIMIT_M, MOVE_LIMIT_M)
    }

    fun rotate(degrees: Double) {
        if (canAct()) angle = (((angle + degrees) % 360) + 360) % 360
    }

    fun place(): Boolean {
        if (!canAct()) return false
        world.place(current, x, spawnY, Math.toRadians(angle))
        phase = Phase.FALLING
        settling = 0.0
        settleWait = 0.0
        return true
    }

    companion object {
        /** 1 コマで進める物理の時間の上限 [s]（通常モードと同じ） */
        const val MAX_FRAME_S = 0.05

        /** 次の形を出す高さ（塔の上から）[m] */
        const val SPAWN_ABOVE_M = 1.3

        /** 左右に動かせる範囲 [m] */
        const val MOVE_LIMIT_M = 1.5

        /** 形の原点から、いちばん遠い点までの距離 [m]。どう回しても、この半径の円に収まる（チャレンジの形は大きさ 1 倍） */
        fun reachOf(part: Part): Double {
            var reach = 0.0
            for (poly in PartGeometry.shapes(part)) {
                for (v in poly.points) reach = max(reach, hypot(v.x, v.y))
            }
            return reach
        }
    }
}
