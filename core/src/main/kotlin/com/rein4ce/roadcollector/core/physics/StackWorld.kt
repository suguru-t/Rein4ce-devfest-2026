package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import org.dyn4j.dynamics.Body
import org.dyn4j.geometry.Circle
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Polygon
import org.dyn4j.geometry.Vector2
import org.dyn4j.world.ValueMixer
import org.dyn4j.world.World
import kotlin.math.max
import kotlin.math.min

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

    /**
     * 置いたパーツの見た目の形（丸い。パーツのローカル座標）。当たり判定（角ばった fixture）とは別に、置いたときに一度だけ作る。
     * 描くときは、これをボディの位置・向きに動かすだけにする（毎フレーム作り直さない）
     */
    private val visuals = HashMap<Body, List<PartGeometry.Polygon>>()

    /** 置いたパーツの大きさ（長辺 [m]）。保存する配置（[placements]）に入れる */
    private val scales = HashMap<Body, Double>()

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
        world.valueMixer = GripMixer

        platform = Body().apply {
            val rect = Geometry.createRectangle(platformWidth, platformThickness)
            rect.translate(0.0, -platformThickness / 2.0)
            val f = addFixture(rect)
            f.friction = PLATFORM_FRICTION
            f.restitution = 0.0
            setMass(MassType.INFINITE)
            userData = PLATFORM_TAG
        }
        world.addBody(platform)
    }

    /**
     * パーツをワールドに追加して落下させる。
     *
     * @param scale パーツの大きさ（長辺 [m]。歩いた距離で決まる。[PartSize.of]）
     * @param grip すべりやすさ（歩いたときの天気で決まる。[Grip.of]）
     */
    fun place(part: Part, x: Double, y: Double, angle: Double, scale: Double = 1.0, grip: Grip = Grip.NORMAL): Body {
        val body = PartBodyFactory.create(part, scale, PartBodyFactory.DEFAULT_MATERIAL.copy(friction = grip.friction))
        body.rotate(angle)
        body.translate(x, y)
        world.addBody(body)
        placed[body] = part
        visuals[body] = PartGeometry.shapes(part, scale)
        scales[body] = scale
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
        fallen.forEach {
            visuals.remove(it)
            scales.remove(it)
        }
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
            scale = (scales[b] ?: 1.0).toFloat(),
        )
    }

    fun clear() {
        placed.keys.forEach { world.removeBody(it) }
        placed.clear()
        visuals.clear()
        scales.clear()
    }

    // ---- 描画用 ----

    sealed class RenderShape {
        abstract val part: Part?

        /** ワールド座標の多角形頂点（x, y の順に並ぶ） */
        data class Poly(override val part: Part?, val xs: DoubleArray, val ys: DoubleArray) : RenderShape()
        data class Disc(override val part: Part?, val cx: Double, val cy: Double, val r: Double) : RenderShape()
    }

    /**
     * 台と全パーツを、ワールド座標の図形にする（毎フレーム描画用）。
     * 台は fixture の形、パーツは見た目の形（丸い。[PartGeometry.shapes]）。当たり判定の角ばった形は描かない
     */
    fun renderShapes(): List<RenderShape> {
        val out = ArrayList<RenderShape>()
        appendShapes(platform, null, out)
        for ((b, p) in placed) appendVisual(b, p, out)
        return out
    }

    private fun appendVisual(body: Body, part: Part, out: MutableList<RenderShape>) {
        val t = body.transform
        val v = Vector2()
        for (poly in visuals[body].orEmpty()) {
            val n = poly.points.size
            val xs = DoubleArray(n)
            val ys = DoubleArray(n)
            for (i in 0 until n) {
                v.set(poly.points[i].x, poly.points[i].y)
                t.transform(v)
                xs[i] = v.x
                ys[i] = v.y
            }
            out += RenderShape.Poly(part, xs, ys)
        }
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

    /**
     * 2 つの面が触れたときの摩擦の決め方。
     * - **台との間**：パーツの天気によらず、晴れのパーツと同じ値（台は滑らない）。雪や雨のパーツでも、台に置いたものは滑り落ちない。
     *   台の上でも滑ると、5 本とも落ちて送れない回が増えたため（#71 の D のレビュー：雪だけで積むと 5% → 30.5%）
     * - **パーツどうし**：滑りやすい面（雨・雪の日のパーツ。[Grip]）があれば、滑りやすい方の値。雪のパーツを乗せても、
     *   雪のパーツの上に乗せても滑る（dyn4j の決まり（掛けて平方根）だと、相手の面に引っ張られて、ほとんど滑らなかった）
     * - それ以外（晴れのパーツどうしなど）：今までどおり、掛けて平方根
     *
     * 台かどうかは摩擦の値で見分ける（台は [PLATFORM_FRICTION]、パーツはどれも [Grip.NORMAL] 以下）
     */
    private object GripMixer : ValueMixer {
        override fun mixFriction(friction1: Double, friction2: Double): Double {
            val platform = max(friction1, friction2)
            val part = min(friction1, friction2)
            return when {
                platform >= PLATFORM_FRICTION -> ValueMixer.DEFAULT_MIXER.mixFriction(max(part, Grip.NORMAL.friction), platform)
                part < Grip.NORMAL.friction -> part
                else -> ValueMixer.DEFAULT_MIXER.mixFriction(friction1, friction2)
            }
        }

        override fun mixRestitution(restitution1: Double, restitution2: Double): Double =
            ValueMixer.DEFAULT_MIXER.mixRestitution(restitution1, restitution2)

        override fun mixRestitutionVelocity(restitutionVelocity1: Double, restitutionVelocity2: Double): Double =
            ValueMixer.DEFAULT_MIXER.mixRestitutionVelocity(restitutionVelocity1, restitutionVelocity2)
    }

    companion object {
        const val PLATFORM_TAG = "platform"

        /** 台の面の摩擦。パーツ（どれも [Grip.NORMAL] 以下）より大きいので、摩擦の混ぜ方で台を見分けるのにも使う */
        const val PLATFORM_FRICTION = 0.9

        /** 台の幅と厚さ [m]。物理と、共有ページの絵（TowerSvg / TowerPng）と、アプリの絵（TowerSketch）で同じ値を使う */
        const val PLATFORM_WIDTH = 1.4
        const val PLATFORM_THICKNESS = 0.3
    }
}
