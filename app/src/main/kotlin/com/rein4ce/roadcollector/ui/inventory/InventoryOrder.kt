package com.rein4ce.roadcollector.ui.inventory

import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts

/**
 * 手持ちの並び順。「最後に触ったものが上」。
 *
 * - 採集したパーツは、「先頭へ」を押した時刻か、採集した時刻の新しい順（採集したばかりのパーツは一番上に来る）
 * - 見本は、採集したパーツの下に決まった順で並ぶ。「先頭へ」を押した見本は、その時刻で上に来る
 *
 * 保存の順番（parts.json の並び）には頼らない。
 */
internal fun inventoryOrder(parts: List<Part>, movedToTopAt: Map<String, Long>): List<Part> {
    val samples = SampleParts.all.map { it.id }
    fun key(p: Part): Long {
        movedToTopAt[p.id]?.let { return it }
        val i = samples.indexOf(p.id)
        // 見本は、日時の無い古い採集パーツ（0）よりも下に、決まった順で
        return if (i >= 0) Long.MIN_VALUE + (samples.size - i) else p.walkedAt
    }
    return parts.sortedByDescending { key(it) }
}
