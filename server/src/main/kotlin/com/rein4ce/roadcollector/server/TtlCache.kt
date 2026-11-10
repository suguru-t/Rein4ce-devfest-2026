package com.rein4ce.roadcollector.server

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 決まった時間（[ttlMs]）だけ値を覚えておく入れ物（決定 88）。Firestore から一覧を読む回数を抑えるのに使う。
 * 一覧は 1 回で最大 500 件を読み、端末ごとの回数制限は印を変えればすり抜けられるので、覚えておかないと読み取りの料金をいくらでも膨らませられた。
 *
 * - 期限が切れていたら、最初に頼んだ 1 件だけが取り直し、同時に来たほかの頼みはその結果を待つ（同じ問い合わせを重ねない）
 * - [invalidate] したら（このサーバで書き込んだら）次の頼みで取り直す。取り直している最中に [invalidate] されたら、
 *   その結果は覚えない（書き込む前の一覧を、書き込んだあとの一覧として覚えないように）
 *
 * @param now テストで時刻を進めるため
 */
internal class TtlCache<K : Any, V>(
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val at: Long)

    private val entries = ConcurrentHashMap<K, Entry<V>>()
    private val versions = ConcurrentHashMap<K, Long>()
    private val locks = ConcurrentHashMap<K, Mutex>()

    suspend fun get(key: K, load: suspend () -> V): V {
        fresh(key)?.let { return it.value }
        val lock = locks.computeIfAbsent(key) { Mutex() }
        return lock.withLock {
            fresh(key)?.let { return@withLock it.value }
            val version = versions[key] ?: 0L
            val value = load()
            if ((versions[key] ?: 0L) == version) entries[key] = Entry(value, now())
            value
        }
    }

    /** [key] の値を捨てる（次の [get] で取り直す） */
    fun invalidate(key: K) {
        versions.merge(key, 1L, Long::plus)
        entries.remove(key)
    }

    private fun fresh(key: K): Entry<V>? = entries[key]?.takeIf { now() - it.at < ttlMs }
}
