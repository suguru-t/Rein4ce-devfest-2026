package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.model.Part
import org.dyn4j.dynamics.Body
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType

/**
 * Part → dyn4j の Body。
 *
 * 凹型（L・U・輪）を 1 つの凸多角形にはせず、線分ごとの矩形＋関節の円を
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
        val shapes = PartGeometry.shapes(part, scale)

        for (s in shapes.segs) {
            val rect = Geometry.createRectangle(s.length, s.thickness)
            rect.rotate(s.angle)
            rect.translate(s.cx, s.cy)
            val f = body.addFixture(rect)
            f.density = material.density
            f.friction = material.friction
            f.restitution = material.restitution
        }
        for (j in shapes.joints) {
            val circle = Geometry.createCircle(j.radius)
            circle.translate(j.cx, j.cy)
            val f = body.addFixture(circle)
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
