package com.rein4ce.roadcollector.server

import com.google.api.core.SettableApiFuture
import com.google.cloud.NoCredentials
import com.google.cloud.firestore.FirestoreOptions
import com.rein4ce.roadcollector.core.model.TowerMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

/**
 * Firestore 版のうち、本物の Firestore につながずに確かめられるところ。
 * - 一覧のクエリが Firestore に要求する内容（toProto で見る。RPC は出ない）
 * - ApiFuture の待ち方（成功・失敗・キャンセルの伝わり方）
 */
class FirestoreTowerStoreTest {

    /** 認証なし・接続なしのクライアント。クエリの組み立てだけに使う */
    private fun store(collection: String = "towers_test"): FirestoreTowerStore {
        val db = FirestoreOptions.newBuilder()
            .setProjectId("test-project")
            .setCredentials(NoCredentials.getInstance())
            .build()
            .service
        return FirestoreTowerStore(db, collection)
    }

    // --- F1：一覧は 5 項目だけ取る ---

    @Test
    fun `一覧のクエリは一覧用の項目だけを要求し、json は要求しない`() {
        val q = store().summariesQuery().toProto().structuredQuery

        val fields = q.select.fieldsList.map { it.fieldPath }
        assertEquals(listOf("height", "partCount", "createdAt", "author", "ownerId", "rule", "streakDays", "lastDay"), fields)
        assertFalse("json" in fields)
    }

    @Test
    fun `遊び方ごとに別のコレクションを新しい順に読む（決定 73）`() {
        val s = store("towers")
        for ((mode, name) in listOf(TowerMode.NORMAL to "towers", TowerMode.CHALLENGE to "towers_challenge", TowerMode.DAILY to "towers_daily")) {
            val q = s.summariesQuery(mode).toProto().structuredQuery
            assertEquals(name, q.fromList.single().collectionId)
            assertEquals("createdAt", q.orderByList.single().field.fieldPath)
        }
    }

    @Test
    fun `一覧のクエリは createdAt の新しい順で、上限は SUMMARY_LIMIT`() {
        val q = store().summariesQuery().toProto().structuredQuery

        assertEquals("createdAt", q.orderByList.single().field.fieldPath)
        assertEquals("DESCENDING", q.orderByList.single().direction.name)
        assertEquals(TowerStore.SUMMARY_LIMIT, q.limit.value)
    }

    @Test
    fun `接続先の説明にプロジェクト・データベース・コレクションが入る`() {
        assertEquals(
            "project=test-project, database=(default), collections=towers_test, towers_test_challenge, towers_test_daily",
            store().description,
        )
    }

    // --- F3：ApiFuture の待ち方 ---

    @Test
    fun `成功したら結果が返る`() = runBlocking {
        val f = SettableApiFuture.create<String>()
        val job = async { f.await() }
        f.set("ok")
        assertEquals("ok", job.await())
    }

    @Test
    fun `失敗したら ExecutionException に包まず元の例外がそのまま届く`() = runBlocking {
        val f = SettableApiFuture.create<String>()
        val boom = IllegalStateException("already exists")
        val job = async { runCatching { f.await() }.exceptionOrNull() }
        f.setException(boom)
        val got = job.await()
        // コルーチンはスレッドをまたぐと例外のコピーを作るので、同一性ではなく型とメッセージで見る
        assertTrue(got is IllegalStateException, "届いた例外: $got")
        assertEquals("already exists", got.message)
    }

    @Test
    fun `待っているコルーチンをキャンセルすると、すぐ終わり、元の Future もキャンセルされる`() = runBlocking {
        val f = SettableApiFuture.create<String>()
        // UNDISPATCHED：最初の中断点（await() の中）まで今のスレッドで進めてから返る。
        // await() は中断する前にコールバックとキャンセル時の処理を登録するので、時間待ちなしで前提が作れる
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.await() }

        job.cancel(CancellationException("test"))
        withTimeout(1_000) { job.join() } // Future が終わっていなくても、待ちはすぐ解ける

        assertTrue(job.isCancelled)
        assertTrue(f.isCancelled, "元の ApiFuture にキャンセルが伝わっていない")
    }
}
