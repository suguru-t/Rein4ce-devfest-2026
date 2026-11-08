package com.rein4ce.roadcollector.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 下のタブバー。地は透明で、4 つのアイコンを 1 つの丸いピル（錠剤型）で囲んで浮かせる。
 * Instagram のタブのように文字なし・アイコンだけで、選択中は「塗り」（選ばれた瞬間にタブごとの動き。ui/TabIcons.kt）、
 * それ以外は「線画」。
 *
 * 選択中の後ろには、iPhone の Liquid Glass のように動く「しずく」を置く（見た目は野帳の紙に合わせて、光沢のないうすい墨色）：
 * - タブを切り替えると、進む側の端が先に、後ろの端が遅れて動くので、しずくが伸びてから縮んで着く（伸びた分だけ少し薄くなる）
 * - 押すと、しずくがふくらんで持ち上がり（影が濃くなる）、指でタブバーをなぞると指に付いてくる。離した所のタブに切り替わる。
 *   しずくの近くのアイコンは、レンズで拡大したように少し大きくなる
 * - タブバーそのものも、押すと少しへこみ、離すとぷるんと戻る。なぞっている間は、指の方へ少し引っぱられる
 * - 押したときと、なぞって次のタブに移ったときに、軽い振動を返す
 * - 指をタブバーの外へ出してから離すと、取り消し（切り替えない）。押している途中に取り消されたときも切り替えない
 *
 * 文字が無いぶん、読み上げ（TalkBack）にはタブ名と選択状態を渡し、ダブルタップで切り替えられるようにする
 */
@Composable
internal fun TabBar(current: NavDestination?, onGo: (Dest) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp),
    ) {
        // 指でなぞっている間は、しずくのまん中を指の位置に置く（null のときは選ばれているタブ）
        var fingerX by remember { mutableStateOf<Float?>(null) }
        // タブバーそのものの押された感じ：押すと少しへこみ（縮む）、離すとぷるんと戻る（少し行き過ぎてから）。
        // なぞっている間は、指の方へ少しだけ引っぱられる
        val press = remember { Animatable(0f) }
        LaunchedEffect(fingerX != null) {
            if (fingerX != null) press.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 900f))
            else press.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 480f))
        }
        val maxPull = with(LocalDensity.current) { BarPull.toPx() }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .graphicsLayer {
                    val p = press.value
                    scaleX = 1f - BarSquash * p
                    scaleY = 1f - BarSquash * 1.4f * p
                    val pull = ((fingerX ?: (size.width / 2)) - size.width / 2) * 0.03f
                    translationX = pull.coerceIn(-maxPull, maxPull) * p.coerceIn(0f, 1f)
                }
                .shadow(elevation = 6.dp, shape = PillShape, ambientColor = Fieldbook.Ink, spotColor = Fieldbook.Ink)
                // 紙色を少し透かして、下の方眼が見えるように
                .background(Fieldbook.Paper.copy(alpha = 0.88f), PillShape)
                .border(1.dp, Fieldbook.GridMajor, PillShape)
                .clip(PillShape),
        ) {
            val density = LocalDensity.current
            val haptics = rememberHaptics()
            val count = Dest.all.size
            val barWidthPx = with(density) { maxWidth.toPx() }
            val tabWidthPx = barWidthPx / count
            val lensWidthPx = with(density) { LensWidth.toPx() }
            fun centerOf(index: Int) = tabWidthPx * (index + 0.5f)
            fun indexAt(x: Float) = (x / tabWidthPx).toInt().coerceIn(0, count - 1)

            val selectedIndex = Dest.all.indexOfFirst { dest -> current?.hierarchy?.any { it.route == dest.route } == true }
            val selectedNow by rememberUpdatedState(selectedIndex)
            // しずくのまん中の行き先：指でなぞっている間は指の位置、それ以外は選ばれているタブ
            fun targetCenter(): Float? = fingerX?.coerceIn(lensWidthPx / 2, barWidthPx - lensWidthPx / 2)
                ?: if (selectedNow >= 0) centerOf(selectedNow) else null

            // しずくの左右の端。進む側の端は固いバネで先に、後ろの端はやわらかいバネで遅れて動く
            val start = if (selectedIndex >= 0) centerOf(selectedIndex) else 0f
            val left = remember { Animatable(start - lensWidthPx / 2) }
            val right = remember { Animatable(start + lensWidthPx / 2) }
            // アプリを開いて最初にタブが決まったときは、すべらせずにその場に置く
            val placed = remember { booleanArrayOf(selectedIndex >= 0) }
            // 行き先が変わるたびに、両端のバネを向け直す。効果は 1 つのまま、行き先だけを見る
            // （なぞっている間、指が動くたびに効果を作り直さないように。#79 のレビュー）
            LaunchedEffect(lensWidthPx, tabWidthPx) {
                snapshotFlow { targetCenter() to (fingerX != null) }.collectLatest { (center, following) ->
                    if (center == null) return@collectLatest
                    if (!placed[0]) {
                        placed[0] = true
                        left.snapTo(center - lensWidthPx / 2)
                        right.snapTo(center + lensWidthPx / 2)
                        return@collectLatest
                    }
                    val toLeft = center - lensWidthPx / 2
                    val toRight = center + lensWidthPx / 2
                    val movingRight = toLeft > left.value
                    coroutineScope {
                        launch { right.animateTo(toRight, if (movingRight) lead(following) else trail(following)) }
                        launch { left.animateTo(toLeft, if (movingRight) trail(following) else lead(following)) }
                    }
                }
            }
            // 押している間、しずくがふくらんで持ち上がる（離すとぷるんと戻る）
            val lift = remember { Animatable(0f) }
            LaunchedEffect(fingerX != null) {
                lift.animateTo(if (fingerX != null) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 420f))
            }
            if (selectedIndex >= 0 || fingerX != null) {
                GlassLens(left = left, right = right, lift = { lift.value })
            }

            // タブバーを作ったときに選ばれているタブは、アイコンを動かさない。そこから一度でも選び直したら動かす
            // （アプリ全体で 1 つの印にすると、画面の作り直しやすぐ開き直したときに、開いた瞬間に動いてしまう。#79 のレビュー）
            val firstPick = remember { intArrayOf(-1) }
            val moved = remember { booleanArrayOf(false) }
            if (selectedIndex >= 0) {
                if (firstPick[0] < 0) firstPick[0] = selectedIndex else if (selectedIndex != firstPick[0]) moved[0] = true
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    // 押す・なぞる・離すを、タブバー全体で受ける（1 つずつのタブでは、なぞって移る動きが作れないため）
                    .pointerInput(count, tabWidthPx) {
                        val margin = OutsideMargin.toPx()
                        // 指がタブバーの外（少しの余裕を足した範囲）にあるか
                        fun outside(p: Offset) =
                            p.x < -margin || p.x > size.width + margin || p.y < -margin || p.y > size.height + margin
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            var x = down.position.x
                            var hovered = indexAt(x)
                            fingerX = x
                            var go = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        // 離したとき：タブバーの中（余裕を含む）で離したときだけ切り替える。
                                        // 外へ出してから離したら取り消し。押している途中に取り消された（戻るジェスチャーや通知のパネルなど）ときは、
                                        // Compose が「離した」を消費済みにして知らせてくるので、それも取り消し（#79 のレビュー）
                                        go = !change.isConsumed && !outside(change.position)
                                        break
                                    }
                                    x = change.position.x
                                    if (outside(change.position)) {
                                        // 外へ出たら、しずくは選ばれているタブへ戻す（離すと取り消しになることが見て分かるように）
                                        fingerX = null
                                    } else {
                                        fingerX = x
                                        val now = indexAt(x)
                                        if (now != hovered) {
                                            hovered = now
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    }
                                    change.consume()
                                }
                            } finally {
                                // どう終わっても（鍵が変わって作り直されたときも）、しずくとタブバーを元に戻す
                                fingerX = null
                            }
                            if (go) onGo(Dest.all[indexAt(x)])
                        }
                    },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dest.all.forEachIndexed { index, dest ->
                    val isSelected = index == selectedIndex
                    // 選ばれたタブは少し大きいまま（1.18 倍）。選ばれた瞬間はひと呼吸縮んでから、バネで弾んで大きくなる
                    val scale = remember { Animatable(if (isSelected) SelectedScale else 1f) }
                    LaunchedEffect(isSelected) {
                        if (isSelected) {
                            scale.snapTo(0.85f)
                            scale.animateTo(SelectedScale, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        } else {
                            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Tab
                                selected = isSelected
                                contentDescription = dest.label
                                onClick { onGo(dest); true }
                            },
                    ) {
                        TabIcon(
                            dest = dest,
                            selected = isSelected,
                            animate = moved[0],
                            tint = Fieldbook.Ink,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    // 押している間は、しずくの近くのアイコンほど大きく（レンズで拡大したように）
                                    val lensCenter = (left.value + right.value) / 2
                                    val near = 1f - min(1f, abs(lensCenter - centerOf(index)) / tabWidthPx)
                                    val magnify = 1f + LensMagnify * near * lift.value.coerceIn(0f, 1.2f)
                                    scaleX = scale.value * magnify
                                    scaleY = scale.value * magnify
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * しずく（選択中のタブの後ろ）。[left]・[right] の端の間に描く。
 * 伸びている間は縦に少しつぶれ（水の粒のように体積を保つ）、[lift] で持ち上がる（ふくらんで、うすい影が出る）。
 * ガラスのような光沢は、野帳の紙の画面から浮いて見えたので付けない
 */
@Composable
private fun GlassLens(
    left: Animatable<Float, AnimationVector1D>,
    right: Animatable<Float, AnimationVector1D>,
    lift: () -> Float,
) {
    val ink = Fieldbook.Ink
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val lensWidth = LensWidth.toPx()
                val baseHeight = LensHeight.toPx()
                val l = min(left.value, right.value)
                val r = max(left.value, right.value)
                val center = (l + r) / 2
                val stretch = ((r - l) / lensWidth - 1f).coerceIn(0f, 1.5f)
                val up = lift()
                val swell = 1f + 0.12f * up
                val width = (r - l) * swell
                val height = baseHeight * (1f - 0.16f * stretch.coerceAtMost(1f)) * swell
                val topLeft = Offset(center - width / 2, (size.height - height) / 2)
                drawLens(topLeft, Size(width, height), ink, up)
            },
    )
}

private fun DrawScope.drawLens(topLeft: Offset, size: Size, ink: Color, lift: Float) {
    val radius = CornerRadius(size.height / 2)
    // 持ち上がっている間だけ、うすい影を少し下に（つや消しのまま、浮いたことが分かるように）
    val up = lift.coerceIn(0f, 1f)
    if (up > 0f) {
        drawRoundRect(ink.copy(alpha = 0.06f * up), topLeft = topLeft + Offset(0f, 2.dp.toPx() * up), size = size, cornerRadius = radius)
    }
    // 地：うすい墨色（野帳の紙に合わせて、光沢は付けない）。持ち上がると少しだけ濃く
    drawRoundRect(ink.copy(alpha = 0.08f + 0.04f * up), topLeft = topLeft, size = size, cornerRadius = radius)
}

/** 進む側の端のバネ（固い。先に着く）。指でなぞっている間は、さらに固くして指に付いてこさせる */
private fun lead(following: Boolean) =
    spring<Float>(dampingRatio = 0.72f, stiffness = if (following) 1400f else 700f)

/** 後ろの端のバネ（やわらかい。遅れて付いてくるので、しずくが伸びる） */
private fun trail(following: Boolean) =
    spring<Float>(dampingRatio = 0.68f, stiffness = if (following) 520f else 210f)

/** 指がタブバーの外へ出たとみなすまでの余裕（ここより外で離すと取り消し） */
private val OutsideMargin = 24.dp

/** ピルの形（高さ 56dp の半分の角丸） */
private val PillShape = RoundedCornerShape(28.dp)
private val BarHeight = 56.dp

/** 押したときに、タブバーがどれだけ縮むか（横。縦はその 1.4 倍）と、なぞったときに指の方へ引っぱられる上限 */
private const val BarSquash = 0.035f
private val BarPull = 6.dp

/** 止まっているときのしずくの大きさ */
private val LensWidth = 60.dp
private val LensHeight = 40.dp

/** 押している間、しずくの近くのアイコンをどれだけ大きくするか */
private const val LensMagnify = 0.22f

/** 選ばれたタブのアイコンの大きさ（元の何倍か） */
private const val SelectedScale = 1.18f
