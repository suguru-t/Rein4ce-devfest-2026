package com.rein4ce.roadcollector.core.physics

import com.rein4ce.roadcollector.core.model.Tower

/**
 * 天気のボーナス（決定 68）：積む画面の通常モードで、終わったときに台の上に残っていたパーツの天気（すべりやすさ）で、
 * 模型を大きくする倍率。滑りやすい道ほど積むのが難しいので、そのぶん大きくする。
 *
 * - 倍率は 1 に、残っていたパーツ 1 個ずつの [Grip.bonusPercent] を足したもの（雨の日の道が 3 個なら 1 + 0.10 × 3 = 1.30 倍）。
 *   同じパーツを何度も置いたら、置いた数だけ足す。落ちたパーツは塔に入っていないので数えない
 * - 上限は [MAX_MULTIPLIER] 倍（「みんな」に送る大きさが、サーバの受け付ける範囲（パーツの大きさ 5 倍まで）に収まるように）
 * - 倍率は百分の一の単位の整数で足す（0.1 を足し続けたときの小数のずれで、1.30 が 1.2999… にならないように）
 */
object WeatherBonus {
    /** 倍率の上限 */
    const val MAX_MULTIPLIER = 3.0

    /** 何も足さないとき（晴れだけ・パーツが残っていない） */
    val NONE = Result(emptyMap())

    /** 塔に残ったパーツのすべりやすさ（置いた数だけ並ぶ）から */
    fun of(grips: List<Grip>): Result = Result(grips.filter { it.bonusPercent > 0 }.groupingBy { it }.eachCount())

    /**
     * @property counts ボーナスの付くすべりやすさごとの、残っていたパーツの数（晴れ（[Grip.NORMAL]）は入れない）
     */
    data class Result(val counts: Map<Grip, Int>) {
        /** 足す分（上限をかける前）[百分の一] */
        val addedPercent: Int get() = counts.entries.sumOf { (grip, n) -> grip.bonusPercent * n }

        /** 模型を大きくする倍率（1 以上、[MAX_MULTIPLIER] まで） */
        val multiplier: Double get() = minOf(1.0 + addedPercent / 100.0, MAX_MULTIPLIER)

        /** 上限で止めたか */
        val capped: Boolean get() = 1.0 + addedPercent / 100.0 > MAX_MULTIPLIER

        /** ボーナスが付いたか（1 倍より大きい） */
        val active: Boolean get() = addedPercent > 0

        /** 画面に出す並び（滑りやすいほうから） */
        val entries: List<Pair<Grip, Int>> get() = counts.entries.sortedByDescending { it.key.bonusPercent }.map { it.key to it.value }
    }
}

/**
 * タワーを、台の上面のまん中（原点）を中心に [factor] 倍に大きくする（天気のボーナス。決定 68）。
 * 置いた位置と大きさ（[com.rein4ce.roadcollector.core.model.Placement.scale]）と高さに同じ倍率をかけるので、
 * 形も並び方も変わらず、そのまま大きくなる（世界の点 = 回転(大きさ × 形の点) + 位置 なので、両方に掛ければ全体が掛かる）
 */
fun Tower.enlarged(factor: Double): Tower {
    if (factor == 1.0) return this
    val f = factor.toFloat()
    return copy(
        placements = placements.map { it.copy(x = it.x * f, y = it.y * f, scale = it.scale * f) },
        height = height * f,
    )
}
