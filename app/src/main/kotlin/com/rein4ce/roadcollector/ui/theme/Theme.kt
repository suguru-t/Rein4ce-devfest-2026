package com.rein4ce.roadcollector.ui.theme

import androidx.compose.material3.MaterialTheme
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

@Composable
fun RoadCollectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
