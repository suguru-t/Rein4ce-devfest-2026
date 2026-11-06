package com.rein4ce.roadcollector.data

import android.content.Context
import android.util.Log
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * 手持ちパーツの保存。いまは JSON ファイル 1 つ（filesDir/parts.json）。
 * Room に置き換えるときは、このクラスの中身だけ差し替えればよい。
 *
 * テストでは [file] と [scope] を差し替えて、Android なしで動かす（app/src/test の PartRepositoryTest）。
 *
 * @param file    保存先
 * @param scope   読み込み・保存を走らせる場所
 * @param logError 読み込み・保存に失敗したときの記録（テストでは何もしない関数を渡す）
 */
class PartRepository(
    private val file: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val logError: (String, Throwable) -> Unit = { message, e -> Log.w(TAG, message, e) },
) {

    /** アプリからはこちら。保存先は filesDir/parts.json */
    constructor(context: Context) : this(File(context.filesDir, "parts.json"))

    private val serializer = ListSerializer(Part.serializer())

    /** 「先頭へ」を押した時刻（パーツの id → epoch millis）の保存先。パーツのデータ形式は変えたくないので別のファイルにする */
    private val orderFile = File(file.parentFile, "${file.nameWithoutExtension}_order.json")
    private val orderSerializer = MapSerializer(String.serializer(), Long.serializer())
    private val writeLock = Mutex()

    private val _parts = MutableStateFlow<List<Part>>(emptyList())
    val parts: StateFlow<List<Part>> = _parts

    private val _movedToTopAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** 手持ちで「先頭へ」を押した時刻（パーツの id → epoch millis）。並び順は手持ち画面が決める（InventoryOrder.kt） */
    val movedToTopAt: StateFlow<Map<String, Long>> = _movedToTopAt

    private val _isLoaded = MutableStateFlow(false)

    /**
     * 起動時の読み込みが終わったか。
     * - 保存（save）はこれが true になるまで待つ。待たないと、読み込みより先に保存が走ったとき、保存済みの手持ちを上書きして消してしまう
     * - 終わるまでは [parts] が空なので、画面は件数や「見本を戻す」を出さない
     *   （読み込み中に「見本を戻す」を押すと、わざと消した見本まで戻ってしまうため）
     */
    val isLoaded: StateFlow<Boolean> = _isLoaded

    init {
        scope.launch {
            try {
                load()
            } finally {
                _isLoaded.value = true
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
        }.onFailure { logError("parts.json could not be read, reseeding", it) }.getOrNull()

        val base = fromFile ?: SampleParts.all
        var addedBeforeLoad = false
        _parts.update { early ->
            val ids = base.mapTo(HashSet()) { it.id }
            val extra = early.filterNot { it.id in ids }
            addedBeforeLoad = extra.isNotEmpty()
            base + extra
        }
        if (fromFile == null || addedBeforeLoad) persist()

        // 「先頭へ」の記録。読み込み前に押された分（ふつうは無い）は、そちらを優先して残す
        val orderFromFile: Map<String, Long> = runCatching {
            if (orderFile.exists()) AppJson.decodeFromString(orderSerializer, orderFile.readText()) else emptyMap()
        }.onFailure { logError("${orderFile.name} could not be read, ignoring", it) }.getOrDefault(emptyMap())
        val ids = _parts.value.mapTo(HashSet()) { it.id }
        _movedToTopAt.update { early -> (orderFromFile + early).filterKeys { it in ids } }
    }

    /** 採集したパーツを足す。並び順は手持ち画面が採集日時と「先頭へ」の時刻で決める（InventoryOrder.kt）ので、保存の順番には意味を持たせない */
    fun add(part: Part) {
        _parts.update { it + part }
        persist()
    }

    fun remove(id: String) {
        _parts.update { list -> list.filterNot { it.id == id } }
        persist()
        if (id in _movedToTopAt.value) {
            _movedToTopAt.update { it - id }
            persistOrder()
        }
    }

    /**
     * 手持ちの先頭に移す（「先頭へ」を押した時刻を記録する）。
     * 新しく採集したパーツも採集した時刻で上に来るので、「最後に触ったものが上」という並びになる。
     * @return そのパーツがあれば true
     */
    fun moveToTop(id: String, now: Long = System.currentTimeMillis()): Boolean {
        if (_parts.value.none { it.id == id }) return false
        _movedToTopAt.update { it + (id to now) }
        persistOrder()
        return true
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
     * パーツに名前を付ける。命名 API（POST /parts/name）の結果を入れるときと、手持ち画面で付け直すときに使う。
     *
     * - 前後の空白は落とす。空なら名前を外す（表示は「○ m の散歩」に戻る）
     * - そのパーツがもう無い（名前が届く前に削除された）ときは何もしない
     * - 積む画面に渡した選択（[GameSession.selected]）には反映しない。反映すると、積んでいる途中のゲームが最初からやり直しになるため
     *
     * @return 名前を付けた（そのパーツがあった）なら true
     */
    fun rename(id: String, name: String?): Boolean {
        val cleaned = name?.trim()?.takeIf { it.isNotEmpty() }
        var found = false
        _parts.update { list ->
            found = false // update は競合すると関数を呼び直すので、毎回数え直す
            list.map { p ->
                if (p.id == id) {
                    found = true
                    p.copy(name = cleaned)
                } else {
                    p
                }
            }
        }
        if (found) persist()
        return found
    }

    /**
     * parts.json に保存する。
     * - 起動時の読み込みが終わるまで待つ（読む前に書くと、保存済みの手持ちを上書きしてしまう）
     * - 書き込みは 1 本ずつ順番に行い、書く直前の最新の状態を書く（古い状態が後から上書きして、消したパーツが復活しないように）
     * - 一時ファイルに書いてから差し替える（書き込み中にアプリが落ちても parts.json が壊れないように）
     */
    private fun persist() = save(file) { AppJson.encodeToString(serializer, _parts.value) }

    private fun persistOrder() = save(orderFile) { AppJson.encodeToString(orderSerializer, _movedToTopAt.value) }

    /**
     * ファイルに保存する（parts.json も、並び順のファイルも同じ書き方）。
     * - 起動時の読み込みが終わるまで待つ（読む前に書くと、保存済みの手持ちを上書きしてしまう）
     * - 書き込みは 1 本ずつ順番に行い、書く直前の最新の状態を書く（古い状態が後から上書きして、消したパーツが復活しないように）
     * - 一時ファイルに書いてから差し替える（書き込み中にアプリが落ちても壊れないように）
     */
    private fun save(target: File, encode: () -> String) {
        scope.launch {
            isLoaded.first { it }
            writeLock.withLock {
                runCatching {
                    val json = encode()
                    val tmp = File(target.parentFile, "${target.name}.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(target)) {
                        target.writeText(json)
                        tmp.delete()
                    }
                }.onFailure { logError("failed to save ${target.name}", it) }
            }
        }
    }

    companion object {
        private const val TAG = "PartRepository"
    }
}
