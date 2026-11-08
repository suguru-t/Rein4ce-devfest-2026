package com.rein4ce.roadcollector.core.sample

import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlin.math.hypot
import kotlin.random.Random

/**
 * デモ・テスト用の散歩軌跡。
 * 東京電機大学 千住キャンパス付近を起点に、メートル単位の経路点を GPS 風の点列に展開する。
 * 「デモ散歩を再生」とユニットテストで使う。
 */
object SampleTracks {

    data class SampleTrack(
        val id: String,
        val name: String,
        val expectedKind: Kind,
        val points: List<TrackPoint>,
    )

    private const val ORIGIN_LAT = 35.7496
    private const val ORIGIN_LNG = 139.8049

    /** 棒：北へ 420 m */
    val bar = build("sample-bar", "まっすぐ北へ", Kind.BAR, listOf(0.0 to 0.0, 0.0 to 420.0), seed = 1)

    /** L 字：東へ 300 m、北へ 250 m */
    val l = build("sample-l", "角を一つ曲がる", Kind.L, listOf(0.0 to 0.0, 300.0 to 0.0, 300.0 to 250.0), seed = 2)

    /** U 字：北へ 250 m、東へ 180 m、南へ 250 m */
    val u = build(
        "sample-u", "ぐるっと回って戻る", Kind.U,
        listOf(0.0 to 0.0, 0.0 to 250.0, 180.0 to 250.0, 180.0 to 0.0), seed = 3,
    )

    /** 輪：街区を一周（220 × 160 m） */
    val ring = build(
        "sample-ring", "街区を一周", Kind.RING,
        listOf(0.0 to 0.0, 220.0 to 0.0, 220.0 to 160.0, 0.0 to 160.0, 0.0 to 0.0), seed = 4,
    )

    /** ジグザグ：右・左・右と曲がる */
    val zigzag = build(
        "sample-zigzag", "ジグザグに進む", Kind.ZIGZAG,
        listOf(0.0 to 0.0, 150.0 to 0.0, 150.0 to 120.0, 300.0 to 120.0, 300.0 to 240.0), seed = 5,
    )

    /** 短い L 字：東へ 90 m、北へ 70 m（パーツになる最短の 150 m に近い、短い道の見本。積むと小さい） */
    val corner = build(
        "sample-corner", "となりの角まで", Kind.L,
        listOf(0.0 to 0.0, 90.0 to 0.0, 90.0 to 70.0), seed = 6,
    )

    /** 長い棒：土手の上を北東へ 1.6 km（長い道の見本。積むと大きい） */
    val river = build(
        "sample-river", "土手をずっと歩く", Kind.BAR,
        listOf(0.0 to 0.0, 900.0 to 1300.0), seed = 7,
    )

    /**
     * 見本の 7 本。天気は 7 つ全部がそろうように決めてある（[SampleParts.weather]）。
     * 長さも短い道（パーツにすると 165 m）から長い道（1,631 m）まであるので、積むときの大きさの違い（PartSize）も見られる
     */
    val all: List<SampleTrack> = listOf(bar, l, u, ring, zigzag, corner, river)

    fun byId(id: String): SampleTrack? = all.firstOrNull { it.id == id }

    /**
     * 経路点（メートル）を 4 m 刻みで補間し、GPS 風のノイズ（±2 m 程度）と時刻を付けて緯度経度にする。
     */
    private fun build(
        id: String,
        name: String,
        kind: Kind,
        waypointsM: List<Pair<Double, Double>>,
        seed: Int,
        stepM: Double = 4.0,
        noiseM: Double = 2.0,
        startTimeMs: Long = 1_760_000_000_000L,
    ): SampleTrack {
        val rnd = Random(seed)
        val mPerDegLat = Projection.M_PER_DEG_LAT
        val mPerDegLng = Projection.metersPerDegLng(ORIGIN_LAT)
        val pts = ArrayList<TrackPoint>()
        var t = startTimeMs
        var first = true
        for (i in 1 until waypointsM.size) {
            val (x0, y0) = waypointsM[i - 1]
            val (x1, y1) = waypointsM[i]
            val len = hypot(x1 - x0, y1 - y0)
            val n = (len / stepM).toInt().coerceAtLeast(1)
            val from = if (first) 0 else 1
            first = false
            for (k in from..n) {
                val f = k.toDouble() / n
                val x = x0 + (x1 - x0) * f + (rnd.nextDouble() * 2 - 1) * noiseM
                val y = y0 + (y1 - y0) * f + (rnd.nextDouble() * 2 - 1) * noiseM
                pts += TrackPoint(
                    lat = ORIGIN_LAT + y / mPerDegLat,
                    lng = ORIGIN_LNG + x / mPerDegLng,
                    accuracyM = (6 + rnd.nextDouble() * 8).toFloat(),
                    timeMs = t,
                )
                t += 3_000L // 時速 4.8 km 相当
            }
        }
        return SampleTrack(id, name, kind, pts)
    }
}
