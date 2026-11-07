package com.rein4ce.roadcollector

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.ui.AppNav
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.theme.RoadCollectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = RoadCollectorApp.from(this).settings
        setContent {
            // 画面の明るさ：設定（採集画面の歯車）と、端末のダークテーマから決める
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = mode.isDark(isSystemInDarkTheme())
            // 野帳の色は、描く処理（Canvas）の中でも読むので、テーマとは別にここで切り替える。
            // 読むのはこの下の画面だけなので、ここで入れてから描けば最初の 1 コマから正しい色になる
            Fieldbook.dark = dark
            // 上の時計・電池の字と、下のバーの色も合わせる（ダークなら白い字）
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { dark },
                )
                onDispose {}
            }
            RoadCollectorTheme(dark = dark) {
                AppNav()
            }
        }
    }

    private companion object {
        /** 下のバー（3 ボタンのナビゲーション）の、うすい地の色。enableEdgeToEdge の既定と同じ */
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
