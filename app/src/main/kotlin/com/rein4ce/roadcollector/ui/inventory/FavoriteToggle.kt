package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * カードの右下のお気に入り。入っていないときは灰色の ☆、押すと黄色の ★（もう一度押すと外れる）。
 * コレクションのカードと、積む画面の選ぶ窓のカードで使う。押せる範囲の大きさは [modifier] で、
 * その中のどこに星を置くかは [alignment] で渡す。
 * 読み上げではチェックボックスとして伝える。
 * 押したときは、押せる範囲の四角（灰色の波紋）ではなく、星だけが少し縮んで応える。
 * 入れたときだけ、星がぽんと弾み、周りに小さな光が散る（外すときは静かに ☆ に戻す）。
 * 端末の設定でアニメーションを切っている人には、どちらも動かさない（Compose が設定に従う）
 */
@Composable
internal fun FavoriteToggle(
    favorite: Boolean,
    name: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 20.dp,
    alignment: Alignment = Alignment.Center,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.8f else 1f, label = "favoriteStarPress")
    // 押した瞬間に、ほかのボタン（ui/common/PressFeel）と同じ軽い振動を返す（#75 のレビュー）
    val haptics = rememberHaptics()
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Press) haptics.performHapticFeedback(HapticFeedbackType.VirtualKey) }
    }
    val pop = remember { Animatable(1f) }
    // 光の進み具合（0 → 1）。1 は何も描かない
    val burst = remember { Animatable(1f) }
    // 前に見た値。はじめから入っているカードや、一覧に戻ってきたカードでは弾ませない
    val last = remember { booleanArrayOf(favorite) }
    LaunchedEffect(favorite) {
        val turnedOn = favorite && !last[0]
        last[0] = favorite
        if (!turnedOn) {
            // 入れた直後にすぐ外すと、弾みと光が途中で止まったまま残るので、元に戻す（#75 のレビュー）
            pop.snapTo(1f)
            burst.snapTo(1f)
            return@LaunchedEffect
        }
        launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        }
        pop.animateTo(1.45f, tween(durationMillis = 110, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
    }
    Box(
        contentAlignment = alignment,
        modifier = modifier
            // 光の広がりも、星の大きさに合わせる
            .drawBehind { drawFavoriteBurst(center, burst.value, scale = starSize / 20.dp) }
            .toggleable(
                value = favorite,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics {
                contentDescription = "「$name」をお気に入りに入れる"
                stateDescription = if (favorite) "お気に入り" else "お気に入りではない"
            },
    ) {
        // 星の大きさは文字の大きさの設定で変えない（押せる範囲は決まっているので、大きくするとはみ出す）
        val fontSize = with(LocalDensity.current) { starSize.toSp() }
        Text(
            if (favorite) "★" else "☆",
            color = if (favorite) FavoriteStar else Fieldbook.Muted,
            fontSize = fontSize,
            lineHeight = fontSize,
            modifier = Modifier.graphicsLayer {
                scaleX = pressScale * pop.value
                scaleY = pressScale * pop.value
            },
        )
    }
}

/**
 * お気に入りに入れたときの、星の周りに散る小さな光。
 * [t] が 0 → 1 で、輪が広がって消え、粒が外へ飛びながら小さくなって消える（1 は何も描かない）。
 * [scale] は星の大きさに合わせた倍率（コレクションのカードの 20dp の星で 1）
 */
internal fun DrawScope.drawFavoriteBurst(center: Offset, t: Float, scale: Float = 1f) {
    if (t >= 1f) return
    val fade = 1f - t
    // 広がる輪（はじめの半分だけ）
    if (t < 0.5f) {
        val k = t / 0.5f
        drawCircle(
            FavoriteStar.copy(alpha = 0.55f * (1f - k)),
            radius = (5.dp + 9.dp * k).toPx() * scale,
            center = center,
            style = Stroke(width = (2.dp * (1f - k)).toPx().coerceAtLeast(0.5f) * scale),
        )
    }
    // 6 つの粒：濃い黄色と淡い黄色を交互に。淡いほうは少し内側
    val inner = 7.dp.toPx() * scale
    val outer = 17.dp.toPx() * scale
    for (i in 0 until 6) {
        val a = (i * 60f - 90f) * PI.toFloat() / 180f
        val strong = i % 2 == 0
        val reach = if (strong) outer else outer - 3.dp.toPx() * scale
        val d = inner + (reach - inner) * t
        val r = (if (strong) 2.4.dp else 1.8.dp).toPx() * scale * (1f - 0.6f * t)
        drawCircle(
            (if (strong) FavoriteStar else SparkleLight).copy(alpha = fade),
            radius = r,
            center = Offset(center.x + cos(a) * d, center.y + sin(a) * d),
        )
    }
}

/** 散る光の淡い黄色 */
private val SparkleLight = Color(0xFFFFE27A)
