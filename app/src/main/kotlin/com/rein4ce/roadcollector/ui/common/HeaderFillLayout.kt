package com.rein4ce.roadcollector.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * 上の部分（[header]）と下の行（[rows]）を、[minHeight] の高さに収まるように並べる（ホームと、積む画面のモードを選ぶ画面）：
 * - 行はふつうの大きさのまま、いちばん下に置く（親指の届く所）
 * - 上の部分は、いったんふつうの大きさで並べて測り、残りの高さと画面の幅に入る倍率で並べ直す（[maxScale] 倍まで）。
 *   倍率の上限で余った高さは、上の部分と行の間にあける。上の部分は、渡した倍率で中の大きさ（dp・sp）を大きくして描く
 * - 足りないとき（小さい画面・文字が大きいとき）は、どちらもふつうの大きさで上から並べ、画面ごと動かして見る
 * ふつうの大きさで測るほうは置かないので、画面には出ず、押せず、読み上げにも出ない
 *
 * @param gap 上の部分と行の間（いちばん狭いとき）
 */
@Composable
internal fun HeaderFillLayout(
    minHeight: Dp,
    maxScale: Float,
    gap: Dp,
    header: @Composable (scale: Float) -> Unit,
    rows: @Composable () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val width = constraints.maxWidth
        val full = Constraints(minWidth = width, maxWidth = width)
        val loose = Constraints(maxWidth = width)
        val rowsPlaced = subcompose(FillSlot.ROWS, rows).map { it.measure(full) }
        val rowsHeight = rowsPlaced.sumOf { it.height }
        val gapPx = gap.roundToPx()
        val probe = subcompose(FillSlot.MEASURE) { header(1f) }.map { it.measure(loose) }
        val probeHeight = probe.maxOfOrNull { it.height } ?: 0
        val probeWidth = probe.maxOfOrNull { it.width } ?: 0
        val room = minHeight.roundToPx() - rowsHeight - gapPx
        var scale = if (probeHeight > 0 && room > probeHeight) room.toFloat() / probeHeight else 1f
        if (probeWidth > 0) scale = minOf(scale, width.toFloat() / probeWidth)
        scale = scale.coerceIn(1f, maxScale)
        val headerPlaced = subcompose(FillSlot.HEADER) { header(scale) }.map { it.measure(loose) }
        val headerHeight = headerPlaced.maxOfOrNull { it.height } ?: 0
        val height = maxOf(headerHeight + gapPx + rowsHeight, minHeight.roundToPx())
        layout(width, height) {
            headerPlaced.forEach { it.placeRelative((width - it.width) / 2, 0) }
            var y = height - rowsHeight
            rowsPlaced.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/** [HeaderFillLayout] の並び：行・上の部分（測るだけ）・上の部分（出す） */
private enum class FillSlot { ROWS, MEASURE, HEADER }
