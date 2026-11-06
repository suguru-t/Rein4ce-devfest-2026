package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.model.Part
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * パーツの「見た目の形」を、矩形（線分ごと）と円（関節）の集まりとして表す。
 * 物理ボディ（dyn4j の fixture）と描画（Compose Canvas / SVG）の両方がこれを使うので、
 * 見た目と当たり判定が必ず一致する。
 *
 * 座標はパーツのローカル座標（長辺 = scale、中心が原点、y 上向き）。
 */
object PartGeometry {

    /** 線分 1 本ぶんの矩形。center を中心に angle だけ回転した length × thickness。 */
    data class Seg(val cx: Double, val cy: Double, val length: Double, val angle: Double, val thickness: Double)

    /** 関節の円。 */
    data class Joint(val cx: Double, val cy: Double, val radius: Double)

    data class Shapes(val segs: List<Seg>, val joints: List<Joint>)

    /**
     * @param scale パーツの長辺を何 [m] にするか（ゲーム内では 1.0）
     */
    fun shapes(part: Part, scale: Double = 1.0): Shapes {
        val pts = part.points
        val thickness = part.thickness * scale
        val segs = ArrayList<Seg>()
        val joints = ArrayList<Joint>()
        if (pts.isEmpty()) return Shapes(segs, joints)

        val n = pts.size
        val segCount = if (part.closed) n else n - 1
        for (i in 0 until segCount) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            val dx = (b.x - a.x) * scale
            val dy = (b.y - a.y) * scale
            val len = hypot(dx, dy)
            if (len < 1e-6) continue
            segs += Seg(
                cx = (a.x + b.x) / 2.0 * scale,
                cy = (a.y + b.y) / 2.0 * scale,
                length = len,
                angle = atan2(dy, dx),
                thickness = thickness,
            )
        }
        for (p in pts) {
            joints += Joint(p.x * scale, p.y * scale, thickness / 2.0)
        }
        // 点が 1 つしかない（ありえないが）場合でも円 1 個は返す
        return Shapes(segs, joints)
    }
}
