package com.rein4ce.roadcollector

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.window.SplashScreenView
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.ui.AppNav
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.theme.RoadCollectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 起動画面（Android 12 以降）が消えるとき、積み木のアイコンの「門」の穴をくぐって採集画面に入るように見せる
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash -> enterThroughGate(splash) }
        }
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
            // 記録中は画面を消さない（設定でオンにしたときだけ。地図を見ながら歩くとき用）。記録を止めたら元に戻す
            val keepOn by settings.keepScreenOn.collectAsStateWithLifecycle()
            val recording by TrackRecorder.isRecording.collectAsStateWithLifecycle()
            val view = LocalView.current
            DisposableEffect(keepOn && recording) {
                view.keepScreenOn = keepOn && recording
                onDispose { view.keepScreenOn = false }
            }
            RoadCollectorTheme(dark = dark) {
                AppNav()
            }
        }
    }

    /**
     * 起動画面のアイコン（splash_icon）を、積み木の門の形の穴に向かって大きくしながら、起動画面ごとうすくして消す。
     * 穴をくぐると採集画面が出てくるように見える。「動きを減らす」設定の人には、端末が動きを止めるのですぐ消える
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun enterThroughGate(splash: SplashScreenView) {
        val icon = splash.iconView
        if (icon == null) {
            splash.remove()
            return
        }
        // 起動画面のアイコンは、部品のまん中に絵のまん中が来るように、そのままの大きさ（1dp = 1dp）で描かれる。
        // 部品の大きさは端末によって違う（絵の 288dp と同じとは限らない）ので、まん中からの距離で穴の位置を決める
        val dp = resources.displayMetrics.density
        icon.pivotX = icon.width / 2f + GATE_DX_DP * dp
        icon.pivotY = icon.height / 2f + GATE_DY_DP * dp
        val zoom = ObjectAnimator.ofPropertyValuesHolder(
            icon,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, GATE_ZOOM),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, GATE_ZOOM),
        ).apply {
            duration = 320
            interpolator = AccelerateInterpolator(2f)
        }
        // くぐり始めてから、起動画面をうすくして採集画面を見せる
        val fade = ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f).apply {
            startDelay = 180
            duration = 140
        }
        // 起動画面のアイコンの動き（最後の「ミ」が右の外から飛び込んできて収まる）が終わるまで待ってから、門をくぐる
        val animStart = splash.iconAnimationStart
        val animDuration = splash.iconAnimationDuration
        val wait = if (animStart != null && animDuration != null) {
            (animStart.toEpochMilli() + animDuration.toMillis() - System.currentTimeMillis()).coerceIn(0L, MAX_WAIT_MS)
        } else {
            0L
        }
        AnimatorSet().apply {
            startDelay = wait
            playTogether(zoom, fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = splash.remove()
            })
            start()
        }
    }

    private companion object {
        /**
         * 起動画面のアイコン（288×288dp の絵）のまん中から見た、門の形の穴のまん中 [dp]（絵の中では x 144・y 108）。
         * tools/make_splash_icon.py の大きさ・位置で決まるので、作り直したら合わせる
         */
        const val GATE_DX_DP = 0f
        const val GATE_DY_DP = -35.8f

        /** 起動画面のアイコンの動きを待つのは、長くてもこれだけ [ms]（起動が遅くなりすぎないように） */
        const val MAX_WAIT_MS = 350L

        /** 穴が画面いっぱいより大きくなるまで、アイコンを大きくする倍率 */
        const val GATE_ZOOM = 40f

        /** 下のバー（3 ボタンのナビゲーション）の、うすい地の色。enableEdgeToEdge の既定と同じ */
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
