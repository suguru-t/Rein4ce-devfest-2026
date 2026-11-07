package com.rein4ce.roadcollector.server

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.Query
import com.google.common.util.concurrent.MoreExecutors
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firestore 版。コレクション `towers`（TOWER_COLLECTION で変更可）に 1 タワー 1 ドキュメント。
 *
 * ドキュメントの中身：
 *   json       タワー全体（:core の Tower を AppJson で文字列化）。形式の正はこれ
 *   height, partCount, createdAt, author   一覧用。一覧は [SUMMARY_FIELDS] だけを取り、json は読まない
 *
 * 認証は Application Default Credentials。Cloud Run ではサービスアカウント、
 * 手元では `gcloud auth application-default login` の結果を使う。鍵ファイルは使わない。
 *
 * 保存先のプロジェクトは [create] の projectId（環境変数 GOOGLE_CLOUD_PROJECT）で決まる。
 * 認証情報が「誰の権限か」、projectId が「どのプロジェクトの Firestore か」で、別のもの。
 */
class FirestoreTowerStore(
    private val db: Firestore,
    collectionName: String = "towers",
) : TowerStore {

    private val towers: CollectionReference = db.collection(collectionName)

    /** 起動ログ用。接続先を人が確かめられるように（認証情報は含めない） */
    val description: String
        get() = "project=${db.options.projectId}, database=${db.options.databaseId}, collection=${towers.id}"

    override suspend fun add(tower: Tower): Tower {
        // id の衝突はほぼ起きないが、create() は既存なら失敗するので数回だけ振り直す
        repeat(5) {
            val saved = tower.stamped(TowerIds.next())
            val fields = saved.toFields()
            val document = towers.document(saved.id)
            try {
                document.create(fields).await()
                return saved
            } catch (e: AlreadyExistsException) {
                // 「既にある」には 2 通りある：
                //  a. 別のタワーと id がぶつかった → 次の id で振り直す
                //  b. 自分の create は Firestore 側で成功していたが、その応答が届かず SDK が同じ create を再送した
                //     （SDK は UNAVAILABLE などを自動で再試行する）→ 振り直すと同じタワーが 2 件できるので、
                //     既にある文書が今回の内容と同じなら成功として返す
                // 読み取り自体に失敗したら、保存できたか不明なまま次の id に進まず、そのエラーを伝える
                if (document.get().await().getString("json") == fields["json"]) return saved
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
        summariesQuery().get().await()
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

    /**
     * 一覧のクエリ。新しい順に [TowerStore.SUMMARY_LIMIT] 件、取る項目は一覧用の 4 つだけ。
     * select を付けないと json（タワー全体）まで Firestore から届き、500 件で数 MB の無駄になる。
     */
    internal fun summariesQuery(): Query =
        towers.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(TowerStore.SUMMARY_LIMIT)
            .select(*SUMMARY_FIELDS)

    private fun Tower.toFields(): Map<String, Any?> = mapOf(
        "json" to AppJson.encodeToString(Tower.serializer(), this),
        "height" to height.toDouble(),
        "partCount" to parts.size,
        "createdAt" to createdAt,
        "author" to author,
    )

    companion object {
        private val ID_PATTERN = Regex("^[a-z0-9]{1,32}$")

        /** 一覧で Firestore から取る項目。json は含めない */
        val SUMMARY_FIELDS = arrayOf("height", "partCount", "createdAt", "author")

        /**
         * @param projectId 保存先のプロジェクト。null なら Java クライアントの既定の決め方
         *   （環境変数 → Cloud Run などの実行環境 → gcloud の設定）に任せる。手元で複数のプロジェクトを
         *   使っていると別のプロジェクトを向くことがあるので、手元では GOOGLE_CLOUD_PROJECT で明示する
         */
        fun create(projectId: String?, collection: String): FirestoreTowerStore {
            val options = FirestoreOptions.getDefaultInstance().toBuilder()
                .apply { if (projectId != null) setProjectId(projectId) }
                .build()
            return FirestoreTowerStore(options.service, collection)
        }
    }
}

/**
 * Firestore の Java クライアントは ApiFuture を返す。スレッドを止めずに待ち、
 * 待っているコルーチンがキャンセルされたら元の Future にもキャンセルを伝える
 * （Future.get() で待つと、キャンセルしても IO スレッドが Future の完了まで残る）。
 * 失敗したときは、ExecutionException に包まず元の例外（AlreadyExistsException など）をそのまま投げる。
 */
internal suspend fun <T> ApiFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    ApiFutures.addCallback(
        this,
        object : ApiFutureCallback<T> {
            override fun onSuccess(result: T) {
                if (cont.isActive) cont.resume(result)
            }

            override fun onFailure(t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            }
        },
        MoreExecutors.directExecutor(),
    )
    cont.invokeOnCancellation { cancel(true) }
}
