package com.rein4ce.roadcollector.core.model

import kotlinx.serialization.Serializable

/**
 * POST /parts/name の応答。リクエストは [Part] をそのまま送る。
 *
 * 名前は形・距離・日時だけから付ける（位置情報は送らない）。
 * 命名に失敗したときサーバは 503 を返し、これは返さない。アプリは今の表示のままにする。
 */
@Serializable
data class PartNameResponse(
    val name: String,
)
