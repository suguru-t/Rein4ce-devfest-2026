package com.rein4ce.roadcollector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFE07A2A)
val Teal = Color(0xFF2A9D8F)
val Ink = Color(0xFF1F2A37)
val Cream = Color(0xFFFFF8F2)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = Cream,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

/** ダークモードの色。積む画面の地（クリーム色）は、あたたかみのある黒にする */
private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = Color(0xFF1C1915),
    surface = Color(0xFF26221E),
    onBackground = Color(0xFFEFE7DF),
    onSurface = Color(0xFFEFE7DF),
)

/**
 * アプリのテーマ。[dark] はダークモードか（設定と端末から MainActivity が決める）。
 * 野帳の色（Fieldbook）は描く処理の中でも使うので、テーマとは別に MainActivity で切り替えている
 */
@Composable
fun RoadCollectorTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
