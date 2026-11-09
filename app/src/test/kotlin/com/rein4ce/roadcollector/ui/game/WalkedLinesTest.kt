package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.GameSession
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
            listOf("使った道：合計 0.50 km", "見本・デモ・歩いた記録か確かめられないパーツ 3 個は距離に含めていません"),
            walkedLines(WalkDistance(meters = 500, walks = 1, others = 3, unknown = 1)),
        )
    }

    @Test
    fun `見本とデモだけで積んだら、合計 0 km は出さずに 1 行だけ`() {
        // 発表は見本とデモで積むので、毎回「合計 0.00 km」が出て失敗したように見えないように（#87 のレビュー）
        assertEquals(
            listOf("見本・デモのパーツなので、歩いた距離は出ません"),
            walkedLines(WalkDistance(meters = 0, walks = 0, others = 5, unknown = 0)),
        )
        assertEquals(
            listOf("歩いた記録か確かめられないパーツなので、歩いた距離は出ません"),
            walkedLines(WalkDistance(meters = 0, walks = 0, others = 5, unknown = 2)),
        )
    }

    @Test
    fun `使った道は、落ちたパーツも含めて、始めたときの値で固定する`() {
        val walk = SampleParts.all.first().copy(id = "walk", distanceM = 500)
        val demo = walk.copy(id = "demo")
        val origins = mapOf("walk" to PartOrigin.WALK, "demo" to PartOrigin.DEMO)
        val vm = StackViewModel(settleTiming = StackViewModel.SettleTiming(0.0, 0.01))
        vm.updateInventory(listOf(walk, demo), true, GameSession.StartRequest(1, listOf("walk", "demo")), origins)
        repeat(2) {
            vm.place()
            vm.tick(0.05)
        }
        val result = requireNotNull(vm.result)
        assertEquals(WalkDistance(meters = 500, walks = 1, others = 1, unknown = 0), result.walked)

        // 終わったあとにコレクションが空になっても、結果は変わらない
        vm.updateInventory(emptyList(), true, null)
        repeat(10) { vm.tick(0.05) }
        assertSame(result, vm.result)
    }
}
