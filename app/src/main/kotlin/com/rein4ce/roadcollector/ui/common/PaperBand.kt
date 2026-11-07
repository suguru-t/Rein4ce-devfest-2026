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
 * 方眼紙の上に字を書くところの下地。紙の色を敷いて、方眼の線と背景の天気を字の下だけ隠す。
 * 上・下・右の端ではだんだん透明にして、まわりの方眼に溶け込ませる（新しい枠は増やさない）。
 *
 * 部品の外側まで広げて描く（左右は [sideBleed]。上は [topFade]、下は [fade] だけ、外に向かって薄くなる）。
 * 字より上は方眼紙が見えるように、上の端のすぐ外から濃くする。
 * 右は部品の幅の [solidUntil] の割合まで紙の色で、そこから右の端（＋[sideBleed]）にかけて透明になる。
 * 右上に出る晴れの太陽などを隠しすぎないため。薄まり方は、直線ではなくなめらかな曲線（境目が線に見えないように）。
 * 描くだけなので、配置は変わらない
 *
 * @param opacity 紙の色の濃さ。1 で方眼が見えなくなる。少しだけ方眼を透かして、紙の上に書いた感じを残す
 */
fun Modifier.paperBand(
    sideBleed: Dp = 16.dp,
    topFade: Dp = 24.dp,
    fade: Dp = 52.dp,
    solidUntil: Float = 0.5f,
    opacity: Float = 0.9f,
): Modifier = drawBehind {
    val paper = Fieldbook.Paper.copy(alpha = opacity)
    val clear = Fieldbook.Paper.copy(alpha = 0f)
    val left = -sideBleed.toPx()
    val right = size.width + sideBleed.toPx()
    val top = -topFade.toPx()
    val bottom = size.height + fade.toPx()
    val area = Rect(left, top, right, bottom)
    // 縦の薄まり（紙の色）と横の薄まり（型抜き）を重ねるため、この範囲だけ別の層に描いてから戻す
    drawIntoCanvas { canvas ->
        canvas.saveLayer(area, Paint())
        drawRect(
            brush = Brush.verticalGradient(
                *(
                    easedStops(0f, (0f - top) / (bottom - top), clear, paper) +
                        easedStops((size.height - top) / (bottom - top), 1f, paper, clear)
                    ),
                startY = top,
                endY = bottom,
            ),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                *easedStops((size.width * solidUntil - left) / (right - left), 1f, Color.Black, Color.Transparent),
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
