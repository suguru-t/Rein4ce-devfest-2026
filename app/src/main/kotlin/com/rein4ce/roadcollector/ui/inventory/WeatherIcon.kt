package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rein4ce.roadcollector.core.weather.Sky
import kotlin.math.cos
import kotlin.math.sin

/**
 * 歩いたときの天気のアイコン（天気アプリと同じ絵）。野帳の図の左上に、右上の北と対になるように描く。
 * 道の色（[Fieldbook.roadColors]）と同じ色を使って、道の色の意味（青は雨、水色は雪…）も一緒に分かるようにする。
 *
 * - 晴れ：太陽 ・ くもり：雲 ・ 雨：雲と雨 3 本 ・ 雪：雪の結晶
 * - 画面に出す天気は 4 つ（決定 73。`Sky.shown`）なので、霧はくもりの雲、霧雨と雷雨は雨と同じ絵にする
 *   （前は霧の横に流れる線、霧雨の短い雨 2 本、雷雨の稲妻もあった）
 *
 * 雲は白っぽく塗って縁を濃い色で描き、雨・雪は道の色にする。
 * 線だけの絵（雪の結晶）は、ライトでは濃い縁の色、ダークでは道の色にする（暗い紙の上で濃い色は見えないため）
 *
 * @param center アイコンのまん中 [px]
 * @param size アイコンの一辺 [px]
 */
internal fun DrawScope.drawWeatherIcon(sky: Sky, center: Offset, size: Float) {
    val (road, casing) = Fieldbook.roadColors(sky)
    val s = size
    val edge = Stroke(width = 0.07f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val line = if (Fieldbook.dark) road else casing
    fun at(x: Float, y: Float) = Offset(center.x + x * s, center.y + y * s)

    when (sky) {
        Sky.CLEAR -> {
            // 太陽：丸と 8 本の光
            for (k in 0 until 8) {
                val a = k * Math.PI.toFloat() / 4f
                drawLine(road, at(0.31f * cos(a), 0.31f * sin(a)), at(0.46f * cos(a), 0.46f * sin(a)), strokeWidth = 0.08f * s, cap = StrokeCap.Round)
            }
            drawCircle(road, radius = 0.21f * s, center = center)
            drawCircle(casing, radius = 0.21f * s, center = center, style = edge)
        }
        // 画面に出す天気は 4 つ（決定 73）。霧はくもりの雲、霧雨と雷雨は雨の雲と雨粒
        Sky.CLOUDY, Sky.FOG -> cloud(center, s, casing, dy = 0f)
        Sky.RAIN, Sky.DRIZZLE, Sky.THUNDER -> {
            cloud(center, s, casing, dy = -0.14f)
            rain(at = ::at, xs = listOf(-0.2f, 0f, 0.2f), length = 0.2f, color = road, width = 0.08f * s)
        }
        Sky.SNOW -> {
            // 雪の結晶：中心を通る 3 本の線（6 本の腕）と、それぞれの腕の先の小さな V
            val w = 0.07f * s
            for (k in 0 until 6) {
                val a = (90f + k * 60f) * Math.PI.toFloat() / 180f
                val tip = at(0.44f * cos(a), -0.44f * sin(a))
                drawLine(line, center, tip, strokeWidth = w, cap = StrokeCap.Round)
                val mid = at(0.27f * cos(a), -0.27f * sin(a))
                for (side in listOf(-1f, 1f)) {
                    val b = a + side * Math.PI.toFloat() / 4f
                    drawLine(line, mid, Offset(mid.x + 0.13f * s * cos(b), mid.y - 0.13f * s * sin(b)), strokeWidth = w, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** 雲：丸 3 つと、角の丸い長方形を合わせた形。白っぽく塗り、縁を濃い色で描く。[dy] だけ上下にずらす（雨・稲妻の場所を空ける） */
private fun DrawScope.cloud(center: Offset, s: Float, edge: Color, dy: Float) {
    fun at(x: Float, y: Float) = Offset(center.x + x * s, center.y + (y + dy) * s)
    val shape = Path().apply { addOval(Rect(at(-0.17f, 0.02f), 0.17f * s)) }
    for (part in listOf(
        Path().apply { addOval(Rect(at(0.04f, -0.1f), 0.23f * s)) },
        Path().apply { addOval(Rect(at(0.23f, 0.04f), 0.15f * s)) },
        Path().apply {
            addRoundRect(RoundRect(Rect(at(-0.36f, 0f), at(0.38f, 0.19f)), CornerRadius(0.095f * s)))
        },
    )) {
        shape.op(shape, part, PathOperation.Union)
    }
    drawPath(shape, CloudFill)
    drawPath(shape, edge, style = Stroke(width = 0.07f * s, join = StrokeJoin.Round))
}

/** 雲の下の雨：斜めの短い線（背景の雨と同じく、右上から左下へ） */
private fun DrawScope.rain(at: (Float, Float) -> Offset, xs: List<Float>, length: Float, color: Color, width: Float) {
    for (x in xs) {
        drawLine(color, at(x + 0.06f, 0.22f), at(x + 0.06f - 0.3f * length, 0.22f + length), strokeWidth = width, cap = StrokeCap.Round)
    }
}

/** 雲の白（ライトでもダークでも、雲だと分かる明るさ） */
private val CloudFill = Color(0xFFF1F3F5)
