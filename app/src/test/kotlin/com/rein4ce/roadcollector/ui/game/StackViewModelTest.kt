package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerCreated
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.WeatherBonus
import com.rein4ce.roadcollector.core.sample.SampleParts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 積む画面の通常モード（決定 62・#108）：コレクションの全部のパーツからランダムに 1 つずつ出し、台から落ちたら終わり。
 * 落下待ち・結果の固定・送信・カメラは、5 本まで選んで積んでいたころ（#40〜#42・#71）からの決まりを引き継ぐ
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StackViewModelTest {
    private val a = SampleParts.all.first().copy(id = "a", name = "before")
    private val b = a.copy(id = "b")
    private val c = a.copy(id = "c")

    // ---- 始め方 ----

    @Test
    fun `読み込みが済んだら、コレクションの全部のパーツで始め、そこから 1 つ出す`() {
        val vm = StackViewModel()
        val inventory = (0..6).map { a.copy(id = "part-$it") }
        vm.updateInventory(inventory, false)
        assertEquals(StackViewModel.Phase.Idle, vm.phase)
        assertEquals(0L, vm.runId)

        vm.updateInventory(inventory, true)
        assertEquals(1L, vm.runId)
        assertEquals(inventory, vm.pool) // 5 本で切らない
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertTrue(vm.current in inventory)
        assertEquals(0, vm.placedCount)
    }

    @Test
    fun `空のコレクションでは始めず、パーツが来たときに始める`() {
        val vm = StackViewModel()
        vm.updateInventory(emptyList(), true)
        assertEquals(StackViewModel.Phase.Idle, vm.phase)
        vm.updateInventory(listOf(a), true)
        assertEquals(1L, vm.runId)
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
    }

    @Test
    fun `一覧が変わっても始めたゲームはそのままで、やり直すと今のコレクションで取り直す`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true)
        placeFlat(vm)
        vm.updateInventory(listOf(a.copy(name = "renamed"), b, c), true)
        assertEquals(1L, vm.runId)
        assertEquals(listOf(a, b), vm.pool)
        assertEquals(1, vm.placedCount) // 置いたパーツも残る

        vm.restart(listOf(b, c))
        assertEquals(2L, vm.runId)
        assertEquals(listOf(b, c), vm.pool)
        assertEquals(0, vm.placedCount)
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
    }

    @Test
    fun `やり直すときコレクションが空なら、今のゲームのままで案内を出す`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        placeFlat(vm)
        vm.restart(emptyList())
        assertEquals(1L, vm.runId)
        assertEquals(1, vm.placedCount)
        assertEquals(StackViewModel.MESSAGE_COLLECTION_EMPTY, vm.startMessage)
        vm.dismissStartMessage()
        assertNull(vm.startMessage)
    }

    @Test
    fun `同じ id のパーツが 2 つあっても、出す候補には 1 つだけ入れる`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, a.copy(name = "dup"), b), true)
        assertEquals(listOf("a", "b"), vm.pool.map { it.id })
    }

    @Test
    fun `台は 1 本ずつ選んで積んでいたころの 2 倍の幅で、狙える範囲もそのぶん広い`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        assertEquals(2.8, vm.platformWidth, 1e-9)
        vm.dragBy(10f)
        assertEquals(1.4f + StackViewModel.DRAG_MARGIN_M.toFloat(), vm.ghostX, 1e-6f)
    }

    // ---- 終わりなく出てくる ----

    @Test
    fun `置くたびに次のパーツが出て、落ちるまで終わらない`() {
        val picks = SequenceRandom(listOf(0, 1, 2, 1))
        val vm = StackViewModel(random = picks)
        vm.updateInventory(listOf(a, b, c), true)
        assertEquals("a", vm.current!!.id)
        for (expected in listOf("b", "c", "b")) {
            placeFlat(vm)
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
            assertEquals(expected, vm.current!!.id)
        }
        assertEquals(3, vm.placedCount)
        assertEquals(0, vm.fallenCount)
        assertNull(vm.result)
    }

    @Test
    fun `同じパーツが何度出てもよい`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        placeFlat(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        assertEquals("a", vm.current!!.id)
        assertEquals(1, vm.placedCount)
    }

    // ---- チェンジ（狙っているパーツを替える） ----

    @Test
    fun `チェンジすると、ほかのパーツに替わり、残りの回数が減る`() {
        // a が出たあと、チェンジの候補（a 以外）から b を選ぶ
        val vm = StackViewModel(random = SequenceRandom(listOf(0, 0)))
        vm.updateInventory(listOf(a, b, c), true)
        assertEquals("a", vm.current!!.id)
        vm.rotate(45f)
        vm.dragBy(0.3f)
        vm.change()
        assertEquals("b", vm.current!!.id)
        assertEquals(StackViewModel.CHANGES_PER_GAME - 1, vm.changesLeft)
        assertEquals(0f, vm.ghostAngleDeg, 0f) // 向きは 0° に戻す
        assertEquals(0.3f, vm.ghostX, 1e-6f) // 位置はそのまま
        assertEquals(0, vm.placedCount) // 置いたことにはならない
    }

    @Test
    fun `チェンジは 1 ゲームに決まった回数までで、やり直すとまた使える`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b, c), true)
        repeat(StackViewModel.CHANGES_PER_GAME) {
            val before = vm.current!!.id
            vm.change()
            assertTrue(vm.current!!.id != before) // いつも違うパーツに替わる
        }
        assertEquals(0, vm.changesLeft)
        assertFalse(vm.canChange)
        val last = vm.current
        vm.change()
        assertSame(last, vm.current)

        vm.restart(listOf(a, b, c))
        assertEquals(StackViewModel.CHANGES_PER_GAME, vm.changesLeft)
        assertTrue(vm.canChange)
    }

    @Test
    fun `落下中と、ほかに替えるパーツが無いときはチェンジできない`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true)
        placeFlat(vm)
        assertFalse(vm.canChange) // 落下中
        vm.change()
        assertEquals(StackViewModel.CHANGES_PER_GAME, vm.changesLeft)

        val single = StackViewModel()
        single.updateInventory(listOf(a), true)
        assertFalse(single.canChange) // パーツが 1 種類だけ
        single.change()
        assertEquals("a", single.current!!.id)
        assertEquals(StackViewModel.CHANGES_PER_GAME, single.changesLeft)
    }

    // ---- 終わり：台から落ちたら ----

    @Test
    fun `パーツが台から落ちたら、塔が落ち着くのを待って終わる`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true)
        placeFlat(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        placeOffPlatform(vm)
        val seconds = tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        // 落ち続けるパーツは落ちた時点で取り除くので、上限の 6 秒までは待たない
        assertTrue("took $seconds s", seconds < 3.0)
        assertEquals(StackViewModel.Phase.Finished, vm.phase)
        assertNull(vm.current)
        val result = vm.result!!
        assertEquals(2, result.usedCount)
        assertEquals(1, result.fallenCount)
        assertEquals(1, result.remainingCount)
        assertTrue(result.settled)
        assertTrue(vm.canUpload)
        assertEquals(vm.height, result.tower.height)
    }

    @Test
    fun `同じパーツを何度置いても、送るタワーのパーツは 1 つにまとめ、配置は置いた数だけ`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        repeat(2) {
            placeFlat(vm)
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        }
        placeOffPlatform(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
        val tower = vm.result!!.tower
        assertEquals(listOf("a"), tower.parts.map { it.id }) // サーバは同じ id のパーツが 2 つあると断る
        assertEquals(2, tower.placements.size)
        assertTrue(tower.placements.all { it.partId == "a" })
        assertEquals(2, vm.result!!.remainingCount)
    }

    @Test
    fun `送るタワーには台の幅を入れ、みんなの絵と共有画像も同じ幅の台で描く`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        placeFlat(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        placeOffPlatform(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
        assertEquals(2.8f, vm.result!!.tower.platformWidth, 1e-6f) // 決定 66
    }

    @Test
    fun `狙っている間に塔が崩れて落ちたら、狙っていたパーツは置かずに終わる`() {
        // 1 個目を台の外に置き、落ちきる前に時間切れにして次へ進む。2 個目を狙っている間に、1 個目が落ちる
        val vm = StackViewModel(settleTiming = shortTiming)
        vm.updateInventory(listOf(a, b), true)
        placeOffPlatform(vm)
        tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertEquals(0, vm.fallenCount)
        tickUntil(vm) { vm.fallenCount > 0 }
        assertNull(vm.current) // 狙っていたパーツは置かない
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
        val result = vm.result!!
        assertEquals(1, result.usedCount)
        assertEquals(1, result.fallenCount) // 2 回数えない
        assertEquals(0, result.remainingCount)
        assertFalse(vm.canUpload) // 台に何も残っていない
    }

    @Test
    fun `終わったあと揺れが止まらなければ、判定できない結果になり送れない`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            // 待ち時間を 1 コマにして、動いているうちに時間切れにする
            val vm = StackViewModel(settleTiming = StackViewModel.SettleTiming(minSeconds = 0.0, maxSeconds = 1.0 / 60)) {
                calls++
                TowerCreated("x", "https://example.test/x")
            }
            vm.updateInventory(listOf(a), true)
            placeOffPlatform(vm)
            // 落ちるまで、真ん中に次々と置いて塔を揺らし続ける
            tickUntil(vm) {
                if (vm.phase == StackViewModel.Phase.Placing) placeFlat(vm)
                vm.phase == StackViewModel.Phase.Unresolved || vm.phase == StackViewModel.Phase.Finished
            }
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
    fun `途中のパーツは止まらなくても時間切れで次へ進む`() {
        val vm = StackViewModel(settleTiming = shortTiming)
        vm.updateInventory(listOf(a, b), true)
        placeFlat(vm)
        tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertNotNull(vm.current)
        assertNull(vm.result)
    }

    @Test
    fun `終わったときの結果は固定し、そのあとの tick では変わらない`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true)
        finishWithTower(vm)
        val result = vm.result!!
        val height = vm.height
        val shapes = vm.shapes
        repeat(100) { vm.tick(1.0 / 60) }
        assertSame(result, vm.result)
        assertSame(shapes, vm.shapes)
        assertEquals(height, vm.height)
        assertEquals(result.tower.height, vm.height)
    }

    @Test
    fun `裏から戻ったあとの長い 1 コマでも、落下待ちの判定を飛ばさない`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        placeFlat(vm)
        vm.tick(10.0) // 10 秒、裏に回していたあとの 1 フレーム
        assertEquals(StackViewModel.Phase.Settling, vm.phase)
        tickUntil(vm) { vm.phase != StackViewModel.Phase.Settling }
        assertEquals(StackViewModel.Phase.Placing, vm.phase)
        assertEquals(1, vm.placedCount)
    }

    // ---- 送信 ----

    @Test
    fun `新しいゲームでは、前のゲームの塔は送れない`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val posted = mutableListOf<Tower>()
            val vm = StackViewModel { tower ->
                posted += tower
                TowerCreated("result", "https://example.test/result")
            }
            vm.updateInventory(listOf(a), true)
            finishWithTower(vm)
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
    fun `新しいゲームを始めても、前の送信は打ち切らない`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = CompletableDeferred<TowerCreated>()
            var completed = false
            val vm = StackViewModel {
                val result = response.await()
                completed = true
                result
            }
            vm.updateInventory(listOf(a), true)
            finishWithTower(vm)
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
    fun `遅れて届いた前の送信の結果は、次のゲームの送信の表示を上書きしない`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val oldResponse = CompletableDeferred<TowerCreated>()
            val newResponse = CompletableDeferred<TowerCreated>()
            var calls = 0
            val vm = StackViewModel {
                if (++calls == 1) oldResponse.await() else newResponse.await()
            }
            vm.updateInventory(listOf(a), true)
            finishWithTower(vm)
            vm.upload()
            runCurrent()
            vm.restart(listOf(a))
            finishWithTower(vm)
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
    fun `送れるのは 1 回だけで、新しいゲームになったらまた送れる`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = StackViewModel { TowerCreated("${++calls}", "https://example.test/$calls") }
            vm.updateInventory(listOf(a), true)
            finishWithTower(vm)
            vm.upload()
            runCurrent()
            assertTrue(vm.uploaded)
            vm.upload()
            vm.upload()
            runCurrent()
            assertEquals(1, calls)

            vm.restart(emptyList()) // 空なら始め直さない
            assertEquals(1L, vm.runId)
            assertTrue(vm.uploaded)

            vm.restart(listOf(a))
            assertFalse(vm.uploaded)
            finishWithTower(vm)
            vm.upload()
            runCurrent()
            assertEquals(2, calls)
            assertTrue(vm.uploaded)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `送れなかったときは接続先を出し、同じゲームでもう一度送れる`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = StackViewModel(serverUrl = { "https://server.test" }) {
                if (++calls == 1) error("offline")
                TowerCreated("ok", "https://example.test/ok")
            }
            vm.updateInventory(listOf(a), true)
            finishWithTower(vm)
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
    fun `長く歩いた道ほど大きく置き、その大きさをタワーと一緒に送る`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (distance in listOf(120, 1500)) {
                var sent: Tower? = null
                val vm = StackViewModel {
                    sent = it
                    TowerCreated("ok", "https://example.test/ok")
                }
                vm.updateInventory(listOf(a.copy(distanceM = distance)), true)
                finishWithTower(vm)
                vm.upload()
                runCurrent()
                assertEquals(PartSize.of(distance).toFloat(), sent!!.placements.single().scale, 1e-6f)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `台に何も残らなかった塔は送らない`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = StackViewModel { calls++; TowerCreated("x", "https://example.test/x") }
            vm.updateInventory(listOf(a), true)
            placeOffPlatform(vm)
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
            assertEquals(0, vm.result!!.remainingCount)
            assertFalse(vm.canUpload)
            vm.upload()
            runCurrent()
            assertEquals(0, calls) // 空のタワーはサーバが 400 で断る（#58）。送らない
            assertNull(vm.uploadMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---- カメラ（狙っている間は大きさを変えずに上げ、終わったら全体が入るように引く。#42） ----

    @Test
    fun `狙っている間は大きさを変えず、次のパーツが出てくる所まで入れる`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        val camera = vm.camera as CameraTarget.Follow
        assertTrue(camera.top > vm.spawnY()) // 出てくる高さに、どう回しても入る半径を足したところまで
    }

    @Test
    fun `長く歩いた道の大きなパーツは、そのぶん上まで入れる`() {
        val short = StackViewModel().apply { updateInventory(listOf(a.copy(distanceM = 150)), true) }
        val long = StackViewModel().apply { updateInventory(listOf(a.copy(distanceM = 2000)), true) }
        val shortReach = (short.camera as CameraTarget.Follow).top - short.spawnY()
        val longReach = (long.camera as CameraTarget.Follow).top - long.spawnY()
        assertEquals(PartSize.of(2000) / PartSize.of(150), (longReach / shortReach).toDouble(), 0.05)
    }

    @Test
    fun `狙っている間と落下中はカメラを動かさない`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a, b), true)
        val before = vm.camera as CameraTarget.Follow
        vm.dragBy(1f)
        vm.rotate(45f)
        assertSame(before, vm.camera) // 狙っている間は変えない（指で動かしている最中にカメラが動かない）
        vm.dragBy(-1f)
        vm.rotate(-45f)
        placeFlat(vm)
        repeat(20) { vm.tick(1.0 / 60) }
        assertEquals(StackViewModel.Phase.Settling, vm.phase)
        assertSame(before, vm.camera) // 落下中も変えない
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        // 止まったら、塔の上に出てくる次のパーツの高さまで上げる（大きさは変えないまま）
        assertTrue((vm.camera as CameraTarget.Follow).top > before.top)
    }

    @Test
    fun `終わったら、塔全体と台が入るように引く`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        finishWithTower(vm)
        val focus = (vm.camera as CameraTarget.Overview).focus
        val half = (vm.platformWidth / 2).toFloat()
        assertTrue(focus.minX <= -half && focus.maxX >= half) // 台の端から端まで
        assertTrue(focus.minY <= -0.3f + 1e-6f) // 台の下面
        assertTrue("maxY=${focus.maxY} height=${vm.height}", focus.maxY >= vm.height - 0.05f) // 塔のてっぺん
        assertTrue(focus.maxY < vm.spawnY() - 1f) // 次のパーツが出てくる場所は入れない（もう置かない）
    }

    // ---- 天気のボーナス（決定 68）：終わったときに台に残っていたパーツの天気で、模型を大きくする ----

    @Test
    fun `台に残った雨と雪の道のぶん、終わったら模型を大きくし、その高さと塔を記録・送信に使う`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var sent: Tower? = null
            val vm = StackViewModel {
                sent = it
                TowerCreated("ok", "https://example.test/ok")
            }
            vm.updateInventory(listOf(a), true)
            // 雪と雨の道を台の上に 1 個ずつ（並べて置く。台は滑らない）
            placeFlat(vm, x = -0.7f, grip = Grip.ICY)
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
            assertEquals(1.2, vm.bonus.multiplier, 1e-9) // 止まったら、台に残っているぶんで数え直す
            placeFlat(vm, x = 0.7f, grip = Grip.WET)
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
            assertEquals(1.3, vm.bonus.multiplier, 1e-9)
            // 落ちた雪の道は塔に入っていないので数えない
            placeOffPlatform(vm, grip = Grip.ICY)
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }

            val result = vm.result!!
            assertEquals(mapOf(Grip.ICY to 1, Grip.WET to 1), result.bonus.counts)
            assertEquals(1.3, result.bonus.multiplier, 1e-9)
            assertEquals(vm.height, result.tower.height) // 物理で積んだままの高さ
            assertEquals(result.tower.height * 1.3f, result.finalHeight, 1e-5f)

            vm.upload()
            runCurrent()
            val tower = sent!!
            assertEquals(result.finalHeight, tower.height, 1e-5f) // 大きくした塔を送る
            val size = PartSize.of(a.distanceM).toFloat()
            assertTrue(tower.placements.all { kotlin.math.abs(it.scale - size * 1.3f) < 1e-5f })
            assertEquals(result.tower.placements.map { it.x * 1.3f }, tower.placements.map { it.x })
            assertEquals(StackViewModel.PLATFORM_WIDTH_M.toFloat() * 1.3f, tower.platformWidth, 1e-5f) // 台ごと大きくする
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `晴れの道だけなら、模型の大きさは変えない`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        finishWithTower(vm)
        val result = vm.result!!
        assertEquals(WeatherBonus.NONE, result.bonus)
        assertEquals(result.tower.height, result.finalHeight)
        assertSame(result.tower, result.uploadTower)
    }

    @Test
    fun `大きくした模型と台が入るように引き、新しいゲームではボーナスを数え直す`() {
        val vm = StackViewModel()
        vm.updateInventory(listOf(a), true)
        placeFlat(vm, grip = Grip.ICY)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        placeOffPlatform(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
        val result = vm.result!!
        val m = result.bonus.multiplier.toFloat()
        assertEquals(1.2f, m, 1e-6f)
        val focus = (vm.camera as CameraTarget.Overview).focus
        val half = (vm.platformWidth / 2).toFloat()
        assertTrue(focus.minX <= -half * m + 1e-5f && focus.maxX >= half * m - 1e-5f) // 大きくした台の端から端まで
        assertTrue("maxY=${focus.maxY} final=${result.finalHeight}", focus.maxY >= result.finalHeight - 0.05f)

        vm.restart(listOf(a))
        assertEquals(WeatherBonus.NONE, vm.bonus)
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

    /** 棒（縦長）を寝かせて、台の真ん中（[x] だけずらせる）に置く。[grip] は歩いた日の天気のすべりやすさ */
    private fun placeFlat(vm: StackViewModel, x: Float = 0f, grip: Grip = Grip.NORMAL) {
        vm.dragBy(x)
        vm.rotate(90f)
        vm.place(grip)
    }

    /** 寝かせた棒を右端まで動かして置く。台に当たらずに落ちる */
    private fun placeOffPlatform(vm: StackViewModel, grip: Grip = Grip.NORMAL) {
        vm.dragBy(10f)
        placeFlat(vm, grip = grip)
    }

    /**
     * 1 個を台の真ん中に寝かせて置き、次の 1 個を台の外に落として終わらせる（台に 1 個残った、送れる結果になる）。
     * 棒は縦長なので寝かせて置く（立てて落とすと倒れて台から落ちることがあるため）
     */
    private fun finishWithTower(vm: StackViewModel) {
        placeFlat(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        placeOffPlatform(vm)
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
    }
}

/** 決まった順に番号を返す乱数（[StackViewModel] が次に出すパーツを決めるのに使う） */
internal class SequenceRandom(private val indices: List<Int>) : Random() {
    private var next = 0
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(until: Int): Int = indices[next++ % indices.size] % until
}
