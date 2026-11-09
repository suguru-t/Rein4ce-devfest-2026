package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.daily.DailyBlock
import com.rein4ce.roadcollector.core.daily.DailyTower
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.data.DailyTowerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 毎日 1 つずつ積むの、昨日までの塔を固めることと、落ちたときの置き直し（決定 70） */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModelTest {
    private val day = "2026-11-10"

    /** 最後に保存した塔（保存は IO のスレッドで書く） */
    @Volatile private var saved: DailyTower? = null

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private fun part(id: String) = Part(id = id, points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)), kind = Kind.BAR, distanceM = 400, walkedAt = 0L)

    private fun newVm(initial: DailyTower): DailyViewModel {
        val text = AppJson.encodeToString(DailyTower.serializer(), initial)
        val store = DailyTowerStore(
            read = { text },
            write = { saved = AppJson.decodeFromString(DailyTower.serializer(), it) },
            quarantine = {},
        )
        val vm = DailyViewModel(store, today = { day })
        waitUntil { vm.phase != DailyViewModel.Phase.Loading }
        return vm
    }

    /** 読み込み・保存は IO のスレッドで進むので、終わるまで待つ */
    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "待ちきれなかった" }
            Thread.sleep(5)
        }
    }

    /** 落ちている間、物理を進める */
    private fun DailyViewModel.runUntilStill() {
        var frames = 0
        while (phase == DailyViewModel.Phase.Settling && frames < 400) {
            tick(0.05)
            frames++
        }
    }

    @Test fun `今日のパーツが台から落ちたら同じパーツで置き直し、4 回目に落ちたらその日はもう積めない`() {
        val vm = newVm(DailyTower())
        val road = part("today")
        vm.choose(road, Grip.NORMAL)
        vm.dragBy(10f) // 台の外まで寄せる
        val x = vm.ghostX
        for (n in 1..DailyTower.MAX_RETRIES) {
            vm.place()
            vm.runUntilStill()
            assertEquals("$n 回目に落ちたら、置き直す", DailyViewModel.Phase.Aiming, vm.phase)
            assertSame(road, vm.current)
            assertTrue(vm.lastFell)
            assertEquals(n, vm.fallsToday)
            assertEquals("位置はそのまま", x, vm.ghostX)
        }
        vm.place()
        vm.runUntilStill()
        assertEquals(DailyViewModel.Phase.Viewing, vm.phase)
        assertNull(vm.current)
        assertFalse(vm.canPlaceToday)
        vm.choose(road, Grip.NORMAL)
        assertEquals("置き直しを使い切ったら、選べない", DailyViewModel.Phase.Viewing, vm.phase)
        waitUntil { saved?.fallsOn(day) == DailyTower.MAX_RETRIES + 1 }
    }

    @Test fun `落ちた回数は保存してあるので、開き直しても置き直しは増えない`() {
        val vm = newVm(DailyTower(fellDay = day, fellCount = DailyTower.MAX_RETRIES + 1))
        assertFalse(vm.canPlaceToday)
        vm.choose(part("today"), Grip.NORMAL)
        assertEquals(DailyViewModel.Phase.Viewing, vm.phase)
        val tomorrow = newVm(DailyTower(fellDay = "2026-11-09", fellCount = DailyTower.MAX_RETRIES + 1))
        assertTrue("前の日に使い切っていても、今日は積める", tomorrow.canPlaceToday)
    }

    @Test fun `台は通常と同じ長さ（台の端から 80 cm 外まで狙える）`() {
        val vm = newVm(DailyTower())
        vm.choose(part("today"), Grip.NORMAL)
        vm.dragBy(10f)
        assertEquals(StackViewModel.PLATFORM_WIDTH_M.toFloat() / 2 + 0.8f, vm.ghostX, 1e-4f)
    }

    @Test fun `昨日までの塔は固めてあり、今日のパーツが当たっても動かない`() {
        // 昨日の棒は、台（2.8 m）の右の端から半分はみ出している（固めていないと、上に落ちてきたパーツに押されて傾き、落ちる）
        val yesterday = Placement("yesterday", x = 1.4f, y = 0.05f, angleRad = 0f, scale = 1f)
        val vm = newVm(DailyTower(listOf(DailyBlock(part("yesterday"), yesterday, Grip.NORMAL, "2026-11-09")), height = 0.1))
        vm.choose(part("today"), Grip.NORMAL)
        vm.dragBy(1.8f) // はみ出した端の上
        vm.place()
        vm.runUntilStill()
        val kept = vm.tower.blocks.single { it.part.id == "yesterday" }
        assertEquals(yesterday, kept.placement)
    }

    @Test fun `今日のパーツが台に残ったら、塔を自動で「みんな」に送り、送れた日を残す（決定 73）`() {
        val sent = java.util.concurrent.CopyOnWriteArrayList<com.rein4ce.roadcollector.core.model.Tower>()
        val text = AppJson.encodeToString(DailyTower.serializer(), DailyTower())
        val store = DailyTowerStore(read = { text }, write = { saved = AppJson.decodeFromString(DailyTower.serializer(), it) }, quarantine = {})
        val vm = DailyViewModel(store, today = { day }, sendTower = { tower ->
            sent += tower
            com.rein4ce.roadcollector.core.model.TowerCreated("d1", "https://example.test/towers/d1")
        })
        waitUntil { vm.phase != DailyViewModel.Phase.Loading }
        assertTrue("まだ積んでいなければ送らない", sent.isEmpty())
        vm.choose(part("today"), Grip.NORMAL)
        vm.place() // 台のまん中に落とす
        vm.runUntilStill()
        assertTrue(vm.placedToday)
        waitUntil { vm.sendState == DailyViewModel.SendState.SENT }
        val tower = sent.single()
        assertEquals(com.rein4ce.roadcollector.core.model.TowerMode.DAILY, tower.mode)
        assertEquals(1, tower.streakDays)
        assertEquals(day, tower.lastDay)
        assertEquals(StackViewModel.PLATFORM_WIDTH_M.toFloat(), tower.platformWidth)
        waitUntil { saved?.sentDay == day }
        assertFalse(vm.tower.needsSend)
    }
}
