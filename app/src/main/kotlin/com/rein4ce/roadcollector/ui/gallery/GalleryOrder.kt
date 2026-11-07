package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.TowerSummary

/**
 * 「みんなのタワー」の並び順。画面の上の札で選ぶ。
 * どの並びでも、同じ値なら新しいほうを上にする（並びが取り直すたびに入れ替わらないように）
 *
 * @property label 札に出す字
 * @property spoken 読み上げ（TalkBack）で言う言葉
 * @property topLabel その並びで 1 番のタワーに付ける札（見出しの数字と同じ青い札）
 */
internal enum class TowerSort(val label: String, val spoken: String, val topLabel: String) {
    HEIGHT("高い順", "高い順に並べる", "最長"),
    NEWEST("新しい順", "新しい順に並べる", "最新"),
    PARTS("パーツ数", "パーツの多い順に並べる", "最多"),
    ;

    val comparator: Comparator<TowerSummary>
        get() = when (this) {
            HEIGHT -> compareByDescending<TowerSummary> { it.height }.thenByDescending { it.createdAt }
            NEWEST -> compareByDescending<TowerSummary> { it.createdAt }.thenByDescending { it.height }
            PARTS -> compareByDescending<TowerSummary> { it.partCount }.thenByDescending { it.height }.thenByDescending { it.createdAt }
        }
}

/** 一覧を [sort] の順に並べ直す */
internal fun sortTowers(towers: List<TowerSummary>, sort: TowerSort): List<TowerSummary> = towers.sortedWith(sort.comparator)

/** 一度に出すタワーの数。それより多いときは「次へ」でめくる */
internal const val PAGE_SIZE = 5

/** [count] 個のタワーを [PAGE_SIZE] ずつに分けたときのページの数（0 個なら 0） */
internal fun pageCount(count: Int): Int = (count + PAGE_SIZE - 1) / PAGE_SIZE

/** [page]（0 から）に出すタワー。範囲の外なら空 */
internal fun <T> pageOf(items: List<T>, page: Int): List<T> =
    if (page < 0) emptyList() else items.drop(page * PAGE_SIZE).take(PAGE_SIZE)
