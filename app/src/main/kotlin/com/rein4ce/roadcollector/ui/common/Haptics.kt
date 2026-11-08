package com.rein4ce.roadcollector.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.rein4ce.roadcollector.RoadCollectorApp

/**
 * アプリの振動（押したとき・並べ替えを始めたとき・題字や足跡を押したとき・タブをなぞったときなど）。
 * 設定の「振動」がオフなら鳴らさない。振動させるときは、LocalHapticFeedback を直接使わず、かならずこれを使う
 * （画面の上に開く窓（設定・選ぶ窓など）の中では、LocalHapticFeedback が端末のものに戻ってしまい、
 * 上で差し替えておいても設定が効かないため）。
 * 設定は鳴らす瞬間に読む（ボタン 1 つずつが設定を見張らないように。#79 のレビュー）
 */
@Composable
fun rememberHaptics(): HapticFeedback {
    val device = LocalHapticFeedback.current
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    return remember(device, settings) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (settings.haptics.value) device.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
}

/**
 * この中の LocalHapticFeedback を、設定の「振動」に従うものにする。
 * Compose の部品が自分で鳴らす振動（入力欄で字を長押しして選ぶときなど）にも設定が効くように、アプリの根元（AppNav）と、
 * 入力欄のある窓（ニックネーム・パーツの名前）の中身をこれで包む（窓の中は端末のものに戻るため。#79 のレビュー）
 */
@Composable
fun ProvideAppHaptics(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHapticFeedback provides rememberHaptics(), content = content)
}
