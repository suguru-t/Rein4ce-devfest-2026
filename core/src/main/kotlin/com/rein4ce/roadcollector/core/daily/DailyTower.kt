package com.rein4ce.roadcollector.core.daily

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.physics.Grip
import kotlinx.serialization.Serializable
import java.time.LocalDate

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
 * @property sentDay 「みんな」のランキングに送れた塔の、最後に積んだ日（[lastDay]）。これと違えば、まだ送っていない形がある（決定 77）
 */
@Serializable
data class DailyTower(
    val blocks: List<DailyBlock> = emptyList(),
    val height: Double = 0.0,
    val fellDay: String = "",
    val fellCount: Int = 0,
    val sentDay: String = "",
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

    /** 最後に積んだ日（まだ積んでいなければ null） */
    val lastDay: String? get() = blocks.maxOfOrNull { it.day }

    /**
     * 最後に積んだ日（[lastDay]）までの連続日数（決定 77）。最後に積んだ日から 1 日ずつさかのぼって、積んだ日が続くあいだを数える。
     * 積んでいなければ 0
     */
    val streakDays: Int
        get() {
            val days = blocks.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }.toSet()
            var day = days.maxOrNull() ?: return 0
            var count = 0
            while (day in days) {
                count++
                day = day.minusDays(1)
            }
            return count
        }

    /** まだ「みんな」に送っていない形があるか（積んでいて、送れた日が最後に積んだ日と違う） */
    val needsSend: Boolean get() = lastDay != null && sentDay != lastDay

    /**
     * 「みんな」に送るタワー（決定 77）。パーツは同じものを 1 つにまとめ、配置は積んだ数だけ。
     * スマホ 1 台につき 1 基で、送るたびにサーバの同じ 1 基を新しい形にする（`PUT /towers/daily`）
     *
     * @param platformWidth 台の幅 [m]（通常と同じ 2.8 m。決定 71）
     */
    fun toTower(platformWidth: Double, createdAt: Long): Tower = Tower(
        parts = blocks.map { it.part }.distinctBy { it.id },
        placements = blocks.map { it.placement },
        height = height.toFloat(),
        createdAt = createdAt,
        platformWidth = platformWidth.toFloat(),
        mode = TowerMode.DAILY,
        streakDays = streakDays,
        lastDay = lastDay,
    )

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
