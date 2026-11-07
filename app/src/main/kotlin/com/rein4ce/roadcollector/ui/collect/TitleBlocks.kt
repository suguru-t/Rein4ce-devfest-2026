package com.rein4ce.roadcollector.ui.collect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 題字のブロックの上の字の色（起動画面の題字と同じ） */
private val TitleLetter = Color(0xFFF3F6F0)

/**
 * 題字「ミチツミ」の字。アプリのアイコンのように見えるよう、太い丸ゴシック M PLUS Rounded 1c Black（OFL）を
 * この 3 文字だけに切り出して同梱している（出どころとライセンスは app/fonts/）
 */
private val TitleRounded = FontFamily(Font(R.font.mplus_rounded_michitsumi, FontWeight.Black))

/** 題字のブロック 1 つ。[tiltDeg] は傾き、[liftDp] は持ち上げ（負で上）。色はアプリのアイコン（ic_launcher_foreground）の 4 色 */
private class TitleBlock(val char: Char, val color: Color, val tiltDeg: Float, val liftDp: Float)

/** 最後の 1 つは浮かせて（影も濃く）「置く直前」に見せる */
private val Blocks = listOf(
    TitleBlock('ミ', Color(0xFFE07A2A), tiltDeg = -5f, liftDp = 0f),
    TitleBlock('チ', Color(0xFF2A9D8F), tiltDeg = 3f, liftDp = 1f),
    TitleBlock('ツ', Color(0xFFF2C14E), tiltDeg = -2f, liftDp = 0f),
    TitleBlock('ミ', Color(0xFF4A4E69), tiltDeg = 8f, liftDp = -9f),
)

/** ブロック 1 つの、ふだんの位置からのずれ（横・縦 [dp]、回転 [度]）。押したときだけ動く */
private class BlockMotion {
    val dx = Animatable(0f)
    val dy = Animatable(0f)
    val turn = Animatable(0f)
}

/**
 * 採集画面の題字「ミチツミ」。1 文字ずつ色の付いたブロックに白抜きで載せ、少しずつ傾けて並べた、積み木を積んでいる途中の絵。
 *
 * 押すと動く（遊び）：
 * - 「ミ」「チ」「ツ」：そのブロックがその場で軽く跳ね、1 回はねて元に戻る
 * - 最後の「ミ」：起動画面と同じように、右の画面の外から回りながら落ちてきて、1 回はねて収まる
 *
 * 動くのは見た目だけ（graphicsLayer）で、まわりの配置は変わらない。押せる場所はふだんの位置のままなので、
 * 動いている途中に同じ場所を押すと、初めから動き直す。題字は飾りなので、読み上げではボタンにしない。
 * 端末で「動きを減らす」（アニメーションの速度 0）にしていれば、動きは飛ばされてすぐ元の位置に戻る
 */
@Composable
internal fun TitleBlocks(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(7.dp)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val motions = remember { List(Blocks.size) { BlockMotion() } }
    val running = remember { arrayOfNulls<Job>(Blocks.size) }
    // 最後の「ミ」を画面の右の外から落とすための、画面の幅とブロックの左端 [dp]
    val screenWidthDp by rememberUpdatedState(LocalConfiguration.current.screenWidthDp.toFloat())
    val lastLeftDp = remember { FloatArray(1) }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "ミチツミ" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Blocks.forEachIndexed { i, block ->
            val motion = motions[i]
            val isLast = i == Blocks.lastIndex
            val lifted = block.liftDp < 0f
            Box(
                modifier = Modifier
                    // 浮かせた分の場所を上に取っておく（傾けた角が見出しの外にはみ出さないように少し余裕も）
                    .padding(top = 10.dp)
                    .size(32.dp)
                    .onGloballyPositioned { if (isLast) lastLeftDp[0] = with(density) { it.positionInRoot().x.toDp().value } }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            running[i]?.cancel()
                            running[i] = scope.launch {
                                if (isLast) {
                                    // 画面の右の外（ブロック 1 つ半ぶん先）から
                                    val fromRight = screenWidthDp - lastLeftDp[0] + 48f
                                    motion.dropIn(fromRight) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    motion.hop(sign = if (block.tiltDeg < 0f) -1f else 1f)
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        rotationZ = block.tiltDeg + motion.turn.value
                        translationX = motion.dx.value.dp.toPx()
                        translationY = (block.liftDp + motion.dy.value).dp.toPx()
                    }
                    .shadow(if (lifted) 6.dp else 2.dp, shape, clip = false)
                    .background(block.color, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    block.char.toString(),
                    fontFamily = TitleRounded,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { 21.dp.toSp() },
                    // 題字はロゴなので、ダークモードでも白い字のまま（紙の色に合わせて暗くしない）
                    color = TitleLetter,
                )
            }
        }
    }
}

/**
 * その場で軽く跳ねる：傾きを少し増しながら上がって落ち、1 回小さくはねて戻る。
 * はね方は起動画面の「ミ」と同じ形（上がりは減速、下がりは加速）。[sign] は傾ける向き（ふだんの傾きと同じ向きに増す）
 */
private suspend fun BlockMotion.hop(sign: Float) = coroutineScope {
    launch { dx.animateTo(0f, tween(HOP_UP_MS)) }
    launch {
        turn.animateTo(8f * sign, tween(HOP_UP_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
        turn.animateTo(-3f * sign, tween(BOUNCE_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }
    dy.animateTo(-12f, tween(HOP_UP_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
    dy.animateTo(-4f, tween(BOUNCE_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
}

/**
 * 起動画面（splash_icon_animated）の「ミ」と同じ落ち方：右上から回りながら落ちてきて、止まる位置の少し右に着き、
 * 左へ弧を描いて 1 回はねて収まる。起動画面の絵は題字の 1.55 倍なので、着く位置のずれ・はねる高さはその分小さくする。
 * [fromRightDp] は落ち始めの横のずれ（画面の右の外）。遠いほど少し長く落とす。[onLand] は着いたとき（手ごたえの振動）
 */
private suspend fun BlockMotion.dropIn(fromRightDp: Float, onLand: () -> Unit) = coroutineScope {
    val startDx = maxOf(fromRightDp, 168f)
    // 起動画面の落ち方の傾き（横 260 に対して縦 70）に合わせる
    dx.snapTo(startDx)
    dy.snapTo(-startDx * 70f / 260f)
    turn.snapTo(60f)
    val fallMs = (150 * startDx / 168f).toInt().coerceIn(150, 230)
    launch {
        dx.animateTo(13f, tween(fallMs, easing = LinearEasing))
        dx.animateTo(0f, tween(BOUNCE_MS * 2, easing = LinearEasing))
    }
    launch {
        turn.animateTo(10f, tween(fallMs, easing = LinearEasing))
        turn.animateTo(-6f, tween(BOUNCE_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }
    dy.animateTo(0f, tween(fallMs, easing = EaseInQuad))
    onLand()
    dy.animateTo(-8f, tween(BOUNCE_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
}

/** 跳ねるときの、上がる（下がる）時間 [ms] */
private const val HOP_UP_MS = 120

/** 1 回はねるときの、上がる（下がる）時間 [ms]。起動画面の「ミ」と同じ */
private const val BOUNCE_MS = 55
