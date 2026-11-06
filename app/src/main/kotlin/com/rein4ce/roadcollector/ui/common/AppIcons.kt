package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 下のタブのアイコン。Material の標準セットに「靴」「鞄」「積み木」が無いので、24×24 のシンプルな塗りで自作。
 * 小さく出しても分かるように、細部は入れずに輪郭をはっきりさせている。
 * 色は呼び出し側（タブバー）が tint で付けるので、ここでは黒で描く。
 */
object AppIcons {

    /** 採集：歩く靴（横から見たハイキングブーツ。つま先が左、足首の筒が右） */
    val Shoe: ImageVector by lazy {
        ImageVector.Builder(name = "Shoe", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                // 本体：足首の筒（縦長）＋ つま先に向かって下がる甲。靴ひもの穴 2 つをくり抜く
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
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
                    moveTo(11.6f, 6f)
                    lineTo(15.4f, 6f)
                    lineTo(15.4f, 7.2f)
                    lineTo(11.6f, 7.2f)
                    close()
                    moveTo(11.6f, 8.8f)
                    lineTo(15.4f, 8.8f)
                    lineTo(15.4f, 10f)
                    lineTo(11.6f, 10f)
                    close()
                }
                // 靴底（本体と少し離して、線が 1 本入っているように見せる）
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2f, 18.6f)
                    lineTo(22f, 18.6f)
                    lineTo(22f, 19.8f)
                    curveTo(22f, 20.5f, 21.4f, 21f, 20.7f, 21f)
                    lineTo(3.3f, 21f)
                    curveTo(2.6f, 21f, 2f, 20.5f, 2f, 19.8f)
                    close()
                }
            }
            .build()
    }

    /** 手持ち：採集した道を入れる鞄（持ち手 2 本のトートバッグ） */
    val Bag: ImageVector by lazy {
        ImageVector.Builder(name = "Bag", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                // 持ち手（輪の内側をくり抜く）
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                    moveTo(7.5f, 8.5f)
                    lineTo(7.5f, 7f)
                    curveTo(7.5f, 4.5f, 9.5f, 2.5f, 12f, 2.5f)
                    curveTo(14.5f, 2.5f, 16.5f, 4.5f, 16.5f, 7f)
                    lineTo(16.5f, 8.5f)
                    lineTo(14.6f, 8.5f)
                    lineTo(14.6f, 7f)
                    curveTo(14.6f, 5.6f, 13.4f, 4.4f, 12f, 4.4f)
                    curveTo(10.6f, 4.4f, 9.4f, 5.6f, 9.4f, 7f)
                    lineTo(9.4f, 8.5f)
                    close()
                }
                // 本体（下に向かって少し広がる袋）
                path(fill = SolidColor(Color.Black)) {
                    moveTo(4.6f, 8f)
                    lineTo(19.4f, 8f)
                    lineTo(21f, 20.2f)
                    curveTo(21.1f, 20.9f, 20.5f, 21.5f, 19.8f, 21.5f)
                    lineTo(4.2f, 21.5f)
                    curveTo(3.5f, 21.5f, 2.9f, 20.9f, 3f, 20.2f)
                    close()
                }
            }
            .build()
    }

    /** 積む：積み木（下に 2 つ、上に 1 つ） */
    val Blocks: ImageVector by lazy {
        ImageVector.Builder(name = "Blocks", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                fun block(x: Float, y: Float, s: Float, r: Float = 1.3f) {
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
                val s = 9.6f
                block(1.5f, 12.4f, s)
                block(12.9f, 12.4f, s)
                block(7.2f, 2f, s)
            }
            .build()
    }
}
