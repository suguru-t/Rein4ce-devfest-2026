package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.challenge.ChallengeRules
import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeViewModelTest {
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `result saves without a screen and only once despite repeated pause`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        var writes = 0
        val disk = CompletableDeferred<Unit>()
        val vm = ChallengeViewModel(updateRecords = { score ->
            if (score == null) emptyList() else { writes++; disk.await(); listOf(score) }
        }, newRun = { ChallengeRun(1, clock = { now }) })
        vm.start(); vm.resume()
        now = ChallengeRules.DURATION_NS; vm.tick()
        repeat(20) { now += 50_000_000; vm.tick() }
        assertTrue(vm.saving)
        assertFalse(vm.canStart)
        val frame = vm.frame
        vm.pause(); vm.pause(); vm.start()
        assertEquals(frame, vm.frame)
        assertEquals(1, writes)
        disk.complete(Unit); runCurrent()
        assertEquals(listOf(frame!!.score), vm.ranking)
        assertTrue(vm.canStart)
        vm.start(); assertFalse(vm.frame!!.terminal)
    }

    @Test fun `save failure remains retryable and blocks discarding the result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        var broken = true
        val vm = ChallengeViewModel(updateRecords = { score ->
            if (score == null) emptyList() else { if (broken) error("disk full"); listOf(score) }
        }, newRun = { ChallengeRun(1, clock = { now }) })
        vm.start(); vm.resume(); now = ChallengeRules.DURATION_NS; vm.tick()
        repeat(20) { now += 50_000_000; vm.tick() }
        assertTrue(vm.saveError); assertFalse(vm.canStart)
        broken = false; vm.refreshRecords()
        assertFalse(vm.saveError); assertTrue(vm.canStart)
        assertEquals(1, vm.ranking.size)
    }

    @Test fun `late initial load cannot replace newly saved ranking`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        val initial = CompletableDeferred<List<ChallengeScore>>()
        val vm = ChallengeViewModel(updateRecords = { score ->
            if (score == null) initial.await() else listOf(score)
        }, newRun = { ChallengeRun(1, clock = { now }) })
        vm.start(); vm.resume(); now = ChallengeRules.DURATION_NS; vm.tick()
        repeat(20) { now += 50_000_000; vm.tick() }
        val saved = vm.ranking
        initial.complete(emptyList()); runCurrent()
        assertEquals(1, saved.size); assertEquals(saved, vm.ranking)
    }

    @Test fun `pause can finalize and persist without another rendered frame`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        val vm = ChallengeViewModel(updateRecords = { listOfNotNull(it) }, newRun = { ChallengeRun(1, clock = { now }) })
        vm.start(); vm.resume(); now = ChallengeRules.DURATION_NS; vm.tick()
        repeat(15) { now += 50_000_000; vm.tick() }
        now += 50_000_000; vm.pause()
        assertEquals(ChallengeRun.Phase.FINISHED, vm.frame!!.phase)
        assertEquals(1, vm.ranking.size)
    }
}
