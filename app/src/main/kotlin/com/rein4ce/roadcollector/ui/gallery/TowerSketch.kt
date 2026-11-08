package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.ui.common.color
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * タワー 1 基の小さな絵。台の上に積んだパーツを、細い墨の縁取りを付けて描く。
 * 色は積む画面・共有ページの絵と同じ「種類ごとの色」（棒は黄、輪は青など）。小さな絵では色が
 * いちばんの手がかりで、野帳の道の色 1 色だとどのパーツか分からず「同じタワー」に見えなかったため。
 * 形と置き方は共有ページの絵（TowerSvg / TowerPng）と同じ `PartGeometry` と `placements` を使う。
 *
 * 見える範囲は [sketchBounds] で決め、タワー全体が収まるように縮める。
 */
@Composable
fun TowerSketch(tower: Tower, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val b = sketchBounds(tower)
        val scale = min(size.width / b.width, size.height / b.height)
        val offX = (size.width - b.width * scale) / 2
        val offY = (size.height - b.height * scale) / 2
        fun sx(x: Float) = offX + (x - b.minX) * scale
        fun sy(y: Float) = size.height - (offY + (y - b.minY) * scale)

        // 台：上面が y=0。墨で塗る
        drawRoundRect(
            color = Fieldbook.Ink,
            topLeft = Offset(sx(-PLATFORM_WIDTH / 2), sy(0f)),
            size = Size(PLATFORM_WIDTH * scale, PLATFORM_THICKNESS * scale),
            cornerRadius = CornerRadius(1.5f * scale * 0.05f),
        )

        // パーツ：細い墨の縁取りを全部描いてから、種類ごとの色で本体を重ねる（縁取りは細く。太いと輪の穴や棒の細さがつぶれる）
        val partsById = tower.parts.associateBy { it.id }
        val edgePx = max(1f, 0.02f * scale)
        // パーツの大きさ（歩いた距離で決まる。Placement.scale）を掛けて描く
        for (pl in tower.placements) {
            val part = partsById[pl.partId] ?: continue
            val partScale = scale * pl.scale
            drawPart(
                part.copy(thickness = part.thickness + 2 * edgePx / partScale),
                center = Offset(sx(pl.x), sy(pl.y)),
                scalePx = partScale,
                angleRad = pl.angleRad,
                color = Fieldbook.Ink,
            )
        }
        for (pl in tower.placements) {
            val part = partsById[pl.partId] ?: continue
            drawPart(part, center = Offset(sx(pl.x), sy(pl.y)), scalePx = scale * pl.scale, angleRad = pl.angleRad, color = part.kind.color())
        }
    }
}

/** 台の幅と厚さ [m]。物理と共有ページの絵と同じ値（StackWorld が持つ） */
internal const val PLATFORM_WIDTH = StackWorld.PLATFORM_WIDTH.toFloat()
internal const val PLATFORM_THICKNESS = StackWorld.PLATFORM_THICKNESS.toFloat()

/**
 * 置いた点からパーツが届きうる最大の距離 [m]（大きさ 1 倍のとき。大きさ [com.rein4ce.roadcollector.core.model.Placement.scale] を掛けて使う）。
 * パーツの点は 1.0×1.0 の箱に正規化されていて回転もするので、角までは最大 √2/2 ≒ 0.71。
 * それに道の太さの半分と縁取りを足して 0.8。共有ページの絵（TowerSvg）は 1.0 で見ているが、小さな絵なのでやや詰める
 */
internal const val PART_REACH = 0.8f

/** 絵に入れる範囲 [m]（ワールド座標。台の上面が y=0、y は上向き） */
internal data class SketchBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

/**
 * 台と、置いたパーツ全部が入る範囲。置いた点から上下左右に [PART_REACH] を見ておく。
 * 下にも広げるのは、台の端からぶら下がったまま静止したパーツ（物理は y > -1.5 なら落ちた扱いにしない）が保存されることがあるため。
 * 高さが 1 m 未満でも 1 m ぶんは見せて、低いタワーがやたら大きく描かれないようにする。
 */
internal fun sketchBounds(tower: Tower): SketchBounds {
    var minX = -PLATFORM_WIDTH / 2 - 0.3f
    var maxX = PLATFORM_WIDTH / 2 + 0.3f
    var minY = -PLATFORM_THICKNESS - 0.1f
    var maxY = max(tower.height, 1f) + 0.3f
    for (p in tower.placements) {
        val reach = PART_REACH * p.scale
        minX = min(minX, p.x - reach)
        maxX = max(maxX, p.x + reach)
        maxY = max(maxY, p.y + reach)
        // 台の上に乗っているパーツは台より下には行けないので、下に広げるのは台の端より外に置かれたものだけ。
        // 全部に広げると、台の下に無駄な余白ができてタワーが小さく描かれる
        if (abs(p.x) > PLATFORM_WIDTH / 2) minY = min(minY, p.y - reach)
    }
    return SketchBounds(minX, maxX, minY, maxY)
}
