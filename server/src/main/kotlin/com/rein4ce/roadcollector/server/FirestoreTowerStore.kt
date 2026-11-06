package com.rein4ce.roadcollector.server

import com.google.api.core.ApiFuture
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.Query
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Firestore 版。コレクション `towers`（TOWER_COLLECTION で変更可）に 1 タワー 1 ドキュメント。
 *
 * ドキュメントの中身：
 *   json       タワー全体（:core の Tower を AppJson で文字列化）。形式の正はこれ
 *   height, partCount, createdAt, author   一覧用。一覧のたびに全パーツを読まずに済む
 *
 * 認証は Application Default Credentials。Cloud Run ではサービスアカウント、
 * 手元では `gcloud auth application-default login` の結果を使う。鍵ファイルは使わない。
 */
class FirestoreTowerStore(
    private val db: Firestore,
    collectionName: String = "towers",
) : TowerStore {

    private val towers: CollectionReference = db.collection(collectionName)

    override suspend fun add(tower: Tower): Tower {
        // id の衝突はほぼ起きないが、create() は既存なら失敗するので数回だけ振り直す
        repeat(5) {
            val saved = tower.stamped(TowerIds.next())
            try {
                towers.document(saved.id).create(saved.toFields()).await()
                return saved
            } catch (e: AlreadyExistsException) {
                // 次の id で再試行
            }
        }
        error("tower id の採番に失敗しました")
    }

    override suspend fun get(id: String): Tower? {
        if (!ID_PATTERN.matches(id)) return null // 変な id で Firestore に問い合わせない
        val snap = towers.document(id).get().await()
        val json = snap.getString("json") ?: return null
        return AppJson.decodeFromString(Tower.serializer(), json).copy(id = snap.id)
    }

    override suspend fun count(): Int =
        towers.count().get().await().count.toInt()

    override suspend fun summaries(): List<TowerSummary> =
        towers.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(TowerStore.SUMMARY_LIMIT)
            .get().await()
            .documents
            .map { doc ->
                TowerSummary(
                    id = doc.id,
                    height = doc.getDouble("height")?.toFloat() ?: 0f,
                    partCount = doc.getLong("partCount")?.toInt() ?: 0,
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    author = doc.getString("author"),
                )
            }

    private fun Tower.toFields(): Map<String, Any?> = mapOf(
        "json" to AppJson.encodeToString(Tower.serializer(), this),
        "height" to height.toDouble(),
        "partCount" to parts.size,
        "createdAt" to createdAt,
        "author" to author,
    )

    companion object {
        private val ID_PATTERN = Regex("^[a-z0-9]{1,32}$")

        /** projectId が null なら、ADC・環境から自動で決まる（手元は ADC の quota project） */
        fun create(projectId: String?, collection: String): FirestoreTowerStore {
            val options = FirestoreOptions.getDefaultInstance().toBuilder()
                .apply { if (projectId != null) setProjectId(projectId) }
                .build()
            return FirestoreTowerStore(options.service, collection)
        }
    }
}

/** Firestore の Java クライアントは ApiFuture を返す。待つ間はスレッドを IO に逃がす */
private suspend fun <T> ApiFuture<T>.await(): T = withContext(Dispatchers.IO) {
    try {
        get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
}
