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
 * 昨日までの塔は固めてあり（決定 70）、落ちるのは今日のパーツだけ。今日のパーツが台から落ちたら、1 日 [MAX_RETRIES] 回まで置き直せる
 *
 * @property height 止まったときの塔の高さ [m]（台の上面からいちばん上まで。選ぶ画面のカードに出す）
 * @property fellDay 今日のパーツが台から落ちた日（[fellCount] を数えている日。"2026-11-10"）
 * @property fellCount [fellDay] に今日のパーツが台から落ちた回数。保存するので、画面を出入りしても置き直しは増えない
 */
@Serializable
data class DailyTower(
    val blocks: List<DailyBlock> = emptyList(),
    val height: Double = 0.0,
    val fellDay: String = "",
    val fellCount: Int = 0,
) {

    /** [day] にもう積んだか（1 日 1 つまで） */
    fun placedOn(day: String): Boolean = blocks.any { it.day == day }

    /** [day] に今日のパーツが台から落ちた回数 */
    fun fallsOn(day: String): Int = if (fellDay == day) fellCount else 0

    /**
     * [day] にあと何回置き直せるか（落ちるたびに 1 回置き直せて、1 日 [MAX_RETRIES] 回まで。決定 70）。
     * まだ落ちていなければ [MAX_RETRIES]。1 回落ちたら、これからする置き直しを含めて [MAX_RETRIES]、[MAX_RETRIES] + 1 回落ちたら 0
     */
    fun retriesLeft(day: String): Int = (MAX_RETRIES + 1 - fallsOn(day)).coerceIn(0, MAX_RETRIES)

    /** [day] にまだ積めるか（まだ積んでいなくて、置き直しを使い切っていない） */
    fun canPlaceOn(day: String): Boolean = !placedOn(day) && retriesLeft(day) > 0

    /** 積んだ日の数（落ちて外れたパーツの日は数えない。塔に残っているパーツの日数） */
    val days: Int get() = blocks.map { it.day }.distinct().size

    /**
     * 積んで止まったあとの塔にする。[added] が今日積んだパーツ、[settled] が止まったあとの全部のパーツの配置（パーツの id ごと）。
     * 配置が無いパーツ（台から落ちて外れたもの）は塔から外す。今日のパーツが落ちたときは、今日はまだ積んでいないことになり
     * （[placedOn] が false）、落ちた回数（[fallsOn]）を 1 つ増やす。置き直しが残っていれば（[canPlaceOn]）、もう一度積める
     *
     * @param height 止まったあとの塔の高さ [m]
     */
    fun settledWith(added: DailyBlock, settled: List<Placement>, height: Double): DailyTower {
        val byId = settled.associateBy { it.partId }
        val kept = (blocks + added).mapNotNull { block -> byId[block.part.id]?.let { block.copy(placement = it) } }
        val fell = added.part.id !in byId
        return if (fell) {
            copy(blocks = kept, height = height, fellDay = added.day, fellCount = fallsOn(added.day) + 1)
        } else {
            copy(blocks = kept, height = height)
        }
    }

    companion object {
        /** 今日のパーツが台から落ちたときに、置き直せる回数（1 日に。決定 70） */
        const val MAX_RETRIES = 3
    }
}
