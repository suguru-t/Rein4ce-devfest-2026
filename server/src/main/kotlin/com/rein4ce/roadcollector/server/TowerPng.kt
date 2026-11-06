package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.svg.TowerSvg
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/**
 * 共有ページのプレビュー画像（og:image）を PNG で描く。
 *
 * LINE / X などのクローラは SVG を読まないので、og:image には PNG が要る。
 * Cloud Storage に保存せず、配置データから**その場で描く**（保存・権限の仕組みが要らない。タワーは
 * 保存後に変わらないので、応答はキャッシュさせる）。形の計算は SVG と同じ PartGeometry を使い、色も TowerSvg と揃える。
 *
 * - 大きさは OGP の推奨 1200 × 630（横長）。タワーは縦長なので、真ん中に縦いっぱいで描く
 * - **文字は描かない**：Cloud Run のコンテナ（eclipse-temurin:17-jre）には日本語フォントがなく、文字化けするため。
 *   タイトルや高さは og:title / og:description で出る
 * - java.awt はヘッドレス（画面なし）で使う
 */
object TowerPng {

    const val WIDTH = 1200
    const val HEIGHT = 630

    private val BACKGROUND = Color(0xFF, 0xF8, 0xF2)
    private val PLATFORM = Color(0x4A, 0x4E, 0x69)

    init {
        System.setProperty("java.awt.headless", "true")
    }

    fun render(tower: Tower, widthPx: Int = WIDTH, heightPx: Int = HEIGHT, platformWidth: Double = 1.4): ByteArray {
        val img = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = BACKGROUND
            g.fillRect(0, 0, widthPx, heightPx)

            // 表示範囲：台を含み、タワー全体が入るように（TowerSvg と同じ考え方。上下の余白は少し詰める）
            val partsById = tower.parts.associateBy { it.id }
            var minX = -platformWidth / 2 - 0.5
            var maxX = platformWidth / 2 + 0.5
            val minY = -0.4
            var maxY = max(tower.height.toDouble(), 1.0) + 0.4
            for (p in tower.placements) {
                minX = min(minX, p.x - 1.0)
                maxX = max(maxX, p.x + 1.0)
                maxY = max(maxY, p.y + 1.0)
            }
            val w = maxX - minX
            val h = maxY - minY
            val scale = min(widthPx / w, heightPx / h)
            val offX = (widthPx - w * scale) / 2
            val offY = (heightPx - h * scale) / 2
            fun sx(x: Double) = offX + (x - minX) * scale
            fun sy(y: Double) = heightPx - (offY + (y - minY) * scale) // 画像は y 下向き

            // 台
            g.color = PLATFORM
            g.fill(RoundRectangle2D.Double(sx(-platformWidth / 2), sy(0.0), platformWidth * scale, 0.3 * scale, 8.0, 8.0))

            // パーツ
            for (pl in tower.placements) {
                val part = partsById[pl.partId] ?: continue
                g.color = Color.decode(TowerSvg.colorOf(part.kind))
                val shapes = PartGeometry.shapes(part, 1.0)
                val saved = g.transform
                g.translate(sx(pl.x.toDouble()), sy(pl.y.toDouble()))
                g.rotate(-pl.angleRad.toDouble()) // y 下向きなので回転も反転
                for (s in shapes.segs) {
                    val segSaved = g.transform
                    g.translate(s.cx * scale, -s.cy * scale)
                    g.rotate(-s.angle)
                    g.fill(Rectangle2D.Double(-s.length * scale / 2, -s.thickness * scale / 2, s.length * scale, s.thickness * scale))
                    g.transform = segSaved
                }
                for (j in shapes.joints) {
                    val r = j.radius * scale
                    g.fill(Ellipse2D.Double(j.cx * scale - r, -j.cy * scale - r, r * 2, r * 2))
                }
                g.transform = saved
            }
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(img, "png", out)
            out.toByteArray()
        }
    }
}
