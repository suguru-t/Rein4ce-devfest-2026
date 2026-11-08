package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.NicknameHints
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts

/**
 * ニックネームを AI に考えてもらうときに送る手がかり（[NicknameHints]）を、コレクションのパーツから作る。
 *
 * - 見本のパーツは入れない（自分で歩いた道ではないため）
 * - 名前は、名前の付いたパーツだけを新しく歩いた順に、重ならないように [NicknameHints.MAX_PART_NAMES] 個まで
 *   （「341 m の散歩」のような、名前のないパーツの呼び名は手がかりにならないので送らない）
 * - 本数と合計の長さは、自分のパーツ全部から数える。歩いた場所は送らない
 */
fun nicknameHintsFor(parts: List<Part>): NicknameHints {
    val own = parts.filter { SampleParts.byId(it.id) == null }
    val names = own.sortedByDescending { it.walkedAt }
        .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .take(NicknameHints.MAX_PART_NAMES)
    return NicknameHints(partNames = names, partCount = own.size, totalDistanceM = own.sumOf { it.distanceM })
}
