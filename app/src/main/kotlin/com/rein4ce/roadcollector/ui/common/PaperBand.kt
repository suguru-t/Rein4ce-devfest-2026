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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 方眼紙の上に字を書くところの下地。紙の色を敷いて、方眼の線と背景の天気を字の下だけ隠す。
 * 下の端と右の端ではだんだん透明にして、まわりの方眼に溶け込ませる（新しい枠は増やさない）。
 *
 * 部品の外側まで広げて描く（左右は [sideBleed]、上は [topBleed]、下は [fade] だけ方眼に向かって薄くなる）。
 * 右は部品の幅の [solidUntil] の割合まで紙の色で、そこから右の端（＋[sideBleed]）にかけて透明になる。
 * 右上に出る晴れの太陽などを隠しすぎないため。描くだけなので、配置は変わらない
 *
 * @param opacity 紙の色の濃さ。1 で方眼が見えなくなる。少しだけ方眼を透かして、紙の上に書いた感じを残す
 */
fun Modifier.paperBand(
    sideBleed: Dp = 16.dp,
    topBleed: Dp = 120.dp,
    fade: Dp = 28.dp,
    solidUntil: Float = 0.62f,
    opacity: Float = 0.9f,
): Modifier = drawBehind {
    val paper = Fieldbook.Paper.copy(alpha = opacity)
    val clear = Fieldbook.Paper.copy(alpha = 0f)
    val left = -sideBleed.toPx()
    val right = size.width + sideBleed.toPx()
    val top = -topBleed.toPx()
    val bottom = size.height + fade.toPx()
    val area = Rect(left, top, right, bottom)
    // 縦の薄まり（紙の色）と横の薄まり（型抜き）を重ねるため、この範囲だけ別の層に描いてから戻す
    drawIntoCanvas { canvas ->
        canvas.saveLayer(area, Paint())
        drawRect(
            brush = Brush.verticalGradient(
                0f to paper,
                (size.height - top) / (bottom - top) to paper,
                1f to clear,
                startY = top,
                endY = bottom,
            ),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                (size.width * solidUntil - left) / (right - left) to Color.Black,
                1f to Color.Transparent,
                startX = left,
                endX = right,
            ),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()
    }
}
