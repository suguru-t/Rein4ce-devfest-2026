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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _parts = MutableStateFlow<List<Part>>(emptyList())
    val parts: StateFlow<List<Part>> = _parts

    init {
        scope.launch { load() }
    }

    private fun load() {
        val loaded: List<Part>? = runCatching {
            if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else null
        }.onFailure { Log.w(TAG, "parts.json could not be read, reseeding", it) }.getOrNull()

        if (loaded == null) {
            _parts.value = SampleParts.all
            persist()
        } else {
            _parts.value = loaded
        }
    }

    fun add(part: Part) {
        _parts.update { it + part }
        persist()
    }

    fun remove(id: String) {
        _parts.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun resetToSamples() {
        _parts.value = SampleParts.all
        persist()
    }

    private fun persist() {
        val snapshot = _parts.value
        scope.launch {
            runCatching { file.writeText(AppJson.encodeToString(serializer, snapshot)) }
                .onFailure { Log.e(TAG, "failed to save parts.json", it) }
        }
    }

    companion object {
        private const val TAG = "PartRepository"
    }
}
