package com.rein4ce.roadcollector.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.collect.TrackRecorder
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.ui.collect.TitleBlocks
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.PopTag
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.inventory.CollectionMeter
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.rememberMeter
import com.rein4ce.roadcollector.ui.settings.SettingsGearButton

/**
 * ホーム（開いたときに最初に出す画面。決定 52・53）。上から：
 * - まん中に、アプリのアイコンの積み木の塔を載せた題字「ミチツミ」（押すと動く）、一言（注記の行と同じ字）。右上に設定の歯車
 * - まん中に、コレクションと同じメーター：「歩いた合計 14.6 km」（開くたびに 0 から回って上がる）と「これまでに集めた道 25 本」（見本の本数は出さない）
 * - 4 つの画面へ行く行：左の列に、題字と同じ色の積み木（タブのアイコン）を並べ、それを道（パーツの道と同じオレンジと縁取り）でつなぐ。
 *   アプリを使う順（歩いて記録 → コレクション → 積む → みんな）が、1 本の道でつながって見える。右は紙のカード（コレクションのカードと同じ面・縁）に、
 *   画面の名前と一言。行のどこを押しても、下のタブを押したときと同じように、その画面へ移る
 *
 * タブバーはホームでは出さず、4 つの画面に入ると下から出てくる。4 つの画面で、タブバーの左の家のボタンか端末の「戻る」を押すと、ここへ戻る。
 * 開いたときのタイトル（TitleIntro.kt）は、ここの題字へ飛んで収まる。そのために題字の場所を知らせ、収まるまでは隠しておく
 * （飛んでくる題字と、ここの題字が二重に見えないように）。
 * 小さい画面や文字を大きくしている人の画面で入りきらないときは、画面ごと上下に動かせる
 */
@Composable
internal fun HomeScreen(onGo: (Dest) -> Unit) {
    val recording by TrackRecorder.isRecording.collectAsStateWithLifecycle()
    // メーターはコレクションの見出しと同じ数え方（見本は距離に含めない）
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    val isLoaded by repository.isLoaded.collectAsStateWithLifecycle()
    val sampleCount = parts.count { SampleParts.byId(it.id) != null }
    val totalM = parts.filter { SampleParts.byId(it.id) == null }.sumOf { it.distanceM }
    val meter = rememberMeter(totalM, isLoaded, rememberReduceMotion())
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(WindowInsets.systemBars.asPaddingValues())) {
        val viewport = maxHeight
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 左右と上の余白は、ほかの画面の見出しと同じ
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = PAGE_TOP, bottom = PAGE_BOTTOM)) {
                // 画面の高さいっぱいまで使う。4 つの行はふつうの大きさのまま、余った高さは上の部分を大きくして埋める
                HeaderFillLayout(
                    minHeight = viewport - PAGE_TOP - PAGE_BOTTOM,
                    header = { scale ->
                        HomeHeader(
                            scale = scale,
                            meter = meter,
                            totalM = totalM,
                            walks = parts.size - sampleCount,
                            samples = sampleCount,
                            isLoaded = isLoaded,
                        )
                    },
                    rows = { HomeRoute(recording = recording, onGo = onGo) },
                )
                // 設定の歯車は右上（上の部分を大きくしても、歯車は大きくしない）
                SettingsGearButton(modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/**
 * ホームの上の部分：塔と題字、一言、メーター。まん中にそろえる（52 のホームと同じ組み方）。
 * 中の大きさ（dp・sp）を [scale] 倍にする（画面に高さが余るとき、ボタンではなくここを大きくして埋める。[HeaderFillLayout]）
 */
@Composable
private fun HomeHeader(scale: Float, meter: Animatable<Float, AnimationVector1D>, totalM: Int, walks: Int, samples: Int, isLoaded: Boolean) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 塔を題字の上に（起動画面のアイコンと同じ組み方）
            AppIconTower(height = TOWER_HEIGHT)
            Spacer(Modifier.height(2.dp))
            // 題字は押すと動く（TitleBlocks.kt）。落ちてくる「ミ」がまわりの上を通るよう、手前に描く
            TitleBlocks(
                modifier = Modifier
                    .zIndex(1f)
                    // タイトルが終わったら、もう知らせない（余計な更新をしない）
                    .onGloballyPositioned { if (!TitleIntroState.done) TitleIntroState.anchor = it.boundsInRoot() }
                    .graphicsLayer { alpha = if (TitleIntroState.done) 1f else 0f },
            )
            // 一言は、コレクションの注記の行と同じ字
            Text(
                TAGLINE,
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(14.dp))
            // メーターは字の幅だけ取る（大きくしたときに画面の幅に入るかを、中身の幅で測るため）
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                CollectionMeter(
                    meter = meter,
                    totalM = totalM,
                    walks = walks,
                    samples = samples,
                    isLoaded = isLoaded,
                    // 見本の本数はコレクションの中の話なので、ホームでは出さない
                    showSamples = false,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
            }
        }
    }
}

/**
 * 上の部分（[header]）と 4 つの行（[rows]）を、[minHeight] の高さに収まるように並べる：
 * - 行はふつうの大きさのまま、いちばん下に置く（親指の届く所）
 * - 上の部分は、いったんふつうの大きさで並べて測り、残りの高さと画面の幅に入る倍率で並べ直す（[HEADER_SCALE_MAX] 倍まで）。
 *   倍率の上限で余った高さは、上の部分と行の間にあける
 * - 足りないとき（小さい画面・文字が大きいとき）は、どちらもふつうの大きさで上から並べ、画面ごと動かして見る
 * ふつうの大きさで測るほうは置かないので、画面には出ず、押せず、読み上げにも出ない
 */
@Composable
private fun HeaderFillLayout(minHeight: Dp, header: @Composable (scale: Float) -> Unit, rows: @Composable () -> Unit) {
    SubcomposeLayout { constraints ->
        val width = constraints.maxWidth
        val full = Constraints(minWidth = width, maxWidth = width)
        val loose = Constraints(maxWidth = width)
        val rowsPlaced = subcompose(HomeSlot.ROWS, rows).map { it.measure(full) }
        val rowsHeight = rowsPlaced.sumOf { it.height }
        val gap = HEADER_ROWS_GAP.roundToPx()
        val probe = subcompose(HomeSlot.MEASURE) { header(1f) }.map { it.measure(loose) }
        val probeHeight = probe.maxOfOrNull { it.height } ?: 0
        val probeWidth = probe.maxOfOrNull { it.width } ?: 0
        val room = minHeight.roundToPx() - rowsHeight - gap
        var scale = if (probeHeight > 0 && room > probeHeight) room.toFloat() / probeHeight else 1f
        if (probeWidth > 0) scale = minOf(scale, width.toFloat() / probeWidth)
        scale = scale.coerceIn(1f, HEADER_SCALE_MAX)
        val headerPlaced = subcompose(HomeSlot.HEADER) { header(scale) }.map { it.measure(loose) }
        val headerHeight = headerPlaced.maxOfOrNull { it.height } ?: 0
        val height = maxOf(headerHeight + gap + rowsHeight, minHeight.roundToPx())
        layout(width, height) {
            headerPlaced.forEach { it.placeRelative((width - it.width) / 2, 0) }
            var y = height - rowsHeight
            rowsPlaced.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/** [HeaderFillLayout] の並び：行・上の部分（測るだけ）・上の部分（出す） */
private enum class HomeSlot { ROWS, MEASURE, HEADER }

/**
 * 4 つの画面へ行く行を並べ、行の積み木を 1 本の道でつなぐ（行の後ろに描く）。行はふつうの大きさ
 * （画面に高さが余っても、ボタンは大きくしない。上の部分を大きくする。[HeaderFillLayout]）
 */
@Composable
private fun HomeRoute(recording: Boolean, onGo: (Dest) -> Unit) {
    // 最初と最後の行の積み木のまん中の高さ（この列の上から [px]）。行の高さは文字の大きさで変わるので、置いてから測る
    var roadTop by remember { mutableFloatStateOf(-1f) }
    var roadBottom by remember { mutableFloatStateOf(-1f) }
    Column(
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (roadTop < 0f || roadBottom < 0f) return@drawBehind
                val x = STOP_COLUMN.toPx() / 2
                val top = Offset(x, roadTop)
                val bottom = Offset(x, roadBottom)
                // パーツの道と同じく、縁取り（暗い色）を描いてから本体を重ねる（両端は積み木の後ろに隠れる）
                drawLine(Fieldbook.RoadCasing, top, bottom, ROAD_WIDTH.toPx() + 3.dp.toPx())
                drawLine(Fieldbook.Road, top, bottom, ROAD_WIDTH.toPx())
            },
    ) {
        Dest.all.forEachIndexed { index, dest ->
            HomeStop(
                dest = dest,
                // 記録中なら、記録の行に「記録中」の札（ホームへ戻っても、歩いている途中だと分かるように）
                badge = if (dest == Dest.Collect && recording) "記録中" else null,
                onClick = { onGo(dest) },
                modifier = Modifier.onGloballyPositioned {
                    val center = it.positionInParent().y + it.size.height / 2f
                    if (index == 0) roadTop = center
                    if (index == Dest.all.lastIndex) roadBottom = center
                },
            )
        }
    }
}

/**
 * 4 つの画面へ行く行 1 つ：左に積み木（タブのアイコン）、右に紙のカード（画面の名前・一言・›）。積み木は、行の後ろの道の上に載る（HomeScreen）。
 * 行のどこを押しても移る。押すとほかのボタンと同じ押し心地で縮む（道は縮まない）。読み上げでは、名前と一言を続けて読む
 */
@Composable
private fun HomeStop(dest: Dest, badge: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = lookOf(dest)
    val source = remember { MutableInteractionSource() }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .pressFeel(source)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        ) {
            Box(modifier = Modifier.width(STOP_COLUMN), contentAlignment = Alignment.Center) {
                // 題字と同じ色の積み木に、タブの塗りのアイコン。題字の積み木のように少し傾ける
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = look.tiltDeg }
                        .size(width = BLOCK_SIZE, height = BLOCK_SIZE + BLOCK_DEPTH)
                        .popBlock(face = look.face, corner = 16.dp, depth = BLOCK_DEPTH),
                ) {
                    TabIcon(dest = dest, selected = true, animate = false, tint = look.icon, modifier = Modifier.size(ICON_SIZE))
                }
            }
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
                    .heightIn(min = CARD_MIN_HEIGHT)
                    .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dest.label,
                        fontFamily = Pop.Rounded,
                        fontWeight = FontWeight.Black,
                        fontSize = 21.sp,
                        color = Fieldbook.Ink,
                    )
                    Text(
                        look.note,
                        fontFamily = Pop.Rounded,
                        fontWeight = FontWeight.Black,
                        // 小さい画面で文字を 1.3 倍にしても、1 行に入る大きさ
                        fontSize = 12.sp,
                        color = Fieldbook.Muted,
                        // 文字を大きくして 2 行になるときは、言葉の切れ目で折り返す
                        style = noteStyle,
                    )
                }
                if (badge != null) {
                    PopTag(badge, face = Pop.OrangeDeep, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Fieldbook.Muted,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

/**
 * アプリのアイコンの積み木の塔（res/drawable/ic_launcher_foreground。起動画面でも題字の上に出している）。
 * アイコンの絵は 108×108 の中の x 27〜81・y 22〜90 に描いてあるので、塔の所だけの場所を取る（まわりの余白は取らない）。
 * 飾りなので読み上げない（題字が「ミチツミ」と読まれる）
 */
@Composable
private fun AppIconTower(height: Dp, modifier: Modifier = Modifier) {
    val unit = height / ICON_ART_HEIGHT
    Box(modifier = modifier.size(width = unit * ICON_ART_WIDTH, height = height), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            // 絵の全体（108×108）を置き、塔のまん中（x 54・y 56）を場所のまん中に合わせる
            modifier = Modifier.requiredSize(unit * ICON_VIEWPORT).offset(y = unit * (ICON_VIEWPORT / 2 - ICON_ART_CENTER_Y)),
        )
    }
}

/** アイコンの絵の大きさ（108×108）と、その中の塔の幅・高さ・まん中の高さ（ic_launcher_foreground の座標） */
private const val ICON_VIEWPORT = 108f
private const val ICON_ART_WIDTH = 54f
private const val ICON_ART_HEIGHT = 68f
private const val ICON_ART_CENTER_Y = 56f

/** 見出しの、題字の上の塔の高さ（アプリの印として大きく出す） */
private val TOWER_HEIGHT = 120.dp

/** ページの上と下の余白 */
private val PAGE_TOP = 20.dp
private val PAGE_BOTTOM = 20.dp

/** 行と行の間 */
private val ROW_GAP = 14.dp

/** 画面に高さが余るとき、上の部分（塔・題字・一言・メーター）を大きくする上限（ふつうの大きさの何倍まで） */
private const val HEADER_SCALE_MAX = 1.8f

/** 上の部分と 4 つの行のあいだ（いちばん狭いとき） */
private val HEADER_ROWS_GAP = 22.dp

/** 左の積み木の列の幅と、積み木の大きさ・下にのぞく側面・中のアイコンの大きさ（最初のホームのボタンのように大きく） */
private val STOP_COLUMN = 92.dp
private val BLOCK_SIZE = 76.dp
private val BLOCK_DEPTH = 5.dp
private val ICON_SIZE = 42.dp

/** 右の紙のカードの、いちばん低い高さ（積み木と同じくらい） */
private val CARD_MIN_HEIGHT = 84.dp

/** 積み木をつなぐ道の太さ（縁取りは、これより 3dp 太い） */
private val ROAD_WIDTH = 12.dp

/**
 * 行の見た目：積み木の面の色・アイコンの色・傾き・一言。面の色は題字の積み木と同じ並び（ミ：オレンジ、チ：青緑、ツ：黄、ミ：紺）で、
 * 傾きも題字に合わせる。白いアイコンは題字の字と同じ色。黄色の面には墨のアイコン（Pop.kt の決まり）
 */
private class HomeLook(val face: Color, val icon: Color, val tiltDeg: Float, val note: String)

private fun lookOf(dest: Dest): HomeLook = when (dest) {
    Dest.Collect -> HomeLook(Pop.Orange, Pop.Letter, -5f, "歩いた道を記録する")
    Dest.Inventory -> HomeLook(Pop.Teal, Pop.Letter, 3f, "集めたパーツを見る")
    Dest.Stack -> HomeLook(Pop.Yellow, Pop.InkOnYellow, -2f, "パーツを積み上げる")
    Dest.Gallery -> HomeLook(Pop.Navy, Pop.Letter, 6f, "みんなのタワーを見る")
}
