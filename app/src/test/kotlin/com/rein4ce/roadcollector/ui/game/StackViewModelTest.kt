package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.physics.PartSize
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

    @Test
    fun `a longer walk is placed bigger and the size is sent with the tower`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (distance in listOf(120, 1500)) {
                var sent: Tower? = null
                val vm = StackViewModel {
                    sent = it
                    TowerCreated("ok", "https://example.test/ok")
                }
                vm.updateInventory(listOf(a.copy(distanceM = distance)), true, null)
                vm.rotate(90f) // 寝かせて、台から落ちないように
                finish(vm)
                vm.upload()
                runCurrent()
                assertEquals(PartSize.of(distance).toFloat(), sent!!.placements.single().scale, 1e-6f)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun finish(vm: StackViewModel) {
        vm.place()
        repeat(1000) {
            if (vm.phase == StackViewModel.Phase.Finished) return
            vm.tick(1.0 / 60)
        }
        error("Single part did not finish")
    }
}
