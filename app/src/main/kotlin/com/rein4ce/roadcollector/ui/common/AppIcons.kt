package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 下のタブのアイコン。Material の標準セットに「靴」「鞄」「積み木」が無いので、24×24 のシンプルな塗りで自作。
 * 色は呼び出し側（NavigationBarItem）が tint で付けるので、ここでは黒で描く。
 */
object AppIcons {

    /** 採集：歩く靴（横から見たスニーカー） */
    val Shoe: ImageVector by lazy {
        ImageVector.Builder(name = "Shoe", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                // 靴本体（つま先が左、足首が右上）
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                    moveTo(2f, 16f)
                    curveTo(2f, 14.2f, 3f, 13f, 4.8f, 12.8f)
                    lineTo(9.4f, 12.2f)
                    lineTo(11.2f, 6.2f)
                    curveTo(11.4f, 5.5f, 12.1f, 5.1f, 12.8f, 5.3f)
                    lineTo(16.2f, 6.4f)
                    curveTo(16.7f, 6.6f, 17f, 7f, 17f, 7.5f)
                    lineTo(17.3f, 10.3f)
                    lineTo(20.3f, 12.3f)
                    curveTo(21.4f, 13f, 22f, 14.1f, 22f, 15.4f)
                    lineTo(22f, 16f)
                    close()
                    // 靴ひもの穴（くり抜き）
                    moveTo(12.9f, 7.4f)
                    lineTo(14.3f, 7.9f)
                    lineTo(14f, 8.9f)
                    lineTo(12.6f, 8.4f)
                    close()
                    moveTo(12.2f, 9.6f)
                    lineTo(13.6f, 10.1f)
                    lineTo(13.3f, 11.1f)
                    lineTo(11.9f, 10.6f)
                    close()
                }
                // 靴底（本体と少し離して、線が 1 本入っているように見せる）
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2f, 17f)
                    lineTo(22f, 17f)
                    lineTo(22f, 18f)
                    curveTo(22f, 18.7f, 21.4f, 19.3f, 20.7f, 19.3f)
                    lineTo(3.3f, 19.3f)
                    curveTo(2.6f, 19.3f, 2f, 18.7f, 2f, 18f)
                    close()
                }
            }
            .build()
    }

    /** 手持ち：採集した道を入れる鞄（持ち手つきのかばん） */
    val Bag: ImageVector by lazy {
        ImageVector.Builder(name = "Bag", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                // 持ち手（輪の内側をくり抜く）
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                    moveTo(8f, 9f)
                    lineTo(8f, 7.5f)
                    curveTo(8f, 5.3f, 9.8f, 3.5f, 12f, 3.5f)
                    curveTo(14.2f, 3.5f, 16f, 5.3f, 16f, 7.5f)
                    lineTo(16f, 9f)
                    lineTo(14.2f, 9f)
                    lineTo(14.2f, 7.5f)
                    curveTo(14.2f, 6.3f, 13.2f, 5.3f, 12f, 5.3f)
                    curveTo(10.8f, 5.3f, 9.8f, 6.3f, 9.8f, 7.5f)
                    lineTo(9.8f, 9f)
                    close()
                }
                // 本体（留め具の部分をくり抜く）
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                    moveTo(4.5f, 9f)
                    lineTo(19.5f, 9f)
                    curveTo(20.6f, 9f, 21.5f, 9.9f, 21.5f, 11f)
                    lineTo(21.5f, 18.5f)
                    curveTo(21.5f, 19.6f, 20.6f, 20.5f, 19.5f, 20.5f)
                    lineTo(4.5f, 20.5f)
                    curveTo(3.4f, 20.5f, 2.5f, 19.6f, 2.5f, 18.5f)
                    lineTo(2.5f, 11f)
                    curveTo(2.5f, 9.9f, 3.4f, 9f, 4.5f, 9f)
                    close()
                    // 留め具
                    moveTo(10.5f, 13f)
                    lineTo(13.5f, 13f)
                    lineTo(13.5f, 15.2f)
                    lineTo(10.5f, 15.2f)
                    close()
                }
            }
            .build()
    }

    /** 積む：積み木（下に 2 つ、上に 1 つ） */
    val Blocks: ImageVector by lazy {
        ImageVector.Builder(name = "Blocks", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                fun block(x: Float, y: Float, s: Float, r: Float = 1.2f) {
                    path(fill = SolidColor(Color.Black)) {
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
                }
                block(2.5f, 12.5f, 8.5f)
                block(13f, 12.5f, 8.5f)
                block(7.75f, 3f, 8.5f)
            }
            .build()
    }
}
