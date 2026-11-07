package com.rein4ce.roadcollector.data

import android.content.Context
import android.util.Log
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
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

    /** 手持ちの並び順（パーツの id の列）の保存先。パーツのデータ形式は変えたくないので別のファイルにする */
    private val orderFile = File(file.parentFile, "${file.nameWithoutExtension}_order.json")
    private val orderSerializer = ListSerializer(String.serializer())

    /**
     * パーツを歩いた場所（id → 形の中心と長辺の長さ）の保存先。拡大表示の背景の地図に使う。
     * **Part とは別のファイルで、スマホの中だけに持つ**（Part はタワーと一緒にサーバへ送られるので、入れると歩いた場所が分かってしまう）
     */
    private val placesFile = File(file.parentFile, "${file.nameWithoutExtension}_places.json")
    private val placesSerializer = MapSerializer(String.serializer(), PartPlace.serializer())
    private val writeLock = Mutex()

    private val _parts = MutableStateFlow<List<Part>>(emptyList())
    val parts: StateFlow<List<Part>> = _parts

    private val _order = MutableStateFlow<List<String>>(emptyList())

    /**
     * 手持ちで並べ替えた順（パーツの id の列）。まだ並べ替えていなければ空。
     * ここに無いパーツ（並べ替えたあとに採集したもの、戻した見本）の置き場所は、手持ち画面が決める（InventoryOrder.kt）
     */
    val order: StateFlow<List<String>> = _order

    private val _places = MutableStateFlow<Map<String, PartPlace>>(emptyMap())

    /**
     * 採集したパーツを歩いた場所（id → 場所）。場所を残す前に採集したパーツは入っていない。
     * 見本の場所はここには無く、[placeOf] が SampleParts から返す
     */
    val places: StateFlow<Map<String, PartPlace>> = _places

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

        // 並び順。読み込み前に並べ替えられていたら（ふつうは無い）そちらを使う。
        // 前の形式（「先頭へ」の時刻の表）や壊れたファイルは読めないので、並べ替えていない状態から始める
        val orderFromFile: List<String> = runCatching {
            if (orderFile.exists()) AppJson.decodeFromString(orderSerializer, orderFile.readText()) else emptyList()
        }.onFailure { logError("${orderFile.name} could not be read, ignoring", it) }.getOrDefault(emptyList())
        val ids = _parts.value.mapTo(HashSet()) { it.id }
        _order.update { early -> (early.ifEmpty { orderFromFile }).filter { it in ids }.distinct() }

        // 歩いた場所。読み込み前に足されたパーツの場所（起動直後の採集）も残す
        val placesFromFile: Map<String, PartPlace> = runCatching {
            if (placesFile.exists()) AppJson.decodeFromString(placesSerializer, placesFile.readText()) else emptyMap()
        }.onFailure { logError("${placesFile.name} could not be read, ignoring", it) }.getOrDefault(emptyMap())
        var changed = false
        _places.update { early ->
            // 残すかどうかは、上の ids ではなく、いまの手持ちで決める。ids を作ったあとに採集（add）が割り込むと、
            // そのパーツの場所まで捨ててしまうため。add はパーツを足してから場所を足すので、ここで見れば必ず手持ちにある
            val have = _parts.value.mapTo(HashSet()) { it.id }
            val kept = (placesFromFile + early).filterKeys { it in have }
            changed = kept != placesFromFile
            kept
        }
        // ファイルと違ったら書き直す。読み込み前に足された場所を残すためと、手持ちに無いパーツの場所
        // （parts.json が壊れて見本から作り直したときなど）を、ファイルからも消すため（歩いた場所をスマホに残し続けない）
        if (changed) persistPlaces()
    }

    /**
     * 採集したパーツを足す。並び順は手持ち画面が [order] と採集日時から決める（InventoryOrder.kt）ので、保存の順番には意味を持たせない。
     * パーツを先に足してから場所を足す（[load] は、場所を残すかを手持ちにあるかで決めるため、この順番に頼っている）。
     * @param place 歩いた場所（TrackToPart の結果）。parts.json とは別のファイルに保存する
     */
    fun add(part: Part, place: PartPlace) {
        _parts.update { it + part }
        persist()
        _places.update { it + (part.id to place) }
        persistPlaces()
    }

    /**
     * そのパーツを歩いた場所。保存した場所（[places]）に無ければ見本の場所（千住キャンパス付近）。
     * 場所を残す前に採集したパーツは null。
     * 画面は [places] を見ていて、変わったら描き直すので、そのとき見ている値を [saved] に渡す
     */
    fun placeOf(id: String, saved: Map<String, PartPlace> = _places.value): PartPlace? = saved[id] ?: SampleParts.places[id]

    fun remove(id: String) {
        _parts.update { list -> list.filterNot { it.id == id } }
        persist()
        if (id in _order.value) {
            _order.update { it - id }
            persistOrder()
        }
        if (id in _places.value) {
            _places.update { it - id }
            persistPlaces()
        }
    }

    /**
     * 手持ちの並び順を保存する（ドラッグで並べ替えた、「先頭へ」を押した、など）。
     * 手持ちに無い id と重複は落とす。
     */
    fun setOrder(ids: List<String>) {
        val have = _parts.value.mapTo(HashSet()) { it.id }
        _order.value = ids.filter { it in have }.distinct()
        persistOrder()
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

    private fun persistOrder() = save(orderFile) { AppJson.encodeToString(orderSerializer, _order.value) }

    private fun persistPlaces() = save(placesFile) { AppJson.encodeToString(placesSerializer, _places.value) }

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
