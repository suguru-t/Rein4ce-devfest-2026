package com.rein4ce.roadcollector.ui.inventory

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts

/**
 * 手持ちの並び順。
 *
 * 1. 並べ替えたあとに採集したパーツ（[order] に無いもの）を、採集の新しい順に一番上へ
 * 2. 次に、ドラッグなどで並べ替えた順（[order]）
 * 3. 最後に、[order] に無い見本（戻した見本など）を決まった順で
 *
 * まだ並べ替えていなければ（[order] が空）、「採集したパーツを新しい順 → 見本を決まった順」になる。
 * 保存の順番（parts.json の並び）には頼らない。
 */
internal fun inventoryOrder(parts: List<Part>, order: List<String>): List<Part> {
    val byId = parts.associateBy { it.id }
    val arranged = order.distinct().mapNotNull { byId[it] }
    val arrangedIds = arranged.mapTo(HashSet()) { it.id }
    val samples = SampleParts.all.map { it.id }
    val (restSamples, restCollected) = parts.filterNot { it.id in arrangedIds }.partition { it.id in samples }
    return restCollected.sortedByDescending { it.walkedAt } +
        arranged +
        restSamples.sortedBy { samples.indexOf(it.id) }
}

/** [from] 番目の要素を [to] 番目へ動かした列 */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (from == to || from !in indices || to !in indices) this
    else toMutableList().apply { add(to, removeAt(from)) }
