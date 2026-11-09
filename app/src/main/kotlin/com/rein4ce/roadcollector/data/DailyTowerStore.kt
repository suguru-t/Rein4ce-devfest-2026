package com.rein4ce.roadcollector.data

import android.util.AtomicFile
import com.rein4ce.roadcollector.core.daily.DailyTower
import com.rein4ce.roadcollector.core.model.AppJson
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * 「毎日 1 つずつ積む」の塔の保存先（filesDir/daily_tower.json。決定 61）。スマホの中だけで、サーバには送らない。
 * AtomicFile で書くので、書いている途中で落ちても壊れない。読めないファイルは別の名前に退避して、空の塔から始める
 * （3分チャレンジの記録と同じ。上書きして消さない）
 *
 * @param read  ファイルの中身（無ければ null）。テストではただの変数
 * @param write ファイルに書く
 * @param quarantine 読めなかった中身を別の名前で残す
 * @param io 読み書きするスレッド。アプリは IO。テストでは同じスレッドで終わらせる（Dispatchers.Unconfined）。
 *   裏のスレッドで読み書きすると、テストが終わったあとに保存が残り、次のテストに例外を持ち込むことがあった
 */
class DailyTowerStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val quarantine: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** アプリからはこちら */
    constructor(directory: File) : this(AtomicFile(File(directory, FILE_NAME)), directory)

    private constructor(file: AtomicFile, directory: File) : this(
        read = {
            try {
                file.openRead().bufferedReader().use { it.readText() }
            } catch (e: FileNotFoundException) {
                if (file.baseFile.exists()) throw e
                null
            }
        },
        write = { text ->
            val stream = file.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        },
        quarantine = {
            val broken = File(directory, "daily_tower.broken-${System.currentTimeMillis()}.json")
            if (!file.baseFile.renameTo(broken)) file.delete()
        },
    )

    private val _tower = MutableStateFlow<DailyTower?>(null)

    /** 今の塔。読み込むまでは null */
    val tower: StateFlow<DailyTower?> = _tower

    /** ファイルから読む（読めなければ退避して空の塔） */
    suspend fun load(): DailyTower = withContext(io) {
        synchronized(lock) {
            val tower = try {
                read()?.let { AppJson.decodeFromString(DailyTower.serializer(), it) } ?: DailyTower()
            } catch (e: Exception) {
                runCatching { quarantine() }
                DailyTower()
            }
            _tower.value = tower
            tower
        }
    }

    /** 塔を保存する。画面を離れても、書き終えるまで止めない */
    suspend fun save(tower: DailyTower) = withContext(io + NonCancellable) {
        synchronized(lock) {
            write(AppJson.encodeToString(DailyTower.serializer(), tower))
            _tower.value = tower
        }
    }

    private val lock = Any()

    private companion object {
        const val FILE_NAME = "daily_tower.json"
    }
}
