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
import kotlin.math.min

/**
 * 3分チャレンジの決まり。形・摩擦・時間の決まりを変えたら [ID] も変える（違う決まりの記録が同じランキングに混ざらないように）。
 *
 * - v2：途中の揺れ待ちは 6 秒まで
 * - v3：途中の揺れ待ちを 3 秒までにした（塔が揺れていても、すぐ次の形を積めるように。#78 のユーザーの依頼）
 * - v4：パーツの回転の減衰を 0.3 から 3.0 にした（着地で跳ねすぎないように。#110・決定 63）。積み方が変わるので、v3 までの記録とは混ぜない
 */
object ChallengeRules {
    const val ID = "manual-180-v4"
    const val DURATION_NS = 180_000_000_000L

    /** 置いてから、止まっていても次へ進まない時間 [s]（置いた直後の一瞬の静止を拾わないため） */
    const val MIN_SETTLE_S = 0.8

    /** 途中の揺れ待ちの上限 [s]。揺れていても、これだけ待ったら次の形へ進む */
    const val SETTLE_WAIT_S = 3.0

    /** 締切のあと、塔が止まるのを待つ上限 [s]。止まらなければ記録に残さない（浮いている高さを記録しない。#41） */
    const val FINAL_WAIT_S = 6.0

    /**
     * 「自動で落ちる」（A 案）の決まりの ID。自分で落とす（[ID]）とは記録を混ぜない（#45・決定 65）。
     * v2：「置く」のボタンをやめ、下がるのを待たずにすぐ落とすことができなくなった（3 分で積める数が変わる。決定 67）
     */
    const val AUTO_ID = "auto-180-v2"

    /** 「自動で落ちる」で、狙っている形が下がる速さ [m/s]。出てくる高さ（塔の上 1.3 m）から塔に着くまで 2〜3 秒 */
    const val AUTO_FALL_SPEED_M_S = 0.4

    val parts = listOf(
        Part("bar", listOf(Pt(-.5f, 0f), Pt(.5f, 0f)), kind = Kind.BAR, name = "棒"),
        Part("l", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f)), kind = Kind.L, name = "L字"),
        Part("u", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f), Pt(.5f, .5f)), kind = Kind.U, name = "U字"),
        Part("ring", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f), Pt(.5f, .5f)), closed = true, kind = Kind.RING, name = "輪"),
        Part("zigzag", listOf(Pt(-.5f, .5f), Pt(.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f)), kind = Kind.ZIGZAG, name = "ジグザグ"),
    )
}

/**
 * 3分チャレンジの遊び方（#45）。決まりが違うので、記録は [ruleId] で分けて混ぜない
 */
enum class ChallengeStyle(val ruleId: String) {
    /** B 案：狙っている間、形は出てきた高さで止まっていて、「置く」で落とす */
    MANUAL(ChallengeRules.ID),

    /**
     * A 案：狙っている間も、形が [ChallengeRules.AUTO_FALL_SPEED_M_S] で下がっていき、塔か台に触れたら、その位置から物理で落ちる。
     * 下がっている間に ◀▶ と回転で合わせる（塔や台に重なる向き・位置には動かせない）。「置く」を押すと、その場ですぐ落とす（決定 65）
     */
    AUTO(ChallengeRules.AUTO_ID),
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
 * 縮尺は変えずに視点を上げる（画面は通常モードと同じ StackViewport.follow）。次の形に進むときと、狙っている間に塔が崩れて
 * 低くなったときだけ変わるので、ふだん狙っている間と落下中はカメラが動かない
 */
class ChallengeRun(
    val seed: Long,
    val style: ChallengeStyle = ChallengeStyle.MANUAL,
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

    /** いま狙っている形の高さ [m]。自分で落とすときは、いつも [spawnY]。自動で落ちるときは、ここから下がっていく */
    var ghostY = spawnY; private set

    /** 止まった塔の高さ [m]。次の形に進むときと、終わったときに測り直す（画面の「模型の高さ」）。狙っている間に崩れて低くなったら下げる */
    var towerHeight = 0.0; private set

    /** 画面に入れたいいちばん上 [m]。次の形に進むときと、狙っている間に塔が崩れて低くなったときだけ変わる */
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
        if (phase == Phase.AIMING) lowerToTower()
        if (style == ChallengeStyle.AUTO && phase == Phase.AIMING && remainingNs > 0) descend(dt)
        if (phase == Phase.FALLING || phase == Phase.FINALIZING) {
            val waitDelta = if (crossedDeadline) (elapsed - beforeDeadline) / 1e9 else elapsed / 1e9
            settling += if (crossedDeadline) minOf(dt, waitDelta) else dt
            settleWait += waitDelta
            if (settling >= ChallengeRules.MIN_SETTLE_S && world.isSettled()) {
                if (phase == Phase.FINALIZING) {
                    towerHeight = world.height()
                    score = ChallengeScore(runId, seed, towerHeight, wallClock(), style.ruleId)
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

    /**
     * 狙っている間に塔が崩れて低くなったら、塔の高さ・次の形が出てくる高さ・見せたい上を、崩れたあとの高さまで下げる。
     * 揺れ待ちの上限（[ChallengeRules.SETTLE_WAIT_S]）で、崩れている途中に次の形へ進むと、崩れる前の高さで決まってしまい、
     * 何もない空を見ながら狙うことになっていた（#78 の suguru-t のレビュー）。高さは崩れたときにしか下がらないので、下げる向きだけ見る。
     * 細かい揺れで毎コマ変わらないよう、[LOWER_STEP_M] 以上下がったときだけ
     */
    private fun lowerToTower() {
        val height = world.height()
        if (height > towerHeight - LOWER_STEP_M) return
        towerHeight = height
        spawnY = towerHeight + SPAWN_ABOVE_M
        // 自動で落ちる形は、下がっている途中の高さのまま（出てくる高さより上にはしない）
        ghostY = if (style == ChallengeStyle.AUTO) min(ghostY, spawnY) else spawnY
        cameraTop = spawnY + reachOf(current)
    }

    private fun nextPiece() {
        current = next
        next = pieces.next()
        x = 0.0
        angle = 0.0
        towerHeight = world.height()
        spawnY = towerHeight + SPAWN_ABOVE_M
        ghostY = spawnY
        cameraTop = spawnY + reachOf(current)
        phase = Phase.AIMING
    }

    private fun canAct(): Boolean {
        tick()
        return active && phase == Phase.AIMING && remainingNs > 0
    }

    fun move(dx: Double) {
        if (!canAct()) return
        val nextX = (x + dx).coerceIn(-MOVE_LIMIT_M, MOVE_LIMIT_M)
        if (!blocked(nextX, angle)) x = nextX
    }

    fun rotate(degrees: Double) {
        if (!canAct()) return
        val nextAngle = (((angle + degrees) % 360) + 360) % 360
        if (!blocked(x, nextAngle)) angle = nextAngle
    }

    /** 自動で落ちる形を、塔や台に重なる位置・向きには動かさない（自分で落とすときは、出てくる高さで重ならないので見ない） */
    private fun blocked(x: Double, angle: Double): Boolean =
        style == ChallengeStyle.AUTO && world.overlaps(current, x, ghostY, Math.toRadians(angle))

    /**
     * 自動で落ちる形を、[dt] 秒ぶん下げる。下げた位置で塔か台に重なるなら、今の位置から物理で落とす（触れた）。
     * 台の外を下がって、台の下まで行ってしまったときも、そこで落とす（落ちて数えられる）
     */
    private fun descend(dt: Double) {
        val nextY = ghostY - ChallengeRules.AUTO_FALL_SPEED_M_S * dt
        when {
            world.overlaps(current, x, nextY, Math.toRadians(angle)) -> drop()
            nextY < LOST_BELOW_M -> {
                ghostY = nextY
                drop()
            }
            else -> ghostY = nextY
        }
    }

    /** 今の位置（[ghostY]）から物理で落とす。自動で落ちるときは、下がっていた速さのまま落ち始める */
    private fun drop() {
        val body = world.place(current, x, ghostY, Math.toRadians(angle))
        if (style == ChallengeStyle.AUTO) body.linearVelocity.y = -ChallengeRules.AUTO_FALL_SPEED_M_S
        phase = Phase.FALLING
        settling = 0.0
        settleWait = 0.0
    }

    /** 「置く」：今の位置から落とす（自動で落ちるときは、下がるのを待たずにすぐ落とす） */
    fun place(): Boolean {
        if (!canAct()) return false
        drop()
        return true
    }

    companion object {
        /** 1 コマで進める物理の時間の上限 [s]（通常モードと同じ） */
        const val MAX_FRAME_S = 0.05

        /** 次の形を出す高さ（塔の上から）[m] */
        const val SPAWN_ABOVE_M = 1.3

        /** 狙っている間に塔が崩れたとき、出てくる高さを下げ直す、いちばん小さい下がり幅 [m] */
        const val LOWER_STEP_M = 0.05

        /** 自動で落ちる形が、塔にも台にも触れずにここまで下がったら（台の外を下がった）、そこで落とす [m] */
        const val LOST_BELOW_M = -1.0

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
