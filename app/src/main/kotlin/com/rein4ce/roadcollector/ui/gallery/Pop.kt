package com.rein4ce.roadcollector.ui.gallery

import android.os.Build
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 「みんな」のポップな見た目。題字「ミチツミ」（ui/collect/TitleBlocks.kt）の積み木に合わせて、
 * 太い丸ゴシック、アイコンの 4 色、下に濃い色がのぞく「ブロック」の面でそろえる。
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

    /** 白い字を載せる面（紙の上で 4.5:1 を超えるよう、[Orange]・[Teal] を濃くしたもの） */
    val OrangeDeep = Color(0xFFBF5418)
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
     * 太い丸ゴシック（M PLUS Rounded 1c Black を「みんな」で使う字だけに切り出したもの。出どころとライセンスは app/fonts/）。
     * 作者名のような、人が入れる字は入っていないので使わない（入っていない字は端末の字体で出る）
     */
    val Rounded = FontFamily(Font(R.font.mplus_rounded_gallery, FontWeight.Black))
}

/** 物差しの色の面（白い字を載せる）：高い順はオレンジ、多い順は青緑、新しい順は紺 */
internal fun sortFace(sort: TowerSort): Color = when (sort) {
    TowerSort.HEIGHT -> Pop.OrangeDeep
    TowerSort.PARTS -> Pop.TealDeep
    TowerSort.NEWEST -> Pop.Navy
}

/** 物差しの色の字（紙の上に直接書く）。ダークモードでは濃い色が紙に沈むので、明るい色にする */
internal fun sortInk(sort: TowerSort): Color = if (!Fieldbook.dark) sortFace(sort) else when (sort) {
    TowerSort.HEIGHT -> Pop.Orange
    TowerSort.PARTS -> Pop.Teal
    TowerSort.NEWEST -> Color(0xFFB9BCD0)
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
 * @param face   面の色
 * @param text   字の色
 * @param edge   縁取り（白い面に色の縁の、二番手のボタン）
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
) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .popBlock(face = face, corner = corner, edge = edge)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = fontSize, color = text, maxLines = 2)
    }
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
 * リロードの印：青緑のブロックに白い矢印。少し傾けて、題字の積み木と並べたときに浮かないように。
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
                .graphicsLayer { rotationZ = 5f }
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
 * 動きの形は題字（ui/collect/TitleBlocks.kt の hop・dropIn）と同じで、ブロックが小さいぶん跳ねる高さを少し低くしている。
 * 題字のほうは B の担当で中の関数が private なので、ここに同じ形を書いている（動きを変えるときは両方そろえる）。
 * 動くのは見た目だけ（graphicsLayer）で、まわりの配置は変わらない。飾りなので読み上げではボタンにせず、字そのものを読む。
 * 押したときの振動は設定（振動のオン・オフ）に従う。端末で動きを減らしていれば、動きは飛ばされてすぐ元の位置に戻る
 *
 * @param blockDp ブロック 1 つの大きさ（題字は 32dp）
 */
@Composable
internal fun BlockWord(text: String, modifier: Modifier = Modifier, blockDp: Dp = 26.dp) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(blockDp * 0.22f)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val view = LocalView.current
    val motions = remember(text) { List(text.length) { BlockMotion() } }
    val running = remember(text) { arrayOfNulls<Job>(text.length) }
    // 最後のブロックをカメラの穴のあたりから落とすための、ブロックのまん中（画面の左上から [px]。傾け・動きの前）
    val lastCenter = remember { FloatArray(2) }
    Row(
        // 落ちてくるブロックが、まわりの部品の下にもぐらないように手前に描く
        modifier = modifier.zIndex(1f).semantics(mergeDescendants = true) { contentDescription = text },
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
                                    val camera = cameraPoint(view, offscreenPx = (blockDp * 0.75f).toPx())
                                    val fromDx = (camera.x - lastCenter[0]) / density.density
                                    val fromDy = (camera.y - lastCenter[1]) / density.density
                                    motion.dropIn(fromDx, fromDy) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    motion.hop(sign = if (tilt < 0f) -1f else 1f)
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

/** ブロック 1 つの、ふだんの位置からのずれ（横・縦 [dp]、回転 [度]）。押したときだけ動く */
private class BlockMotion {
    val dx = Animatable(0f)
    val dy = Animatable(0f)
    val turn = Animatable(0f)
}

/**
 * その場で軽く跳ねる：傾きを少し増しながら上がって落ち、1 回小さくはねて戻る（題字の hop と同じ形。高さは 26/32）。
 * [sign] は傾ける向き（ふだんの傾きと同じ向きに増す）
 */
private suspend fun BlockMotion.hop(sign: Float) = coroutineScope {
    launch {
        turn.animateTo(8f * sign, tween(HOP_UP_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
        turn.animateTo(-3f * sign, tween(BOUNCE_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }
    dy.animateTo(-10f, tween(HOP_UP_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(HOP_UP_MS, easing = EaseInQuad))
    dy.animateTo(-3f, tween(BOUNCE_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
}

/**
 * カメラの穴のあたり（ふだんの位置から [fromDx], [fromDy] [dp] ずれた所）から回りながら斜めに落ちてきて、
 * 止まる位置の少し右に着き、左へ弧を描いて 1 回はねて収まる（題字の dropIn と同じ形）。[onLand] は着いたとき（手ごたえの振動）。
 * 画面名は題字より下にあって落ちる距離が長いので、落ちる時間を少しだけ長くする
 */
private suspend fun BlockMotion.dropIn(fromDx: Float, fromDy: Float, onLand: () -> Unit) = coroutineScope {
    dx.snapTo(fromDx)
    dy.snapTo(fromDy)
    turn.snapTo(60f)
    val fallMs = 220
    launch {
        dx.animateTo(10f, tween(fallMs, easing = LinearEasing))
        dx.animateTo(0f, tween(BOUNCE_MS * 2, easing = LinearEasing))
    }
    launch {
        turn.animateTo(10f, tween(fallMs, easing = LinearEasing))
        turn.animateTo(-6f, tween(BOUNCE_MS, easing = EaseOutQuad))
        turn.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
    }
    dy.animateTo(0f, tween(fallMs, easing = EaseInQuad))
    onLand()
    dy.animateTo(-6f, tween(BOUNCE_MS, easing = EaseOutQuad))
    dy.animateTo(0f, tween(BOUNCE_MS, easing = EaseInQuad))
}

/**
 * 落ち始めの点：画面の上のカメラの穴のまん中（画面の左上から [px]）。
 * 穴のない端末では、画面のまん中の、上の端より [offscreenPx] だけ上（ブロックが画面の外に隠れている所）
 */
private fun cameraPoint(view: View, offscreenPx: Float): Offset {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val hole = view.rootWindowInsets?.displayCutout?.boundingRects
            ?.filter { it.top <= 0 && !it.isEmpty }
            ?.minByOrNull { abs(it.exactCenterX() - view.rootView.width / 2f) }
        if (hole != null) return Offset(hole.exactCenterX(), hole.exactCenterY())
    }
    return Offset(view.rootView.width / 2f, -offscreenPx)
}

/** 跳ねるときの、上がる（下がる）時間 [ms]。題字と同じ */
private const val HOP_UP_MS = 120

/** 1 回はねるときの、上がる（下がる）時間 [ms]。題字・起動画面の「ミ」と同じ */
private const val BOUNCE_MS = 55

/** ブロックの色：アイコンの 4 色を順に */
private val BlockColors = listOf(Pop.Orange, Pop.Teal, Pop.Yellow, Pop.Navy)

/** 傾き [度]。左右交互に、少しずつ違えて、きっちり並んで見えないように */
private val BlockTilts = listOf(-4f, 3f, -2f, 4f, -3f, 2f, -4f, 3f)
