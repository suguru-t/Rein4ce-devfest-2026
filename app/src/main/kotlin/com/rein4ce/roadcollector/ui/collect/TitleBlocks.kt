package com.rein4ce.roadcollector.ui.collect

import android.os.Build
import android.view.View
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.ui.common.rememberHaptics
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

/** 題字のブロック 1 つの大きさ [dp]。間・字・角の丸み・持ち上げ・跳ね方は、これに合わせて同じ割合で大きくなる */
internal const val TITLE_BLOCK_DP = 38f

/** 32dp のブロックで決めた寸法を、[TITLE_BLOCK_DP] の大きさに直す倍率 */
private const val K = TITLE_BLOCK_DP / 32f

/** ブロックの間 [dp]。ブロック 1 つ分の送り（[TITLE_BLOCK_DP] + これ）は、開いたときのタイトルが起動画面の題字に合わせるのに使う */
internal const val TITLE_GAP_DP = 4f * K

/** 題字の上の余白 [dp]（浮かせた最後の「ミ」と、傾けた角が見出しの外にはみ出さないように） */
internal const val TITLE_TOP_PAD_DP = 10f * K

/** 題字のブロック 1 つ。[tiltDeg] は傾き、[liftDp] は持ち上げ（負で上。32dp のブロックでの値）。色はアプリのアイコン（ic_launcher_foreground）の 4 色 */
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
 * - 最後の「ミ」：画面の上のカメラの穴のあたりから、回りながら斜めに落ちてきて、1 回はねて収まる（起動画面と同じはね方）
 *
 * 動くのは見た目だけ（graphicsLayer）で、まわりの配置は変わらない。押せる場所はふだんの位置のままなので、
 * 動いている途中に同じ場所を押すと、初めから動き直す。題字は飾りなので、読み上げではボタンにしない。
 * 端末で「動きを減らす」（アニメーションの速度 0）にしていれば、動きは飛ばされてすぐ元の位置に戻る
 *
 * @param interactive 押すと動くか。開いたときのタイトル（ui/TitleIntro.kt）では、押すと飛ばすので false
 */
@Composable
internal fun TitleBlocks(modifier: Modifier = Modifier, interactive: Boolean = true) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape((7f * K).dp)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val motions = remember { List(Blocks.size) { BlockMotion() } }
    val running = remember { arrayOfNulls<Job>(Blocks.size) }
    // 最後の「ミ」をカメラの穴のあたりから落とすための、ブロックのまん中（画面の左上から [px]。傾け・持ち上げの前）
    val view = LocalView.current
    val lastCenter = remember { FloatArray(2) }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "ミチツミ" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(TITLE_GAP_DP.dp),
    ) {
        Blocks.forEachIndexed { i, block ->
            val motion = motions[i]
            val isLast = i == Blocks.lastIndex
            val lifted = block.liftDp < 0f
            val liftDp = block.liftDp * K
            Box(
                modifier = Modifier
                    // 浮かせた分の場所を上に取っておく（傾けた角が見出しの外にはみ出さないように少し余裕も）
                    .padding(top = TITLE_TOP_PAD_DP.dp)
                    .size(TITLE_BLOCK_DP.dp)
                    .onGloballyPositioned {
                        if (isLast) {
                            val p = it.positionInRoot()
                            lastCenter[0] = p.x + it.size.width / 2f
                            lastCenter[1] = p.y + it.size.height / 2f
                        }
                    }
                    // 押すと動く（開いたときのタイトルでは押しを受けない）
                    .then(if (!interactive) Modifier else Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            running[i]?.cancel()
                            running[i] = scope.launch {
                                if (isLast) {
                                    // カメラの穴のあたりに、ブロックのまん中が来るところから落とす
                                    val camera = cameraPoint(view, offscreenPx = OFFSCREEN_DP * density.density)
                                    val fromDx = (camera.x - lastCenter[0]) / density.density
                                    val fromDy = (camera.y - lastCenter[1]) / density.density - liftDp
                                    motion.dropIn(fromDx, fromDy) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    motion.hop(sign = if (block.tiltDeg < 0f) -1f else 1f)
                                }
                            }
                        }
                    })
                    .graphicsLayer {
                        rotationZ = block.tiltDeg + motion.turn.value
                        translationX = motion.dx.value.dp.toPx()
                        translationY = (liftDp + motion.dy.value).dp.toPx()
                    }
                    .shadow(if (lifted) 6.dp else 2.dp, shape, clip = false)
                    .background(block.color, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    block.char.toString(),
                    fontFamily = TitleRounded,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { (21f * K).dp.toSp() },
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
    launch {
        turn.animateTo(8f * sign, tween(HOP_UP_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
        turn.animateTo(-3f * sign, tween(BOUNCE_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }
    dy.animateTo(-12f * K, tween(HOP_UP_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
    dy.animateTo(-4f * K, tween(BOUNCE_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
}

/**
 * 落ち始めの点：画面の上のカメラの穴のまん中（画面の左上から [px]）。ブロックは穴とほぼ同じ大きさなので、穴から出てくるように見える。
 * 穴のない端末では、画面のまん中の、上の端より [offscreenPx] だけ上（ブロックが画面の外に隠れている所）
 */
private fun cameraPoint(view: View, offscreenPx: Float): Offset {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val hole = view.rootWindowInsets?.displayCutout?.boundingRects
            ?.filter { it.top <= 0 && !it.isEmpty }
            ?.minByOrNull { kotlin.math.abs(it.exactCenterX() - view.rootView.width / 2f) }
        if (hole != null) return Offset(hole.exactCenterX(), hole.exactCenterY())
    }
    return Offset(view.rootView.width / 2f, -offscreenPx)
}

/**
 * カメラの穴のあたり（ふだんの位置から [fromDx], [fromDy] [dp] ずれた所）から回りながら斜めに落ちてきて、
 * 止まる位置の少し右に着き、左へ弧を描いて 1 回はねて収まる。はね方は起動画面（splash_icon_animated）の「ミ」と同じで、
 * 着く位置のずれ・はねる高さは、起動画面の絵と題字の大きさの比で小さくする（32dp のブロックのとき 1.55 倍として決め、
 * 題字の大きさ [TITLE_BLOCK_DP] に合わせて同じ割合で大きくする）。[onLand] は着いたとき（手ごたえの振動）
 */
private suspend fun BlockMotion.dropIn(fromDx: Float, fromDy: Float, onLand: () -> Unit) = coroutineScope {
    dx.snapTo(fromDx)
    dy.snapTo(fromDy)
    turn.snapTo(60f)
    val fallMs = 160
    launch {
        dx.animateTo(13f * K, tween(fallMs, easing = LinearEasing))
        dx.animateTo(0f, tween(BOUNCE_MS * 2, easing = LinearEasing))
    }
    launch {
        turn.animateTo(10f, tween(fallMs, easing = LinearEasing))
        turn.animateTo(-6f, tween(BOUNCE_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }
    dy.animateTo(0f, tween(fallMs, easing = EaseInQuad))
    onLand()
    dy.animateTo(-8f * K, tween(BOUNCE_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
}

/**
 * 穴のない端末で、落ち始めにブロックのまん中を画面の上の端からどれだけ上に置くか [dp]。
 * ブロックを 60° 傾けても、角が画面に出ない長さ（対角線の半分 ≈ 0.71 倍より少し長く）
 */
private const val OFFSCREEN_DP = TITLE_BLOCK_DP * 0.75f

/** 跳ねるときの、上がる（下がる）時間 [ms] */
private const val HOP_UP_MS = 120

/** 1 回はねるときの、上がる（下がる）時間 [ms]。起動画面の「ミ」と同じ */
private const val BOUNCE_MS = 55
