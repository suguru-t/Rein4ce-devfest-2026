package com.rein4ce.roadcollector.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Tower

/**
 * 画面をまたいで渡す「いま遊んでいるもの」。
 * 手持ち画面で選んだパーツ → 積む画面、完成したタワー → 送信、など。
 */
object GameSession {
    /** 手持ち画面で選んだパーツ（最大 5 個） */
    var selected: List<Part> by mutableStateOf(emptyList())

    /** 直近に完成したタワー */
    var lastTower: Tower? by mutableStateOf(null)

    const val MAX_PARTS = 5
}
