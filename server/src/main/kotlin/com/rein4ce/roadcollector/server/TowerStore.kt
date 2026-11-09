package com.rein4ce.roadcollector.server

import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
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
 *
 * 遊び方（[TowerMode]。通常・3 分チャレンジ・毎日 1 つずつ積む）ごとに分けて持ち、一覧も遊び方ごとに返す（決定 73）。
 */
interface TowerStore {
    /**
     * id を振って保存し、保存したものを返す。createdAt が 0 以下なら現在時刻を入れる。
     * 保存先はタワーの遊び方（[Tower.mode]）で決まる（通常・3 分チャレンジ。毎日 1 つずつ積むは [putDaily]）
     */
    suspend fun add(tower: Tower): Tower

    /**
     * 毎日 1 つずつ積むの塔を、持ち主（[Tower.ownerId]）ごとに 1 基だけ持つ（決定 73）。同じ持ち主の塔があれば、その id のまま
     * 新しい形にし、無ければ id を振って作る。ownerId が無いタワーは渡さない（Routes の検査で断る）
     */
    suspend fun putDaily(tower: Tower): Tower

    /** id から 1 基（どの遊び方のものでも） */
    suspend fun get(id: String): Tower?

    suspend fun count(): Int

    /**
     * [mode] の遊び方のタワーを新しい順。多すぎると重いので [SUMMARY_LIMIT] 件まで。
     * @param owner 一覧を取るアプリの持ち主の印（X-Owner-Id）。送ったタワーなら [TowerSummary.mine] を true にする。null なら全部 false
     */
    suspend fun summaries(owner: String? = null, mode: TowerMode = TowerMode.NORMAL): List<TowerSummary>

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
/**
 * 一覧用の形。パーツ数は台に残った数（配置の数）で数える。通常は同じパーツが何度も出る（#108）ので、`parts`（同じパーツを 1 つにまとめる）
 * だと置いた数より少なく出るため（決定 66）。今までのタワーは 1 本 1 回ずつなので、数は変わらない
 */
internal fun Tower.toSummary(owner: String? = null) = TowerSummary(
    id, height, placements.size, createdAt, author, mine = isOwnedBy(owner),
    mode = mode, rule = rule, streakDays = streakDays, lastDay = lastDay,
)

/** [owner] が送ったタワーか。印が無いタワー・印を送っていない相手は、どちらも「違う」 */
internal fun isOwnedBy(ownerId: String?, owner: String?): Boolean = !owner.isNullOrBlank() && ownerId == owner

private fun Tower.isOwnedBy(owner: String?) = isOwnedBy(ownerId, owner)

internal fun Tower.stamped(id: String) = copy(
    id = id,
    createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
)

/** プロセス内メモリ版。遊び方ごとに、古いものから消して [maxEntries] 件を保つ */
class MemoryTowerStore(private val maxEntries: Int = TowerStore.SUMMARY_LIMIT) : TowerStore {

    private val towers = ConcurrentHashMap<String, Tower>()
    private val lock = Any()

    override suspend fun add(tower: Tower): Tower = synchronized(lock) {
        val saved = tower.stamped(newId())
        towers[saved.id] = saved
        trim(saved.mode)
        saved
    }

    override suspend fun putDaily(tower: Tower): Tower = synchronized(lock) {
        val existing = towers.values.firstOrNull { it.mode == TowerMode.DAILY && it.ownerId != null && it.ownerId == tower.ownerId }
        val saved = tower.copy(mode = TowerMode.DAILY).stamped(existing?.id ?: newId())
        towers[saved.id] = saved
        trim(TowerMode.DAILY)
        saved
    }

    private fun newId(): String {
        var id = TowerIds.next()
        while (towers.containsKey(id)) id = TowerIds.next()
        return id
    }

    override suspend fun get(id: String): Tower? = towers[id]

    override suspend fun count(): Int = towers.size

    override suspend fun summaries(owner: String?, mode: TowerMode): List<TowerSummary> = towers.values
        .filter { it.mode == mode }
        .sortedByDescending { it.createdAt }
        .map { it.toSummary(owner) }

    private fun trim(mode: TowerMode) {
        val same = towers.values.filter { it.mode == mode }
        if (same.size <= maxEntries) return
        same.sortedBy { it.createdAt }
            .take(same.size - maxEntries)
            .forEach { towers.remove(it.id) }
    }
}
