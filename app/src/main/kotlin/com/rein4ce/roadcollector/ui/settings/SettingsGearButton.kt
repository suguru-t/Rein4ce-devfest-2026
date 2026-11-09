package com.rein4ce.roadcollector.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.rein4ce.roadcollector.ui.common.PressIconButton
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 設定を開く歯車のボタン（ホームの右上。決定 52。前は記録画面の見出しの右）。
 *
 * 押すと歯車がくるっと半回転して止まってから、設定の窓が開く（窓が歯車の半分にかかるので、先に回して見せる）。
 * 回っている間に押しても回り足さない（続けて押しても、1 回目の 0.4 秒後に開く。#77 のレビュー）。
 * 次に押したときは同じ向きへ回り足す（歯車の歯は 8 つなので、半回転で止まっても形は同じ）。
 * 端末の設定でアニメーションを切っている人には回さず、すぐ開く（Compose が設定に従う）
 */
@Composable
fun SettingsGearButton(modifier: Modifier = Modifier) {
    var showSettings by remember { mutableStateOf(false) }
    val gearTurn = remember { Animatable(0f) }
    val gearScope = rememberCoroutineScope()
    val gearJob = remember { mutableStateOf<Job?>(null) }
    // 押したときの縮みと振動（#76 の押し心地）に、回転を重ねる
    PressIconButton(
        modifier = modifier,
        onClick = {
            if (gearJob.value?.isActive != true) {
                gearJob.value = gearScope.launch {
                    gearTurn.animateTo(gearTurn.targetValue + 180f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
                    showSettings = true
                }
            }
        },
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "設定",
            tint = Fieldbook.Ink,
            modifier = Modifier.graphicsLayer { rotationZ = gearTurn.value },
        )
    }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}
