package com.rein4ce.roadcollector.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.rein4ce.roadcollector.ui.collect.WalkingFootprints
import com.rein4ce.roadcollector.ui.collect.walkFootprints
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 下のタブのアイコン。選ばれていないときは線画、選ばれたときは塗りで、選ばれた瞬間にタブごとの動きをする。
 *
 * - 記録（足跡）：足跡が 2 歩、上へ歩く（記録画面の見出しの、歩いた長さの横の足跡と同じ動き）
 * - コレクション（ノート）：表紙が左へめくれて、方眼のページに描いた道（オレンジ）が見える。選ばれている間は開いたまま
 * - 積む（積み木）：上の三角が落ちてきて、下の積み木に乗る（乗った瞬間、下が少しつぶれて弾む）
 * - みんな（トロフィー）：トロフィーが跳ねて、カップに光が走り、まわりに小さな星がきらめく
 *
 * 動くのは選ばれた瞬間だけ。タブバーを作ったとき（アプリを開いたとき・画面の作り直し）に選ばれているタブは、動かさずに止まった形で出す。
 * 端末の設定でアニメーションを切っている人には動かさない（Compose が設定に従う）
 *
 * @param animate 選ばれた瞬間に動かすか。タブバーを作ったときに選ばれているタブ（アプリを開いたとき・画面の作り直し）は false
 */
@Composable
internal fun TabIcon(dest: Dest, selected: Boolean, animate: Boolean, tint: Color, modifier: Modifier = Modifier) {
    if (!selected) {
        Icon(dest.iconOutline, contentDescription = null, tint = tint, modifier = modifier)
        return
    }
    // 動きの進み具合（0 → 1）。はじめは動き終わった形（1）。選ばれるたびに、ここが新しく作られて動く
    val play = remember { Animatable(1f) }
    val walk = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // タブバーを作ったときに選ばれているタブは動かさない
        if (!animate) return@LaunchedEffect
        when (dest) {
            Dest.Collect -> walk.walkFootprints(count = 2)
            Dest.Inventory -> {
                play.snapTo(0f)
                play.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 220f))
            }
            Dest.Stack -> {
                play.snapTo(0f)
                play.animateTo(1f, tween(durationMillis = 800, easing = LinearEasing))
            }
            Dest.Gallery -> {
                play.snapTo(0f)
                play.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
            }
        }
    }
    when (dest) {
        Dest.Collect -> WalkingFootprints(steps = { walk.value }, tint = tint, modifier = modifier)
        else -> Canvas(modifier = modifier) {
            scale(size.minDimension / ICON_UNITS, pivot = Offset.Zero) {
                when (dest) {
                    Dest.Inventory -> drawOpeningNotebook(play.value, tint)
                    Dest.Stack -> drawStackingBlocks(play.value, tint)
                    Dest.Gallery -> drawShiningTrophy(play.value, tint)
                    else -> Unit
                }
            }
        }
    }
}

/**
 * ノート。[open] が 0 で閉じた形（塗りのアイコンと同じ）、1 で開いた形。表紙はリングの所（左の端）を中心に左へめくれ、
 * 裏返った表紙は罫線のページ、下のページは方眼に道。開くと横に広がるので、全体を少し小さくし、リングをまん中へ寄せる
 */
private fun DrawScope.drawOpeningNotebook(open: Float, ink: Color) {
    val t = open.coerceIn(0f, 1f)
    val w = AppIcons.NOTE_WIDTH
    val top = AppIcons.NOTE_TOP
    val h = AppIcons.NOTE_HEIGHT
    val corner = CornerRadius(1.4f)
    // リングの所（左の端）の位置：閉じているときは表紙の左の端、開くとまん中
    val spine = AppIcons.NOTE_LEFT + (12f - AppIcons.NOTE_LEFT) * t
    // 開くと幅が 2 倍になるので、見開きが 23 ほどに収まるまで小さくする
    scale(1f - (1f - OPEN_SPREAD / (2 * w)) * t, pivot = Offset(12f, 12f)) {
        // 下のページ：紙の色に墨の縁、うすい方眼と、道
        val page = Rect(spine, top, spine + w, top + h)
        drawRect(Fieldbook.Paper, topLeft = page.topLeft, size = page.size)
        if (t > 0f) {
            val grid = ink.copy(alpha = ink.alpha * 0.2f * t)
            var x = page.left + 2.6f
            while (x < page.right - 0.5f) { drawLine(grid, Offset(x, page.top + 0.8f), Offset(x, page.bottom - 0.8f), strokeWidth = 0.35f); x += 2.6f }
            var y = page.top + 2.6f
            while (y < page.bottom - 0.5f) { drawLine(grid, Offset(page.left + 0.8f, y), Offset(page.right - 0.8f, y), strokeWidth = 0.35f); y += 2.6f }
            // 曲がり角のある道（右肩上がりの線だと、グラフに見えるため）
            drawRoad(
                listOf(Offset(spine + 4.4f, 7f), Offset(spine + 4.4f, 16.8f), Offset(spine + 11f, 16.8f), Offset(spine + 11f, 11.2f)),
                alpha = t,
            )
        }
        drawRoundRect(ink, topLeft = page.topLeft, size = page.size, cornerRadius = corner, style = Stroke(width = 1.3f))
        // 表紙：リングの所を中心に横に縮んで裏返る（cos で 1 → -1）。表は背の線と題名の線の入った表紙、裏返ったら罫線のページ
        // ほかの部分と同じく 0〜1 に収めた t を使う（バネの行き過ぎで、表紙だけがもう一度揺れないように）
        val flip = cos(PI.toFloat() * t)
        scale(scaleX = flip, scaleY = 1f, pivot = Offset(spine, 12f)) {
            translate(left = spine - AppIcons.NOTE_LEFT) {
                val left = AppIcons.NOTE_LEFT
                if (flip >= 0f) {
                    drawPath(AppIcons.notebookCoverPath, ink)
                } else {
                    drawRect(Fieldbook.Paper, topLeft = Offset(left, top), size = Size(w, h))
                    val line = ink.copy(alpha = ink.alpha * 0.45f)
                    var y = top + 4.4f
                    while (y < top + h - 2f) { drawLine(line, Offset(left + 2.6f, y), Offset(left + w - 1.6f, y), strokeWidth = 0.6f); y += 2.9f }
                    drawRoundRect(ink, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = corner, style = Stroke(width = 1.3f))
                }
            }
        }
        // リング：左の端（開いたらまん中の綴じ目）の内と外に半分ずつかかる小さな輪。いちばん手前に描く
        AppIcons.NOTE_RINGS.forEach { y ->
            drawRoundRect(
                ink,
                topLeft = Offset(spine - AppIcons.RING_HALF_W, y - AppIcons.RING_HALF_H),
                size = Size(2 * AppIcons.RING_HALF_W, 2 * AppIcons.RING_HALF_H),
                cornerRadius = CornerRadius(AppIcons.RING_HALF_H),
                style = Stroke(width = AppIcons.RING_STROKE),
            )
        }
    }
}

/** コレクションの道と同じ見た目の小さな道（墨の縁取り＋オレンジ） */
private fun DrawScope.drawRoad(points: List<Offset>, alpha: Float = 1f) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, Fieldbook.RoadCasing.copy(alpha = alpha), style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Fieldbook.Road.copy(alpha = alpha), style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * 積み木。[t] が 0 → 1 で、上の三角が落ちてきて（はじめの 4 割）、乗った瞬間に下の積み木が少しつぶれ、三角が小さく弾んで止まる
 */
private fun DrawScope.drawStackingBlocks(t: Float, ink: Color) {
    val fall = 0.4f
    // 三角の高さのずれ（上がマイナス）。落ちる → 小さく跳ねる → 止まる
    val dropY = when {
        t < fall -> -12f * (1f - easeIn(t / fall))
        t < 1f -> -1.8f * sin(PI.toFloat() * ((t - fall) / (1f - fall)).coerceIn(0f, 1f)) * (1f - (t - fall) / (1f - fall))
        else -> 0f
    }
    // 乗った瞬間の、下の積み木のつぶれ（1 がふつう）
    val squash = if (t in fall..(fall + 0.3f)) 1f - 0.12f * sin(PI.toFloat() * (t - fall) / 0.3f) else 1f
    val baseBottom = 22f
    scale(scaleX = 1f + (1f - squash) * 0.5f, scaleY = squash, pivot = Offset(12f, baseBottom)) {
        drawPath(AppIcons.blockSquarePath, ink)
        drawPath(AppIcons.blockCirclePath, ink)
    }
    // 下がつぶれた分だけ、三角も下がる
    val sink = (baseBottom - BASE_TOP) * (1f - squash)
    val alpha = if (t < fall * 0.4f) (t / (fall * 0.4f)).coerceIn(0f, 1f) else 1f
    translate(top = dropY + sink) { drawPath(AppIcons.blockTrianglePath, ink.copy(alpha = ink.alpha * alpha)) }
}

/**
 * トロフィー。[t] が 0 → 1 で、跳ねて（はじめの 4 割）、カップに光が走り（3〜8 割）、まわりの星がきらめく（4〜10 割）
 */
private fun DrawScope.drawShiningTrophy(t: Float, ink: Color) {
    val hop = if (t < 0.4f) -2.6f * sin(PI.toFloat() * t / 0.4f) else 0f
    translate(top = hop) {
        drawPath(AppIcons.trophyCupPath, ink)
        drawPath(AppIcons.trophyEarsPath, ink, style = Stroke(width = AppIcons.STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(AppIcons.trophyStandPath, ink)
        // カップに斜めの光が左から右へ走る（カップの形で切り抜く）
        if (t in 0.25f..0.85f) {
            val k = (t - 0.25f) / 0.6f
            val x = -2f + 26f * FastOutSlowInEasing.transform(k)
            clipPath(AppIcons.trophyCupPath) {
                val band = Path().apply {
                    moveTo(x, 1f); lineTo(x + 3f, 1f); lineTo(x - 3f, 15f); lineTo(x - 6f, 15f); close()
                }
                drawPath(band, Fieldbook.Paper.copy(alpha = 0.85f))
            }
        }
    }
    // まわりの小さな星（順にふくらんで消える）
    drawSparkle(Offset(3.2f, 2.2f), phase(t, 0.35f, 0.75f), 3.2f)
    drawSparkle(Offset(21.2f, 1.6f), phase(t, 0.45f, 0.88f), 3.6f)
    drawSparkle(Offset(21.8f, 14.2f), phase(t, 0.58f, 1f), 2.6f)
}

/** [from]〜[to] の間で 0 → 1 → 0 になる値（その外は 0） */
private fun phase(t: Float, from: Float, to: Float): Float =
    if (t <= from || t >= to) 0f else sin(PI.toFloat() * (t - from) / (to - from))

/** 4 つの角の小さな星（金色）。[k] が大きさ（0 で描かない） */
private fun DrawScope.drawSparkle(center: Offset, k: Float, radius: Float) {
    if (k <= 0f) return
    val r = radius * k
    val w = r * 0.32f
    val star = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + w * 0.4f, center.y - w * 0.4f, center.x + r, center.y)
        quadraticTo(center.x + w * 0.4f, center.y + w * 0.4f, center.x, center.y + r)
        quadraticTo(center.x - w * 0.4f, center.y + w * 0.4f, center.x - r, center.y)
        quadraticTo(center.x - w * 0.4f, center.y - w * 0.4f, center.x, center.y - r)
        close()
    }
    drawPath(star, SparkleGold)
}

private fun easeIn(x: Float): Float = x * x

/** 星の金色（コレクションのお気に入りの ★ と同じ色） */
private val SparkleGold = Color(0xFFE0A100)

/** アイコンの一辺（AppIcons と同じ 24） */
private const val ICON_UNITS = 24f

/** 開いたノートの見開きの幅（24 の目盛りで） */
private const val OPEN_SPREAD = 23f

/** 下の積み木（四角と丸）の上の端 */
private const val BASE_TOP = 12.4f
