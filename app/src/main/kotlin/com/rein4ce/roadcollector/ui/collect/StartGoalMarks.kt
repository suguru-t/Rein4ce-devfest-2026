package com.rein4ce.roadcollector.ui.collect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.atan2
import kotlin.math.cos

/*
 * 記録画面の地図に出す、スタート・歩いているところ・ゴールの印。
 * - スタート：赤い旗（歩き始めた場所）
 * - 記録中・デモ散歩の再生中：今いる場所に足跡。点が増えるたびに左右の足が入れ替わり、一歩ずつ進んで見える。歩いている向きに回す
 * - 止めたあと：最後の点に青い旗（ゴール）
 * どの色の上（地図・オレンジの道）でも見えるよう、旗は白で縁取る。
 * 地図あり（Google Maps の印）と、地図なし（TrackCanvas）の両方で、同じ描き方を使う。
 */

/** 旗の竿の長さと、旗の幅 */
internal val FlagPoleHeight = 36.dp
internal val FlagWidth = 22.dp

/** スタートの旗の色（赤）とゴールの旗の色（測量の青） */
internal val StartFlagColor: Color get() = Fieldbook.Danger
internal val GoalFlagColor: Color get() = Fieldbook.Survey

/** 旗。[at] に竿の根もとを立て、上の右に [color] の三角の旗 */
internal fun DrawScope.drawFlag(at: Offset, color: Color) {
    val top = Offset(at.x, at.y - FlagPoleHeight.toPx())
    val w = FlagWidth.toPx()
    val flag = Path().apply {
        moveTo(top.x, top.y)
        lineTo(top.x + w, top.y + w * 0.33f)
        lineTo(top.x, top.y + w * 0.66f)
        close()
    }
    // 白い縁（竿と旗の下に、少し太く白を敷く）
    drawLine(Fieldbook.Card, at, top, strokeWidth = 6.dp.toPx())
    drawPath(flag, Fieldbook.Card, style = Stroke(width = 3.5.dp.toPx(), join = StrokeJoin.Round))
    // 竿と旗、根もとの点
    drawLine(Fieldbook.Ink, at, top, strokeWidth = 2.8.dp.toPx())
    drawPath(flag, color)
    drawCircle(Fieldbook.Card, radius = 3.6.dp.toPx(), center = at)
    drawCircle(Fieldbook.Ink, radius = 2.4.dp.toPx(), center = at)
}

/** 地図の印として使う旗（竿の根もとが箱の左下。地図の印の基準点は [FlagAnchor]） */
@Composable
internal fun FlagMarker(color: Color) {
    Canvas(Modifier.size(width = FlagWidth + 10.dp, height = FlagPoleHeight + 6.dp)) {
        drawFlag(Offset(4.dp.toPx(), size.height - 3.dp.toPx()), color)
    }
}

/** 旗の印の基準点（箱の中の割合）：竿の根もと */
internal val FlagAnchor: Offset
    get() = Offset(4f / (FlagWidth.value + 10f), 1f - 3f / (FlagPoleHeight.value + 6f))

/** 地図の今いる場所に出す足跡の大きさ */
internal val WalkerSize = 42.dp

/**
 * 地図の今いる場所に出す足跡（記録画面の歩いた長さの横と同じ足跡。[WalkingFootprints]）。
 * [step] が 1 増えるたびに、左右の足が入れ替わる（偶数はアイコンと同じ並び、奇数は 1 歩進んだ並び）
 */
@Composable
internal fun WalkerMarker(step: Int) {
    WalkingFootprints(steps = { (step % 2).toFloat() }, tint = Fieldbook.Ink, modifier = Modifier.size(WalkerSize))
}

/**
 * 歩いている向き [度]（北が 0、時計回り）。最後の点と、その少し前の点から求める（GPS の小さなぶれで向きが暴れないように、
 * 最後の点から [minMeters] 以上離れた点まで戻って比べる）。向きが決まらなければ 0（北）
 */
internal fun walkingBearing(points: List<TrackPoint>, minMeters: Double = 15.0): Float {
    if (points.size < 2) return 0f
    val last = points.last()
    val cosLat = cos(Math.toRadians(last.lat))
    for (i in points.lastIndex - 1 downTo 0) {
        val p = points[i]
        val dx = (last.lng - p.lng) * 111_320.0 * cosLat
        val dy = (last.lat - p.lat) * 110_540.0
        if (dx * dx + dy * dy >= minMeters * minMeters) {
            return Math.toDegrees(atan2(dx, dy)).toFloat()
        }
    }
    return 0f
}
