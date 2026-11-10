package com.rein4ce.roadcollector.server

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.AlreadyExistsException
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.Query
import com.google.common.util.concurrent.MoreExecutors
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.TowerSummary
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firestore 版。コレクション `towers`（TOWER_COLLECTION で変更可）に 1 タワー 1 ドキュメント。
 * 遊び方ごとにコレクションを分ける（決定 77）：通常は `towers`、3 分チャレンジは `towers_challenge`、毎日 1 つずつ積むは `towers_daily`。
 * 1 つのコレクションに遊び方の項目を入れて絞ると、「遊び方 ＋ 新しい順」の複合インデックスを Firestore に作る手間が要るのと、
 * 今までのタワーには遊び方の項目が無いため。分けておけば、どのコレクションも今までと同じ「新しい順」だけで並べられる
 *
 * ドキュメントの中身：
 *   json       タワー全体（:core の Tower を AppJson で文字列化）。形式の正はこれ
 *   height, partCount, createdAt, author, rule, streakDays, lastDay   一覧用。一覧は [SUMMARY_FIELDS] だけを取り、json は読まない
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

    /** 遊び方ごとのコレクション。通常は今までどおりの名前 */
    private val collections: Map<TowerMode, CollectionReference> =
        TowerMode.entries.associateWith { mode -> db.collection(collectionFor(collectionName, mode)) }

    private fun towersOf(mode: TowerMode): CollectionReference = collections.getValue(mode)

    /** 一覧の 1 行と、その持ち主の印（「あなた」の判定は、頼まれるたびにする） */
    private class SummaryRow(val summary: TowerSummary, val ownerId: String?)

    /**
     * 一覧を遊び方ごとに [SUMMARY_CACHE_MS] だけ覚えておく（決定 88）。前は一覧を頼まれるたびに Firestore から最大 500 件を読み、
     * 端末ごとの回数制限は印を変えればすり抜けられるので、読み取りの料金をいくらでも膨らませられた。
     * このサーバで書き込んだら、その遊び方の一覧はすぐ取り直す（送ったばかりの塔が出るように）。
     * サーバがもう 1 台動いているときは、そちらで送った塔が出るまで最大 [SUMMARY_CACHE_MS] かかる
     */
    private val summaryCache = TtlCache<TowerMode, List<SummaryRow>>(SUMMARY_CACHE_MS)

    /** 「動作確認」（GET /）の数。誰でも叩けるので、数えるのも覚えておく */
    private val countCache = TtlCache<Unit, Int>(COUNT_CACHE_MS)

    /** 書き込んだ遊び方の一覧と数を、次に頼まれたときに取り直す */
    private fun written(mode: TowerMode) {
        summaryCache.invalidate(mode)
        countCache.invalidate(Unit)
    }

    /** 起動ログ用。接続先を人が確かめられるように（認証情報は含めない） */
    val description: String
        get() = "project=${db.options.projectId}, database=${db.options.databaseId}, collections=${collections.values.joinToString { it.id }}"

    override suspend fun add(tower: Tower): Tower {
        val towers = towersOf(tower.mode)
        // id の衝突はほぼ起きないが、create() は既存なら失敗するので数回だけ振り直す
        repeat(5) {
            val saved = tower.stamped(TowerIds.next())
            val fields = saved.toFields()
            val document = towers.document(saved.id)
            try {
                document.create(fields).await()
                written(saved.mode)
                return saved
            } catch (e: AlreadyExistsException) {
                // 「既にある」には 2 通りある：
                //  a. 別のタワーと id がぶつかった → 次の id で振り直す
                //  b. 自分の create は Firestore 側で成功していたが、その応答が届かず SDK が同じ create を再送した
                //     （SDK は UNAVAILABLE などを自動で再試行する）→ 振り直すと同じタワーが 2 件できるので、
                //     既にある文書が今回の内容と同じなら成功として返す
                // 読み取り自体に失敗したら、保存できたか不明なまま次の id に進まず、そのエラーを伝える
                if (document.get().await().getString("json") == fields["json"]) {
                    written(saved.mode)
                    return saved
                }
            }
        }
        error("tower id の採番に失敗しました")
    }

    /**
     * 毎日 1 つずつ積む：同じ持ち主の 1 基を新しい形にする（決定 77）。持ち主の印で 1 件だけ探し（等しいかだけの問い合わせなので、
     * Firestore が自動で作る 1 項目のインデックスで足りる）、あれば同じ id のまま上書きする。
     * 無ければ、持ち主の印から決まる id（[dailyIdFor]）に書く（決定 88）。前はランダムな id で作っていたので、
     * 同じ持ち主の送信が重なると（時間切れで送り直したときなど）、どちらも「無い」と見て 2 基できることがあった。
     * id が決まっていれば、重なっても同じ 1 件への上書きになる
     */
    override suspend fun putDaily(tower: Tower): Tower {
        val daily = towersOf(TowerMode.DAILY)
        val owner = requireNotNull(tower.ownerId) { "daily tower needs ownerId" }
        val found = daily.whereEqualTo("ownerId", owner).limit(1).get().await().documents.firstOrNull()
        val id = found?.id ?: dailyIdFor(owner)
        val saved = tower.copy(mode = TowerMode.DAILY).stamped(id)
        daily.document(id).set(saved.toFields()).await()
        written(TowerMode.DAILY)
        return saved
    }

    override suspend fun get(id: String): Tower? {
        if (!ID_PATTERN.matches(id)) return null // 変な id で Firestore に問い合わせない
        // 通常から順に探す（共有ページの多くは通常のタワー。無ければ 3 分チャレンジ、毎日 1 つずつ積むの順）
        for (mode in TowerMode.entries) {
            val snap = towersOf(mode).document(id).get().await()
            val json = snap.getString("json") ?: continue
            return AppJson.decodeFromString(Tower.serializer(), json).copy(id = snap.id)
        }
        return null
    }

    override suspend fun count(): Int = countCache.get(Unit) {
        TowerMode.entries.sumOf { towersOf(it).count().get().await().count.toInt() }
    }

    /**
     * 一覧（決定 88）：新しい順の [TowerStore.SUMMARY_LIMIT] 件に、高い順と多い順（毎日 1 つずつ積むは連続日数順）の
     * 上位 [RANK_LIMIT] 件ずつと、頼んだ人（[owner]）の塔を足す。前は新しい順の 500 件だけで、それより古い塔は、
     * どんなに高くてもランキングと「あなたのタワー」から消えた。並べ替えはアプリがする
     */
    override suspend fun summaries(owner: String?, mode: TowerMode): List<TowerSummary> {
        val shared = summaryCache.get(mode) { readSummaries(mode) }
        // 自分の塔は人ごとに違うので覚えない（印を送ってきたときだけ、等しいかの問い合わせで取る）
        val own = if (owner.isNullOrBlank()) emptyList() else rowsOf(ownerQuery(owner, mode).get().await().documents, mode)
        return (shared + own).distinctBy { it.summary.id }
            // 持ち主の印は Firestore から読むだけで、返事には「同じか」だけを入れる
            .map { row -> row.summary.copy(mine = isOwnedBy(row.ownerId, owner)) }
    }

    private suspend fun readSummaries(mode: TowerMode): List<SummaryRow> = coroutineScope {
        listQueries(mode)
            .map { q -> async { q.get().await().documents } }
            .flatMap { it.await() }
            .let { docs -> rowsOf(docs, mode) }
            .distinctBy { it.summary.id }
    }

    private fun rowsOf(docs: List<DocumentSnapshot>, mode: TowerMode): List<SummaryRow> =
        docs.map { doc ->
                SummaryRow(
                    TowerSummary(
                        id = doc.id,
                        height = doc.getDouble("height")?.toFloat() ?: 0f,
                        partCount = doc.getLong("partCount")?.toInt() ?: 0,
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        author = doc.getString("author"),
                        // 遊び方は、どのコレクションから読んだかで決まる（今までのタワーには項目が無い）
                        mode = mode,
                        rule = doc.getString("rule"),
                        streakDays = doc.getLong("streakDays")?.toInt() ?: 0,
                        lastDay = doc.getString("lastDay"),
                    ),
                    ownerId = doc.getString("ownerId"),
                )
            }

    /** 一覧に使う問い合わせ（決定 88）：新しい順・高い順・多い順（毎日は連続日数順）。どれも 1 項目の並べ替えなので、自動のインデックスで足りる */
    internal fun listQueries(mode: TowerMode): List<Query> = listOf(
        summariesQuery(mode),
        towersOf(mode).orderBy("height", Query.Direction.DESCENDING).limit(RANK_LIMIT).select(*SUMMARY_FIELDS),
        towersOf(mode).orderBy(if (mode == TowerMode.DAILY) "streakDays" else "partCount", Query.Direction.DESCENDING)
            .limit(RANK_LIMIT).select(*SUMMARY_FIELDS),
    )

    /** 頼んだ人の塔（「あなたのタワー」）。等しいかだけの問い合わせなので、自動のインデックスで足りる */
    internal fun ownerQuery(owner: String, mode: TowerMode): Query =
        towersOf(mode).whereEqualTo("ownerId", owner).limit(OWN_LIMIT).select(*SUMMARY_FIELDS)

    /**
     * 一覧のクエリ。[mode] のコレクションを新しい順に [TowerStore.SUMMARY_LIMIT] 件、取る項目は一覧用のもの（[SUMMARY_FIELDS]）だけ。
     * select を付けないと json（タワー全体）まで Firestore から届き、500 件で数 MB の無駄になる。
     */
    internal fun summariesQuery(mode: TowerMode = TowerMode.NORMAL): Query =
        towersOf(mode).orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(TowerStore.SUMMARY_LIMIT)
            .select(*SUMMARY_FIELDS)

    private fun Tower.toFields(): Map<String, Any?> = mapOf(
        "json" to AppJson.encodeToString(Tower.serializer(), this),
        "height" to height.toDouble(),
        // 台に残った数（配置の数）。同じパーツを何度も置いたタワーも、置いた数で並べる（toSummary と同じ。決定 66）
        "partCount" to placements.size,
        "createdAt" to createdAt,
        "author" to author,
        // 持ち主の印（アプリが端末ごとに作るハッシュ。古いアプリからは null）。一覧の「あなた」の判定に使う
        "ownerId" to ownerId,
        // 3 分チャレンジの決まりの ID と、毎日 1 つずつ積むの連続日数・最後に積んだ日（決定 77。ほかの遊び方は null と 0）
        "rule" to rule,
        "streakDays" to streakDays,
        "lastDay" to lastDay,
    )

    companion object {
        private val ID_PATTERN = Regex("^[a-z0-9]{1,32}$")

        /** 一覧を覚えておく時間 [ms]（決定 88） */
        const val SUMMARY_CACHE_MS = 20_000L

        /** 一覧に足す、高い順・多い順（連続日数順）の上位の数（決定 88） */
        const val RANK_LIMIT = 300

        /** 一覧に足す、頼んだ人の塔の数の上限 */
        const val OWN_LIMIT = 100

        /**
         * 毎日 1 つずつ積むの塔の id（決定 88）。持ち主の印から決まるが、印そのものは id に使わない
         * （id は共有 URL に出るので、印をそのまま入れると、ほかの人がその人の塔を書き換えられる）。
         * 印に別の言葉を付けてハッシュし、先頭の 16 桁を使う（[ID_PATTERN] に合う形）
         */
        fun dailyIdFor(owner: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest("daily-tower:$owner".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(16)
        }

        /** 数を覚えておく時間 [ms] */
        const val COUNT_CACHE_MS = 60_000L

        /** 一覧で Firestore から取る項目。json は含めない */
        val SUMMARY_FIELDS = arrayOf("height", "partCount", "createdAt", "author", "ownerId", "rule", "streakDays", "lastDay")

        /** 遊び方 [mode] のコレクションの名前。通常は [base] のまま、ほかは `_challenge`・`_daily` を付ける（決定 77） */
        fun collectionFor(base: String, mode: TowerMode): String =
            if (mode == TowerMode.NORMAL) base else "${base}_${mode.wire}"

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
