package com.rein4ce.roadcollector.ui.inventory

import com.rein4ce.roadcollector.data.GameSession
import kotlin.random.Random

/**
 * 積むパーツを「おまかせ」で選ぶ（Issue #44）。候補（パーツの id）から、重複なしで [count] 個をランダムに選ぶ。
 * 返す順が選んだ順（＝積む順）。候補が [count] より少なければ全部を選ぶ（順はランダム）。
 * [count] は 1〜[GameSession.MAX_PARTS] に収める。
 */
internal fun pickRandom(candidates: List<String>, count: Int, random: Random = Random.Default): List<String> =
    candidates.distinct().shuffled(random).take(count.coerceIn(1, GameSession.MAX_PARTS))

/**
 * おまかせで選んだあとの一言。候補が足りなくて頼んだ数より少なくなったときは、黙って減らさずにそう伝える
 */
internal fun randomPickNote(picked: Int, asked: Int): String =
    if (picked < asked) "コレクションが $picked 本なので、全部選びました" else "ランダムに $picked 本選びました"
