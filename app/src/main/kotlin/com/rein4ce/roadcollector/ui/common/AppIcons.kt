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

    // ---- 靴（横から見たハイキングブーツ。つま先が左、足首の筒が右） ----

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

    val Blocks: ImageVector by lazy {
        icon("Blocks") {
            filled { block(1.5f, 12.4f, BLOCK) }
            filled { block(12.9f, 12.4f, BLOCK) }
            filled { block(7.2f, 2f, BLOCK) }
        }
    }

    val BlocksOutline: ImageVector by lazy {
        icon("BlocksOutline") {
            // 線が太いぶん、少し内側に
            val i = STROKE / 2
            outlined { block(1.5f + i, 12.4f + i, BLOCK - STROKE) }
            outlined { block(12.9f + i, 12.4f + i, BLOCK - STROKE) }
            outlined { block(7.2f + i, 2f + i, BLOCK - STROKE) }
        }
    }
}
