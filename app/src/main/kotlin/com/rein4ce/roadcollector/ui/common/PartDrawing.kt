package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.PartGeometry
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.svg.TowerSvg
import kotlin.math.min

/** 種類ごとの色（SVG と同じ値を使う） */
fun Kind.color(): Color = Color(android.graphics.Color.parseColor(TowerSvg.colorOf(this)))

/**
 * パーツを描く。物理と同じ PartGeometry を使うので、見た目と当たり判定が一致する。
 * 形の多角形（長方形と曲がり角の継ぎ目）を 1 つのパスにまとめて塗るので、継ぎ目に線が出ず、半透明でも重なりが濃くならない。
 *
 * @param center     パーツ原点のスクリーン座標 [px]
 * @param scalePx    パーツの長辺 1.0 を何 px にするか
 * @param angleRad   パーツ全体の回転（反時計回り正、ワールドと同じ向き）
 */
fun DrawScope.drawPart(
    part: Part,
    center: Offset,
    scalePx: Float,
    angleRad: Float = 0f,
    color: Color = part.kind.color(),
    alpha: Float = 1f,
) {
    // Canvas は y 下向きなので、y を反転して置く（多角形の向きは全部そろって逆になるだけなので、重なりは塗りつぶされる）
    val path = Path()
    for (poly in PartGeometry.shapes(part, scalePx.toDouble())) {
        poly.points.forEachIndexed { i, v ->
            val x = center.x + v.x.toFloat()
            val y = center.y - v.y.toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    // 反時計回りの回転は負の角度にする
    rotate(degrees = -Math.toDegrees(angleRad.toDouble()).toFloat(), pivot = center) {
        drawPath(path, color = color, alpha = alpha)
    }
}

/**
 * 積み木（パーツ）を、配色（[PiecePalette]。決定 74）の面の色で塗り、自分の色を暗くした線で囲んで描く。
 * 線は、形を 1 つにまとめたパスを太い線でなぞってから面を重ねて作る（形どうしの継ぎ目の線は面に隠れ、外側だけが線になる）。
 * [alpha] が 1 より小さい（狙っている形のうすい影）ときは、線と面を 1 枚の層に描いてから薄くする（線の上に面が重なって濃くならないように）
 *
 * @param outlinePx 線の太さ（形の外側に出る幅）[px]
 */
fun DrawScope.drawPiece(
    part: Part,
    center: Offset,
    scalePx: Float,
    colors: PieceColors,
    outlinePx: Float,
    angleRad: Float = 0f,
    alpha: Float = 1f,
) {
    val path = Path()
    for (poly in PartGeometry.shapes(part, scalePx.toDouble())) {
        poly.points.forEachIndexed { i, v ->
            val x = center.x + v.x.toFloat()
            val y = center.y - v.y.toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    rotate(degrees = -Math.toDegrees(angleRad.toDouble()).toFloat(), pivot = center) {
        if (alpha >= 1f) {
            drawPieceOutlined(path, colors, outlinePx)
        } else {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
                drawPieceOutlined(path, colors, outlinePx)
                canvas.restore()
            }
        }
    }
}

/** 形のパスを、線（[PieceColors.outline]。外側へ [outlinePx]）で囲んでから、面（[PieceColors.fill]）で塗る */
fun DrawScope.drawPieceOutlined(path: Path, colors: PieceColors, outlinePx: Float) {
    drawPath(path, colors.outline, style = Stroke(width = outlinePx * 2, join = StrokeJoin.Round))
    drawPath(path, colors.fill)
}

/**
 * 物理ワールドの図形（台と置いたパーツ。`StackWorld.renderShapes`）を描く（積む画面・毎日 1 つずつ積む）。
 * 台は [platform] で塗るだけ。置いたパーツは、積み木の配色（[colorsOf]。決定 74）で、1 つの積み木（同じ `body` の図形）ずつ
 * まとめたパスを、自分の色を暗くした線で囲んでから塗る（[drawPieceOutlined]）。あとに置いた積み木ほど手前に描く
 *
 * @param toX ワールドの x [m] を画面の x [px] に
 * @param toY ワールドの y [m] を画面の y [px] に
 * @param scalePx 1 m が何 px か（丸い図形の半径に使う）
 */
fun DrawScope.drawWorldShapes(
    shapes: List<StackWorld.RenderShape>,
    platform: Color,
    outlinePx: Float,
    toX: (Double) -> Float,
    toY: (Double) -> Float,
    scalePx: Float,
    colorsOf: (Part) -> PieceColors,
) {
    var i = 0
    while (i < shapes.size) {
        val first = shapes[i]
        val path = Path()
        var j = i
        while (j < shapes.size && shapes[j].body == first.body) {
            when (val s = shapes[j]) {
                is StackWorld.RenderShape.Poly -> {
                    for (k in s.xs.indices) {
                        val x = toX(s.xs[k])
                        val y = toY(s.ys[k])
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                }
                is StackWorld.RenderShape.Disc -> path.addOval(Rect(Offset(toX(s.cx), toY(s.cy)), s.r.toFloat() * scalePx))
            }
            j++
        }
        val part = first.part
        if (part == null) drawPath(path, platform) else drawPieceOutlined(path, colorsOf(part), outlinePx)
        i = j
    }
}

/** 手持ち一覧などのサムネイル。 */
@Composable
fun PartThumbnail(part: Part, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scale = min(size.width, size.height) * 0.8f
        drawPart(part, center = Offset(size.width / 2, size.height / 2), scalePx = scale)
    }
}
