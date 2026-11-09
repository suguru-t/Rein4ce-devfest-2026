package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.model.Part
import org.dyn4j.dynamics.Body
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Vector2

/**
 * Part → dyn4j の Body。
 *
 * 凹型（L・U・輪）を 1 つの凸多角形にはせず、PartGeometry の凸多角形（線分ごとの長方形＋曲がり角の継ぎ目）を
 * 1 ボディの複数 fixture として合成する。凹分解が不要で、輪の内側の穴も残る。
 */
object PartBodyFactory {

    /**
     * @property restitution 反発。0（跳ね返らない）
     * @property angularDamping 回転の減衰。着地したときに角から当たって、回りながら跳ねるのを抑える（#110。決定 63）。
     *   反発はもとから 0 で、跳ねて見えていたのは、角から当たったときの回転だった。0.3 から 3.0 にすると、見本のパーツを台と塔に
     *   いろいろな向きで落としたときの、着いてからの跳ね上がりが平均 3.6 cm → 2.4 cm、いちばん大きいときで 16 cm → 9 cm になった。
     *   4.0 にするともっと小さくなるが、塔から倒れるときの回り方が重く見えるので、3.0 にした。
     *   すべりやすさ（摩擦。歩いた日の天気）と、まっすぐ落ちる速さには効かない
     */
    data class Material(
        val density: Double = 1.0,
        val friction: Double = 0.7,
        val restitution: Double = 0.0,
        val angularDamping: Double = 3.0,
        val linearDamping: Double = 0.05,
    )

    val DEFAULT_MATERIAL = Material()

    /**
     * @param scale パーツの長辺 [m]
     */
    fun create(part: Part, scale: Double = 1.0, material: Material = DEFAULT_MATERIAL): Body {
        val body = Body()
        // 当たり判定は角ばった形（見た目は丸い。PartGeometry）
        for (poly in PartGeometry.collisionShapes(part, scale)) {
            val shape = Geometry.createPolygon(*poly.points.map { Vector2(it.x, it.y) }.toTypedArray())
            val f = body.addFixture(shape)
            f.density = material.density
            f.friction = material.friction
            f.restitution = material.restitution
        }

        body.setMass(MassType.NORMAL)
        body.angularDamping = material.angularDamping
        body.linearDamping = material.linearDamping
        body.userData = part
        return body
    }
}
