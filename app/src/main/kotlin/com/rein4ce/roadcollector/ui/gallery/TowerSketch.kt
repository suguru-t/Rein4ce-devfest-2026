package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.physics.PartSize
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
 *
 * @param referenceHeight 縮尺をそろえるときの、枠の高さに入れる範囲 [m]（ランキングで、どのカードも同じ縮尺にする）。
 *   指定すると、幅も一覧で同じ範囲（[RANKING_WIDTH_M]）で見て、台の中心を枠の真ん中・台の下を枠の下に置くので、
 *   どのカードも同じ縮尺・同じ台の位置になり、高いタワーほど大きく描かれる（はみ出した所は呼ぶ側の枠で切れる）。
 *   null なら、このタワーが枠いっぱいに入る縮尺で真ん中に描く（紙片・共有の絵）
 * @param referenceWidth [referenceHeight] を指定したときに、枠の幅に入れる範囲 [m]。一覧で同じ縮尺にするので、呼ぶ側が
 *   一覧でいちばん広い台のタワーに合わせて渡す（[rankingWidthFor]。広い台で積んだタワーがはみ出さないように。決定 66）
 */
@Composable
fun TowerSketch(tower: Tower, modifier: Modifier = Modifier, referenceHeight: Float? = null, referenceWidth: Float = RANKING_WIDTH_M) {
    Canvas(modifier) {
        val sx: (Float) -> Float
        val sy: (Float) -> Float
        val scale: Float
        if (referenceHeight != null) {
            // 一覧で同じ縮尺：幅は一覧でいちばん広い範囲、高さは一覧でいちばん高い範囲。台の中心を真ん中、範囲の下を枠の下に
            scale = min(size.width / referenceWidth, size.height / referenceHeight)
            sx = { x -> size.width / 2 + x * scale }
            sy = { y -> size.height - (y + PLATFORM_THICKNESS + 0.1f) * scale }
        } else {
            val b = sketchBounds(tower)
            scale = min(size.width / b.width, size.height / b.height)
            val offX = (size.width - b.width * scale) / 2
            val offY = (size.height - b.height * scale) / 2
            sx = { x -> offX + (x - b.minX) * scale }
            sy = { y -> size.height - (offY + (y - b.minY) * scale) }
        }

        // 台：上面が y=0。墨で塗る。幅はタワーが持つ台の幅（広い台で積んだタワーは広い台。決定 66）
        val platformWidth = tower.platformWidth
        drawRoundRect(
            color = Fieldbook.Ink,
            topLeft = Offset(sx(-platformWidth / 2), sy(0f)),
            size = Size(platformWidth * scale, PLATFORM_THICKNESS * scale),
            cornerRadius = CornerRadius(1.5f * scale * 0.05f),
        )
        drawTowerParts(tower, at = { x, y -> Offset(sx(x), sy(y)) }, pxPerM = scale)
    }
}

/**
 * 置いたパーツを描く：細い墨の縁取りを全部描いてから、種類ごとの色で本体を重ねる（縁取りは細く。太いと輪の穴や棒の細さがつぶれる）。
 * 紙片の絵（[TowerSketch]）と断面図（TowerSection）の両方がこれを使うので、描き方を変えるときは 1 か所で済む。
 *
 * @param at     ワールド座標 [m]（台の上面が y=0、y は上向き）を Canvas の位置にする
 * @param pxPerM 1 m を何 px にするか
 */
internal fun DrawScope.drawTowerParts(tower: Tower, at: (Float, Float) -> Offset, pxPerM: Float) {
    val partsById = tower.parts.associateBy { it.id }
    val edgePx = max(1f, 0.02f * pxPerM)
    // パーツの大きさ（歩いた距離で決まる。Placement.scale）を掛けて描く（#71。積む画面・共有ページの絵と同じ）。
    // 縁取りの太さは px で決めたいので、大きさで割って形の太さに足す
    for (pl in tower.placements) {
        val part = partsById[pl.partId] ?: continue
        val partScale = pxPerM * pl.scale
        drawPart(
            part.copy(thickness = part.thickness + 2 * edgePx / partScale),
            center = at(pl.x, pl.y),
            scalePx = partScale,
            angleRad = pl.angleRad,
            color = Fieldbook.Ink,
        )
    }
    for (pl in tower.placements) {
        val part = partsById[pl.partId] ?: continue
        drawPart(part, center = at(pl.x, pl.y), scalePx = pxPerM * pl.scale, angleRad = pl.angleRad, color = part.kind.color())
    }
}

/** 台の幅と厚さ [m]。物理と共有ページの絵と同じ値（StackWorld が持つ）。幅は今までの台。広い台で積んだタワーは [Tower.platformWidth] を使う */
internal const val PLATFORM_WIDTH = StackWorld.PLATFORM_WIDTH.toFloat()
internal const val PLATFORM_THICKNESS = StackWorld.PLATFORM_THICKNESS.toFloat()

/**
 * 置いた点からパーツが届きうる最大の距離 [m]（大きさ 1 倍のとき。大きさ [com.rein4ce.roadcollector.core.model.Placement.scale] を掛けて使う）。
 * パーツの点は 1.0×1.0 の箱に正規化されていて回転もするので、角までは最大 √2/2 ≒ 0.71。
 * それに道の太さの半分と縁取りを足して 0.8。共有ページの絵（TowerSvg）は 1.0 で見ているが、小さな絵なのでやや詰める
 */
internal const val PART_REACH = 0.8f

/** パーツの大きさの上限（[PartSize.MAX]。2 km 以上歩いた道）。届く範囲を見積もるときに掛ける */
internal val MAX_PART_SCALE: Float = PartSize.MAX.toFloat()

/**
 * ランキングの絵で、どのカードにも同じ幅で入れる範囲 [m]：台の幅と、台の両端からいちばん大きいパーツが届く範囲
 * （この 3 つの定義のあとに置く）
 */
internal val RANKING_WIDTH_M: Float get() = rankingWidthFor(PLATFORM_WIDTH)

/** 台の幅 [platformWidth] のタワーを、ランキングの絵に入れる範囲の幅 [m]（台と、台の両端からいちばん大きいパーツが届く範囲） */
internal fun rankingWidthFor(platformWidth: Float): Float = platformWidth + 2 * PART_REACH * MAX_PART_SCALE

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
    val halfPlatform = tower.platformWidth / 2
    var minX = -halfPlatform - 0.3f
    var maxX = halfPlatform + 0.3f
    var minY = -PLATFORM_THICKNESS - 0.1f
    var maxY = max(tower.height, 1f) + 0.3f
    for (p in tower.placements) {
        val reach = PART_REACH * p.scale
        minX = min(minX, p.x - reach)
        maxX = max(maxX, p.x + reach)
        maxY = max(maxY, p.y + reach)
        // 台の上に乗っているパーツは台より下には行けないので、下に広げるのは台の端より外に置かれたものだけ。
        // 全部に広げると、台の下に無駄な余白ができてタワーが小さく描かれる
        if (abs(p.x) > halfPlatform) minY = min(minY, p.y - reach)
    }
    return SketchBounds(minX, maxX, minY, maxY)
}

/**
 * 高さ [heightM] のタワーが占める範囲の高さ [m]（台の下 [PLATFORM_THICKNESS] + 0.1 から、いちばん上のパーツが届く所まで。
 * [sketchBounds] と同じ見積もり）。ランキングで、いちばん高いタワーの範囲を枠の高さに入れて、どのカードも同じ縮尺で描くのに使う
 * （絵がまだ届いていなくても高さは分かる）
 */
internal fun sketchHeightFor(heightM: Float): Float = (max(heightM, 1f) + PART_REACH) + (PLATFORM_THICKNESS + 0.1f)
