package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.weather.Sky

/** コレクションの並び */
enum class PartSort(val label: String) {
    /** 自分で並べ替えた順（並べ替えていなければ、新しく歩いた順 → 見本。ui/inventory/InventoryOrder.kt） */
    CUSTOM("いつもの順"),
    NEWEST("新しい順"),
    OLDEST("古い順"),
    LONGEST("長い順"),
    SHORTEST("短い順"),
}

/**
 * パーツの並びと絞り込み。コレクションで使い、設定に保存する（アプリを閉じても覚えておく）。
 *
 * @property sky この天気の日に歩いたパーツだけ（画面に出す 4 つの天気で比べる。くもりを選ぶと霧の日も、雨を選ぶと霧雨・雷雨の日も入る。決定 73）。null なら全部
 * @property favoritesOnly お気に入りに登録したパーツだけ
 */
data class PartFilter(
    val sort: PartSort = PartSort.CUSTOM,
    val sky: Sky? = null,
    val favoritesOnly: Boolean = false,
) {
    /** いつもの順で、絞り込みもしていない。コレクションの長押しの並べ替えは、このときだけできる */
    val isDefault: Boolean get() = sort == PartSort.CUSTOM && !narrows

    /** 天気かお気に入りで絞り込んでいる */
    val narrows: Boolean get() = sky != null || favoritesOnly
}

/**
 * [arranged]（いつもの順）を [filter] で絞り込み、並べ替える。
 * 同じ値（同じ長さ・同じ日時）のあいだは、いつもの順のまま並べる（並べ替えは安定）。
 * 歩いた日時の無いパーツ（古いデータ）は、いちばん古い扱い
 *
 * @param skyOf そのパーツを歩いたときの空のようす（分からなければ null。天気で絞り込むと出ない）
 */
fun applyPartFilter(arranged: List<Part>, filter: PartFilter, skyOf: (Part) -> Sky?, favorites: Set<String>): List<Part> {
    val narrowed = arranged.filter { part ->
        (filter.sky == null || skyOf(part)?.shown == filter.sky.shown) && (!filter.favoritesOnly || part.id in favorites)
    }
    return when (filter.sort) {
        PartSort.CUSTOM -> narrowed
        PartSort.NEWEST -> narrowed.sortedByDescending { it.walkedAt }
        PartSort.OLDEST -> narrowed.sortedBy { it.walkedAt }
        PartSort.LONGEST -> narrowed.sortedByDescending { it.distanceM }
        PartSort.SHORTEST -> narrowed.sortedBy { it.distanceM }
    }
}
