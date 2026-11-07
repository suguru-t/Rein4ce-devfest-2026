package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

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

    /**
     * 新しい順。多すぎると重いので [SUMMARY_LIMIT] 件まで。
     * @param owner 一覧を取るアプリの持ち主の印（X-Owner-Id）。送ったタワーなら [TowerSummary.mine] を true にする。null なら全部 false
     */
    suspend fun summaries(owner: String? = null): List<TowerSummary>

    companion object {
        const val SUMMARY_LIMIT = 500

        private val log = LoggerFactory.getLogger(TowerStore::class.java)

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
            if (!useFirestore) {
                log.info("TowerStore: memory（再起動で消える）")
                return MemoryTowerStore()
            }
            val store = FirestoreTowerStore.create(
                projectId = env("GOOGLE_CLOUD_PROJECT")?.takeIf { it.isNotBlank() },
                collection = env("TOWER_COLLECTION")?.takeIf { it.isNotBlank() } ?: "towers",
            )
            // この行は「Firestore 版を選んで接続先を決めた」ことしか示さない。読み書きできるかは POST → GET で確かめる
            log.info("TowerStore: firestore（${store.description}）")
            return store
        }
    }
}

/** 8 文字の id。紛らわしい文字（l, o, 0, 1）は使わない */
internal object TowerIds {
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    fun next(): String = buildString { repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}

/** 一覧の 1 行。持ち主の印は入れず、[owner] と同じかどうかだけを入れる */
internal fun Tower.toSummary(owner: String? = null) = TowerSummary(id, height, parts.size, createdAt, author, mine = isOwnedBy(owner))

/** [owner] が送ったタワーか。印が無いタワー・印を送っていない相手は、どちらも「違う」 */
internal fun isOwnedBy(ownerId: String?, owner: String?): Boolean = !owner.isNullOrBlank() && ownerId == owner

private fun Tower.isOwnedBy(owner: String?) = isOwnedBy(ownerId, owner)

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

    override suspend fun summaries(owner: String?): List<TowerSummary> = towers.values
        .sortedByDescending { it.createdAt }
        .map { it.toSummary(owner) }

    private fun trim() {
        if (towers.size <= maxEntries) return
        towers.values.sortedBy { it.createdAt }
            .take(towers.size - maxEntries)
            .forEach { towers.remove(it.id) }
    }
}
