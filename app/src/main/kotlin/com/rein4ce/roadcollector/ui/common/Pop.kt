package com.rein4ce.roadcollector.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.audio.BlockNotes
import com.rein4ce.roadcollector.audio.Sfx
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * ポップな見た目の部品（「みんな」とコレクションで共通。決定 44・45）。題字「ミチツミ」（ui/collect/TitleBlocks.kt）の積み木に合わせて、
 * 太い丸ゴシック、アイコンの 4 色、下に濃い色がのぞく「ブロック」の面でそろえる。
 * 「みんな」だけで使うもの（物差しの色・タワーの注記の行）は ui/gallery/GalleryPop.kt
 *
 * 字の色の決まり：白い字を載せる面は、紙の上で 4.5:1 を超える濃い色（[OrangeDeep]・[TealDeep]・[Navy]）。
 * 明るい色（[Yellow] など）の面には墨の字。アイコンや飾りだけの面は明るい色のまま（3:1 を超える）
 */
internal object Pop {
    /** アイコンの 4 色（題字のブロックと同じ） */
    val Orange = Color(0xFFE07A2A)
    val Teal = Color(0xFF2A9D8F)
    val Yellow = Color(0xFFF2C14E)
    val Navy = Color(0xFF4A4E69)

    /**
     * 白い字を載せる面（白い字 [Letter] と 4.5:1 を超えるよう、[Orange]・[Teal] を濃くしたもの）。
     * OrangeDeep は 4.70:1、TealDeep は 4.73:1（前の #BF5418 は 4.28:1 で届いていなかった。#89 のレビュー）
     */
    val OrangeDeep = Color(0xFFB54F16)
    val TealDeep = Color(0xFF1F7A6F)

    /** 順位の 2 位・3 位（銀と銅のブロック。墨の字） */
    val Silver = Color(0xFFC9CCDA)
    val Bronze = Color(0xFFF0A868)

    /** ブロックの上の白い字（題字と同じ。ダークモードでも白） */
    val Letter = Color(0xFFF3F6F0)

    /** 黄色・銀・銅の面の上の字（面の色はダークモードでも変わらないので、字も墨のまま） */
    val InkOnYellow = Color(0xFF2F3A2E)

    /** 縁取りだけの面の、縁と字の色。ダークモードでは濃い色が紙に沈むので、明るい色にする */
    val TealLine: Color get() = if (Fieldbook.dark) Teal else TealDeep

    /**
     * 紙の上に直接書く「あなた」の字の色。ライトは道の濃い縁の色、ダークは道のオレンジ
     * （道の縁の色はダークでも濃い茶色で、夜の紙の上では読めないため）
     */
    val MineInk: Color get() = if (Fieldbook.dark) Orange else Fieldbook.RoadCasing

    /** ブロックの下にのぞく側面の色（面の色を暗くしたもの） */
    fun side(face: Color): Color = lerp(face, Color.Black, 0.3f)

    /**
     * 太い丸ゴシック（M PLUS Rounded 1c Black を「みんな」とコレクションで使う字だけに切り出したもの。tools/subset_rounded_font.py で作る。
     * 出どころとライセンスは app/fonts/）。
     * 作者名のような、人が入れる字は入っていないので使わない（入っていない字は端末の字体で出る）
     */
    val Rounded = FontFamily(Font(R.font.mplus_rounded_ui, FontWeight.Black))
}

/**
 * ブロックの面：角の丸い面の下に、[depth] だけ濃い側面がのぞく（積み木を横から見たように）。
 * 側面のぶん下に余白を取るので、中身は面の上だけに載る
 *
 * @param edge 面の縁取り（null なら付けない）。縁取りだけの面（白い面に色の縁）は、側面もその色にする
 */
internal fun Modifier.popBlock(face: Color, corner: Dp = 12.dp, depth: Dp = 4.dp, edge: Color? = null): Modifier =
    this
        .padding(bottom = depth)
        .drawBehind {
            val r = CornerRadius(corner.toPx())
            drawRoundRect(Pop.side(edge ?: face), topLeft = Offset(0f, depth.toPx()), size = size, cornerRadius = r)
            drawRoundRect(face, size = size, cornerRadius = r)
            if (edge != null) {
                val w = 2.dp.toPx()
                drawRoundRect(
                    edge,
                    topLeft = Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(corner.toPx() - w / 2),
                    style = Stroke(w),
                )
            }
        }

/**
 * ブロックのボタン。押すとほかのボタンと同じ押し心地（ui/common/PressFeel）で縮む。押せる高さは 48dp 以上
 *
 * @param face    面の色（押せるとき）
 * @param text    字の色（押せるとき）
 * @param edge    縁取り（白い面に色の縁の、二番手のボタン）
 * @param enabled false なら押せない。面は灰色・字は薄い色・縁なしにする（押せないボタンの見た目を、使う所ごとに書かないように）
 * @param padding 面の内側の余白（狭い所に 2 つ並べるときに、字を 1 行に入れやすくするため左右を狭くする）
 * @param reserveLabels 押すと字が入れ替わるボタンで、入れ替わる字（[label] も含めてよい）。どの字でもボタンの大きさが
 *   変わらないよう、いちばん大きい字の分の場所を取っておく（見えない・読み上げない字を重ねる）
 */
@Composable
internal fun PopButton(
    label: String,
    onClick: () -> Unit,
    face: Color,
    text: Color,
    modifier: Modifier = Modifier,
    edge: Color? = null,
    fontSize: TextUnit = 16.sp,
    enabled: Boolean = true,
    corner: Dp = 14.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    reserveLabels: List<String> = emptyList(),
) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .popBlock(face = if (enabled) face else Fieldbook.GridMajor, corner = corner, edge = if (enabled) edge else null)
            .heightIn(min = 48.dp)
            .padding(padding),
    ) {
        // 入れ替わるほかの字の分の場所（見えない・読み上げない）。字が変わっても、ボタンと並ぶ段の高さが変わらないように
        for (other in reserveLabels) {
            if (other != label) {
                PopButtonLabel(other, fontSize, Color.Transparent, Modifier.alpha(0f).clearAndSetSemantics {})
            }
        }
        PopButtonLabel(label, fontSize, if (enabled) text else Fieldbook.Muted)
    }
}

/** [PopButton] の字 */
@Composable
private fun PopButtonLabel(label: String, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(
        label,
        fontFamily = Pop.Rounded,
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        color = color,
        maxLines = 2,
        // 文字を大きくして 2 行になるときは、言葉の切れ目（「もう一度」「歩く」）で折り返して、まん中にそろえる
        textAlign = TextAlign.Center,
        style = noteStyle,
        modifier = modifier,
    )
}

/**
 * 小さなブロックの札（押せる札にも、ただの札にもなる）。「‹ 戻る」「次の 5 個 ›」、見出しの「最長まで あと 0.77 m」など
 *
 * @param onClick null なら押せない札
 */
@Composable
internal fun PopChip(
    label: String,
    face: Color,
    text: Color,
    modifier: Modifier = Modifier,
    edge: Color? = null,
    fontSize: TextUnit = 13.sp,
    depth: Dp = 3.dp,
    tiltDeg: Float = 0f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(
                if (onClick == null) Modifier
                else Modifier
                    // 札の見た目は小さいが、押せる高さは 44dp（指で押しやすいように）
                    .heightIn(min = 44.dp)
                    .pressFeel(source)
                    .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            ),
    ) {
        Text(
            label,
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            color = text,
            maxLines = 1,
            modifier = Modifier
                .graphicsLayer { rotationZ = tiltDeg }
                .popBlock(face = face, corner = 50.dp, depth = depth, edge = edge)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * 色の面に白い字の小さな札（「最長」「最多」「あなた」）。少し傾けて、積み木の札に見せる。
 * 白い字なので、面は紙の上で 4.5:1 を超える濃い色（[Pop.OrangeDeep]・[Pop.TealDeep]・[Pop.Navy]）にする
 */
@Composable
internal fun PopTag(label: String, face: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp, tiltDeg: Float = -3f) {
    Text(
        label,
        fontFamily = Pop.Rounded,
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        color = Pop.Letter,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer { rotationZ = tiltDeg }
            .popBlock(face = face, corner = 6.dp, depth = 2.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** 断面図・紙片・説明の紙の角の丸さ（ブロックの面にそろえて大きめ） */
internal val SECTION_CORNER: Dp = 14.dp

/**
 * リロードの印：青緑のブロックに白い矢印。傾けずに水平に置く（見出しの札・並びの札とそろえる）。
 * 取り直している間は矢印が回る（端末で動きを減らしていれば回さない）
 */
@Composable
internal fun PopRefreshButton(refreshing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val reduceMotion = rememberReduceMotion()
    val spin = if (refreshing && !reduceMotion) {
        rememberInfiniteTransition(label = "refresh").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        ).value
    } else {
        0f
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, enabled = !refreshing, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "再読み込み" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 38.dp, height = 41.dp)
                .popBlock(face = if (refreshing) Pop.Teal.copy(alpha = 0.7f) else Pop.Teal, corner = 10.dp, depth = 3.dp),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = Pop.Letter,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = spin },
            )
        }
    }
}

/**
 * 画面名を、題字「ミチツミ」と同じ積み木のブロックで出す：1 文字ずつ色の付いたブロックに白抜きで載せ、少しずつ傾けて並べる。
 * 色はアイコンの 4 色を順に、傾きは左右交互。題字より一回り小さくして、題字（アプリの名前）と画面名の格を分ける。
 *
 * 押すと題字と同じように動く（遊び）：
 * - 最後以外のブロック：その場で軽く跳ね、1 回はねて元に戻る
 * - 最後のブロック：画面の上のカメラの穴のあたりから、回りながら斜めに落ちてきて、1 回はねて収まる
 *
 * 動きは題字と共通（ui/common/BlockMotion.kt）で、大きさだけ変えている（[WordMotion]）。
 * 動くのは見た目だけ（graphicsLayer）で、まわりの配置は変わらない。飾りなので読み上げではボタンにせず、字そのものを見出しとして読む。
 * 押したときの振動は設定（振動のオン・オフ）に従う。端末で動きを減らしていれば、動きは飛ばされてすぐ元の位置に戻る
 *
 * @param blockDp ブロック 1 つの大きさ（題字は 38dp）
 */
@Composable
internal fun BlockWord(text: String, modifier: Modifier = Modifier, blockDp: Dp = 26.dp) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(blockDp * 0.22f)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val sfx = rememberSfx()
    val view = LocalView.current
    val motions = remember(text) { List(text.length) { BlockMotion() } }
    val running = remember(text) { arrayOfNulls<Job>(text.length) }
    // 最後のブロックをカメラの穴のあたりから落とすための、ブロックのまん中（画面の左上から [px]。傾け・動きの前）
    val lastCenter = remember { FloatArray(2) }
    Row(
        // 落ちてくるブロックが、まわりの部品の下にもぐらないように手前に描く
        modifier = modifier.zIndex(1f).semantics(mergeDescendants = true) {
            contentDescription = text
            // 画面名なので、読み上げの「見出しで移動」で飛べるように
            heading()
        },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        text.forEachIndexed { i, ch ->
            val motion = motions[i]
            val isLast = i == text.lastIndex
            val tilt = BlockTilts[i % BlockTilts.size]
            Box(
                modifier = Modifier
                    // 傾けた角が上にはみ出す分の余裕
                    .padding(top = 3.dp)
                    .size(blockDp)
                    .onGloballyPositioned {
                        if (isLast) {
                            val p = it.positionInRoot()
                            lastCenter[0] = p.x + it.size.width / 2f
                            lastCenter[1] = p.y + it.size.height / 2f
                        }
                    }
                    // 押せる場所はふだんの位置のまま。動いている途中に押すと、初めから動き直す
                    .pointerInput(i, text) {
                        detectTapGestures {
                            running[i]?.cancel()
                            running[i] = scope.launch {
                                if (isLast) {
                                    // 押したときは、ほかの積み木と同じく自分の高さの「ぴょん」。落ちてきて着いたら、軽い「ことっ」
                                    sfx.play(Sfx.HOP, BlockNotes.rate(i, text.length))
                                    val camera = cameraPoint(view, blockPx = blockDp.toPx())
                                    val fromDx = (camera.x - lastCenter[0]) / density.density
                                    val fromDy = (camera.y - lastCenter[1]) / density.density
                                    motion.dropIn(fromDx, fromDy, WordMotion) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        sfx.play(Sfx.LAND)
                                    }
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    // 題字と同じく、「ぴょん」の高さを積み木ごとに変える（音階を上がって、最後の積み木が上のド）
                                    sfx.play(Sfx.HOP, BlockNotes.rate(i, text.length))
                                    motion.hop(sign = if (tilt < 0f) -1f else 1f, spec = WordMotion)
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        rotationZ = tilt + motion.turn.value
                        translationX = motion.dx.value.dp.toPx()
                        translationY = motion.dy.value.dp.toPx()
                    }
                    .shadow(2.dp, shape, clip = false)
                    .background(BlockColors[i % BlockColors.size], shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ch.toString(),
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    // ブロックの大きさは決まっているので、文字の大きさの設定では大きくしない（はみ出すため）
                    fontSize = with(density) { (blockDp * 0.66f).toSp() },
                    color = Pop.Letter,
                )
            }
        }
    }
}

/**
 * 画面名の積み木の動きの大きさ（ui/common/BlockMotion.kt。題字と同じ動きで、大きさだけ違う）。
 * ブロックが題字より小さいので跳ねる高さを少し低く（題字の 32dp のときの 26/32）、題字より下にあって落ちる距離が長いので、落ちる時間を少し長くする
 */
private val WordMotion = BlockMotionSpec(hopDp = 10f, hopBounceDp = 3f, fallMs = 220, landDxDp = 10f, landBounceDp = 6f)

/** ブロックの色：アイコンの 4 色を順に */
private val BlockColors = listOf(Pop.Orange, Pop.Teal, Pop.Yellow, Pop.Navy)

/** 傾き [度]。左右交互に、少しずつ違えて、きっちり並んで見えないように */
private val BlockTilts = listOf(-4f, 3f, -2f, 4f, -3f, 2f, -4f, 3f)
