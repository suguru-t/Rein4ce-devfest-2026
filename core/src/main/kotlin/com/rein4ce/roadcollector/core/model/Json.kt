package com.rein4ce.roadcollector.core.model

import kotlinx.serialization.json.Json

/**
 * アプリ・サーバで共通の JSON 設定。
 * 未知のフィールドは無視する（フィールド追加で古いデータが読めなくならないように）。
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}
