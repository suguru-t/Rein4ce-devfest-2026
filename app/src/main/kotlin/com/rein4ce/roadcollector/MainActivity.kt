package com.rein4ce.roadcollector

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.res.Resources
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
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.data.ThemeMode
import com.rein4ce.roadcollector.ui.AppNav
import com.rein4ce.roadcollector.ui.TitleIntro
import com.rein4ce.roadcollector.ui.TitleIntroState
import com.rein4ce.roadcollector.ui.common.animationsOff
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.theme.JapaneseText
import com.rein4ce.roadcollector.ui.theme.RoadCollectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 字を日本語として描く（#46）。画面の設定が変わると Android が端末の言語に戻すので、画面を作るたびに入れ直す
        JapaneseText.apply()
        // 起動画面（Android 12 以降）が消えるとき、積み木のアイコンの「門」の穴をくぐって採集画面に入るように見せる
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash -> enterThroughGate(splash) }
        } else {
            TitleIntroState.splashGone = true
        }
        // 開いたときのタイトル（ui/TitleIntro.kt）を出すか。アプリを新しく開いたときだけ出す。
        // 記録中（通知から戻ったときなど）、画面の作り直し（回転・ダークの切り替え）、端末で動きを減らしているときは出さない
        val showIntro = savedInstanceState == null && !introShown && !TrackRecorder.isRecording.value && !animationsOff(this)
        // 出したときだけ「出した」にする（記録中に開いて出さなかったあと、アイコンから開き直したときは出す。#82 のレビュー）
        if (showIntro) introShown = true
        introThisTime = showIntro
        if (!showIntro) TitleIntroState.done = true
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
            // 次に開くときの起動画面の明るさも、設定に合わせる（Android 13 以降。それより前は端末のダークテーマで決まる）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LaunchedEffect(mode) { splashScreen.setSplashScreenTheme(splashThemeFor(mode)) }
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
                Box {
                    // タイトルを出している間は、下の記録画面を読み上げから外す（読み上げで、見えないボタンを押せないように）
                    Box(modifier = if (TitleIntroState.done) Modifier else Modifier.clearAndSetSemantics {}) {
                        AppNav()
                    }
                    // 開いたときのタイトル。終わったら何も描かない
                    if (showIntro) TitleIntro()
                }
            }
        }
    }

    /** 起動画面のテーマ。「端末に合わせる」なら決めない（マニフェストのテーマのまま、端末のダークテーマで地の色が決まる） */
    private fun splashThemeFor(mode: ThemeMode): Int = when (mode) {
        ThemeMode.SYSTEM -> Resources.ID_NULL
        ThemeMode.LIGHT -> R.style.Theme_RoadCollector_SplashDay
        ThemeMode.DARK -> R.style.Theme_RoadCollector_SplashNight
    }

    /** このあと開いたときのタイトルを出すか（出すなら、起動画面は門をくぐらずに、題字をタイトルへ渡して消える） */
    private var introThisTime = false

    /**
     * 起動画面のアイコン（splash_icon）を、積み木の門の形の穴に向かって大きくしながら、起動画面ごとうすくして消す。
     * 開いたときのタイトルを出すときは、門をくぐらず、アイコンの題字の段の場所をタイトルに渡して、うすくして消す（[handTitleOver]）。
     * 穴をくぐると採集画面が出てくるように見える。「動きを減らす」設定の人には、端末が動きを止めるのですぐ消える
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun enterThroughGate(splash: SplashScreenView) {
        val icon = splash.iconView
        if (icon == null) {
            splash.remove()
            TitleIntroState.splashGone = true
            return
        }
        // タイトルを出すときは、門をくぐらずに題字をタイトルへ渡す
        if (introThisTime) {
            handTitleOver(splash, icon)
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
        AnimatorSet().apply {
            // 起動画面のアイコンの動き（最後の「ミ」が右の外から落ちてきて、1 回はねて収まる）が終わるまで待ってから、門をくぐる
            startDelay = iconSettleWaitMs(splash)
            playTogether(zoom, fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    splash.remove()
                    TitleIntroState.splashGone = true
                }
            })
            start()
        }
    }

    /**
     * 開いたときのタイトルを出すとき：起動画面の積み木の塔は大きくせず、そのままうすくして消す。
     * アイコンの中の題字「ミチツミ」の段のまん中（画面の上の位置）は、待つ前にタイトルに渡しておく
     * （起動が遅くて、タイトルが待ちきれずに先へ進んでも、起動画面の題字の場所から大きくなれるように。#82 のレビュー）。
     * タイトルの題字は、起動画面が消えきってから、そこに同じ大きさで出て、まん中へ大きくなる（ui/TitleIntro.kt）
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun handTitleOver(splash: SplashScreenView, icon: View) {
        val at = IntArray(2)
        icon.getLocationInWindow(at)
        val dp = resources.displayMetrics.density
        TitleIntroState.splashTitle = Offset(at[0] + icon.width / 2f, at[1] + icon.height / 2f + TITLE_DY_DP * dp)
        ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f).apply {
            duration = HANDOVER_FADE_MS
            startDelay = iconSettleWaitMs(splash)
            // 起動画面が消えきってから、タイトルの題字を出す（消えかけの起動画面と、タイトルの題字が二重に見えないように）
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    splash.remove()
                    TitleIntroState.splashGone = true
                }
            })
            start()
        }
    }

    /** 起動画面のアイコンの動きが収まって、一瞬（PAUSE_MS）置くまでの待ち時間 [ms]。長くても MAX_WAIT_MS */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun iconSettleWaitMs(splash: SplashScreenView): Long {
        val animStart = splash.iconAnimationStart ?: return 0L
        val animDuration = splash.iconAnimationDuration ?: return 0L
        return (animStart.toEpochMilli() + animDuration.toMillis() + PAUSE_MS - System.currentTimeMillis()).coerceIn(0L, MAX_WAIT_MS)
    }

    private companion object {
        /**
         * 起動画面のアイコン（288×288dp の絵）のまん中から見た、題字「ミチツミ」の段のまん中 [dp]（絵の中では y 188.2）。
         * tools/make_splash_icon.py の大きさ・位置で決まるので、作り直したら合わせる
         */
        const val TITLE_DY_DP = 44.2f

        /**
         * タイトルに題字を渡すときに、起動画面をうすくして消す時間 [ms]。タイトルの題字は、消えきってから出る。
         * 長いと、消えかけの積み木の塔とタイトルの題字が重なって見えたので短くした
         */
        const val HANDOVER_FADE_MS = 120L

        /** このプロセスで、開いたときのタイトルをもう出したか（画面を作り直しても、もう一度は出さない） */
        var introShown = false

        /**
         * 起動画面のアイコン（288×288dp の絵）のまん中から見た、門の形の穴のまん中 [dp]（絵の中では x 144・y 108）。
         * tools/make_splash_icon.py の大きさ・位置で決まるので、作り直したら合わせる
         */
        const val GATE_DX_DP = 0f
        const val GATE_DY_DP = -35.8f

        /** 起動画面のアイコンの動きを待つのは、長くてもこれだけ [ms]（起動が遅くなりすぎないように） */
        const val MAX_WAIT_MS = 650L

        /** 「ミ」が収まってから、門をくぐり始めるまでの間 [ms] */
        const val PAUSE_MS = 300L

        /** 穴が画面いっぱいより大きくなるまで、アイコンを大きくする倍率 */
        const val GATE_ZOOM = 40f

        /** 下のバー（3 ボタンのナビゲーション）の、うすい地の色。enableEdgeToEdge の既定と同じ */
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
