package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val shapes = PartGeometry.shapes(part, scalePx.toDouble())
    // Canvas は y 下向きなので、反時計回りの回転は負の角度にする
    rotate(degrees = -Math.toDegrees(angleRad.toDouble()).toFloat(), pivot = center) {
        for (s in shapes.segs) {
            val c = Offset(center.x + s.cx.toFloat(), center.y - s.cy.toFloat())
            rotate(degrees = -Math.toDegrees(s.angle).toFloat(), pivot = c) {
                drawRect(
                    color = color,
                    topLeft = Offset(c.x - (s.length / 2).toFloat(), c.y - (s.thickness / 2).toFloat()),
                    size = Size(s.length.toFloat(), s.thickness.toFloat()),
                    alpha = alpha,
                )
            }
        }
        for (j in shapes.joints) {
            drawCircle(
                color = color,
                radius = j.radius.toFloat(),
                center = Offset(center.x + j.cx.toFloat(), center.y - j.cy.toFloat()),
                alpha = alpha,
            )
        }
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
