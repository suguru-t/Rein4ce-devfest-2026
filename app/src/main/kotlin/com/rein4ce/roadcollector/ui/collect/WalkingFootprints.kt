package com.rein4ce.roadcollector.ui.collect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rein4ce.roadcollector.ui.common.AppIcons.Foot
import kotlin.math.floor

/**
 * 歩いた長さの横の足跡（[com.rein4ce.roadcollector.ui.common.AppIcons.Footprints] と同じ形）。
 * 押すと、足跡が上へ 1 歩ずつ進む（上に新しい足跡が付き、下の古い足跡は消えていく。歩く人を追いかけて見ている形）。
 * 止まっているときは、アイコンと同じ 2 つ（左足が上、右足が下）。
 *
 * @param steps 進んだ歩数（[walkFootprints] で動かす）。偶数のときはアイコンと同じ並びになる
 */
@Composable
internal fun WalkingFootprints(steps: () -> Float, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clipToBounds()) {
        val unit = size.minDimension / ICON_UNITS
        val p = steps()
        val n = floor(p).toInt()
        // 今見えている足跡と、上から入ってくる足跡・下へ出ていく足跡
        for (k in n - 1..n + 2) {
            val top = restTop(k) + STEP * p
            // 上の足跡の場所より上は入ってくる途中、下の足跡の場所より下は出ていく途中なので、薄くする
            val alpha = when {
                top < UPPER_TOP -> 1f - (UPPER_TOP - top) / STEP
                top > LOWER_TOP -> 1f - (top - LOWER_TOP) / STEP
                else -> 1f
            }.coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            footprint(cx = if (k % 2 == 0) RIGHT_X else LEFT_X, top = top, unit = unit, color = tint.copy(alpha = tint.alpha * alpha))
        }
    }
}

/** 足跡を [count] 歩進める（1 歩ずつ、踏み出して止まる）。終わったら 0 に戻す（偶数歩なら見た目は同じ） */
internal suspend fun Animatable<Float, *>.walkFootprints(count: Int = 4) {
    snapTo(0f)
    for (i in 1..count) animateTo(i.toFloat(), tween(durationMillis = 260, easing = FastOutSlowInEasing))
    snapTo(0f)
}

/** 足跡 1 つ（足裏とかかと）。形はアイコンと同じ（[Foot]） */
private fun DrawScope.footprint(cx: Float, top: Float, unit: Float, color: Color) {
    fun oval(x: Float, y: Float, rx: Float, ry: Float) =
        drawOval(color, topLeft = Offset((x - rx) * unit, (y - ry) * unit), size = Size(2 * rx * unit, 2 * ry * unit))
    oval(cx, top + Foot.SOLE_DY, Foot.SOLE_RX, Foot.SOLE_RY) // 足裏
    oval(cx, top + Foot.HEEL_DY, Foot.HEEL_RX, Foot.HEEL_RY) // かかと
}

/** k 番目の足跡の、止まっているときの上端。0 は右足（下）、1 は左足（上）、2 はその先の右足… */
private fun restTop(k: Int): Float = LOWER_TOP - STEP * k

/** アイコンの一辺（AppIcons と同じ 24） */
private const val ICON_UNITS = 24f

/** 左足・右足の真ん中の x（アイコンと同じ） */
private const val LEFT_X = Foot.LEFT_X
private const val RIGHT_X = Foot.RIGHT_X

/** 止まっているときの、上の足跡（左足）と下の足跡（右足）の上端（アイコンと同じ） */
private const val UPPER_TOP = Foot.UPPER_TOP
private const val LOWER_TOP = Foot.LOWER_TOP

/** 1 歩の長さ（上の足跡と下の足跡の上端の差） */
private const val STEP = LOWER_TOP - UPPER_TOP
