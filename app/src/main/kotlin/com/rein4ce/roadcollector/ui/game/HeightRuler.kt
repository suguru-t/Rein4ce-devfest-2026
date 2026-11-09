package com.rein4ce.roadcollector.ui.game

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * 目盛り 1 本。[meters] は台の上面からの高さ [m]、[screenY] はキャンバスの上からの位置 [px]、
 * [label] は字（「1m」）。字を書かない細かい目盛りは null
 */
internal data class HeightTick(val meters: Float, val screenY: Float, val label: String?)

/**
 * 積む画面の、模型の高さの目盛り。塔を描くのと同じ [viewport]（追従で上げたカメラ・引いた倍率も含む）で位置を決めるので、
 * 目盛りと塔の高さがずれない。
 *
 * - 字を書く目盛りの刻みは、字の間が [labelGapPx] 以上空く 1・2・5 × 10 のべき [m] のうち最小のもの（ふつうは 1 m ごと）
 * - 1 m ごとのときは、間が [minorGapPx] 以上空けば 20 cm の細かい目盛り（字なし）も出す
 * - キャンバスに見えている範囲だけ返す。台の上面（0 m）より下は出さない
 */
internal fun heightTicks(viewport: StackViewport, minorGapPx: Float, labelGapPx: Float): List<HeightTick> {
    if (viewport.scale <= 0 || !viewport.scale.isFinite() || viewport.canvasHeight <= 0) return emptyList()
    val required = max(1.0, labelGapPx / viewport.scale.toDouble())
    val decade = 10.0.pow(floor(log10(required)))
    val major = listOf(1.0, 2.0, 5.0, 10.0).first { it * decade >= required } * decade
    val minor = if (MINOR_STEP_M * viewport.scale >= minorGapPx && major == 1.0) MINOR_STEP_M else major
    val low = max(0.0, viewport.toWorldY(viewport.canvasHeight).toDouble())
    val high = viewport.toWorldY(0f).toDouble()
    if (!low.isFinite() || !high.isFinite() || high < low) return emptyList()
    val start = ceil((low - 1e-6) / minor).toLong()
    val end = floor((high + 1e-6) / minor).toLong()
    // 縮みすぎて目盛りが多すぎるとき（ふつうは起きない）は描かない
    if (end - start > MAX_TICKS) return emptyList()
    val perLabel = (major / minor).let { kotlin.math.round(it).toLong() }.coerceAtLeast(1L)
    return (start..end).map { index ->
        val meters = (index * minor).toFloat()
        val labeled = index % perLabel == 0L
        HeightTick(meters, viewport.toScreenY(meters), if (labeled) "${meters.toInt()}m" else null)
    }
}

/** 細かい目盛りの刻み [m] */
private const val MINOR_STEP_M = 0.2

/** 返す目盛りの数の上限 */
private const val MAX_TICKS = 2000L
