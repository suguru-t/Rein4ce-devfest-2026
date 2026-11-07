package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 方眼紙の上に書いた字の下地。字の部品（1 行）のうしろにだけ紙の色を敷いて、方眼の線と背景の天気を隠す。
 * 部品の外側に [bleedX]・[bleedY] だけ広げ、その分でだんだん透明にして、まわりの方眼に溶け込ませる。
 *
 * 見出し全体に 1 枚敷くと、字のない所（数字の右のすき間など）まで方眼が消えて不自然なので、行ごとに付ける。
 * 字の幅だけにするため、横いっぱいに広がる部品には付けない（wrapContentWidth などで字の幅にしてから付ける）。
 * 薄まり方は、直線ではなくなめらかな曲線（境目が線に見えないように）。描くだけなので、配置は変わらない
 *
 * @param opacity 紙の色の濃さ。1 で方眼が見えなくなる。少しだけ方眼を透かして、紙の上に書いた感じを残す
 */
fun Modifier.paperBand(
    bleedX: Dp = 14.dp,
    bleedY: Dp = 8.dp,
    opacity: Float = 0.9f,
): Modifier = drawBehind {
    val paper = Fieldbook.Paper.copy(alpha = opacity)
    val clear = Fieldbook.Paper.copy(alpha = 0f)
    val bx = bleedX.toPx()
    val by = bleedY.toPx()
    val left = -bx
    val top = -by
    val width = size.width + bx * 2
    val height = size.height + by * 2
    // 縦の薄まり（紙の色）と横の薄まり（型抜き）を重ねるため、この範囲だけ別の層に描いてから戻す
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(left, top, left + width, top + height), Paint())
        drawRect(
            brush = Brush.verticalGradient(
                *(easedStops(0f, by / height, clear, paper) + easedStops(1f - by / height, 1f, paper, clear)),
                startY = top,
                endY = top + height,
            ),
            topLeft = Offset(left, top),
            size = Size(width, height),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                *(
                    easedStops(0f, bx / width, Color.Transparent, Color.Black) +
                        easedStops(1f - bx / width, 1f, Color.Black, Color.Transparent)
                    ),
                startX = left,
                endX = left + width,
            ),
            topLeft = Offset(left, top),
            size = Size(width, height),
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }
}

/**
 * [from] から [to] の位置で、[a] の色から [b] の色へなめらかに変わる色の区切り（両端はゆっくり、まん中は速く変わる）。
 * 直線で変えると、薄まり始めと終わりの所が線のように見えるため
 */
private fun easedStops(from: Float, to: Float, a: Color, b: Color, steps: Int = 8): Array<Pair<Float, Color>> =
    Array(steps + 1) { i ->
        val t = i / steps.toFloat()
        val eased = t * t * (3f - 2f * t)
        (from + (to - from) * t) to lerp(a, b, eased)
    }
