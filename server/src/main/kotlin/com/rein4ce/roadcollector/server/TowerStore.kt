package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * タワーの保存先。実装は 2 つ。
 *
 * - [MemoryTowerStore]：プロセス内メモリ。テストと、gcloud を入れていない手元用（再起動で消える）
 * - [FirestoreTowerStore]：Firestore。Cloud Run と、手元で TOWER_STORE=firestore にしたとき
 *
 * どちらを使うかは [TowerStore.fromEnvironment] が決める。
 */
interface TowerStore {
    /** id を振って保存し、保存したものを返す。createdAt が 0 以下なら現在時刻を入れる */
    suspend fun add(tower: Tower): Tower

    suspend fun get(id: String): Tower?

    suspend fun count(): Int

    /** 新しい順。多すぎると重いので [SUMMARY_LIMIT] 件まで */
    suspend fun summaries(): List<TowerSummary>

    companion object {
        const val SUMMARY_LIMIT = 500

        /**
         * TOWER_STORE=firestore か memory で明示できる。未指定なら、
         * Cloud Run 上（K_SERVICE がある）では Firestore、手元ではメモリ。
         */
        fun fromEnvironment(env: (String) -> String? = System::getenv): TowerStore {
            val explicit = env("TOWER_STORE")?.trim()?.lowercase()
            val useFirestore = when (explicit) {
                "firestore" -> true
                "memory" -> false
                null, "" -> !env("K_SERVICE").isNullOrBlank()
                else -> error("TOWER_STORE は firestore か memory（指定値: $explicit）")
            }
            return if (useFirestore) {
                FirestoreTowerStore.create(
                    projectId = env("GOOGLE_CLOUD_PROJECT")?.takeIf { it.isNotBlank() },
                    collection = env("TOWER_COLLECTION")?.takeIf { it.isNotBlank() } ?: "towers",
                )
            } else {
                MemoryTowerStore()
            }
        }
    }
}

/** 8 文字の id。紛らわしい文字（l, o, 0, 1）は使わない */
internal object TowerIds {
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    fun next(): String = buildString { repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}

internal fun Tower.toSummary() = TowerSummary(id, height, parts.size, createdAt, author)

internal fun Tower.stamped(id: String) = copy(
    id = id,
    createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
)

/** プロセス内メモリ版。古いものから消して [maxEntries] 件を保つ */
class MemoryTowerStore(private val maxEntries: Int = TowerStore.SUMMARY_LIMIT) : TowerStore {

    private val towers = ConcurrentHashMap<String, Tower>()

    override suspend fun add(tower: Tower): Tower {
        var id = TowerIds.next()
        while (towers.containsKey(id)) id = TowerIds.next()
        val saved = tower.stamped(id)
        towers[id] = saved
        trim()
        return saved
    }

    override suspend fun get(id: String): Tower? = towers[id]

    override suspend fun count(): Int = towers.size

    override suspend fun summaries(): List<TowerSummary> = towers.values
        .sortedByDescending { it.createdAt }
        .map { it.toSummary() }

    private fun trim() {
        if (towers.size <= maxEntries) return
        towers.values.sortedBy { it.createdAt }
            .take(towers.size - maxEntries)
            .forEach { towers.remove(it.id) }
    }
}
