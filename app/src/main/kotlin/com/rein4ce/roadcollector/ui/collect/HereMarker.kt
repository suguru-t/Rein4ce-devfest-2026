package com.rein4ce.roadcollector.ui.collect

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.ui.common.Pop
import kotlin.math.cos
import kotlin.math.sin

/*
 * 記録画面の地図の「今いる場所」の印：黄色の歩く人のピクトグラム（案内標識のような、ひと目で分かる人型）。
 * 色は題字の積み木の黄色（Pop.Yellow）。地図のどの色の上でも見えるよう、濃い色で縁取る。
 * - 地図は上から見た絵、人は横から見た絵なので、人は回さずにいつもまっすぐ立てる。画面の上で東寄りを向いたら右向き、
 *   西寄りなら左向きにする（左右を反転するだけ。ちょうど北・南のあたりは、前の向きのまま）
 * - 正確な方位は、人のまわりを回るオレンジの三角（[HeadingPointer]）が指す。地図に寝かせた印なので、地図を回しても方位のまま
 * - 記録中は足を交互に出して歩き、記録していないときは一歩踏み出した姿のまま止まる（正面を向いた立ち姿だと、向きが分からず、
 *   トイレの案内の人のようにも見えるため）
 */

/** 人の姿：歩く 2 つの姿（記録中は交互に出す。記録していないときは [STEP_A] のまま） */
internal enum class WalkerPose { STEP_A, STEP_B }

/** 人の絵の大きさ（正方形の一辺） */
private val FigureSize = 46.dp

/** 人の色（題字の積み木の黄色）と、縁取りの色（黄色の面の上の墨と同じ） */
private val FigureColor: Color get() = Pop.Yellow
private val FigureEdge: Color get() = Pop.InkOnYellow

/** 縁取りの太さ [マス]（手足の線の両側に足す） */
private const val EDGE_UNITS = 1.6f

/** 方位の三角の、人の中心から三角の付け根までの長さ（人のすぐ外。離れすぎると人とのつながりが分かりにくい） */
private val PointerRadius = 22.dp

/** 方位の三角の幅と高さ */
private val PointerWidth = 20.dp
private val PointerHeight = 14.dp

/** 人の印の基準点（箱の中の割合）：絵のまん中が、今いる場所 */
internal val WalkerAnchor: Offset get() = Offset(0.5f, 0.5f)

/**
 * 今いる場所の人の、地図の印の絵（[facingRight] で向き、[pose] で姿）。ビットマップに直接描いて、ふつうの地図の印（Marker）の絵にする。
 * 向きと姿の組み合わせ（4 つ）ごとに 1 回だけ描いて使い回すので、記録中に 0.5 秒ごとに足を入れ替えても描き直さない
 */
@Composable
internal fun rememberWalkerIcon(facingRight: Boolean, pose: WalkerPose): BitmapDescriptor {
    val density = LocalDensity.current
    return remember(facingRight, pose, density) {
        val side = with(density) { FigureSize.toPx() }.roundToInt()
        val image = ImageBitmap(side, side)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat())) {
            drawWalkerFigure(facingRight, pose)
        }
        BitmapDescriptorFactory.fromBitmap(image.asAndroidBitmap())
    }
}

/** 人の絵を描く。右向きの形で決めてあり、左向きなら形の座標を左右反対にする。縁取り（太い濃い線）→ 黄色の順に重ねる */
private fun DrawScope.drawWalkerFigure(facingRight: Boolean, pose: WalkerPose) {
    val unit = size.minDimension / PICTO_UNITS
    val mirror = !facingRight
    drawWalker(unit = unit, pose = pose, color = FigureEdge, extra = EDGE_UNITS, mirror = mirror)
    drawWalker(unit = unit, pose = pose, color = FigureColor, extra = 0f, mirror = mirror)
}

/** 絵を [PICTO_UNITS] マスに分けたときの、人の形（右向き） */
private const val PICTO_UNITS = 24f

/** 人の線の太さ [マス] と、頭の半径 [マス] */
private const val LIMB_WIDTH = 2.9f
private const val HEAD_RADIUS = 2.5f

private fun DrawScope.drawWalker(unit: Float, pose: WalkerPose, color: Color, extra: Float, mirror: Boolean) {
    fun p(x: Float, y: Float) = Offset((if (mirror) PICTO_UNITS - x else x) * unit, y * unit)
    val stroke = Stroke(width = (LIMB_WIDTH + extra * 2) * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun limb(vararg xy: Float) {
        val path = Path()
        path.moveTo(p(xy[0], xy[1]).x, p(xy[0], xy[1]).y)
        var i = 2
        while (i < xy.size) {
            val q = p(xy[i], xy[i + 1])
            path.lineTo(q.x, q.y)
            i += 2
        }
        drawPath(path, color, style = stroke)
    }
    when (pose) {
        WalkerPose.STEP_A -> {
            // 大きく踏み出した姿：体を前に傾け、頭を前に出す。前の腕と後ろの脚、後ろの腕と前の脚が対になる。
            // 前の足はつま先を前へ向け、後ろの脚は後ろへ蹴り出す（左右を反転したときに、向きがはっきり分かるように）
            drawCircle(color, (HEAD_RADIUS + extra) * unit, p(14.6f, 4.6f))
            limb(13.4f, 8.2f, 11.2f, 14.2f) // 胴
            limb(13.2f, 9.2f, 15.6f, 12.2f, 17.6f, 11f) // 前の腕
            limb(13.2f, 9.2f, 10.4f, 11.4f, 8.6f, 13.6f) // 後ろの腕
            limb(11.2f, 14.2f, 14.2f, 17.2f, 16.4f, 20.6f, 18.2f, 20.6f) // 前の脚とつま先
            limb(11.2f, 14.2f, 9.8f, 17.8f, 6.4f, 19.4f) // 後ろの脚
        }
        WalkerPose.STEP_B -> {
            // 脚がすれ違う姿（歩幅を小さく）。同じく体は前に傾け、前の足のつま先を前へ
            drawCircle(color, (HEAD_RADIUS + extra) * unit, p(14.2f, 4.6f))
            limb(13.2f, 8.2f, 11.6f, 14.2f)
            limb(13f, 9.2f, 14.4f, 12.4f, 15.8f, 13.4f)
            limb(13f, 9.2f, 11.6f, 12.2f, 11f, 14.6f)
            limb(11.6f, 14.2f, 13.2f, 17.6f, 13.4f, 20.8f, 15.2f, 20.8f)
            limb(11.6f, 14.2f, 11f, 17.8f, 9.4f, 20.4f)
        }
    }
}

/**
 * 向いている方位の三角（地図に寝かせた印。地図の印の rotation で方位へ回す）。
 * 箱のまん中が今いる場所で、北（上）の人の外側に、外向きのオレンジの三角を描く
 */
@Composable
internal fun HeadingPointer() {
    val side = (PointerRadius + PointerHeight + 3.dp) * 2
    Canvas(Modifier.size(side)) {
        val c = Offset(size.width / 2, size.height / 2)
        val base = c.y - PointerRadius.toPx()
        val tip = base - PointerHeight.toPx()
        val half = PointerWidth.toPx() / 2
        val tri = Path().apply {
            moveTo(c.x, tip)
            lineTo(c.x + half, base)
            lineTo(c.x - half, base)
            close()
        }
        drawPath(tri, Color.White, style = Stroke(width = 5.dp.toPx(), join = StrokeJoin.Round))
        drawPath(tri, Pop.Orange)
    }
}

/**
 * 人を右向きにするか。[screenDeg] は画面の上で向いている方位（画面の上が 0、時計回り。方位から地図の回転を引いたもの）。
 * 東寄り（右）なら true、西寄り（左）なら false。ちょうど北・南のあたり（±10° ほど）は、前の向き [previous] のまま
 * （北を向いて小さく揺れるたびに、人がぱたぱた反転しないように）
 */
internal fun facingRight(screenDeg: Float, previous: Boolean): Boolean {
    val s = sin(Math.toRadians(screenDeg.toDouble()))
    return when {
        s > FACING_DEAD_ZONE -> true
        s < -FACING_DEAD_ZONE -> false
        else -> previous
    }
}

/** 北・南のあたりで向きを変えない幅（sin 10° ≈ 0.17） */
private const val FACING_DEAD_ZONE = 0.17

/**
 * 歩いている向きが東寄りか（スマホの向きが分からないとき、たとえばデモ散歩の再生中に、人の向きを決める）。
 * 最後の点から [minMeters] 以上離れた点まで戻って比べる（GPS の小さなぶれで向きが変わらないように）。決まらなければ null
 */
internal fun movingEast(points: List<TrackPoint>, minMeters: Double = 10.0): Boolean? {
    if (points.size < 2) return null
    val last = points.last()
    val cosLat = cos(Math.toRadians(last.lat))
    for (i in points.lastIndex - 1 downTo 0) {
        val p = points[i]
        val dx = (last.lng - p.lng) * 111_320.0 * cosLat
        val dy = (last.lat - p.lat) * 110_540.0
        if (dx * dx + dy * dy >= minMeters * minMeters) {
            // ほぼ真北・真南へ歩いているときは決めない
            return if (kotlin.math.abs(dx) < kotlin.math.abs(dy) * 0.18) null else dx > 0
        }
    }
    return null
}
