package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Placement
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
}
