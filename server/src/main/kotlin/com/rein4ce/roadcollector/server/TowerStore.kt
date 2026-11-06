package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * タワーの保存。いまはプロセス内メモリ（再起動・スケールで消える）。
 * C 担当が Firestore に置き換える。インターフェースはこのまま。
 */
class TowerStore(private val maxEntries: Int = 500) {

    private val towers = ConcurrentHashMap<String, Tower>()
    private val random = SecureRandom()

    fun add(tower: Tower): Tower {
        val id = newId()
        val saved = tower.copy(
            id = id,
            createdAt = if (tower.createdAt > 0) tower.createdAt else System.currentTimeMillis(),
        )
        towers[id] = saved
        trim()
        return saved
    }

    fun get(id: String): Tower? = towers[id]

    fun count(): Int = towers.size

    fun summaries(): List<TowerSummary> = towers.values
        .sortedByDescending { it.createdAt }
        .map { TowerSummary(it.id, it.height, it.parts.size, it.createdAt, it.author) }

    private fun trim() {
        if (towers.size <= maxEntries) return
        towers.values.sortedBy { it.createdAt }
            .take(towers.size - maxEntries)
            .forEach { towers.remove(it.id) }
    }

    private fun newId(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        return buildString { repeat(8) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}
