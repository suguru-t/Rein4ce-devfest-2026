package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import org.dyn4j.dynamics.Body
import org.dyn4j.geometry.Circle
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Polygon
import org.dyn4j.world.World

/**
 * 積み上げゲームの物理ワールド。UI フレームワークに依存しない。
 *
 * ワールド座標：単位 [m]、y 上向き。台の上面が y = 0、台の中心が x = 0。
 * パーツの長辺は 1.0 m。
 *
 * 使い方：
 *   val w = StackWorld()
 *   w.place(part, x = 0.0, y = 2.0, angle = 0.0)
 *   毎フレーム w.step(dt)
 *   w.isSettled() が true になったら w.removeFallen() → w.height()
 */
class StackWorld(
    val platformWidth: Double = PLATFORM_WIDTH,
    val platformThickness: Double = PLATFORM_THICKNESS,
    /** これより下に落ちたパーツは「落下」扱い */
    val fallThresholdY: Double = -1.5,
) {
    val world: World<Body> = World()

    private val platform: Body
    private val placed = LinkedHashMap<Body, Part>()

    /** 置かれているパーツ（落下で消えたものは含まない） */
    val placedParts: List<Part> get() = placed.values.toList()

    init {
        val s = world.settings
        s.stepFrequency = 1.0 / 60.0
        s.isAtRestDetectionEnabled = true
        s.maximumAtRestLinearVelocity = 0.05
        s.maximumAtRestAngularVelocity = 0.1
        s.minimumAtRestTime = 0.8
        world.setGravity(0.0, -9.8)

        platform = Body().apply {
            val rect = Geometry.createRectangle(platformWidth, platformThickness)
            rect.translate(0.0, -platformThickness / 2.0)
            val f = addFixture(rect)
            f.friction = 0.9
            f.restitution = 0.0
            setMass(MassType.INFINITE)
            userData = PLATFORM_TAG
        }
        world.addBody(platform)
    }

    /** パーツをワールドに追加して落下させる。 */
    fun place(part: Part, x: Double, y: Double, angle: Double, scale: Double = 1.0): Body {
        val body = PartBodyFactory.create(part, scale)
        body.rotate(angle)
        body.translate(x, y)
        world.addBody(body)
        placed[body] = part
        return body
    }

    /** 経過時間ぶん進める。内部は固定ステップ（1/60 s）。 */
    fun step(elapsedSeconds: Double) {
        // 一度に進めすぎない（アプリ復帰時など）
        world.update(elapsedSeconds.coerceIn(0.0, 0.1), 5)
    }

    /** 全パーツが静止しているか。 */
    fun isSettled(): Boolean = placed.keys.all { it.isAtRest }

    /** まだ動いているパーツの数。 */
    fun movingCount(): Int = placed.keys.count { !it.isAtRest }

    /** 台から落ちたパーツをワールドから外して返す。 */
    fun removeFallen(): List<Part> {
        val fallen = placed.keys.filter { it.worldCenter.y < fallThresholdY }
        val parts = fallen.map { placed.remove(it)!! }
        fallen.forEach { world.removeBody(it) }
        return parts
    }

    /** 台の上面から、残っているパーツの最高点までの高さ [m]。 */
    fun height(): Double {
        var maxY = 0.0
        for (b in placed.keys) {
            val aabb = b.createAABB()
            if (aabb.maxY > maxY) maxY = aabb.maxY
        }
        return maxY
    }

    /** 保存用の最終配置。 */
    fun placements(): List<Placement> = placed.map { (b, p) ->
        val t = b.transform
        Placement(
            partId = p.id,
            x = t.translationX.toFloat(),
            y = t.translationY.toFloat(),
            angleRad = t.rotationAngle.toFloat(),
        )
    }

    fun clear() {
        placed.keys.forEach { world.removeBody(it) }
        placed.clear()
    }

    // ---- 描画用 ----

    sealed class RenderShape {
        abstract val part: Part?

        /** ワールド座標の多角形頂点（x, y の順に並ぶ） */
        data class Poly(override val part: Part?, val xs: DoubleArray, val ys: DoubleArray) : RenderShape()
        data class Disc(override val part: Part?, val cx: Double, val cy: Double, val r: Double) : RenderShape()
    }

    /** 台＋全パーツの fixture をワールド座標の図形に変換する（毎フレーム描画用）。 */
    fun renderShapes(): List<RenderShape> {
        val out = ArrayList<RenderShape>()
        appendShapes(platform, null, out)
        for ((b, p) in placed) appendShapes(b, p, out)
        return out
    }

    private fun appendShapes(body: Body, part: Part?, out: MutableList<RenderShape>) {
        val t = body.transform
        for (f in body.fixtures) {
            when (val shape = f.shape) {
                is Polygon -> {
                    val v = shape.vertices
                    val xs = DoubleArray(v.size)
                    val ys = DoubleArray(v.size)
                    for (i in v.indices) {
                        val w = t.getTransformed(v[i])
                        xs[i] = w.x
                        ys[i] = w.y
                    }
                    out += RenderShape.Poly(part, xs, ys)
                }
                is Circle -> {
                    val c = t.getTransformed(shape.center)
                    out += RenderShape.Disc(part, c.x, c.y, shape.radius)
                }
                else -> Unit
            }
        }
    }

    companion object {
        const val PLATFORM_TAG = "platform"

        /** 台の幅と厚さ [m]。物理と、共有ページの絵（TowerSvg / TowerPng）と、アプリの絵（TowerSketch）で同じ値を使う */
        const val PLATFORM_WIDTH = 1.4
        const val PLATFORM_THICKNESS = 0.3
    }
}
