package com.rein4ce.roadcollector.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * コレクションから積む画面へ渡す開始要求。
 * Partの古い値ではなくIDだけを渡し、開始時に現在のコレクションから解決する。
 */
object GameSession {
    data class StartRequest(val id: Long, val partIds: List<String>)

    var startRequest: StartRequest? by mutableStateOf(null)
        private set

    private var nextRequestId = 0L

    /** 同じ素材を選び直した場合も、新しい開始操作として区別する。 */
    fun requestStart(partIds: List<String>) {
        startRequest = StartRequest(++nextRequestId, partIds.distinct().take(MAX_PARTS))
    }

    const val MAX_PARTS = 5
}
