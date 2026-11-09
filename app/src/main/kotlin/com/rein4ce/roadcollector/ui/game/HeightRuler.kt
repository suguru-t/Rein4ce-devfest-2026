package com.rein4ce.roadcollector.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.min
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

/**
 * 目盛りの字の大きさ [dp]（「1m」）。「みんな」の断面図の標尺（TowerSection）と同じ。
 * 図の一部なので、文字の大きさの設定では大きくしない。前は 9dp で小さかったので、少し大きくした（決定 75）
 */
internal const val RULER_LABEL_DP = 11f

/** 目盛の縦の線の位置（キャンバスの左端から）[dp]。断面図の標尺の幅と同じ */
internal const val RULER_STAFF_DP = 30f

/**
 * 目盛りの見た目：線の色（[mark]）、1 m ごとの点線の色（[grid]）、字（太い丸ゴシック [RULER_LABEL_DP]・薄い墨）。
 * 目盛の字は「0m」〜「99m」くらいしか出ないので、測った結果を覚えておき、カメラが動いている間も毎コマ測り直さない
 */
internal class HeightRulerLook(val mark: Color, val grid: Color, private val measurer: TextMeasurer, private val style: TextStyle) {
    private val measured = HashMap<String, TextLayoutResult>()
    fun label(text: String): TextLayoutResult = measured.getOrPut(text) { measurer.measure(text, style) }
}

/** 積む画面の目盛りの見た目（[HeightRulerLook]）。ライト・ダークや画面の密度が変わったら作り直す */
@Composable
internal fun rememberHeightRuler(): HeightRulerLook {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val ink = Fieldbook.Muted
    val mark = Fieldbook.Mark
    val grid = Fieldbook.GridMajor
    return remember(ink, mark, grid, density, measurer) {
        val style = TextStyle(fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = with(density) { RULER_LABEL_DP.dp.toSp() }, color = ink)
        HeightRulerLook(mark, grid, measurer, style)
    }
}

/**
 * 模型の高さの目盛を、キャンバスの左端に重ねて描く（通常・毎日 1 つずつ積む・90 秒チャレンジ）。「みんな」の断面図の標尺（TowerSection）と同じ形：
 * 縦の線の左に「1m」の字と短い目盛り、右へ伸びる薄い点線。20 cm の細かい目盛りは、間が空くときだけ（[heightTicks]）。
 * 縦の線は台の上面（0 m）から上だけ（台が下に外れているときは、キャンバスの下の端から）
 *
 * @param ruler 目盛りの色と、字を測った結果（[rememberHeightRuler]）
 */
internal fun DrawScope.drawHeightRuler(viewport: StackViewport, ruler: HeightRulerLook) {
    val mark = ruler.mark
    val grid = ruler.grid
    val label = ruler::label
    val staffX = RULER_STAFF_DP.dp.toPx()
    val labelGap = maxOf(28.dp.toPx(), label("0m").size.height * 1.5f)
    val ticks = heightTicks(viewport, minorGapPx = 8.dp.toPx(), labelGapPx = labelGap)
    if (ticks.isEmpty()) return
    val bottom = min(size.height, viewport.toScreenY(0f))
    if (bottom > 0f) drawLine(mark, Offset(staffX, 0f), Offset(staffX, bottom), 1.dp.toPx())
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
    for (tick in ticks) {
        val y = tick.screenY
        val text = tick.label
        if (text == null) {
            drawLine(mark, Offset(staffX - 2.dp.toPx(), y), Offset(staffX, y), 1.dp.toPx())
            continue
        }
        drawLine(mark, Offset(staffX - 4.dp.toPx(), y), Offset(staffX, y), 1.dp.toPx())
        // 1 m ごとの薄い点線。台の上面（0 m）には台があるので引かない
        if (tick.meters > 0f) drawLine(grid, Offset(staffX, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
        val layout = label(text)
        val top = (y - layout.size.height / 2f).coerceIn(0f, (size.height - layout.size.height).coerceAtLeast(0f))
        drawText(layout, topLeft = Offset(staffX - 6.dp.toPx() - layout.size.width, top))
    }
}

