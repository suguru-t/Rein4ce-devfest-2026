package com.rein4ce.roadcollector.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 積む画面の、模型の高さの目盛り（heightTicks） */
class HeightRulerTest {

    @Test
    fun `0m の目盛りは、引いても台の上面に重なる`() {
        for (zoom in listOf(1f, 0.5f, 0.2f)) {
            val viewport = StackViewport(320f, 600f, zoom)
            val zero = heightTicks(viewport, 8f, 28f).first { it.meters == 0f }
            assertEquals(viewport.toScreenY(0f), zero.screenY, 0.001f)
            assertEquals("0m", zero.label)
        }
    }

    @Test
    fun `カメラを上げても目盛りは本当の高さのままで、見えない 0m は出さない`() {
        val viewport = StackViewport(320f, 200f, cameraY = 10f)
        val ticks = heightTicks(viewport, 8f, 28f)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.none { it.meters == 0f })
        ticks.forEach {
            assertEquals(it.meters, viewport.toWorldY(it.screenY), 0.0001f)
            assertTrue(it.screenY in 0f..200f)
        }
    }

    @Test
    fun `間が空けば 20cm の細かい目盛りを出し、引いたら字の目盛りを間引く`() {
        val full = heightTicks(StackViewport(500f, 800f), 8f, 28f)
        assertEquals(0.2f, full[1].meters - full[0].meters, 0.001f)
        // 字は 1 m ごと（5 本に 1 本）
        assertEquals(listOf("0m", "1m"), full.mapNotNull { it.label }.take(2))

        val small = heightTicks(StackViewport(250f, 800f, 0.2f), 8f, 28f)
        assertTrue(small.all { it.label != null })
        small.zipWithNext().forEach { (a, b) -> assertTrue(abs(a.screenY - b.screenY) >= 28f) }
    }

    @Test
    fun `目盛りはキャンバスに重ねて描くので、塔の大きさは目盛りが無いときと同じ`() {
        // 目盛りの列を別に取ると、キャンバスが狭くなって塔が 1 割ほど小さくなっていた（#87 のレビュー）。
        // 今は目盛りもキャンバス全体の変換で描くので、倍率は「キャンバスの幅 ÷ 5 m」のまま（360 px なら 1 m = 72 px）
        val viewport = StackViewport(360f, 640f)
        assertEquals(72f, viewport.scale, 0.001f)
        val one = requireNotNull(heightTicks(viewport, 8f, 28f).firstOrNull { it.label == "1m" })
        // 1m の目盛りは、高さ 1 m のところ（塔を描く位置と同じ）
        assertEquals(viewport.toScreenY(1f), one.screenY, 0.001f)
    }

    @Test
    fun `大きさが決まる前は目盛りを出さない`() {
        assertTrue(heightTicks(StackViewport(0f, 0f), 8f, 28f).isEmpty())
    }
}

