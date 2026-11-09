package com.rein4ce.roadcollector.ui.common

import android.os.Build
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 押すと動く積み木の字（題字「ミチツミ」の ui/collect/TitleBlocks.kt と、画面名の [BlockWord]）の、ブロック 1 つの動き。
 * ふだんの位置からのずれ（横・縦 [dp]、回転 [度]）を持ち、押したときだけ動く。
 * 動くのは見た目だけ（graphicsLayer で読む）で、まわりの配置は変わらない。
 * 端末で「動きを減らす」（アニメーションの速度 0）にしていれば、動きは飛ばされてすぐ元の位置に戻る
 */
internal class BlockMotion {
    val dx = Animatable(0f)
    val dy = Animatable(0f)
    val turn = Animatable(0f)

    /**
     * その場で軽く跳ねる：傾きを少し増しながら上がって落ち、1 回小さくはねて戻る。
     * はね方は起動画面の「ミ」と同じ形（上がりは減速、下がりは加速）。[sign] は傾ける向き（ふだんの傾きと同じ向きに増す）
     */
    suspend fun hop(sign: Float, spec: BlockMotionSpec) = coroutineScope {
        launch {
            turn.animateTo(8f * sign, tween(HOP_UP_MS, easing = EaseOutQuad))
            turn.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
            turn.animateTo(-3f * sign, tween(BOUNCE_MS, easing = EaseOutQuad))
            turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
        }
        dy.animateTo(-spec.hopDp, tween(HOP_UP_MS, easing = EaseOutQuad))
        dy.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
        dy.animateTo(-spec.hopBounceDp, tween(BOUNCE_MS, easing = EaseOutQuad))
        dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }

    /**
     * カメラの穴のあたり（ふだんの位置から [fromDx], [fromDy] [dp] ずれた所。[cameraPoint] から求める）から回りながら斜めに落ちてきて、
     * 止まる位置の少し右に着き、左へ弧を描いて 1 回はねて収まる。はね方は起動画面（splash_icon_animated）の「ミ」と同じ形。
     * [onLand] は着いたとき（手ごたえの振動）
     */
    suspend fun dropIn(fromDx: Float, fromDy: Float, spec: BlockMotionSpec, onLand: () -> Unit) = coroutineScope {
        dx.snapTo(fromDx)
        dy.snapTo(fromDy)
        turn.snapTo(60f)
        launch {
            dx.animateTo(spec.landDxDp, tween(spec.fallMs, easing = LinearEasing))
            dx.animateTo(0f, tween(BOUNCE_MS * 2, easing = LinearEasing))
        }
        launch {
            turn.animateTo(10f, tween(spec.fallMs, easing = LinearEasing))
            turn.animateTo(-6f, tween(BOUNCE_MS, easing = EaseOutQuad))
            turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
        }
        dy.animateTo(0f, tween(spec.fallMs, easing = EaseInQuad))
        onLand()
        dy.animateTo(-spec.landBounceDp, tween(BOUNCE_MS, easing = EaseOutQuad))
        dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }
}

/**
 * 積み木の動きの大きさ。題字と画面名では、ブロックの大きさと置いてある高さが違うので、ここだけ変える
 * （回り方・時間の形は同じ）
 *
 * @param hopDp 跳ねるときに上がる高さ [dp]
 * @param hopBounceDp 跳ねたあと、1 回はねる高さ [dp]
 * @param fallMs 落ちてくる時間 [ms]（画面の下にあるほど落ちる距離が長いので、少し長くする）
 * @param landDxDp 着く位置の、止まる位置からの右へのずれ [dp]
 * @param landBounceDp 着いたあと、1 回はねる高さ [dp]
 */
internal data class BlockMotionSpec(
    val hopDp: Float,
    val hopBounceDp: Float,
    val fallMs: Int,
    val landDxDp: Float,
    val landBounceDp: Float,
)

/**
 * 落ち始めの点：画面の上のカメラの穴のまん中（画面の左上から [px]）。ブロックは穴とほぼ同じ大きさなので、穴から出てくるように見える。
 * 穴のない端末では、画面のまん中の、上の端よりブロックの大きさ [blockPx] の [BLOCK_OFFSCREEN_RATIO] 倍だけ上（ブロックが画面の外に隠れている所）
 */
internal fun cameraPoint(view: View, blockPx: Float): Offset {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val hole = view.rootWindowInsets?.displayCutout?.boundingRects
            ?.filter { it.top <= 0 && !it.isEmpty }
            ?.minByOrNull { abs(it.exactCenterX() - view.rootView.width / 2f) }
        if (hole != null) return Offset(hole.exactCenterX(), hole.exactCenterY())
    }
    return Offset(view.rootView.width / 2f, -blockPx * BLOCK_OFFSCREEN_RATIO)
}

/** 穴のない端末で、落ち始めにブロックのまん中を画面の上の端から上に置く長さ（ブロックの大きさの倍）。60° 傾けても角が画面に出ない長さ（対角線の半分 ≈ 0.71 倍より少し長く） */
private const val BLOCK_OFFSCREEN_RATIO = 0.75f

/** 跳ねるときの、上がる（下がる）時間 [ms] */
private const val HOP_UP_MS = 120

/** 1 回はねるときの、上がる（下がる）時間 [ms]。起動画面の「ミ」と同じ */
private const val BOUNCE_MS = 55
