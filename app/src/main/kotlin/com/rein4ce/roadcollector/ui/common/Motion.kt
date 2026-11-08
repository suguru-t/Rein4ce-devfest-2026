package com.rein4ce.roadcollector.ui.common

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 端末で動きを減らしているか（開発者向けオプションの「アニメーション再生時間」を 0 にしているなど）。
 * アプリの中で、動きを止める・飛ばすかを決める所は、すべてこれで見る（判定を変えるときに 1 か所で済むように。#82 のレビュー）
 */
fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [animationsOff] を画面の中で 1 回だけ見る（画面を出している間は変わらないものとする） */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { animationsOff(context) }
}
