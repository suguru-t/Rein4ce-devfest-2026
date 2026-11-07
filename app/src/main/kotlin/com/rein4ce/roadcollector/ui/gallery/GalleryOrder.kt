package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.TowerSummary

/**
 * 「みんなのタワー」の並び順（＝比べる物差し）。画面の上の切り替えで選ぶ。
 * どの並びでも、同じ値なら新しいほうを上にする（並びが取り直すたびに入れ替わらないように）
 *
 * @property label    切り替えに出す字
 * @property spoken   読み上げ（TalkBack）で言う言葉
 * @property topLabel その物差しで 1 番のタワーの呼び名（断面図の下の字、ランキングの 1 番の札、見出し）
 * @property unit     値の単位（高さは m、パーツは本）
 */
internal enum class TowerSort(val label: String, val spoken: String, val topLabel: String, val unit: String) {
    HEIGHT("高い順", "高い順に並べる", "最長", "m"),
    PARTS("多い順", "パーツの多い順に並べる", "最多", "本"),

    /** 新しい順。「あなたのタワー」だけで使う（断面図とランキングは、値で比べられる高い順・多い順だけ） */
    NEWEST("新しい順", "新しい順に並べる", "最新", "m"),
    ;

    val comparator: Comparator<TowerSummary>
        get() = when (this) {
            HEIGHT -> compareByDescending<TowerSummary> { it.height }.thenByDescending { it.createdAt }
            PARTS -> compareByDescending<TowerSummary> { it.partCount }.thenByDescending { it.height }.thenByDescending { it.createdAt }
            NEWEST -> compareByDescending<TowerSummary> { it.createdAt }.thenByDescending { it.height }
        }

    /** この物差しでのタワーの値（高さ [m] か、パーツの本数）。新しい順には値の物差しが無いので、横棒は高さで出す */
    fun valueOf(t: TowerSummary): Float = when (this) {
        HEIGHT, NEWEST -> t.height
        PARTS -> t.partCount.toFloat()
    }

    /** 値を字にする（高さは小数 2 桁、本数は整数） */
    fun format(value: Float): String = when (this) {
        HEIGHT, NEWEST -> "%.2f".format(java.util.Locale.US, value)
        PARTS -> "${value.toInt()}"
    }

    companion object {
        /** 断面図とランキングで選べる並び（値で比べられるものだけ） */
        val COMPARABLE: List<TowerSort> = listOf(HEIGHT, PARTS)
    }
}

/** 一覧を [sort] の順に並べ直す */
internal fun sortTowers(towers: List<TowerSummary>, sort: TowerSort): List<TowerSummary> = towers.sortedWith(sort.comparator)

/**
 * 断面図で比べる 2 基：その物差しで 1 番のタワー（[top]）と、自分のいちばん良いタワー（[mine]）。
 * 自分のが 1 番（値が 1 番と同じなら 1 番と見なす。同じ 5 本などはよくあるため）なら、[top] に自分のタワーを置き、
 * [mine] には他人でいちばん良いタワーを入れて、比べる相手がいるようにする（その順位は [otherRank]）。
 * 自分のタワーが無ければ [mine] は null（断面図には空の台を出す）
 *
 * @property mineIsTop 自分のタワーが 1 番か（そのとき [mine] は他人で 1 番のタワー）
 * @property otherRank [mineIsTop] のときの、[mine]（他人で 1 番）の全部の中での順位（1 から）。自分のタワーが 1・2 番なら 3
 */
internal data class Comparison(val top: TowerSummary?, val mine: TowerSummary?, val mineIsTop: Boolean, val otherRank: Int? = null)

internal fun comparison(towers: List<TowerSummary>, sort: TowerSort, isMine: (TowerSummary) -> Boolean): Comparison {
    val sorted = sortTowers(towers, sort)
    val top = sorted.firstOrNull() ?: return Comparison(null, null, mineIsTop = false)
    val myBest = sorted.firstOrNull(isMine) ?: return Comparison(top, null, mineIsTop = false)
    // 値が同じなら自分を 1 番と見なす（他人が先に並んでいても「あと 0 本」にはしない）
    if (sort.valueOf(myBest) < sort.valueOf(top)) return Comparison(top, myBest, mineIsTop = false)
    val otherIndex = sorted.indexOfFirst { !isMine(it) }
    val other = sorted.getOrNull(otherIndex)
    return Comparison(myBest, other, mineIsTop = true, otherRank = other?.let { otherIndex + 1 })
}

/** 一度に出すタワーの数（ランキング）。それより多いときは「次へ」でめくる */
internal const val PAGE_SIZE = 5

/** [count] 個のタワーを [PAGE_SIZE] ずつに分けたときのページの数（0 個なら 0） */
internal fun pageCount(count: Int): Int = (count + PAGE_SIZE - 1) / PAGE_SIZE

/** [page]（0 から）に出すタワー。範囲の外なら空 */
internal fun <T> pageOf(items: List<T>, page: Int): List<T> =
    if (page < 0) emptyList() else items.drop(page * PAGE_SIZE).take(PAGE_SIZE)
