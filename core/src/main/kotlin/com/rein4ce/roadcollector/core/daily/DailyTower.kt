package com.rein4ce.roadcollector.core.daily

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.physics.Grip
import kotlinx.serialization.Serializable

/**
 * 「毎日 1 つずつ積む」の塔に積んだパーツ 1 つ（決定 61・#106）。
 *
 * パーツの形そのもの（[part]）も持つ。コレクションからパーツを消しても、塔の中のパーツは残るように。
 * すべりやすさ（[grip]）も、積んだときの天気で決めたものを持つ（あとで天気が分からなくなっても、同じ滑り方で組み直せるように）
 *
 * @property placement 止まったときの位置・向き・大きさ（次の日は、ここから組み直す）
 * @property day 積んだ日（端末の時刻の日付。"2026-11-10"）
 */
@Serializable
data class DailyBlock(
    val part: Part,
    val placement: Placement,
    val grip: Grip,
    val day: String,
)

/**
 * 「毎日 1 つずつ積む」の塔。1 日に 1 つだけ積め、積んだ形を残して、次の日に続きから積む。
 * 台から落ちたパーツは塔から外れる（ふつうの積む画面と同じ）
 *
 * @property height 止まったときの塔の高さ [m]（台の上面からいちばん上まで。選ぶ画面のカードに出す）
 */
@Serializable
data class DailyTower(val blocks: List<DailyBlock> = emptyList(), val height: Double = 0.0) {

    /** [day] にもう積んだか（1 日 1 つまで） */
    fun placedOn(day: String): Boolean = blocks.any { it.day == day }

    /** 積んだ日の数（落ちて外れたパーツの日は数えない。塔に残っているパーツの日数） */
    val days: Int get() = blocks.map { it.day }.distinct().size

    /**
     * 積んで止まったあとの塔にする。[added] が今日積んだパーツ、[settled] が止まったあとの全部のパーツの配置（パーツの id ごと）。
     * 配置が無いパーツ（台から落ちて外れたもの）は塔から外す。今日のパーツが落ちたときは、今日はまだ積んでいないことになる
     * （[placedOn] が false。その日の道をもう一度選べる）
     *
     * @param height 止まったあとの塔の高さ [m]
     */
    fun settledWith(added: DailyBlock, settled: List<Placement>, height: Double): DailyTower {
        val byId = settled.associateBy { it.partId }
        return DailyTower((blocks + added).mapNotNull { block -> byId[block.part.id]?.let { block.copy(placement = it) } }, height)
    }
}
