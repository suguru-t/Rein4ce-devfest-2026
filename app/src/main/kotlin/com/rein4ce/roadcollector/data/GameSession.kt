package com.rein4ce.roadcollector.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * コレクションから積む画面へ渡す開始要求。
 * Partの古い値ではなくIDだけを渡し、開始時に現在のコレクションから解決する。
 * 重複の除去と最大 [MAX_PARTS] 個への切り詰めは、開始する側（StackViewModel）で行う。
 */
object GameSession {
    /**
     * @property started この要求でゲームを一度始めたか。積む画面（ViewModel）が作り直されたとき、
     *   選んだ直後の要求なのか、前に遊んだ選択なのかを見分けるために使う
     */
    data class StartRequest(val id: Long, val partIds: List<String>, val started: Boolean = false)

    var startRequest: StartRequest? by mutableStateOf(null)
        private set

    private var nextRequestId = 0L

    /** 同じ素材を選び直した場合も、新しい開始操作として区別する。 */
    fun requestStart(partIds: List<String>) {
        startRequest = StartRequest(++nextRequestId, partIds.toList())
    }

    /** [id] の要求でゲームを始めたことを記録する。別の要求に置き換わっていたら何もしない */
    fun markStarted(id: Long) {
        val request = startRequest ?: return
        if (request.id == id && !request.started) startRequest = request.copy(started = true)
    }

    /** テスト用：プロセス全体で共有している状態を初期値に戻す */
    internal fun resetForTest() {
        startRequest = null
        nextRequestId = 0L
    }

    const val MAX_PARTS = 5
}
