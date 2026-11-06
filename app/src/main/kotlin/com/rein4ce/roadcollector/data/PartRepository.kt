package com.rein4ce.roadcollector.data

import android.content.Context
import android.util.Log
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 手持ちパーツの保存。いまは JSON ファイル 1 つ（filesDir/parts.json）。
 * Room に置き換えるときは、このクラスの中身だけ差し替えればよい。
 */
class PartRepository(context: Context) {

    private val file = File(context.filesDir, "parts.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serializer = ListSerializer(Part.serializer())
    private val writeLock = Mutex()

    private val _parts = MutableStateFlow<List<Part>>(emptyList())
    val parts: StateFlow<List<Part>> = _parts

    /**
     * 起動時の読み込みが終わったら完了する。保存（persist）はこれを待つ。
     * 待たないと、読み込みより先に保存が走ったとき、保存済みの手持ちを上書きして消してしまう。
     */
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                load()
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    /**
     * parts.json を読む。
     * 読み込みが終わる前に add() などで足されたパーツ（起動直後の採集やデモ散歩）は、捨てずに後ろに残す。
     */
    private fun load() {
        val fromFile: List<Part>? = runCatching {
            if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else null
        }.onFailure { Log.w(TAG, "parts.json could not be read, reseeding", it) }.getOrNull()

        val base = fromFile ?: SampleParts.all
        var addedBeforeLoad = false
        _parts.update { early ->
            val ids = base.mapTo(HashSet()) { it.id }
            val extra = early.filterNot { it.id in ids }
            addedBeforeLoad = extra.isNotEmpty()
            base + extra
        }
        if (fromFile == null || addedBeforeLoad) persist()
    }

    /** 採集したパーツを足す。並び順（新しい順）は手持ち画面が採集日時で決めるので、保存の順番には意味を持たせない */
    fun add(part: Part) {
        _parts.update { it + part }
        persist()
    }

    fun remove(id: String) {
        _parts.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    /** 削除したサンプルだけを足し戻す。採集したパーツには触らない */
    fun restoreMissingSamples() {
        _parts.update { list ->
            val have = list.mapTo(HashSet()) { it.id }
            list + SampleParts.all.filterNot { it.id in have }
        }
        persist()
    }

    /**
     * parts.json に保存する。
     * - 起動時の読み込みが終わるまで待つ（読む前に書くと、保存済みの手持ちを上書きしてしまう）
     * - 書き込みは 1 本ずつ順番に行い、書く直前の最新の状態を書く（古い状態が後から上書きして、消したパーツが復活しないように）
     * - 一時ファイルに書いてから差し替える（書き込み中にアプリが落ちても parts.json が壊れないように）
     */
    private fun persist() {
        scope.launch {
            loaded.await()
            writeLock.withLock {
                runCatching {
                    val json = AppJson.encodeToString(serializer, _parts.value)
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(file)) {
                        file.writeText(json)
                        tmp.delete()
                    }
                }.onFailure { Log.e(TAG, "failed to save parts.json", it) }
            }
        }
    }

    companion object {
        private const val TAG = "PartRepository"
    }
}
