package com.rein4ce.roadcollector.core.challenge

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.physics.StackWorld
import java.util.Collections
import java.util.Random
import java.util.UUID
import kotlinx.serialization.Serializable

/** Fixed, versioned catalogue; changes to shapes, friction or timing require a new rule id. */
object ChallengeRules {
    const val ID = "manual-180-v1"
    const val DURATION_NS = 180_000_000_000L
    val parts = listOf(
        Part("bar", listOf(Pt(-.5f, 0f), Pt(.5f, 0f)), kind = Kind.BAR, name = "棒"),
        Part("l", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f)), kind = Kind.L, name = "L字"),
        Part("u", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f), Pt(.5f, .5f)), kind = Kind.U, name = "U字"),
        Part("ring", listOf(Pt(-.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f), Pt(.5f, .5f)), closed = true, kind = Kind.RING, name = "輪"),
        Part("zigzag", listOf(Pt(-.5f, .5f), Pt(.5f, .5f), Pt(-.5f, -.5f), Pt(.5f, -.5f)), kind = Kind.ZIGZAG, name = "ジグザグ"),
    )
}

/** Each bag contains all five shapes once. java.util.Random keeps seeded sequences reproducible. */
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

@Serializable
data class ChallengeScore(
    val runId: String, val seed: Long, val height: Double, val createdAt: Long,
    val rule: String = ChallengeRules.ID,
)

/** Foreground monotonic time is independent of wall time and of the physics step cap. */
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
    var focusHeight = 2.2; private set
    var spawnY = 1.3; private set
    private var active = false
    private var last = 0L
    private var settling = 0.0
    val terminal get() = phase == Phase.FINISHED || phase == Phase.UNRESOLVED

    fun resume() { if (!active && !terminal) { active = true; last = clock() } }
    fun pause() { if (active) tick(); active = false }
    fun tick() {
        if (!active || terminal) return
        val now = clock()
        val elapsed = (now - last).coerceAtLeast(0)
        last = now
        val wasFinalizing = phase == Phase.FINALIZING
        remainingNs = (remainingNs - elapsed).coerceAtLeast(0)
        if (remainingNs == 0L && !wasFinalizing) { phase = Phase.FINALIZING; settling = 0.0 }
        val dt = (elapsed / 1e9).coerceAtMost(.05)
        world.step(dt)
        world.removeFallen()
        shapes = world.renderShapes()
        if (phase == Phase.FALLING || phase == Phase.FINALIZING) {
            settling += dt
            if (settling >= .8 && world.isSettled()) {
                if (phase == Phase.FINALIZING) {
                    score = ChallengeScore(runId, seed, world.height(), wallClock())
                    phase = Phase.FINISHED
                } else {
                    current = next; next = pieces.next()
                    x = 0.0; angle = 0.0
                    spawnY = world.height() + 1.3
                    focusHeight = spawnY + .9
                    phase = Phase.AIMING
                }
            } else if (settling >= 6.0) {
                // Never rank an airborne snapshot as a settled tower (#41).
                phase = Phase.UNRESOLVED
            }
        }
    }
    private fun canAct(): Boolean { tick(); return active && phase == Phase.AIMING && remainingNs > 0 }
    fun move(dx: Double) { if (canAct()) x = (x + dx).coerceIn(-1.5, 1.5) }
    fun rotate(degrees: Double) { if (canAct()) angle = (angle + degrees) % 360 }
    fun place(): Boolean {
        if (!canAct()) return false
        world.place(current, x, spawnY, Math.toRadians(angle))
        phase = Phase.FALLING; settling = 0.0
        return true
    }
}
