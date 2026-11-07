package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.ui.common.drawPart
import kotlin.math.hypot
import kotlin.math.min

/**
 * 手持ち画面の見た目「測量野帳」。
 *
 * 方眼紙の野帳に、歩いた道を地図の道路のような縁取りの線で描く。
 * パーツごとに縮尺（実際に歩いた距離から計算）と北の向きを入れて、「本当に歩いた道」だと分かるようにする。
 * 形と太さは物理と同じ PartGeometry で描くので、積む画面での当たり判定と一致する。
 *
 * 色はライトとダーク（夜の野帳：黒に近い緑の紙に、明るい字）の 2 通りを持ち、[dark] で切り替わる。
 * 画面の部品だけでなく、方眼紙や道を描く処理（Canvas）の中でも同じ名前で使える。
 */
object Fieldbook {
    /**
     * いまダークモードか。設定（採集画面の歯車）と端末のダークテーマから、MainActivity が決めて入れる。
     * 色はどれもこれを見て値を返すので、読んだ画面の部品や絵は、切り替えると描き直される
     */
    var dark: Boolean by mutableStateOf(false)

    /** ライトのときの色と、ダークのときの色 */
    private fun c(light: Long, dark: Long): Color = Color(if (this.dark) dark else light)

    val Paper: Color get() = c(0xFFF3F6F0, 0xFF161B17)
    val GridMinor: Color get() = c(0xFFE2E8DF, 0xFF1F2620)
    val GridMajor: Color get() = c(0xFFCED8CA, 0xFF2D3830)
    val Ink: Color get() = c(0xFF2F3A2E, 0xFFE3E9DF)
    val Muted: Color get() = c(0xFF5E6B5D, 0xFFA3AFA0)
    val Mark: Color get() = c(0xFF7B8C7A, 0xFF7E8F7A)
    val Road: Color get() = c(0xFFF08A3C, 0xFFF59A52)
    val RoadCasing: Color get() = c(0xFF6E2A10, 0xFF5E2610)

    /**
     * 歩いたときの天気で変える道の色（本体と縁取り）。雨のなかまは青、雪は水色、くもり・霧は灰色。
     * 晴れ・天気が分からないときは、いつものオレンジ（[Road]）。
     * 白い紙片の上でも道の形が見えるよう、縁取りはどれも濃い色にする（雪の水色も、縁で形が分かる）
     */
    fun roadColors(sky: Sky?): Pair<Color, Color> = when (sky) {
        Sky.DRIZZLE, Sky.RAIN, Sky.THUNDER -> Color(0xFF3F7FC4) to Color(0xFF1B3A60)
        Sky.SNOW -> Color(0xFF8CCBEA) to Color(0xFF235777)
        Sky.CLOUDY, Sky.FOG -> Color(0xFF8D959C) to Color(0xFF3B4247)
        Sky.CLEAR, null -> Road to RoadCasing
    }
    /** 測量の青。字やボタンの色。ボタンの地にも使い、その上の字は白（ダークでも白い字が読める明るさにしてある） */
    val Survey: Color get() = c(0xFF1F5FA8, 0xFF4A8BD6)
    val SelectedPaper: Color get() = c(0xBFFFFFFF, 0x33FFFFFF)
    /** 削除など、取り消せない操作の赤（野帳の赤鉛筆） */
    val Danger: Color get() = c(0xFFB3412F, 0xFFE8806E)

    /** 方位磁針の針の赤（北） */
    val Needle: Color get() = c(0xFFD63A2A, 0xFFE5533F)
    val NeedleEdge: Color get() = c(0xFF8E2418, 0xFFB83A2A)

    /** 見出しの帯（設定の画面の「設定」の下に敷く灰色） */
    val Band: Color get() = c(0xFFDDE1DB, 0xFF2A302B)

    /** パーツ 1 本ぶんの紙片（方眼の上に置いた白い紙。ダークでは少し明るい黒）と、その縁 */
    val Card: Color get() = c(0xFFFBFCF9, 0xFF20271F)
    val CardEdge: Color get() = c(0xFFB8C5B3, 0xFF3A463C)

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

/** 選んだパーツの破線の枠（測量の図面で範囲を囲む線のイメージ）。[corner] でカードの角丸に合わせる */
fun Modifier.dashedOutline(color: Color, width: Dp = 1.5.dp, dash: Dp = 4.dp, corner: Dp = 0.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx() * 0.75f))),
    )
}

/**
 * 方位磁針の針：北が赤、南が白（墨の縁）。真ん中に軸、北の先の左に小さな赤い「N」。
 * パーツも記録中の軌跡も北が上のまま描くので、針はいつも上を向く。
 *
 * @param center 針の中心 [px]
 * @param length 針の長さ（北の先から南の先まで）[px]
 */
fun DrawScope.drawCompassNeedle(measurer: TextMeasurer, center: Offset, length: Float, labelStyle: TextStyle) {
    val half = length / 2
    val w = length * 0.2f
    val edge = (length * 0.045f).coerceAtLeast(1f)
    val north = Path().apply {
        moveTo(center.x, center.y - half)
        lineTo(center.x + w, center.y)
        lineTo(center.x - w, center.y)
        close()
    }
    val south = Path().apply {
        moveTo(center.x, center.y + half)
        lineTo(center.x + w, center.y)
        lineTo(center.x - w, center.y)
        close()
    }
    drawPath(south, Color.White)
    drawPath(south, Fieldbook.Ink, style = Stroke(width = edge, join = StrokeJoin.Round))
    drawPath(north, Fieldbook.Needle)
    drawPath(north, Fieldbook.NeedleEdge, style = Stroke(width = edge, join = StrokeJoin.Round))
    drawCircle(Fieldbook.Ink, radius = length * 0.075f, center = center)
    val n = measurer.measure("N", labelStyle.copy(color = Fieldbook.Needle, fontWeight = FontWeight.Bold))
    drawText(n, topLeft = Offset(center.x - w - n.size.width - length * 0.04f, center.y - half - n.size.height * 0.15f))
}

/**
 * パーツを野帳の図として描く：四隅の見当、北、縁取りの道、縮尺。
 * 寸法は「150 マスの正方形」で決めてあり、実際の大きさに合わせて拡大縮小する。
 * @param sky 歩いたときの空のようす。道の色が変わる（[Fieldbook.roadColors]）。分からなければ null（いつもの色）
 */
@Composable
fun FieldbookPart(part: Part, modifier: Modifier = Modifier, sky: Sky? = null) {
    val (road, casing) = Fieldbook.roadColors(sky)
    val measurer = rememberTextMeasurer()
    // 図の中の字（縮尺・北）は図の一部なので、図と同じ大きさのままにする（文字の大きさの設定では大きくしない）。
    // 図の大きさは決まっているので、字だけ大きくすると四隅の見当や道にぶつかる。
    // この図はコレクションのほか、採集画面の「パーツができました」でも使っている
    val density = LocalDensity.current
    val label = TextStyle(fontFamily = Fieldbook.Data, fontSize = with(density) { 9.dp.toSp() }, color = Fieldbook.Ink)
    val northSize = with(density) { 8.dp.toSp() }
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
        val scalePx = s * FIGURE_ROAD_RATIO
        val center = Offset(ox + s / 2, oy + s / 2)
        val edgePx = 1.8f * u
        drawPart(part.copy(thickness = part.thickness + 2 * edgePx / scalePx), center, scalePx, color = casing)
        drawPart(part, center, scalePx, color = road)

        // 北（パーツは北が上のまま正規化されている）。道に隠れないよう、道のあとに右上の見当の内側に描く
        drawCompassNeedle(measurer, center = Offset(ox + s - 13f * u, oy + 19f * u), length = 18f * u, labelStyle = label.copy(fontSize = northSize))

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

/**
 * 図の幅のうち、パーツの長辺（1.0）を描く割合。左右に 26/150 ずつ余白（四隅の見当・北・縮尺の場所）を残す。
 * 拡大表示の背景の地図（PlaceMap.kt）も、この割合で縮尺を合わせる
 */
internal const val FIGURE_ROAD_RATIO = (150f - 2 * 26f) / 150f

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
