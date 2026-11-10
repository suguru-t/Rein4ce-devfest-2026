package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.PartColors
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.svg.TowerSvg
import java.awt.Color
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/** og:image 用の PNG。絵の中身までは見ず、「PNG として読める・大きさ・パーツの色が描かれている」を確かめる */
class TowerPngTest {

    private fun tower(): Tower {
        val parts = SampleParts.all.take(3)
        return Tower(
            id = "t1",
            parts = parts,
            placements = parts.mapIndexed { i, p -> Placement(p.id, x = 0f, y = 0.2f + i * 0.45f, angleRad = 0.1f * i) },
            height = 1.3f,
            createdAt = 0L,
        )
    }

    @Test
    fun `OGP の推奨サイズの PNG として読める`() {
        val bytes = TowerPng.render(tower())

        // PNG のシグネチャ
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), bytes.take(4).map { it.toInt() and 0xFF })
        val img = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
        assertEquals(TowerPng.WIDTH, img.width)
        assertEquals(TowerPng.HEIGHT, img.height)
    }

    @Test
    fun `使ったパーツの色が描かれている`() {
        val t = tower()
        val img = ImageIO.read(ByteArrayInputStream(TowerPng.render(t)))
        val colors = HashSet<Int>()
        for (y in 0 until img.height step 2) for (x in 0 until img.width step 2) colors += img.getRGB(x, y) and 0xFFFFFF

        for (part in t.parts) {
            val expected = Color.decode(TowerSvg.colorOf(part.kind)).rgb and 0xFFFFFF
            assertTrue(expected in colors, "${part.kind} の色 #${"%06X".format(expected)} が見つからない")
        }
    }

    @Test
    fun `配置が無いパーツや、空に近いタワーでも落ちない`() {
        val t = tower().copy(placements = emptyList())
        assertTrue(TowerPng.render(t).isNotEmpty())
    }

    @Test
    fun `パーツの色が入っていれば、その塗りと縁の色で描く（決定 95）。入っていないパーツは形ごとの色`() {
        val t = tower()
        val colored = t.copy(colors = mapOf(t.parts[0].id to PartColors(fill = "#5C95E0", outline = "#2E5A99")))
        val img = ImageIO.read(ByteArrayInputStream(TowerPng.render(colored)))
        val colors = HashSet<Int>()
        for (y in 0 until img.height) for (x in 0 until img.width) colors += img.getRGB(x, y) and 0xFFFFFF
        assertTrue(0x5C95E0 in colors, "塗りの色")
        assertTrue(0x2E5A99 in colors, "縁の色")
        assertTrue((Color.decode(TowerSvg.colorOf(t.parts[1].kind)).rgb and 0xFFFFFF) in colors, "色の無いパーツは形ごとの色")
        // SVG も同じ（縁の線を描いてから塗る）
        val svg = TowerSvg.render(colored)
        assertTrue(svg.contains("stroke=\"#2E5A99\""))
        assertTrue(svg.contains("fill=\"#5C95E0\""))
        assertTrue(svg.contains("fill=\"${TowerSvg.colorOf(t.parts[1].kind)}\""))
        // 形の違う値は使わない（形ごとの色で描く）
        val broken = t.copy(colors = mapOf(t.parts[0].id to PartColors(fill = "blue", outline = "#2E5A99")))
        assertTrue(!TowerSvg.render(broken).contains("stroke="))
    }
}
