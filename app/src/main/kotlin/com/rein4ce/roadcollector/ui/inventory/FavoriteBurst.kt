package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * お気に入りに入れたときの、星の周りに散る小さな光。
 * [t] が 0 → 1 で、輪が広がって消え、粒が外へ飛びながら小さくなって消える（1 は何も描かない）
 */
internal fun DrawScope.drawFavoriteBurst(center: Offset, t: Float) {
    if (t >= 1f) return
    val fade = 1f - t
    // 広がる輪（はじめの半分だけ）
    if (t < 0.5f) {
        val k = t / 0.5f
        drawCircle(
            FavoriteStar.copy(alpha = 0.55f * (1f - k)),
            radius = (5.dp + 9.dp * k).toPx(),
            center = center,
            style = Stroke(width = (2.dp * (1f - k)).toPx().coerceAtLeast(0.5f)),
        )
    }
    // 6 つの粒：濃い黄色と淡い黄色を交互に。淡いほうは少し内側
    val inner = 7.dp.toPx()
    val outer = 17.dp.toPx()
    for (i in 0 until 6) {
        val a = (i * 60f - 90f) * PI.toFloat() / 180f
        val strong = i % 2 == 0
        val reach = if (strong) outer else outer - 3.dp.toPx()
        val d = inner + (reach - inner) * t
        val r = (if (strong) 2.4.dp else 1.8.dp).toPx() * (1f - 0.6f * t)
        drawCircle(
            (if (strong) FavoriteStar else SparkleLight).copy(alpha = fade),
            radius = r,
            center = Offset(center.x + cos(a) * d, center.y + sin(a) * d),
        )
    }
}

/** 散る光の淡い黄色 */
private val SparkleLight = Color(0xFFFFE27A)
