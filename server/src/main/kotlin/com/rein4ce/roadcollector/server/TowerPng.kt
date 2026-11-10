package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.svg.TowerSvg
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
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

    /** 色が入っているパーツ（決定 95）の縁の線の太さ（形の外側に出る幅）[px]。1200×630 の絵で、積む画面の線と同じくらいに見える太さ */
    private const val OUTLINE_PX = 3.0
    private val PLATFORM = Color(0x4A, 0x4E, 0x69)

    init {
        System.setProperty("java.awt.headless", "true")
    }

    /** @param platformWidth 台の幅 [m]。ふつうはタワーが持つ幅（広い台で積んだタワーは広い台で描く。決定 66） */
    fun render(tower: Tower, widthPx: Int = WIDTH, heightPx: Int = HEIGHT, platformWidth: Double = tower.platformWidth.toDouble()): ByteArray {
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
                // パーツは長辺が大きさ（scale）の箱に入るので、置いた点から大きさぶんを見ておく
                minX = min(minX, p.x - 1.0 * p.scale)
                maxX = max(maxX, p.x + 1.0 * p.scale)
                maxY = max(maxY, p.y + 1.0 * p.scale)
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
            g.fill(RoundRectangle2D.Double(sx(-platformWidth / 2), sy(0.0), platformWidth * scale, StackWorld.PLATFORM_THICKNESS * scale, 8.0, 8.0))

            // パーツ
            for (pl in tower.placements) {
                val part = partsById[pl.partId] ?: continue
                // 色があれば積む画面と同じ色（決定 95）。無ければ形ごとの色
                val colors = tower.colors[part.id]?.takeIf { TowerSvg.isHexColor(it.fill) && TowerSvg.isHexColor(it.outline) }
                g.color = Color.decode(colors?.fill ?: TowerSvg.colorOf(part.kind))
                // 形の多角形（長方形と曲がり角の継ぎ目。アプリ・物理と同じ）を 1 つのパスにまとめて塗る（継ぎ目に線が出ない）
                val path = Path2D.Double(Path2D.WIND_NON_ZERO)
                // 大きさ（歩いた距離で決まる）は、形を作るときに掛ける（物理と同じ）
                for (poly in PartGeometry.shapes(part, pl.scale.toDouble())) {
                    poly.points.forEachIndexed { i, v ->
                        if (i == 0) path.moveTo(v.x * scale, -v.y * scale) else path.lineTo(v.x * scale, -v.y * scale)
                    }
                    path.closePath()
                }
                val saved = g.transform
                g.translate(sx(pl.x.toDouble()), sy(pl.y.toDouble()))
                g.rotate(-pl.angleRad.toDouble()) // y 下向きなので回転も反転
                if (colors != null) {
                    // 形を太い線でなぞってから面を重ねる（外側だけが線になる。TowerSvg と同じ）
                    val fillColor = g.color
                    g.color = Color.decode(colors.outline)
                    val savedStroke = g.stroke
                    g.stroke = BasicStroke((OUTLINE_PX * 2).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.draw(path)
                    g.stroke = savedStroke
                    g.color = fillColor
                }
                g.fill(path)
                g.transform = saved
            }
        } finally {
            g.dispose()
        }
        // ImageIO の既定ではディスクの一時ファイルを経由することがあるので、この書き出しだけメモリ上で行う
        // （ImageIO.setUseCache(false) で共有設定を変えずに済む）。内側のストリームを閉じてから toByteArray() を呼ぶ
        return ByteArrayOutputStream().use { out ->
            MemoryCacheImageOutputStream(out).use { imageOut ->
                check(ImageIO.write(img, "png", imageOut)) { "PNG writer is unavailable" }
            }
            out.toByteArray()
        }
    }
}
