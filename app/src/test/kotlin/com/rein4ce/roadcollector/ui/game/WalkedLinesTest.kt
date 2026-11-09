package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.PartOrigin
import com.rein4ce.roadcollector.data.WalkDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 完成カードの「使った道」の行（walkedLines）と、その距離をゲームを始めたときに固定すること */
class WalkedLinesTest {

    @Test
    fun `歩いた道だけなら、合計の 1 行`() {
        assertEquals(
            listOf("使った道：合計 1.23 km"),
            walkedLines(WalkDistance(meters = 1234, walks = 2, others = 0, unknown = 0)),
        )
    }

    @Test
    fun `ほかのパーツが混ざっていれば、数えなかったことを 2 行目に`() {
        assertEquals(
            listOf("使った道：合計 0.50 km", "見本・デモ・歩いた記録か確かめられない道 3 本は距離に含めていません"),
            walkedLines(WalkDistance(meters = 500, walks = 1, others = 3, unknown = 1)),
        )
    }

    @Test
    fun `見本とデモだけで積んだら、合計 0 km は出さずに 1 行だけ`() {
        // 発表は見本とデモで積むので、毎回「合計 0.00 km」が出て失敗したように見えないように（#87 のレビュー）
        assertEquals(
            listOf("見本・デモの道なので、歩いた距離は出ません"),
            walkedLines(WalkDistance(meters = 0, walks = 0, others = 5, unknown = 0)),
        )
        assertEquals(
            listOf("歩いた記録か確かめられない道なので、歩いた距離は出ません"),
            walkedLines(WalkDistance(meters = 0, walks = 0, others = 5, unknown = 2)),
        )
    }

    @Test
    fun `使った道は、置いたパーツ（落ちたものも含む。同じパーツは 1 回）で数え、終わったときに固定する`() {
        val walk = SampleParts.all.first().copy(id = "walk", distanceM = 500)
        val demo = walk.copy(id = "demo")
        val unused = walk.copy(id = "unused")
        val origins = mapOf("walk" to PartOrigin.WALK, "demo" to PartOrigin.DEMO, "unused" to PartOrigin.WALK)
        // 歩いた道を 2 回、デモを 1 回出す（出さなかった「unused」は数えない）
        val vm = StackViewModel(random = SequenceRandom(listOf(0, 0, 1)))
        vm.updateInventory(listOf(walk, demo, unused), true, origins)
        repeat(2) {
            vm.rotate(90f)
            vm.place()
            tickUntil(vm) { vm.phase == StackViewModel.Phase.Placing }
        }
        // デモのパーツを台の外に落として終わらせる
        vm.dragBy(10f)
        vm.rotate(90f)
        vm.place()
        tickUntil(vm) { vm.phase == StackViewModel.Phase.Finished }
        val result = requireNotNull(vm.result)
        assertEquals(WalkDistance(meters = 500, walks = 1, others = 1, unknown = 0), result.walked)

        // 終わったあとにコレクションが空になっても、結果は変わらない
        vm.updateInventory(emptyList(), true)
        repeat(10) { vm.tick(0.05) }
        assertSame(result, vm.result)
    }

    private fun tickUntil(vm: StackViewModel, done: () -> Boolean) {
        repeat(20 * 60) {
            if (done()) return
            vm.tick(1.0 / 60)
        }
        error("did not finish (phase=${vm.phase})")
    }
}
