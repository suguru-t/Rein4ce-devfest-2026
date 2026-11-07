package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.GameSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StackViewModelTest {
    private val a = SampleParts.all.first().copy(id = "a", name = "before")
    private val b = a.copy(id = "b")
    private val c = a.copy(id = "c")

    private fun request(id: Long, vararg parts: Part, started: Boolean = false) =
        GameSession.StartRequest(id, parts.map { it.id }, started)

    @After
    fun resetSession() {
        GameSession.resetForTest()
    }

    @Test
    fun `initial start waits for load and takes at most five current parts`() {
        val vm = StackViewModel()
        val inventory = (0..6).map { a.copy(id = "part-$it") }
        vm.updateInventory(inventory, false, null)
        assertEquals(StackViewModel.Phase.Idle, vm.phase)
        assertEquals(0L, vm.runId)

        vm.updateInventory(inventory, true, null)
        assertEquals(inventory.take(5), vm.sessionParts)
        assertEquals(1L, vm.runId)
        vm.updateInventory(inventory.reversed(), true, null)
        assertEquals(inventory.take(5), vm.sessionParts)
        assertEquals(1L, vm.runId)
    }

    @Test
    fun `loaded empty collection starts only when first usable inventory arrives`() {
        val vm = StackViewModel()
        vm.updateInventory(emptyList(), true, null)
        assertEquals(StackViewModel.Phase.Idle, vm.phase)
        vm.updateInventory(listOf(a), true, null)
        assertEquals(listOf(a), vm.sessionParts)
        vm.updateInventory(emptyList(), true, null)
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertEquals(1L, vm.runId)
    }

    @Test
    fun `deleting all inventory keeps placed world and the active snapshot`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true, null)
        vm.dragBy(0.2f)
        vm.rotate(15f)
        vm.place()
        vm.tick(1.0 / 60)
        val shapes = vm.shapes
        val current = vm.current
        vm.updateInventory(emptyList(), true, null)
        assertEquals(1L, vm.runId)
        assertEquals(StackViewModel.Phase.Settling, vm.phase)
        assertEquals(listOf(a, b), vm.sessionParts)
        assertEquals(current, vm.current)
        assertEquals(shapes, vm.shapes)
    }

    @Test
    fun `rename add reorder and delete do not restart an explicit run`() {
        val vm = StackViewModel()
        val selected = request(1, a, b)
        vm.updateInventory(listOf(a, b), true, selected)
        vm.dragBy(0.3f)
        vm.rotate(15f)
        vm.updateInventory(listOf(c, b, a.copy(name = "after")), true, selected)
        vm.updateInventory(listOf(c, a.copy(name = "after")), true, selected)
        assertEquals(1L, vm.runId)
        assertEquals(listOf(a, b), vm.sessionParts)
        assertEquals(0.3f, vm.ghostX, 0.00001f)
        assertEquals(15f, vm.ghostAngleDeg)
    }

    @Test
    fun `retry removes deleted parts and resolves latest values in original order`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b, c), true, request(1, b, a, c))
        val renamed = a.copy(name = "after", distanceM = 999)
        vm.restart(listOf(c, renamed))
        assertEquals(listOf(renamed, c), vm.sessionParts)
        assertEquals(renamed, vm.current)
        assertEquals(2L, vm.runId)
    }

    @Test
    fun `retry with no surviving selection preserves current game and asks to reselect`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true, request(1, a))
        vm.dragBy(0.4f)
        vm.restart(listOf(b))
        assertEquals(1L, vm.runId)
        assertEquals(listOf(a), vm.sessionParts)
        assertEquals(0.4f, vm.ghostX, 0.00001f)
        assertEquals(StackViewModel.MESSAGE_RETRY_MISSING, vm.startMessage)
    }

    @Test
    fun `implicit retry takes the current first parts after the original ones were deleted`() {
        val vm = StackViewModel()
        val first = (1..5).map { a.copy(id = "first-$it") }
        vm.updateInventory(first, true, null)
        val added = a.copy(id = "added")
        vm.restart(listOf(added))
        assertEquals(2L, vm.runId)
        assertEquals(listOf(added), vm.sessionParts)
        assertNull(vm.startMessage)
    }

    @Test
    fun `implicit retry with an empty collection keeps the current game`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true, null)
        vm.dragBy(0.4f)
        vm.restart(emptyList())
        assertEquals(1L, vm.runId)
        assertEquals(listOf(a), vm.sessionParts)
        assertEquals(0.4f, vm.ghostX, 0.00001f)
        assertEquals(StackViewModel.MESSAGE_COLLECTION_EMPTY, vm.startMessage)
    }

    @Test
    fun `missing explicit selection never falls back to unrelated inventory`() {
        val vm = StackViewModel()
        val selected = request(1, a)
        vm.updateInventory(listOf(b), true, selected)
        vm.updateInventory(listOf(b, c), true, selected)
        assertEquals(StackViewModel.Phase.Idle, vm.phase)
        assertEquals(0L, vm.runId)
        assertTrue(vm.sessionParts.isEmpty())
        assertEquals(StackViewModel.MESSAGE_SELECTION_MISSING, vm.startMessage)
        vm.updateInventory(listOf(b, c), true, request(2, c))
        assertEquals(listOf(c), vm.sessionParts)
        assertNull(vm.startMessage)
    }

    @Test
    fun `replayed request whose parts are all gone falls back to automatic start`() {
        val vm = StackViewModel()
        val replayed = request(1, a, started = true)
        vm.updateInventory(emptyList(), true, replayed)
        assertEquals(StackViewModel.Phase.Idle, vm.phase)
        assertNull(vm.startMessage)
        vm.updateInventory(listOf(b, c), true, replayed)
        assertEquals(1L, vm.runId)
        assertEquals(listOf(b, c), vm.sessionParts)
        assertNull(vm.startMessage)
    }

    @Test
    fun `starting from a request marks it started for a recreated view model`() {
        GameSession.requestStart(listOf(a.id))
        val original = StackViewModel()
        original.updateInventory(listOf(a), true, GameSession.startRequest)
        assertTrue(GameSession.startRequest!!.started)

        val recreated = StackViewModel()
        recreated.updateInventory(listOf(b), true, GameSession.startRequest)
        assertEquals(listOf(b), recreated.sessionParts)
        assertNull(recreated.startMessage)
    }

    @Test
    fun `pending request is resolved once after load using current names`() {
        val vm = StackViewModel()
        val selected = request(1, b, a)
        vm.updateInventory(emptyList(), false, selected)
        val renamed = a.copy(name = "after")
        vm.updateInventory(listOf(renamed), true, selected)
        vm.updateInventory(listOf(renamed, b), true, selected)
        assertEquals(listOf(renamed), vm.sessionParts)
        assertEquals(1L, vm.runId)
    }

    @Test
    fun `new view model resolves an old request against current inventory`() {
        val selected = request(1, a, b)
        val original = StackViewModel()
        original.updateInventory(listOf(a, b), true, selected)
        val recreated = StackViewModel()
        val renamed = b.copy(name = "after")
        recreated.updateInventory(listOf(renamed), true, selected)
        assertEquals(listOf(renamed), recreated.sessionParts)
    }

    @Test
    fun `new explicit request restarts even with exactly the same parts`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true, request(1, a))
        vm.dragBy(0.4f)
        vm.updateInventory(listOf(a), true, request(2, a))
        assertEquals(2L, vm.runId)
        assertEquals(0f, vm.ghostX)
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
    }

    @Test
    fun `same selection issues different requests and the start keeps five unique parts in order`() {
        GameSession.requestStart(listOf("a", "b"))
        val first = GameSession.startRequest!!
        GameSession.requestStart(first.partIds)
        assertTrue(GameSession.startRequest!!.id > first.id)

        val inventory = (0..6).map { a.copy(id = "p$it") }
        val vm = StackViewModel()
        val ids = listOf("p3", "p3", "p1", "p0", "p6", "p5", "p4")
        vm.updateInventory(inventory, true, GameSession.StartRequest(99, ids))
        assertEquals(listOf("p3", "p1", "p0", "p6", "p5"), vm.sessionParts.map { it.id })
    }

    @Test
    fun `new run cannot upload the previous completed tower`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val posted = mutableListOf<Tower>()
            val vm = StackViewModel { tower ->
                posted += tower
                TowerCreated("result", "https://example.test/result")
            }
            vm.updateInventory(listOf(a), true, null)
            finish(vm)
            vm.upload()
            runCurrent()
            assertEquals(1, posted.size)
            vm.restart(listOf(a))
            vm.upload()
            runCurrent()
            assertEquals(1, posted.size)
            assertNull(vm.uploadMessage)
            assertFalse(vm.uploading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `starting a new run does not cancel the previous upload`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = CompletableDeferred<TowerCreated>()
            var completed = false
            val vm = StackViewModel {
                val result = response.await()
                completed = true
                result
            }
            vm.updateInventory(listOf(a), true, null)
            finish(vm)
            vm.upload()
            runCurrent()
            vm.restart(listOf(a))
            response.complete(TowerCreated("old", "https://example.test/old"))
            runCurrent()
            assertTrue(completed)
            assertFalse(vm.uploading)
            assertNull(vm.uploadMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `late old upload cannot overwrite the next runs upload state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val oldResponse = CompletableDeferred<TowerCreated>()
            val newResponse = CompletableDeferred<TowerCreated>()
            var calls = 0
            val vm = StackViewModel {
                if (++calls == 1) oldResponse.await() else newResponse.await()
            }
            vm.updateInventory(listOf(a), true, null)
            finish(vm)
            vm.upload()
            runCurrent()
            vm.restart(listOf(a))
            finish(vm)
            vm.upload()
            runCurrent()
            oldResponse.complete(TowerCreated("old", "https://example.test/old"))
            runCurrent()
            assertTrue(vm.uploading)
            assertNull(vm.uploadMessage)
            newResponse.complete(TowerCreated("new", "https://example.test/new"))
            runCurrent()
            assertFalse(vm.uploading)
            assertEquals("送信しました：https://example.test/new", vm.uploadMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `upload once survives failed retry and resets only for a new run`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = StackViewModel { TowerCreated("${++calls}", "https://example.test/$calls") }
            vm.updateInventory(listOf(a), true, null)
            finish(vm)
            vm.upload()
            runCurrent()
            assertTrue(vm.uploaded)
            vm.upload()
            vm.upload()
            runCurrent()
            assertEquals(1, calls)

            vm.restart(emptyList())
            assertEquals(1L, vm.runId)
            assertTrue(vm.uploaded)
            vm.upload()
            runCurrent()
            assertEquals(1, calls)

            vm.restart(listOf(a))
            assertFalse(vm.uploaded)
            finish(vm)
            vm.upload()
            runCurrent()
            assertEquals(2, calls)
            assertTrue(vm.uploaded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed upload shows the server url and permits retry on the same run`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = StackViewModel(serverUrl = { "https://server.test" }) {
                if (++calls == 1) error("offline")
                TowerCreated("ok", "https://example.test/ok")
            }
            vm.updateInventory(listOf(a), true, null)
            finish(vm)
            vm.upload()
            runCurrent()
            assertFalse(vm.uploaded)
            assertFalse(vm.uploading)
            assertEquals("送信できませんでした（https://server.test）：offline", vm.uploadMessage)
            vm.upload()
            runCurrent()
            assertEquals(2, calls)
            assertTrue(vm.uploaded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---- #41：落下待ちと完成の判定、完成した結果の固定 ----

    @Test
    fun `a part that falls off is removed as it falls and the turn does not wait for the time limit`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true, null)
        placeOffPlatform(vm)
        val seconds = tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        // 前は、落ち続けるパーツが止まらないので「全部止まった」にならず、上限の 6 秒まで待っていた
        assertTrue("took $seconds s", seconds < 2.0)
        assertEquals(StackViewModel.Phase.Finished, vm.phase)
        assertEquals(1, vm.fallenCount)
        assertEquals(0, vm.result!!.remainingCount)
    }

    @Test
    fun `a tower with no parts left is not uploaded`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = StackViewModel { calls++; TowerCreated("x", "https://example.test/x") }
            vm.updateInventory(listOf(a), true, null)
            placeOffPlatform(vm)
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
            assertFalse(vm.canUpload)
            vm.upload()
            runCurrent()
            assertEquals(0, calls) // 空のタワーはサーバが 400 で断る（#58）。送らない
            assertNull(vm.uploadMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a long frame after resuming does not skip the settle judgement`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true, null)
        placeFlat(vm)
        vm.tick(10.0) // 10 秒、裏に回していたあとの 1 フレーム
        assertEquals(StackViewModel.Phase.Settling, vm.phase)
        tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        assertEquals(StackViewModel.Phase.Finished, vm.phase)
        assertTrue(vm.result!!.settled)
        assertTrue(vm.canUpload)
    }

    @Test
    fun `the result is fixed at completion and later ticks change nothing`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true, null)
        placeFlat(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        placeFlat(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
        val result = vm.result!!
        val height = vm.height
        val shapes = vm.shapes
        repeat(100) { vm.tick(1.0 / 60) }
        assertSame(result, vm.result)
        assertSame(shapes, vm.shapes)
        assertEquals(height, vm.height)
        assertEquals(result.tower.height, vm.height)
        assertEquals(2, result.usedCount)
        assertEquals(result.remainingCount, result.tower.placements.size)
    }

    @Test
    fun `timing out on the last part gives an unresolved result that cannot be uploaded`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            // 落ちきる前に時間切れになる短さ
            val vm = StackViewModel(settleTiming = shortTiming) { calls++; TowerCreated("x", "https://example.test/x") }
            vm.updateInventory(listOf(a), true, null)
            placeFlat(vm)
            tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
            assertEquals(StackViewModel.Phase.Unresolved, vm.phase)
            assertFalse(vm.result!!.settled)
            assertFalse(vm.canUpload)
            vm.upload()
            runCurrent()
            assertEquals(0, calls)
            // 判定できなかった結果も固定する（以後の tick で変わらない）
            val result = vm.result
            repeat(30) { vm.tick(1.0 / 60) }
            assertSame(result, vm.result)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `timing out on a middle part moves on to the next part`() {
        val vm = StackViewModel(settleTiming = shortTiming)
        vm.updateInventory(listOf(a, b), true, null)
        placeFlat(vm)
        tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        // 途中のパーツは止まらなくても次へ進む（最後のパーツの判定で、塔全体が止まったかをもう一度確かめる）
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertEquals("b", vm.current!!.id)
        assertNull(vm.result)
    }

    @Test
    fun `a placed part that falls later is counted once when it falls`() {
        // 1 個目を台の外に置き、落ちきる前に時間切れにして次へ進む。2 個目を狙っている間に、1 個目が落ちていく
        val vm = StackViewModel(settleTiming = shortTiming)
        vm.updateInventory(listOf(a, b), true, null)
        placeOffPlatform(vm)
        tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertEquals(0, vm.fallenCount)
        tickUntil(vm) { vm.fallenCount > 0 }
        assertEquals(StackViewModel.Phase.Placing, vm.phase) // 狙っている間にも回収して数える
        repeat(120) { vm.tick(1.0 / 60) }
        assertEquals(1, vm.fallenCount) // 2 回数えない
    }

    // ---- #42：カメラに入れる範囲 ----

    @Test
    fun `the camera focus covers the platform and everywhere the next part can be moved`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true, null)
        val focus = vm.cameraFocus
        assertTrue(focus.minX <= -vm.dragLimit && focus.maxX >= vm.dragLimit) // 左右の端まで動かしても入る
        assertTrue(focus.maxY >= vm.spawnY()) // 出てくる高さ
        assertTrue(focus.minY <= -0.3f + 1e-6f) // 台の下面
    }

    @Test
    fun `the camera focus does not change while aiming or while the part falls`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true, null)
        val before = vm.cameraFocus
        vm.dragBy(1f)
        vm.rotate(45f)
        assertSame(before, vm.cameraFocus) // 狙っている間は変えない（指で動かしている最中に倍率が変わらない）
        // 真ん中に戻してから寝かせて置く（台に乗せて、塔を高くするため）
        vm.dragBy(-1f)
        vm.rotate(-45f)
        placeFlat(vm)
        repeat(20) { vm.tick(1.0 / 60) }
        assertEquals(StackViewModel.Phase.Settling, vm.phase)
        assertSame(before, vm.cameraFocus) // 落下中も変えない
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        assertTrue(vm.cameraFocus.maxY > before.maxY) // 止まったら、塔の上に出てくる次のパーツの高さまで広げる
    }

    @Test
    fun `after finishing the camera focus no longer includes the next part`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true, null)
        placeFlat(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
        assertTrue(vm.cameraFocus.maxY < vm.spawnY() - 1f)
        assertTrue(vm.cameraFocus.maxX < vm.dragLimit)
    }

    @Test
    fun `a part falling below the platform is not followed by the camera`() {
        // 1 個目を台の外に置き、台より下を落ちている途中で時間切れにして、次のパーツへ進む
        val vm = StackViewModel(settleTiming = StackViewModel.SettleTiming(minSeconds = 0.2, maxSeconds = 0.7))
        vm.updateInventory(listOf(a, b), true, null)
        placeOffPlatform(vm)
        tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertEquals(0, vm.fallenCount) // まだ落ちきってはいない
        assertTrue("minY=${vm.cameraFocus.minY}", vm.cameraFocus.minY >= -0.3f - 1e-6f)
    }

    /** 置いてから落ちきるまで（約 0.5 秒）より短い待ち時間。時間切れの扱いを確かめるのに使う */
    private val shortTiming = StackViewModel.SettleTiming(minSeconds = 0.2, maxSeconds = 0.3)

    /** 1/60 秒ずつ進め、[done] になるまでにかかった時間 [s] を返す */
    private fun tickUntil(vm: StackViewModel, maxSeconds: Double = 20.0, done: () -> Boolean): Double {
        var t = 0.0
        while (t < maxSeconds) {
            if (done()) return t
            vm.tick(1.0 / 60)
            t += 1.0 / 60
        }
        error("did not finish within $maxSeconds s (phase=${vm.phase})")
    }

    /** 棒（縦長）を寝かせて、台の真ん中に置く */
    private fun placeFlat(vm: StackViewModel) {
        vm.rotate(90f)
        vm.place()
    }

    /** 寝かせた棒を右端まで動かして置く。台に当たらずに落ちる */
    private fun placeOffPlatform(vm: StackViewModel) {
        vm.dragBy(10f)
        placeFlat(vm)
    }

    /**
     * 1 個だけのゲームを完成させる。棒は縦長なので寝かせて置く
     * （立てて落とすと倒れて台から落ちることがあり、パーツが残らない結果は送れないため）
     */
    private fun finish(vm: StackViewModel) {
        placeFlat(vm)
        repeat(1000) {
            if (vm.phase == StackViewModel.Phase.Finished) return
            vm.tick(1.0 / 60)
        }
        error("Single part did not finish (phase=${vm.phase})")
    }
}
