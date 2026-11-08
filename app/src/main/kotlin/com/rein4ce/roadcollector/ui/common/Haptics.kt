package com.rein4ce.roadcollector.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp

/**
 * アプリの振動（押したとき・並べ替えを始めたとき・題字や足跡を押したとき・タブをなぞったときなど）。
 * 設定の「振動」がオフなら鳴らさない。振動させるときは、LocalHapticFeedback を直接使わず、かならずこれを使う
 * （画面の上に開く窓（設定・選ぶ窓など）の中では、LocalHapticFeedback が端末のものに戻ってしまい、
 * 上で差し替えておいても設定が効かないため）
 */
@Composable
fun rememberHaptics(): HapticFeedback {
    val device = LocalHapticFeedback.current
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    val enabled by settings.haptics.collectAsStateWithLifecycle()
    val on by rememberUpdatedState(enabled)
    return remember(device) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (on) device.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
}
