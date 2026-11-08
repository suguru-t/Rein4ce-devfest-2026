package com.rein4ce.roadcollector.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rein4ce.roadcollector.core.challenge.ChallengeBounds
import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.StackWorld
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Immutable frame data keeps rendering independent of the mutable physics engine. */
data class ChallengeFrame(
    val phase: ChallengeRun.Phase,
    val remainingNs: Long,
    val current: Part,
    val next: Part,
    val x: Double,
    val angle: Double,
    val spawnY: Double,
    val bounds: ChallengeBounds,
    val shapes: List<StackWorld.RenderShape>,
    val score: ChallengeScore?,
) {
    val terminal get() = phase == ChallengeRun.Phase.FINISHED || phase == ChallengeRun.Phase.UNRESOLVED
}

class ChallengeViewModel(
    private val updateRecords: suspend (ChallengeScore?) -> List<ChallengeScore>,
    private val newRun: () -> ChallengeRun = { ChallengeRun(Random.nextLong()) },
) : ViewModel() {
    private var run: ChallengeRun? = null
    var generation by mutableStateOf(0); private set
    var frame by mutableStateOf<ChallengeFrame?>(null); private set
    var ranking by mutableStateOf<List<ChallengeScore>>(emptyList()); private set
    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf(false); private set
    private var savedRunId: String? = null
    private var pendingRunId: String? = null
    private var operation = 0
    val canStart get() = run?.score?.let { it.runId == savedRunId } ?: true

    init { refreshRecords() }

    fun start() {
        if (!canStart) return
        run?.pause()
        run = newRun()
        generation++
        publishFrame()
    }
    fun resume() { run?.resume(); publishFrame() }
    fun pause() { run?.pause(); publishFrame() }
    fun tick() { run?.tick(); publishFrame() }
    fun move(dx: Double) { run?.move(dx); publishFrame() }
    fun rotate(degrees: Double) { run?.rotate(degrees); publishFrame() }
    fun place() { run?.place(); publishFrame() }

    private fun publishFrame() {
        val r = run ?: return
        frame = ChallengeFrame(r.phase, r.remainingNs, r.current, r.next, r.x, r.angle, r.spawnY, r.bounds, r.shapes, r.score)
        val score = r.score
        if (score != null && score.runId != savedRunId && score.runId != pendingRunId && !saveError) refreshRecords()
    }

    fun refreshRecords() {
        val score = run?.score
        if (score != null && pendingRunId == score.runId) return
        val request = ++operation
        pendingRunId = score?.runId
        saving = score != null
        saveError = false
        // Enter the persistence operation before a navigation event can cancel the ViewModel scope.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val records = updateRecords(score)
                if (request == operation) {
                    ranking = records
                    savedRunId = score?.runId
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (request == operation) saveError = true
            } finally {
                if (request == operation) { pendingRunId = null; saving = false }
            }
        }
    }
}
