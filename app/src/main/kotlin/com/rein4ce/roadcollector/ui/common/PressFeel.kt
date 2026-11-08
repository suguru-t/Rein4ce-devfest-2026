package com.rein4ce.roadcollector.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * ボタンの押し心地（アプリの全ボタン共通）。
 * 押している間は少し縮み、離すとバネで弾んで戻る。押した瞬間に指へ軽く振動を返す。
 * 数値は Figma「ミチツミ ボタンの押し心地」で決めたもの（決定 38）。
 * そのあと下のタブ（ui/TabBar.kt）と同じ手ざわりにそろえた：押すと横より縦が少し多く縮み（へこんだ感じ）、
 * 押したまま指を動かすと指の方へ少し寄り、離すと少し行き過ぎてから、ぷるんと戻る
 * 使い方は Material の Button / OutlinedButton / TextButton / IconButton と同じで、名前の頭に Press を付けるだけ。
 * 無効のボタンは Material が押した知らせを出さないので、縮みも振動もしない。
 */

/** 押している間に縮む割合（横と縦。縦を少し多くして、へこんだ感じにする） */
private const val SquashX = 0.05f
private const val SquashY = 0.08f

/** 押したまま指を動かしたときに、指の方へ寄る割合（ボタンのまん中からの指のずれに掛ける）と、その上限 */
private const val PullRatio = 0.08f
private val MaxPull = 4.dp

/** 塗りのボタンを押している間、塗りをどれだけ黒に寄せるか（調査の青 #1F5FA8 → #1A518F） */
private const val PressedDarken = 0.15f

/** 押したときに縮む速さ。すぐに縮んで、押したことが指に伝わるようにする */
private val PressIn = tween<Float>(durationMillis = 80, easing = FastOutSlowInEasing)

/**
 * 離したときのバネ。はじめは Figma の mass 1 / stiffness 800 / damping 28（減衰比 0.5）だったが、
 * 下のタブと同じく、少し行き過ぎてからぷるんと戻るように、減衰を弱くした
 */
private val ReleaseSpring = spring<Float>(dampingRatio = 0.36f, stiffness = 600f)

/**
 * 押している間の動き。[press] は 0（ふだん）→ 1（押しきった）で、離すとバネで 0 に戻る（少し行き過ぎて、マイナスにもなる）。
 * [finger] は押している指の位置（部品の中の座標。離したあとも最後の位置を残し、[press] と一緒に戻す）
 */
private class PressMotion {
    val press = Animatable(0f)
    var finger by mutableStateOf<Offset?>(null)
}

/**
 * 押している間の動きを作る。押したら縮み、離したらバネで戻る。
 * 一瞬だけのタップでも動きが見えるように、縮み切ってから戻す
 */
@Composable
private fun rememberPressMotion(interactionSource: InteractionSource): PressMotion {
    val motion = remember { PressMotion() }
    val haptics = rememberHaptics()
    LaunchedEffect(interactionSource) {
        var pressing: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    pressing = launch { motion.press.animateTo(1f, PressIn) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val pressed = pressing
                    launch {
                        pressed?.join()
                        motion.press.animateTo(0f, ReleaseSpring)
                        // 戻りきったら指の位置を消す（キーボードや読み上げで押したときに、前に触った位置の方へ寄らないように。#79 のレビュー）
                        motion.finger = null
                    }
                }
            }
        }
    }
    return motion
}

/**
 * 見た目だけ動かす（まわりの配置は変わらない）。押すと縮み、押したまま指を動かすと指の方へ少し寄る。
 * 指の位置は、ボタン自身のクリックの判定より先に見るだけで、横取りはしない
 */
private fun Modifier.pressMotion(motion: PressMotion): Modifier = this
    .pointerInput(motion) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            motion.finger = down.position
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                motion.finger = change.position
            }
        }
    }
    .graphicsLayer {
        val p = motion.press.value
        scaleX = 1f - SquashX * p
        scaleY = 1f - SquashY * p
        val finger = motion.finger
        if (finger != null) {
            val limit = MaxPull.toPx()
            val toward = p.coerceIn(0f, 1f)
            translationX = ((finger.x - size.width / 2) * PullRatio).coerceIn(-limit, limit) * toward
            translationY = ((finger.y - size.height / 2) * PullRatio).coerceIn(-limit, limit) * toward
        }
    }

/**
 * Material のボタンでない押せる部品（窓の下の丸いボタン・選ぶ丸・並び替えの札・下のタブなど）に、
 * ボタンと同じ押し心地（縮んで弾んで戻る・押した瞬間の振動）を付ける。
 * [interactionSource] は、同じ部品の clickable / toggleable / selectable にも渡すこと（押したことをそこから知るため）。
 * 縮むのはこの Modifier より後ろ（内側）に書いた見た目なので、背景や枠より前に付ける
 */
@Composable
fun Modifier.pressFeel(interactionSource: InteractionSource): Modifier =
    pressMotion(rememberPressMotion(interactionSource))

/** 押し心地つきの [Button]。押している間は塗りも少し濃くなる */
@Composable
fun PressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val motion = rememberPressMotion(source)
    val pressed by source.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(colors.containerColor, Color.Black, PressedDarken) else colors.containerColor,
        animationSpec = tween(durationMillis = 120),
        label = "pressedContainer",
    )
    Button(
        onClick = onClick,
        modifier = modifier.pressMotion(motion),
        enabled = enabled,
        shape = shape,
        colors = ButtonColors(
            containerColor = container,
            contentColor = colors.contentColor,
            disabledContainerColor = colors.disabledContainerColor,
            disabledContentColor = colors.disabledContentColor,
        ),
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

/** 押し心地つきの [OutlinedButton]。押している間の色は Material の波紋のまま（うっすら色が付く） */
@Composable
fun PressOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val motion = rememberPressMotion(source)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressMotion(motion),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

/** 押し心地つきの [TextButton]。押している間の色は Material の波紋のまま（うっすら色が付く） */
@Composable
fun PressTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val motion = rememberPressMotion(source)
    TextButton(
        onClick = onClick,
        modifier = modifier.pressMotion(motion),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

/** 押し心地つきの [IconButton] */
@Composable
fun PressIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val motion = rememberPressMotion(source)
    IconButton(
        onClick = onClick,
        modifier = modifier.pressMotion(motion),
        enabled = enabled,
        colors = colors,
        interactionSource = source,
        content = content,
    )
}
