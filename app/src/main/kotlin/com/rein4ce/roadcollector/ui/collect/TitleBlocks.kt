package com.rein4ce.roadcollector.ui.collect

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
import com.rein4ce.roadcollector.audio.BlockNotes
import com.rein4ce.roadcollector.audio.Sfx
import com.rein4ce.roadcollector.ui.common.BlockMotion
import com.rein4ce.roadcollector.ui.common.BlockMotionSpec
import com.rein4ce.roadcollector.ui.common.cameraPoint
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import com.rein4ce.roadcollector.ui.common.rememberSfx
import kotlinx.coroutines.Job
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

/**
 * 題字の動きの大きさ（ui/common/BlockMotion.kt。画面名の積み木と同じ動きで、大きさだけ違う）。
 * 32dp のブロックで決めた値を、[TITLE_BLOCK_DP] に合わせて同じ割合で大きくする。
 * 落ちてきて着く位置のずれ・はねる高さは、起動画面（splash_icon_animated）の「ミ」のはね方を、起動画面の絵と題字の大きさの比で小さくしたもの
 */
private val TitleMotion = BlockMotionSpec(hopDp = 12f * K, hopBounceDp = 4f * K, fallMs = 160, landDxDp = 13f * K, landBounceDp = 8f * K)

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
    val sfx = rememberSfx()
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
                                    // 押したときは、ほかの積み木と同じく自分の高さの「ぴょん」。落ちてきて着いたら、軽い「ことっ」
                                    sfx.play(Sfx.HOP, BlockNotes.rate(i, Blocks.size))
                                    // カメラの穴のあたりに、ブロックのまん中が来るところから落とす
                                    val camera = cameraPoint(view, blockPx = TITLE_BLOCK_DP * density.density)
                                    val fromDx = (camera.x - lastCenter[0]) / density.density
                                    val fromDy = (camera.y - lastCenter[1]) / density.density - liftDp
                                    motion.dropIn(fromDx, fromDy, TitleMotion) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        sfx.play(Sfx.LAND)
                                    }
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    // 「ぴょん」の高さは積み木ごとに変える（左から ソ・ラ・シ・ド と上がり、最後の「ミ」が上のド。audio/SoundEffects.kt の BlockNotes）
                                    sfx.play(Sfx.HOP, BlockNotes.rate(i, Blocks.size))
                                    motion.hop(sign = if (block.tiltDeg < 0f) -1f else 1f, spec = TitleMotion)
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
