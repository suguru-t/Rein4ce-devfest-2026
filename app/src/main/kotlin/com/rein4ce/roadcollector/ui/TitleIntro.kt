package com.rein4ce.roadcollector.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.collect.TitleBlocks
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.fieldbookGrid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 開いたときのタイトルの進み具合。アプリ全体で 1 つ（MainActivity・記録画面の題字・使い方の窓から見る）
 *
 * @property splashGone 起動画面が消えきったか。タイトルの題字は、それまで出さず、消えきったら起動画面の題字の場所に出て大きくなり始める
 *   （消えかけの起動画面と二重に見えないように）
 * @property splashTitle 起動画面のアイコンの中の題字「ミチツミ」の段のまん中（画面の左上から [px]）。
 *   タイトルの題字は、ここから同じ大きさで出て、まん中へ大きくなる（起動画面の題字がそのまま大きくなるように見える）。
 *   起動画面がない（Android 11 以前）・分からないときは null で、はじめからまん中に大きく出す
 * @property anchor 記録画面の見出しの題字の場所（画面の左上から）。タイトルの題字はここへ飛んで収まる
 * @property done タイトルが終わったか（出さないときもすぐ true）。終わるまで、記録画面の題字と使い方の窓は出さない
 */
internal object TitleIntroState {
    var splashGone by mutableStateOf(false)
    var splashTitle by mutableStateOf<Offset?>(null)
    var anchor by mutableStateOf<Rect?>(null)
    var done by mutableStateOf(false)
}

/**
 * 開いたときのタイトル。押さなくても進む（押せばすぐ飛ばせる）。
 *
 * 起動画面の積み木の塔はそのままうすく消え、その下の題字「ミチツミ」だけが、方眼の紙のまん中へ大きくなりながら移る
 * （起動画面の題字と同じ位置・大きさから始めるので、起動画面の題字がそのまま大きくなるように見える）。その下に一言を出す。
 * [HOLD_MS] たったら、題字が縮みながら記録画面の見出しの定位置へ飛んで収まり、紙がうすくなって記録画面になる。
 * 記録中や画面の作り直しのとき、端末で動きを減らしているときは出さない（MainActivity が決める）
 */
@Composable
internal fun TitleIntro() {
    if (TitleIntroState.done) return
    val grow = remember { Animatable(0f) } // 0：起動画面の題字の位置と大きさ、1：まん中に大きく
    val fly = remember { Animatable(0f) } // 0：まん中に大きく、1：見出しの定位置
    val tagline = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var titleBounds by remember { mutableStateOf<Rect?>(null) }
    var skipping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 起動画面が消えるのを待つ（消えた知らせが来なくても、少し待ったら始める）
        withTimeoutOrNull(SPLASH_WAIT_MS) { snapshotFlow { TitleIntroState.splashGone }.first { it } }
        // 知らせが来なかった（起動画面が出ない開き方など）ときも、ここから先は消えたものとして題字を出す（#82 のレビュー）
        TitleIntroState.splashGone = true
        // 起動画面の題字の位置から、まん中へ大きくなる（起動画面が無ければ、はじめからまん中）
        if (TitleIntroState.splashTitle != null) grow.animateTo(1f, tween(GROW_MS, easing = FastOutSlowInEasing)) else grow.snapTo(1f)
        launch { tagline.animateTo(1f, tween(TAGLINE_MS)) }
        delay(HOLD_MS)
        // 待っている間に押して飛ばしていたら、終わらせるのは押した側に任せる（ここで終わらせると、飛ぶ途中で消えるため。#82 のレビュー）
        if (skipping) return@LaunchedEffect
        fly.animateTo(1f, tween(FLY_MS, easing = FastOutSlowInEasing))
        TitleIntroState.done = true
    }

    // 押したら、すぐに見出しへ飛ばして終わる
    fun skip() {
        if (skipping) return
        skipping = true
        scope.launch {
            grow.snapTo(1f)
            fly.animateTo(1f, tween(SKIP_MS, easing = FastOutSlowInEasing))
            TitleIntroState.done = true
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { skip() } }
            // 読み上げでは、題字の名前と「飛ばす」を伝える（下の記録画面は、タイトルの間は読み上げから外している。MainActivity）
            .semantics(mergeDescendants = true) {
                contentDescription = "ミチツミ。$TAGLINE"
                onClick(label = "飛ばす") { skip(); true }
            },
    ) {
        // 紙（方眼）。題字が飛ぶにつれて、うすくなって記録画面が見えてくる
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - fly.value }
                .background(Fieldbook.Paper)
                .fieldbookGrid(),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TAGLINE_GAP),
        ) {
            TitleBlocks(
                interactive = false,
                modifier = Modifier
                    // 大きくする前の場所（ここから見出しの定位置までの距離を出す）
                    .onGloballyPositioned { titleBounds = it.boundsInRoot() }
                    .graphicsLayer {
                        val g = grow.value
                        val f = fly.value
                        val from = titleBounds
                        val to = TitleIntroState.anchor
                        val start = TitleIntroState.splashTitle
                        transformOrigin = TransformOrigin.Center
                        // 起動画面が消えきるまでは出さない（押して飛ばしたときは出す）
                        alpha = if (TitleIntroState.splashGone || skipping) 1f else 0f
                        if (f <= 0f) {
                            // 起動画面の題字の位置と大きさ（SPLASH_SCALE）から、まん中の大きな題字（BIG）へ。
                            // 段のまん中は、題字の枠のまん中より ROW_DROP だけ下にあるので、その分も合わせる
                            val s = SPLASH_SCALE + (BIG - SPLASH_SCALE) * g
                            scaleX = s
                            scaleY = s
                            if (from != null && start != null) {
                                val dropPx = ROW_DROP.toPx() * SPLASH_SCALE
                                translationX = (start.x - from.center.x) * (1f - g)
                                translationY = (start.y - dropPx - from.center.y) * (1f - g)
                            }
                        } else {
                            val s = BIG + (1f - BIG) * f
                            scaleX = s
                            scaleY = s
                            if (from != null && to != null) {
                                translationX = (to.center.x - from.center.x) * f
                                translationY = (to.center.y - from.center.y) * f
                            } else {
                                // 見出しの場所が分からないとき（記録画面が出ていない）は、その場でうすくする
                                alpha = 1f - f
                            }
                        }
                    },
            )
            Text(
                TAGLINE,
                color = Fieldbook.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = tagline.value * (1f - (fly.value * 2f).coerceAtMost(1f)) },
            )
        }
    }
}

/** 題字の下の一言 */
private const val TAGLINE = "歩いた道を、積み上げよう"

/**
 * 起動画面のアイコンの中の題字の大きさ（見出しの題字の何倍か）。起動画面の積み木は 38dp おき、見出しは 36dp おき
 * （tools/make_splash_icon.py の SIZE・GAP と、TitleBlocks の 32dp・4dp）
 */
private const val SPLASH_SCALE = 38f / 36f

/** 題字の枠（上に 10dp の余白がある）のまん中から見た、積み木の段のまん中の下へのずれ（TitleBlocks の作りから） */
private val ROW_DROP = 5.dp

/** まん中に出すときの題字の大きさ（見出しの何倍か）と、一言までの間（大きくした題字にかからないように） */
private const val BIG = 2.4f
private val TAGLINE_GAP = 52.dp

/** 起動画面の題字の位置から、まん中へ大きくなる時間 [ms] */
private const val GROW_MS = 480


/** まん中に大きくなってから、タイトルを見せておく時間 [ms]。題字が飛ぶ時間と、押して飛ばすときの時間 */
private const val HOLD_MS = 1500L
private const val FLY_MS = 520
private const val SKIP_MS = 260
private const val TAGLINE_MS = 360

/** 起動画面が消えた知らせを待つ上限 [ms]（知らせが来ない端末でも、いつまでも止まらないように） */
private const val SPLASH_WAIT_MS = 2500L
