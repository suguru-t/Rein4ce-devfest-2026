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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * 記録画面の地図に出す、スタートとゴールの印（今いる場所の青い点と向きの扇は HeadingBeam.kt）。
 * - スタート：赤い旗（歩き始めた場所）
 * - 止めたあと：最後の点に青い旗（ゴール）
 * どの色の上（地図・オレンジの道）でも見えるよう、旗は白で縁取る。
 * 歩き始めの近くで止めたとき（一周した・同じ道を戻った）は、2 本が重なって 1 本に見えないよう、
 * 根もとから左右に開いて立てる（赤は右へ傾け、青は左右を反転して左へ傾ける）。
 * 地図あり（Google Maps の印）と、地図なし（TrackCanvas）の両方で、同じ描き方を使う。
 */

/** 旗の竿の長さと、旗の幅 */
internal val FlagPoleHeight = 36.dp
internal val FlagWidth = 22.dp

/** スタートの旗の色（赤）とゴールの旗の色（測量の青） */
internal val StartFlagColor: Color get() = Fieldbook.Danger
internal val GoalFlagColor: Color get() = Fieldbook.Survey

/** スタートとゴールの旗を左右に開くときの傾き [度] */
internal const val FLAG_SPREAD_DEGREES = 14f

/**
 * 地図の今の拡大 [zoom] で、旗の幅（[FlagWidth]）が地面で何 m になるか（緯度 [lat] の所）。
 * 地図で、スタートとゴールがこれより近ければ、旗が重なって見えるので左右に開く。
 * 地図なし（TrackCanvas）が画面の上の距離で比べるのと同じく、どの拡大でもそろう
 * （北緯 35.75° では、拡大 15 で約 85 m、16 で約 43 m、17 で約 21 m）
 */
internal fun flagWidthMeters(lat: Double, zoom: Float): Double =
    METERS_PER_DP_AT_ZOOM_0 * cos(Math.toRadians(lat)) / 2.0.pow(zoom.toDouble()) * FlagWidth.value

/** Google の地図で、拡大 0 のときの赤道での 1dp あたりの長さ [m]（地球の周 ÷ 256） */
private const val METERS_PER_DP_AT_ZOOM_0 = 156_543.03392

/**
 * 旗。[at] に竿の根もとを立て、上の右に [color] の三角の旗。
 * [mirrored] なら左右を反転（旗が竿の左）、[tilt] は根もとを中心に時計回りに傾ける角度 [度]
 */
internal fun DrawScope.drawFlag(at: Offset, color: Color, mirrored: Boolean = false, tilt: Float = 0f) {
    rotate(tilt, pivot = at) {
        scale(if (mirrored) -1f else 1f, 1f, pivot = at) { drawUprightFlag(at, color) }
    }
}

private fun DrawScope.drawUprightFlag(at: Offset, color: Color) {
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

/** 地図の印の箱の半分の幅（旗の幅に、傾けたときのはみ出しと白い縁を足す） */
private val FlagMarkerHalfWidth = FlagWidth + 14.dp

/**
 * 地図の印として使う旗。竿の根もとは箱の下の真ん中（地図の印の基準点は [FlagAnchor]）なので、
 * 左右反転・傾けても基準点は変わらない
 */
@Composable
internal fun FlagMarker(color: Color, mirrored: Boolean = false, tilt: Float = 0f) {
    Canvas(Modifier.size(width = FlagMarkerHalfWidth * 2, height = FlagPoleHeight + 6.dp)) {
        drawFlag(Offset(size.width / 2, size.height - 3.dp.toPx()), color, mirrored, tilt)
    }
}

/** 旗の印の基準点（箱の中の割合）：竿の根もと */
internal val FlagAnchor: Offset
    get() = Offset(0.5f, 1f - 3f / (FlagPoleHeight.value + 6f))

/**
 * スタートの赤い旗と、ゴールの青い旗を立てる（[goal] が null ならゴールは立てない。記録中など）。
 * 2 本の根もとが旗の幅より近いと、後の旗に隠れて 1 本に見えるので、赤い旗は右へ、青い旗は左右を反転して左へ、
 * [FLAG_SPREAD_DEGREES] ずつ傾けて根もとから開く（一周した道・同じ道を戻ってきた道・輪）。
 * 画面に絵として描くところで共通に使う：記録画面の地図なしの図（CollectScreen の TrackCanvas）と、
 * コレクションの「もう一度歩く」（ui/inventory/Fieldbook.kt）。
 * 地図ありの記録画面は地図の印なので、開くかどうかを地図の拡大で決める（[flagWidthMeters]）が、開き方は同じ
 */
internal fun DrawScope.drawStartGoalFlags(start: Offset, goal: Offset?) {
    val spread = goal != null && (goal - start).getDistance() < FlagWidth.toPx()
    val tilt = if (spread) FLAG_SPREAD_DEGREES else 0f
    if (goal != null) drawFlag(goal, GoalFlagColor, mirrored = spread, tilt = -tilt)
    drawFlag(start, StartFlagColor, tilt = tilt)
}

/** 2 点の間のおおよその距離 [m]（旗を開くかどうかを決めるだけなので、平面で近似） */
internal fun roughMeters(a: TrackPoint, b: TrackPoint): Double {
    val cosLat = cos(Math.toRadians((a.lat + b.lat) / 2))
    val dx = (b.lng - a.lng) * 111_320.0 * cosLat
    val dy = (b.lat - a.lat) * 110_540.0
    return sqrt(dx * dx + dy * dy)
}
