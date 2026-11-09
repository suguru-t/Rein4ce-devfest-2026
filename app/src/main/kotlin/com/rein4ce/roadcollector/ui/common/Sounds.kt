package com.rein4ce.roadcollector.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.audio.SoundEffects

/**
 * アプリの効果音（audio/SoundEffects.kt）。押した瞬間の振動（[rememberHaptics]）と同じ所で鳴らす：
 * ボタン・カード・☆を押したとき、題字「ミチツミ」と画面名の積み木を押した・着いたとき、道を 1 本集めたとき。
 * 下のタブは、振動だけで鳴らさない（B が決めた）
 * 設定の「効果音」がオフなら鳴らない（鳴らす瞬間に読む）
 */
@Composable
fun rememberSfx(): SoundEffects = RoadCollectorApp.from(LocalContext.current).sfx
