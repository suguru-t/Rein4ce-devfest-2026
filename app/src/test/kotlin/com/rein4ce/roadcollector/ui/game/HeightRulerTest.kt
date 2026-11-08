package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.data.PartOrigin
import com.rein4ce.roadcollector.data.WalkDistance
import com.rein4ce.roadcollector.data.walkDistance
import org.junit.Assert.*
import org.junit.Test

class HeightRulerTest {
    @Test fun `zero is the platform top across zoom levels`() {
        for (zoom in listOf(1f, .5f, .2f)) {
            val viewport = StackViewport(320f, 600f, zoom)
            val zero = heightTicks(viewport, 8f, 28f).first { it.meters == 0f }
            assertEquals(viewport.toScreenY(0f), zero.screenY, .001f)
        }
    }
    @Test fun `follow camera ticks retain absolute height and clip offscreen zero`() {
        val viewport = StackViewport(320f, 200f, cameraY = 10f)
        val ticks = heightTicks(viewport, 8f, 28f)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.none { it.meters == 0f })
        ticks.forEach {
            assertEquals(it.meters, viewport.toWorldY(it.screenY), .0001f)
            assertTrue(it.screenY in 0f..200f)
        }
    }
    @Test fun `minor ticks are twenty cm when visible and labels thin out at low zoom`() {
        val full = heightTicks(StackViewport(500f, 800f), 8f, 28f)
        assertEquals(.2f, full[1].meters - full[0].meters, .001f)
        val small = heightTicks(StackViewport(250f, 800f, .2f), 8f, 28f)
        assertTrue(small.all { it.label != null })
        small.zipWithNext().forEach { (a,b) -> assertTrue(kotlin.math.abs(a.screenY-b.screenY) >= 28f) }
    }
    @Test fun `narrowed canvas still maps drag to the drawn distance`() {
        val viewport = StackViewport(360f - 50f, 200f, .35f, 3f)
        val delta = viewport.dragToMeters(24f)
        assertEquals(24f, viewport.toScreenX(.1f + delta) - viewport.toScreenX(.1f), .001f)
        assertTrue(heightTicks(StackViewport(0f, 0f), 8f, 28f).isEmpty())
    }
    @Test fun `only verified walks count and sum cannot overflow Int`() {
        val sample = SampleParts.all.first()
        val a = sample.copy(id = PartOrigin.WALK.newId(), distanceM = Int.MAX_VALUE)
        val b = a.copy(id = PartOrigin.WALK.newId())
        val demo = a.copy(id = PartOrigin.DEMO.newId())
        val file = a.copy(id = PartOrigin.FILE.newId())
        val old = a.copy(id = "legacy-uuid")
        assertEquals(WalkDistance(2L * Int.MAX_VALUE, 4), walkDistance(listOf(a,b,a,sample,demo,file,old)))
    }
    @Test fun `playback modes have distinct persisted id prefixes`() {
        assertEquals(PartOrigin.WALK, PartOrigin.fromPlayback(null))
        assertEquals(PartOrigin.DEMO, PartOrigin.fromPlayback("デモ散歩"))
        assertEquals(PartOrigin.FILE, PartOrigin.fromPlayback("ファイル"))
        assertNotEquals(PartOrigin.WALK.newId(), PartOrigin.WALK.newId())
    }
    @Test fun `completed distance is frozen from the whole session`() {
        val a = SampleParts.all.first().copy(id = PartOrigin.WALK.newId(), distanceM = 500)
        val demo = a.copy(id = PartOrigin.DEMO.newId())
        val vm = StackViewModel(settleTiming = StackViewModel.SettleTiming(0.0, .01))
        vm.updateInventory(listOf(a,demo), true, GameSession.StartRequest(1, listOf(a.id,demo.id)))
        repeat(2) { vm.place(); vm.tick(.05) }
        val result = vm.result!!
        assertEquals(WalkDistance(500,1), result.walked)
        vm.updateInventory(emptyList(), true, null)
        repeat(10) { vm.tick(.05) }
        assertSame(result, vm.result)
    }
}
