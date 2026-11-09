package com.rein4ce.roadcollector.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin

/*
 * 押した「戻る」のボタンの所から、戻った先の画面が大きくなって出てくる動き（決定 78）。
 * タブバーの家のボタンでホームへ戻るとき（ui/AppNav.kt）と、積む画面の「‹ 戻る」で遊び方を選ぶ画面へ戻るとき
 * （ui/game/ModeSelect.kt）に使う。端末の「戻る」では使わない（今までどおりの切り替え）
 */

/**
 * ボタンのまん中 [center] が、大きくなる画面 [area] の中のどこかを割合で返す（どちらもアプリの画面の左上から）。
 * まだ置かれていないなどで分からないときは null
 */
internal fun zoomOrigin(center: Offset, area: Rect): TransformOrigin? =
    if (center.isSpecified && area.width > 0f && area.height > 0f) {
        TransformOrigin(
            ((center.x - area.left) / area.width).coerceIn(0f, 1f),
            ((center.y - area.top) / area.height).coerceIn(0f, 1f),
        )
    } else {
        null
    }

/**
 * 画面が、ボタンほどの大きさから [origin] を中心に画面いっぱいまで大きくなって出てくる。
 * 出てくる画面を作る最初のコマは重く、そこで時間が進んで小さいところが飛ばされるので、
 * ばねのような速い出だしにはせず、少しゆっくり始めて終わりをゆるめる
 */
internal fun zoomInFrom(origin: TransformOrigin): EnterTransition =
    scaleIn(
        animationSpec = tween(ZOOM_IN_MS, easing = FastOutSlowInEasing),
        initialScale = ZOOM_FROM_SCALE,
        transformOrigin = origin,
    ) + fadeIn(tween(ZOOM_FADE_MS), initialAlpha = ZOOM_FROM_ALPHA)

/**
 * [content] を、出てきたときに [origin] から大きくして見せる（[zoomInFrom] と同じ動き。画面の切り替え（NavHost）の外で使う）。
 * [origin] が null なら、そのまま出す
 */
@Composable
internal fun ZoomInFrom(origin: TransformOrigin?, content: @Composable () -> Unit) {
    if (origin == null) {
        content()
        return
    }
    // 出てきた最初のコマは小さく、そこから大きくする（出てくるたびにこの部品ごと作り直されるので、1 回だけ動く）
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = shown, enter = zoomInFrom(origin), exit = ExitTransition.None) { content() }
}

/** 大きくなり始めるときの大きさ（画面の何倍か）。家のボタン（56dp）は幅 400dp ほどの画面の 0.14 倍なので、それより少し小さく */
private const val ZOOM_FROM_SCALE = 0.12f

/** ボタンの大きさから画面いっぱいになるまでの時間 [ms] */
private const val ZOOM_IN_MS = 480

/** 大きくなりながら、うすい所から濃くなる時間 [ms] */
private const val ZOOM_FADE_MS = 180

/**
 * 大きくなり始めるときの濃さ。0 から濃くすると、軽い画面では最初の 0.1 秒ほど小さくてうすいだけで何も無いように見えるので
 * （押したボタンが消えて、画面が空になったように見える）、押したボタンの所に少し見えている所から始める
 */
private const val ZOOM_FROM_ALPHA = 0.4f
