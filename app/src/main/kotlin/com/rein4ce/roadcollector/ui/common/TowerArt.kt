package com.rein4ce.roadcollector.ui.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/*
 * 画面の上に飾る積み木の塔の絵の描き方（決定 82）。ホームの塔（アプリのアイコン。res/drawable/ic_launcher_foreground）と同じ質感にする：
 * - 色だけのはっきりした面。縁取り・下にのぞく濃い側面・影は付けない
 * - 道のパーツ（L・橋・棒）は、角の立った同じ太さの棒を組み合わせる
 * - 平たい板と台だけ、角を少し丸める
 * - 傾けない（まっすぐ積む）
 * 座標は絵の目盛り（[u] 倍して画面の大きさにする）。みちをつむ（ui/game/ModeSelect.kt）と「みんな」のホーム（ui/gallery/GalleryHome.kt）で使う
 */

/** 角を少し丸めた平たい板（台・板）。左上 ([x], [y])・幅 [w]・高さ [h]・角の丸み [r]（絵の目盛り） */
internal fun DrawScope.artSlab(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float, u: Float) {
    drawRoundRect(color, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u), cornerRadius = CornerRadius(r * u))
}

/** 角の立った棒をいくつか組み合わせた 1 つのパーツ（L・橋など）。[bars] は (左, 上, 幅, 高さ) の並び（絵の目盛り） */
internal fun DrawScope.artBars(color: Color, u: Float, vararg bars: FloatArray) {
    for (b in bars) drawRect(color, topLeft = Offset(b[0] * u, b[1] * u), size = Size(b[2] * u, b[3] * u))
}

/** 棒 1 本ぶん（左, 上, 幅, 高さ）。[artBars] に渡す */
internal fun bar(x: Float, y: Float, w: Float, h: Float): FloatArray = floatArrayOf(x, y, w, h)

/** 5 つの角の星（1 番の印）。まん中 ([cx], [cy])・外の半径 [r]（絵の目盛り）。ほかの積み木と同じく面だけで、傾けない */
internal fun DrawScope.artStar(color: Color, cx: Float, cy: Float, r: Float, u: Float) {
    val path = Path().apply {
        for (i in 0 until 10) {
            val radius = (if (i % 2 == 0) r else r * STAR_INNER) * u
            val a = Math.toRadians(-90.0 + i * 36.0).toFloat()
            val x = cx * u + radius * cos(a)
            val y = cy * u + radius * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, color)
}

/** 星の内側の角の半径（外の半径の何倍か） */
private const val STAR_INNER = 0.45f
