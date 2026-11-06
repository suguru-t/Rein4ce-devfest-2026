package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 下のタブのアイコン。Material の標準セットに「靴」「鞄」「積み木」が無いので、24×24 で自作。
 * Instagram のタブのように、選択中は「塗り」、それ以外は「線画」を使い分ける（同じ形で 2 種類）。
 * 色は呼び出し側（タブバー）が tint で付けるので、ここでは黒で描く。
 */
object AppIcons {

    private const val STROKE = 1.8f

    private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply(build)
            .build()

    /** 塗りのパス */
    private fun ImageVector.Builder.filled(evenOdd: Boolean = false, draw: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            pathBuilder = draw,
        )
    }

    /** 線画のパス */
    private fun ImageVector.Builder.outlined(draw: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    // ---- 足跡（左右の足裏を少しずらして並べる。それぞれ足裏の楕円＋かかとの小さい楕円） ----

    private const val KAPPA = 0.5523f

    /** 楕円（4 本のベジェ曲線で近似） */
    private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        moveTo(cx, cy - ry)
        curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        close()
    }

    /** 足跡 1 つ。[shrink] は線画のときに線の太さぶん内側に寄せる量 */
    private fun PathBuilder.footprint(cx: Float, top: Float, shrink: Float = 0f) {
        ellipse(cx, top + 4.4f, 3.1f - shrink, 4.4f - shrink) // 足裏
        ellipse(cx, top + 11.6f, 2.3f - shrink, 1.9f - shrink) // かかと
    }

    val Footprints: ImageVector by lazy {
        icon("Footprints") {
            filled { footprint(7.2f, 2f) } // 左足（上）
            filled { footprint(16.8f, 8.5f) } // 右足（下）
        }
    }

    val FootprintsOutline: ImageVector by lazy {
        icon("FootprintsOutline") {
            val s = STROKE / 2
            outlined { footprint(7.2f, 2f + s, s) }
            outlined { footprint(16.8f, 8.5f + s, s) }
        }
    }

    // ---- 靴（横から見たハイキングブーツ。つま先が左、足首の筒が右）。今はタブでは足跡を使っている ----

    private fun PathBuilder.bootBody() {
        moveTo(2f, 17.5f)
        lineTo(2f, 16f)
        curveTo(2f, 14.6f, 3f, 13.5f, 4.4f, 13.2f)
        lineTo(9.6f, 12.2f)
        lineTo(9.6f, 4.6f)
        curveTo(9.6f, 3.9f, 10.2f, 3.3f, 10.9f, 3.3f)
        lineTo(16.1f, 3.3f)
        curveTo(16.8f, 3.3f, 17.4f, 3.9f, 17.4f, 4.6f)
        lineTo(17.4f, 11.4f)
        lineTo(20.4f, 13.1f)
        curveTo(21.4f, 13.7f, 22f, 14.7f, 22f, 15.8f)
        lineTo(22f, 17.5f)
        close()
    }

    private fun PathBuilder.bootSole() {
        moveTo(2f, 18.6f)
        lineTo(22f, 18.6f)
        lineTo(22f, 19.8f)
        curveTo(22f, 20.5f, 21.4f, 21f, 20.7f, 21f)
        lineTo(3.3f, 21f)
        curveTo(2.6f, 21f, 2f, 20.5f, 2f, 19.8f)
        close()
    }

    val Shoe: ImageVector by lazy {
        icon("Shoe") {
            filled(evenOdd = true) {
                bootBody()
                // 靴ひもの穴（くり抜き）
                moveTo(11.6f, 6f); lineTo(15.4f, 6f); lineTo(15.4f, 7.2f); lineTo(11.6f, 7.2f); close()
                moveTo(11.6f, 8.8f); lineTo(15.4f, 8.8f); lineTo(15.4f, 10f); lineTo(11.6f, 10f); close()
            }
            filled { bootSole() }
        }
    }

    val ShoeOutline: ImageVector by lazy {
        icon("ShoeOutline") {
            outlined { bootBody() }
            outlined { bootSole() }
            // 靴ひも（線）
            outlined { moveTo(11.8f, 6.6f); lineTo(15.2f, 6.6f) }
            outlined { moveTo(11.8f, 9.4f); lineTo(15.2f, 9.4f) }
        }
    }

    // ---- 鞄（持ち手 2 本のトートバッグ） ----

    private fun PathBuilder.bagBody() {
        moveTo(4.6f, 8f)
        lineTo(19.4f, 8f)
        lineTo(21f, 20.2f)
        curveTo(21.1f, 20.9f, 20.5f, 21.5f, 19.8f, 21.5f)
        lineTo(4.2f, 21.5f)
        curveTo(3.5f, 21.5f, 2.9f, 20.9f, 3f, 20.2f)
        close()
    }

    val Bag: ImageVector by lazy {
        icon("Bag") {
            // 持ち手（輪の内側をくり抜く）
            filled(evenOdd = true) {
                moveTo(7.5f, 8.5f); lineTo(7.5f, 7f)
                curveTo(7.5f, 4.5f, 9.5f, 2.5f, 12f, 2.5f)
                curveTo(14.5f, 2.5f, 16.5f, 4.5f, 16.5f, 7f)
                lineTo(16.5f, 8.5f); lineTo(14.6f, 8.5f); lineTo(14.6f, 7f)
                curveTo(14.6f, 5.6f, 13.4f, 4.4f, 12f, 4.4f)
                curveTo(10.6f, 4.4f, 9.4f, 5.6f, 9.4f, 7f)
                lineTo(9.4f, 8.5f); close()
            }
            filled { bagBody() }
        }
    }

    val BagOutline: ImageVector by lazy {
        icon("BagOutline") {
            outlined { bagBody() }
            // 持ち手（1 本の線の弧）
            outlined {
                moveTo(8.4f, 8f); lineTo(8.4f, 7f)
                curveTo(8.4f, 5f, 10f, 3.4f, 12f, 3.4f)
                curveTo(14f, 3.4f, 15.6f, 5f, 15.6f, 7f)
                lineTo(15.6f, 8f)
            }
        }
    }

    // ---- 積み木（下に 2 つ、上に 1 つ） ----

    private fun PathBuilder.block(x: Float, y: Float, s: Float, r: Float = 1.3f) {
        moveTo(x + r, y)
        lineTo(x + s - r, y)
        curveTo(x + s, y, x + s, y, x + s, y + r)
        lineTo(x + s, y + s - r)
        curveTo(x + s, y + s, x + s, y + s, x + s - r, y + s)
        lineTo(x + r, y + s)
        curveTo(x, y + s, x, y + s, x, y + s - r)
        lineTo(x, y + r)
        curveTo(x, y, x, y, x + r, y)
        close()
    }

    private const val BLOCK = 9.6f

    /** 三角の積み木（頂点が上、底辺が下）。[shrink] は線画のときに内側に寄せる量 */
    private fun PathBuilder.triangle(cx: Float, top: Float, w: Float, h: Float, r: Float = 1.2f, shrink: Float = 0f) {
        // 角を少し丸めた三角。shrink は角の丸みを増やすことで近似する
        val rr = r + shrink
        val halfW = w / 2 - shrink * 1.2f
        val t = top + shrink * 1.5f
        val b = top + h - shrink
        moveTo(cx - rr * 0.6f, t + rr)
        curveTo(cx - rr * 0.2f, t, cx + rr * 0.2f, t, cx + rr * 0.6f, t + rr)
        lineTo(cx + halfW - rr * 0.4f, b - rr * 1.4f)
        curveTo(cx + halfW + rr * 0.3f, b - rr * 0.3f, cx + halfW, b, cx + halfW - rr, b)
        lineTo(cx - halfW + rr, b)
        curveTo(cx - halfW, b, cx - halfW - rr * 0.3f, b - rr * 0.3f, cx - halfW + rr * 0.4f, b - rr * 1.4f)
        close()
    }

    /** 積む：□・○・△ の積み木（下に四角と丸、上に三角） */
    val Blocks: ImageVector by lazy {
        icon("Blocks") {
            filled { block(1.5f, 12.4f, BLOCK) }
            filled { ellipse(17.7f, 17.2f, 4.8f, 4.8f) }
            filled { triangle(12f, 1.8f, 11.4f, 9.8f) }
        }
    }

    val BlocksOutline: ImageVector by lazy {
        icon("BlocksOutline") {
            // 線が太いぶん、少し内側に
            val i = STROKE / 2
            outlined { block(1.5f + i, 12.4f + i, BLOCK - STROKE) }
            outlined { ellipse(17.7f, 17.2f, 4.8f - i, 4.8f - i) }
            outlined { triangle(12f, 1.8f, 11.4f, 9.8f, shrink = i) }
        }
    }
}
