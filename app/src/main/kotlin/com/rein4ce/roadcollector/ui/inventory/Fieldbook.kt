package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.ui.common.drawPart
import kotlin.math.hypot
import kotlin.math.min

/**
 * 手持ち画面の見た目「測量野帳」。
 *
 * 方眼紙の野帳に、歩いた道を地図の道路のような縁取りの線で描く。
 * パーツごとに縮尺（実際に歩いた距離から計算）と北の向きを入れて、「本当に歩いた道」だと分かるようにする。
 * 形と太さは物理と同じ PartGeometry で描くので、積む画面での当たり判定と一致する。
 */
object Fieldbook {
    val Paper = Color(0xFFF3F6F0)
    val GridMinor = Color(0xFFE2E8DF)
    val GridMajor = Color(0xFFCED8CA)
    val Ink = Color(0xFF2F3A2E)
    val Muted = Color(0xFF5E6B5D)
    val Mark = Color(0xFF7B8C7A)
    val Road = Color(0xFFF08A3C)
    val RoadCasing = Color(0xFF6E2A10)
    val Survey = Color(0xFF1F5FA8)
    val SelectedPaper = Color(0xBFFFFFFF)

    /** 測量の数値（L=760 m など）に使う字 */
    val Data = FontFamily.Monospace

    /** 選んだ順の番号（＝積む順） */
    fun orderMark(order: Int): String = "①②③④⑤⑥⑦⑧⑨".getOrNull(order - 1)?.toString() ?: "($order)"
}

/** 方眼紙の地。細い線は 8dp ごと、太い線は 40dp ごと */
fun Modifier.fieldbookGrid(): Modifier = drawBehind {
    drawRect(Fieldbook.Paper)
    val step = 8.dp.toPx()
    val line = 1.dp.toPx()
    var i = 0
    var x = 0f
    while (x <= size.width) {
        drawLine(if (i % 5 == 0) Fieldbook.GridMajor else Fieldbook.GridMinor, Offset(x, 0f), Offset(x, size.height), line)
        x += step
        i++
    }
    i = 0
    var y = 0f
    while (y <= size.height) {
        drawLine(if (i % 5 == 0) Fieldbook.GridMajor else Fieldbook.GridMinor, Offset(0f, y), Offset(size.width, y), line)
        y += step
        i++
    }
}

/** 選んだパーツの破線の枠（測量の図面で範囲を囲む線のイメージ） */
fun Modifier.dashedOutline(color: Color, width: Dp = 1.5.dp, dash: Dp = 4.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx() * 0.75f))),
    )
}

/**
 * パーツを野帳の図として描く：四隅の見当、北、縁取りの道、縮尺。
 * 寸法は「150 マスの正方形」で決めてあり、実際の大きさに合わせて拡大縮小する。
 */
@Composable
fun FieldbookPart(part: Part, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Fieldbook.Data, fontSize = 9.sp, color = Fieldbook.Ink)
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val u = s / 150f
        val ox = (size.width - s) / 2
        val oy = (size.height - s) / 2
        val markStroke = 1.2f * u

        // 四隅の見当（図の範囲）
        val a = 6f * u
        val b = 16f * u
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(Fieldbook.Mark, Offset(ox + x, oy + y), Offset(ox + x + dx, oy + y), markStroke)
            drawLine(Fieldbook.Mark, Offset(ox + x, oy + y), Offset(ox + x, oy + y + dy), markStroke)
        }
        corner(a, a, b - a, b - a)
        corner(s - a, a, -(b - a), b - a)
        corner(a, s - a, b - a, -(b - a))
        corner(s - a, s - a, -(b - a), -(b - a))

        // 道：縁取り（少し太く暗い色）を描いてから、本体を重ねる
        val scalePx = s - 2 * 26f * u
        val center = Offset(ox + s / 2, oy + s / 2)
        val edgePx = 1.8f * u
        drawPart(part.copy(thickness = part.thickness + 2 * edgePx / scalePx), center, scalePx, color = Fieldbook.RoadCasing)
        drawPart(part, center, scalePx, color = Fieldbook.Road)

        // 北（パーツは北が上のまま正規化されている）。道に隠れないよう、道のあとに右上の見当の内側に描く
        val nx = ox + s - 13f * u
        drawText(measurer, "N", topLeft = Offset(nx - 3.5f * u, oy + 7f * u), style = label.copy(fontWeight = FontWeight.Bold))
        drawPath(
            Path().apply {
                moveTo(nx, oy + 20f * u)
                lineTo(nx - 3.5f * u, oy + 27f * u)
                lineTo(nx + 3.5f * u, oy + 27f * u)
                close()
            },
            Fieldbook.Ink,
        )

        // 縮尺：実際に歩いた距離 ÷ 図の上の道の長さ。図は角を間引いた形なので、だいたいの値
        val len = normalizedLength(part)
        if (part.distanceM > 0 && len > 0f) {
            val metersPerPx = part.distanceM / (len * scalePx)
            val barM = niceLength(28f * u * metersPerPx)
            val barPx = barM / metersPerPx
            val x0 = ox + 12f * u
            val y0 = oy + s - 12f * u
            drawLine(Fieldbook.Ink, Offset(x0, y0), Offset(x0 + barPx, y0), 2f * u)
            drawLine(Fieldbook.Ink, Offset(x0, y0 - 4f * u), Offset(x0, y0 + 4f * u), markStroke)
            drawLine(Fieldbook.Ink, Offset(x0 + barPx, y0 - 4f * u), Offset(x0 + barPx, y0 + 4f * u), markStroke)
            val text = if (barM >= 1000) "${barM / 1000} km" else "$barM m"
            drawText(measurer, text, topLeft = Offset(x0 + barPx + 3f * u, y0 - 7f * u), style = label)
        }
    }
}

/** 正規化された図の上の道の長さ（輪なら最後の点から最初の点まで含める） */
internal fun normalizedLength(part: Part): Float {
    val p = part.points
    var total = 0f
    for (i in 1 until p.size) total += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
    if (part.closed && p.size > 2) total += hypot(p.first().x - p.last().x, p.first().y - p.last().y)
    return total
}

/** 縮尺の目盛りに使う切りのいい長さ [m] */
internal fun niceLength(m: Float): Int = listOf(10, 20, 50, 100, 200, 500, 1000, 2000).firstOrNull { it >= m } ?: 5000
