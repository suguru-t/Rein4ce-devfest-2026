package com.rein4ce.roadcollector.ui.common

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 方眼紙の上に書いた字の下だけ、方眼の線を消す（字に線がかぶって読みにくいため）。
 *
 * 字の部品（1 行）の場所を、アプリ全体の方眼（[com.rein4ce.roadcollector.ui.inventory.fieldbookGrid]）に知らせる。
 * 方眼は線を描いたあとに、その場所（外側に [bleedX]・[bleedY] だけ広げ、その分でなめらかに薄める）を紙の色で塗る。
 * 方眼の上には背景の天気（空の色・太陽・雨）が重なるので、紙の色が天気の上に浮いて見えることはなく、
 * どの天気でも「字の下だけ方眼の線がない」見た目になる（前は字の部品のうしろに紙の色を描いていたので、晴れの空の上で白く浮いた）。
 *
 * 見出し全体に 1 つ付けると字のない所まで線が消えて不自然なので、行ごとに付ける。
 * 横いっぱいに広がる部品には付けない（wrapContentWidth などで字の幅にしてから付ける）。描くだけなので、配置は変わらない
 */
fun Modifier.paperBand(bleedX: Dp = 14.dp, bleedY: Dp = 8.dp): Modifier = composed {
    val key = remember { Any() }
    val density = LocalDensity.current
    // 画面から消えたら（タブを切り替えたなど）、線を消す場所も外す
    DisposableEffect(key) { onDispose { GridClearings.zones.remove(key) } }
    onGloballyPositioned { coords ->
        val zone = GridClearings.Zone(
            bounds = coords.boundsInRoot(),
            bleedX = with(density) { bleedX.toPx() },
            bleedY = with(density) { bleedY.toPx() },
        )
        if (GridClearings.zones[key] != zone) GridClearings.zones[key] = zone
    }
}

/** 方眼の線を消す場所の一覧（[paperBand] が置き、方眼を描くときに読む） */
internal object GridClearings {
    /** [bounds] は画面（Compose のいちばん外側）の左上から [px] */
    data class Zone(val bounds: Rect, val bleedX: Float, val bleedY: Float)

    val zones = mutableStateMapOf<Any, Zone>()
}

/**
 * 方眼の線を描いたあとに呼ぶ：[GridClearings] の場所を紙の色で塗り、線を消す。4 辺はなめらかに薄めて、まわりの方眼に溶け込ませる。
 * 呼ぶ側（方眼）は画面いっぱいで、左上が画面の左上と同じであること
 *
 * @param opacity 紙の色の濃さ。1 で線が見えなくなる。少しだけ線を透かして、紙の上に書いた感じを残す
 */
internal fun DrawScope.drawGridClearings(opacity: Float = 0.9f) {
    val paper = Fieldbook.Paper.copy(alpha = opacity)
    val clear = Fieldbook.Paper.copy(alpha = 0f)
    for (zone in GridClearings.zones.values) {
        if (zone.bounds.isEmpty) continue
        val bx = zone.bleedX
        val by = zone.bleedY
        val left = zone.bounds.left - bx
        val top = zone.bounds.top - by
        val width = zone.bounds.width + bx * 2
        val height = zone.bounds.height + by * 2
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
