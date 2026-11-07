package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.PartGeometry
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

/** 手持ち一覧などのサムネイル。 */
@Composable
fun PartThumbnail(part: Part, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scale = min(size.width, size.height) * 0.8f
        drawPart(part, center = Offset(size.width / 2, size.height / 2), scalePx = scale)
    }
}
