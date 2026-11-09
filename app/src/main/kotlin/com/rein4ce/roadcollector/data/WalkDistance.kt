package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts

/**
 * 積んだパーツのうち、本当に歩いた道の距離（完成カードの「使った道」）。
 *
 * @property meters  歩いて記録したパーツ（[PartOrigin.WALK]）の距離の合計 [m]。Int の距離をいくつ足しても溢れないよう Long
 * @property walks   距離に入れたパーツの数
 * @property others  距離に入れなかったパーツの数（見本・デモ散歩・ファイル・出どころが分からないパーツ）
 * @property unknown [others] のうち、出どころが分からないパーツの数（出どころを残すようにする前に作ったパーツ）
 */
data class WalkDistance(val meters: Long, val walks: Int, val others: Int, val unknown: Int) {
    companion object {
        /** まだ数えていない（ゲームを始める前） */
        val NONE = WalkDistance(meters = 0, walks = 0, others = 0, unknown = 0)
    }
}

/**
 * [parts]（ゲームを始めたときに固定したパーツ。落ちたものも含む）の、本当に歩いた道の距離。
 * 同じパーツが 2 回入っていても 1 回だけ数える。
 * 見本は出どころの表（[origins]）に無いが、見本だと分かるので「分からない」には数えない
 */
fun walkDistance(parts: List<Part>, origins: Map<String, PartOrigin>): WalkDistance {
    val unique = parts.distinctBy { it.id }
    val walks = unique.filter { origins[it.id] == PartOrigin.WALK }
    val unknown = unique.count { it.id !in origins && it.id !in sampleIds }
    return WalkDistance(
        meters = walks.sumOf { it.distanceM.coerceAtLeast(0).toLong() },
        walks = walks.size,
        others = unique.size - walks.size,
        unknown = unknown,
    )
}

private val sampleIds: Set<String> by lazy { SampleParts.all.mapTo(HashSet()) { it.id } }
