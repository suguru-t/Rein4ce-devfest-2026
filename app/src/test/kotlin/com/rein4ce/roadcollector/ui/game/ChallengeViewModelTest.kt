package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.challenge.ChallengeRules
import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import com.rein4ce.roadcollector.core.challenge.ChallengeStyle
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

    @Test fun `保存に失敗しても再試行でき、次の回にも進める`() = runTest {
        // 前は保存できるまで「もう一度」が押せず、ファイルが壊れていると先に進めなかった（#78 のレビュー）
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        var broken = true
        var writes = 0
        val vm = ChallengeViewModel(updateRecords = { score ->
            if (score == null) emptyList() else { writes++; if (broken) error("disk full"); listOf(score) }
        }, newRun = { ChallengeRun(1, clock = { now }) })
        vm.start(); vm.resume(); now = ChallengeRules.DURATION_NS; vm.tick()
        repeat(20) { now += 50_000_000; vm.tick() }
        assertTrue(vm.saveError); assertTrue(vm.resultUnsaved); assertTrue(vm.canStart)
        // 失敗した回は、自動では保存し直さない（毎コマ書きに行かない）
        repeat(5) { now += 50_000_000; vm.tick() }
        assertEquals(1, writes)
        broken = false; vm.refreshRecords()
        assertFalse(vm.saveError); assertFalse(vm.resultUnsaved); assertTrue(vm.canStart)
        assertEquals(1, vm.ranking.size)
    }

    @Test fun `開いたときの読み込みに失敗しても、新しい結果は自動で保存する`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        val vm = ChallengeViewModel(updateRecords = { score ->
            if (score == null) error("cannot read") else listOf(score)
        }, newRun = { ChallengeRun(1, clock = { now }) })
        runCurrent()
        assertTrue(vm.saveError)
        vm.start(); vm.resume(); now = ChallengeRules.DURATION_NS; vm.tick()
        repeat(20) { now += 50_000_000; vm.tick() }
        runCurrent()
        assertEquals(listOf(vm.frame!!.score), vm.ranking)
        assertFalse(vm.saveError)
    }

    @Test fun `やり直すと画面を離れたときは、その回を記録に残さない`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        var writes = 0
        var runs = 0
        val vm = ChallengeViewModel(updateRecords = { score -> if (score != null) writes++; listOfNotNull(score) }, newRun = {
            runs++
            ChallengeRun(runs.toLong(), clock = { now })
        })
        vm.start(); vm.resume(); now += 30_000_000_000; vm.tick()
        assertEquals(ChallengeViewModel.FULL_SECONDS - 30, vm.remainingSeconds)
        val generation = vm.generation
        vm.restart()
        assertEquals(2, runs)
        assertTrue(vm.generation > generation)
        assertEquals(ChallengeRun.Phase.AIMING, vm.frame!!.phase)
        assertEquals(ChallengeViewModel.FULL_SECONDS, vm.remainingSeconds)

        vm.leave()
        assertNull(vm.frame)
        assertTrue(vm.shapes.isEmpty())
        assertEquals(ChallengeViewModel.FULL_SECONDS, vm.remainingSeconds)
        runCurrent()
        assertEquals(0, writes)
        assertTrue(vm.canStart)
    }

    @Test fun `終わったあとに画面を離れても保存は続き、次に開いたときは新しい回から（決定 69）`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        var runs = 0
        val disk = CompletableDeferred<Unit>()
        val vm = ChallengeViewModel(updateRecords = { score ->
            if (score == null) emptyList() else { disk.await(); listOf(score) }
        }, newRun = { runs++; ChallengeRun(runs.toLong(), clock = { now }) })
        vm.start(); vm.resume(); now = ChallengeRules.DURATION_NS; vm.tick()
        repeat(20) { now += 50_000_000; vm.tick() }
        val score = vm.frame!!.score!!
        assertTrue(vm.saving)
        vm.leave()
        assertNull("結果の画面は引き継がない", vm.frame)
        assertTrue("保存の途中でも、次の回を始められる", vm.canStart)
        disk.complete(Unit); runCurrent()
        assertEquals("離れたあとも保存は続く", listOf(score), vm.ranking)
        vm.start()
        assertEquals(2, runs)
        assertEquals(ChallengeRun.Phase.AIMING, vm.frame!!.phase)
        assertEquals(ChallengeViewModel.FULL_SECONDS, vm.remainingSeconds)
    }

    @Test fun `何も起きないコマではフレームを変えず、残り時間は秒が変わったときだけ変える`() = runTest {
        // 毎コマ新しいフレームにすると、画面全体を毎コマ組み立て直すことになる（#78 のレビュー）
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        val vm = ChallengeViewModel(updateRecords = { listOfNotNull(it) }, newRun = { ChallengeRun(1, clock = { now }) })
        vm.start(); vm.resume()
        val frame = vm.frame
        repeat(10) { now += 16_000_000; vm.tick() }
        assertSame(frame, vm.frame)
        assertEquals(ChallengeViewModel.FULL_SECONDS, vm.remainingSeconds)
        now += 1_000_000_000; vm.tick()
        assertEquals(ChallengeViewModel.FULL_SECONDS - 1, vm.remainingSeconds)
        vm.move(0.1)
        assertNotEquals(frame, vm.frame)
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

    @Test fun `自分で落とすだけで遊び、その決まりの記録を読む（決定 69）`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = 0L
        val asked = mutableListOf<String>()
        val styles = mutableListOf<ChallengeStyle>()
        val vm = ChallengeViewModel(
            updateRecords = { listOfNotNull(it) },
            newRun = { style -> styles += style; ChallengeRun(1, style = style, clock = { now }) },
            loadRecords = { rule -> asked += rule; emptyList() },
        )
        runCurrent()
        assertEquals(ChallengeStyle.MANUAL, vm.style)
        assertEquals(listOf(ChallengeRules.ID), asked)
        vm.start(); vm.resume()
        assertEquals(listOf(ChallengeStyle.MANUAL), styles)
        // 自分で落とす形は、狙っている間は下がらない（指を離すまで、出てくる高さで待つ）
        val start = vm.ghostY
        val frame = vm.frame
        repeat(20) { now += 50_000_000; vm.tick() }
        assertEquals(start, vm.ghostY)
        assertEquals(ChallengeRun.Phase.AIMING, vm.frame!!.phase)
        assertEquals("何もしなければ、フレームは変わらない", frame, vm.frame)
        // 指を離す（place）と落ちる
        vm.place()
        assertEquals(ChallengeRun.Phase.FALLING, vm.frame!!.phase)
    }
}
