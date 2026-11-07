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

    data class Material(
        val density: Double = 1.0,
        val friction: Double = 0.7,
        val restitution: Double = 0.0,
        val angularDamping: Double = 0.3,
        val linearDamping: Double = 0.05,
    )

    val DEFAULT_MATERIAL = Material()

    /**
     * @param scale パーツの長辺 [m]
     */
    fun create(part: Part, scale: Double = 1.0, material: Material = DEFAULT_MATERIAL): Body {
        val body = Body()
        for (poly in PartGeometry.shapes(part, scale)) {
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
