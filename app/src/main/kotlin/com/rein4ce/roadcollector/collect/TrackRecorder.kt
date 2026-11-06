package com.rein4ce.roadcollector.collect

import com.rein4ce.roadcollector.core.model.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 記録中の軌跡を保持する（サービスと画面の橋渡し）。
 * サービス（実 GPS）もデモ再生もここに点を流し込む。
 */
object TrackRecorder {

    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())
    val points: StateFlow<List<TrackPoint>> = _points

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    fun start() {
        _points.value = emptyList()
        _isRecording.value = true
    }

    fun add(point: TrackPoint) {
        if (!_isRecording.value) return
        _points.update { it + point }
    }

    /** 記録を止めて、点列を返す。 */
    fun stop(): List<TrackPoint> {
        _isRecording.value = false
        return _points.value
    }
}
