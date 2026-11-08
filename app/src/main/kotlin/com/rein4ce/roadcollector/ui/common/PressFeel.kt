package com.rein4ce.roadcollector.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * ボタンの押し心地（アプリの全ボタン共通）。
 * 押している間は少し縮み、離すとバネで弾んで戻る。押した瞬間に指へ軽く振動を返す。
 * 数値は Figma「ミチツミ ボタンの押し心地」で決めたもの（決定 38）。
 * 使い方は Material の Button / OutlinedButton / TextButton / IconButton と同じで、名前の頭に Press を付けるだけ。
 * 無効のボタンは Material が押した知らせを出さないので、縮みも振動もしない。
 */

/** 押している間の大きさ（元の何倍か） */
private const val PressedScale = 0.94f

/** 塗りのボタンを押している間、塗りをどれだけ黒に寄せるか（調査の青 #1F5FA8 → #1A518F） */
private const val PressedDarken = 0.15f

/** 押したときに縮む速さ。すぐに縮んで、押したことが指に伝わるようにする */
private val PressIn = tween<Float>(durationMillis = 80, easing = FastOutSlowInEasing)

/** 離したときのバネ。Figma の mass 1 / stiffness 800 / damping 28 と同じ */
private val ReleaseSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 800f)

/**
 * 押している間の大きさ。押したら縮み、離したらバネで戻る。
 * 一瞬だけのタップでも動きが見えるように、縮み切ってから戻す
 */
@Composable
private fun rememberPressScale(interactionSource: InteractionSource): Animatable<Float, AnimationVector1D> {
    val scale = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(interactionSource) {
        var shrinking: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    shrinking = launch { scale.animateTo(PressedScale, PressIn) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val shrink = shrinking
                    launch {
                        shrink?.join()
                        scale.animateTo(1f, ReleaseSpring)
                    }
                }
            }
        }
    }
    return scale
}

/** 見た目だけ縮める（まわりの配置は変わらない） */
private fun Modifier.pressScale(scale: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

/**
 * Material のボタンでない押せる部品（窓の下の丸いボタン・選ぶ丸・並び替えの札・下のタブなど）に、
 * ボタンと同じ押し心地（縮んで弾んで戻る・押した瞬間の振動）を付ける。
 * [interactionSource] は、同じ部品の clickable / toggleable / selectable にも渡すこと（押したことをそこから知るため）。
 * 縮むのはこの Modifier より後ろ（内側）に書いた見た目なので、背景や枠より前に付ける
 */
@Composable
fun Modifier.pressFeel(interactionSource: InteractionSource): Modifier =
    pressScale(rememberPressScale(interactionSource))

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
    val scale = rememberPressScale(source)
    val pressed by source.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = if (pressed) lerp(colors.containerColor, Color.Black, PressedDarken) else colors.containerColor,
        animationSpec = tween(durationMillis = 120),
        label = "pressedContainer",
    )
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(scale),
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
    val scale = rememberPressScale(source)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressScale(scale),
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
    val scale = rememberPressScale(source)
    TextButton(
        onClick = onClick,
        modifier = modifier.pressScale(scale),
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
    val scale = rememberPressScale(source)
    IconButton(
        onClick = onClick,
        modifier = modifier.pressScale(scale),
        enabled = enabled,
        colors = colors,
        interactionSource = source,
        content = content,
    )
}
