package com.rein4ce.roadcollector.core.svg

import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.StackWorld
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * タワー（最終配置）を SVG 文字列に描く。サーバの共有ページで使う。
 * 画像ファイルを保存しなくても、配置データだけから絵が出せる。
 */
object TowerSvg {

    fun colorOf(kind: Kind): String = when (kind) {
        Kind.BAR -> "#F2C14E"
        Kind.L -> "#E07A2A"
        Kind.U -> "#2A9D8F"
        Kind.RING -> "#6C8EBF"
        Kind.ZIGZAG -> "#C85C8E"
        Kind.OTHER -> "#8D99AE"
    }

    fun render(tower: Tower, widthPx: Int = 600, heightPx: Int = 800, platformWidth: Double = StackWorld.PLATFORM_WIDTH): String {
        val partsById = tower.parts.associateBy { it.id }

        // 表示範囲：台を含み、タワー全体が入るように
        var minX = -platformWidth / 2 - 0.5
        var maxX = platformWidth / 2 + 0.5
        var minY = -0.5
        var maxY = max(tower.height.toDouble(), 1.0) + 0.5
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
        fun sy(y: Double) = heightPx - (offY + (y - minY) * scale)

        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$widthPx" height="$heightPx" viewBox="0 0 $widthPx $heightPx">""")
        sb.append("""<rect width="100%" height="100%" fill="#FFF8F2"/>""")
        // 台
        sb.append(
            """<rect x="${f(sx(-platformWidth / 2))}" y="${f(sy(0.0))}" width="${f(platformWidth * scale)}" height="${f(StackWorld.PLATFORM_THICKNESS * scale)}" fill="#4A4E69" rx="4"/>""",
        )
        // パーツ
        for (pl in tower.placements) {
            val part = partsById[pl.partId] ?: continue
            val color = colorOf(part.kind)
            val shapes = PartGeometry.shapes(part, 1.0)
            val cx = sx(pl.x.toDouble())
            val cy = sy(pl.y.toDouble())
            val deg = -Math.toDegrees(pl.angleRad.toDouble()) // SVG は y 下向きなので回転も反転
            sb.append("""<g transform="translate(${f(cx)} ${f(cy)}) rotate(${f(deg)})">""")
            for (s in shapes.segs) {
                val segDeg = -Math.toDegrees(s.angle)
                sb.append(
                    """<rect x="${f(-s.length * scale / 2)}" y="${f(-s.thickness * scale / 2)}" width="${f(s.length * scale)}" height="${f(s.thickness * scale)}" fill="$color" transform="translate(${f(s.cx * scale)} ${f(-s.cy * scale)}) rotate(${f(segDeg)})"/>""",
                )
            }
            for (j in shapes.joints) {
                sb.append("""<circle cx="${f(j.cx * scale)}" cy="${f(-j.cy * scale)}" r="${f(j.radius * scale)}" fill="$color"/>""")
            }
            sb.append("</g>")
        }
        // 高さ
        sb.append(
            """<text x="${widthPx - 16}" y="36" text-anchor="end" font-family="sans-serif" font-size="28" font-weight="700" fill="#1F2A37">高さ ${String.format(Locale.US, "%.2f", tower.height)} m</text>""",
        )
        sb.append("</svg>")
        return sb.toString()
    }

    private fun f(v: Double): String = String.format(Locale.US, "%.1f", v)
}
