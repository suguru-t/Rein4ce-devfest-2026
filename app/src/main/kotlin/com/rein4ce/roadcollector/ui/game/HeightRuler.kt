package com.rein4ce.roadcollector.ui.game

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

internal data class HeightTick(val meters: Float, val screenY: Float, val label: String?)

/** Tick and tower coordinates use exactly the same animated viewport, including follow-camera Y. */
internal fun heightTicks(viewport: StackViewport, minorGapPx: Float, labelGapPx: Float): List<HeightTick> {
    if (viewport.scale <= 0 || !viewport.scale.isFinite() || viewport.canvasHeight <= 0) return emptyList()
    val required = max(1.0, labelGapPx / viewport.scale.toDouble())
    val decade = 10.0.pow(floor(kotlin.math.log10(required)))
    val major = listOf(1.0, 2.0, 5.0, 10.0).first { it * decade >= required } * decade
    val minor = if (.2 * viewport.scale >= minorGapPx && major == 1.0) .2 else major
    val low = max(0.0, viewport.toWorldY(viewport.canvasHeight).toDouble())
    val high = viewport.toWorldY(0f).toDouble()
    if (!low.isFinite() || !high.isFinite() || high < low) return emptyList()
    val start = ceil((low - 1e-6) / minor).toLong()
    val end = floor((high + 1e-6) / minor).toLong()
    if (end - start > 2000) return emptyList()
    return (start..end).map { index ->
        val meters = (index * minor).toFloat()
        val labeled = minor == major || index % 5L == 0L
        HeightTick(meters, viewport.toScreenY(meters), if (labeled) "${meters.toInt()} m" else null)
    }
}
