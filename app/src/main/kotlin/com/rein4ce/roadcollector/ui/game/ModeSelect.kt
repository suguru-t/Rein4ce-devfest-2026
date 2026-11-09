package com.rein4ce.roadcollector.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.ui.Dest
import com.rein4ce.roadcollector.ui.TabIcon
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.ui.common.BlockWord
import com.rein4ce.roadcollector.ui.common.HeaderFillLayout
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.PopChip
import com.rein4ce.roadcollector.ui.common.PopTag
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 積む画面の遊び方（モード）。並びは、モードを選ぶ画面の上から。
 * まだ作っていないモード（[ready] が false）は、選ぶ画面に「準備中」で出して、押せないようにする。
 *
 * - 通常：いまの積み方（集めたパーツを最大 5 本選んで積み、高さを測る）。#108 で、広い台に終わりなく落ちてくる形にする予定
 * - 3 分チャレンジ：#45・#78（D）。#78 が入ったら [ready] を true にして、[StackTab] から始める
 * - 毎日 1 つずつ積む：#106 で中身を決める
 */
enum class PlayMode(val label: String, val note: String, val ready: Boolean) {
    NORMAL("通常", "5 本のパーツを積んで、高さを測る", ready = true),
    CHALLENGE("3 分チャレンジ", "3 分のあいだに、ピースを高く積む", ready = false),
    DAILY("毎日 1 つずつ積む", "1 日に 1 つずつ積んで、塔を育てる", ready = false),
}

/**
 * いま出すモード（null ならモードを選ぶ画面）。
 *
 * コレクションで選んだパーツの「積む」と、積む画面の「パーツを選ぶ」は、新しい開始要求（[GameSession.StartRequest]）を出す。
 * まだ始めていない要求が来たら、選ぶ画面を飛ばして通常で始める（選んだパーツで積みたいのが分かっているので）。
 * 1 つの要求で飛ぶのは 1 回だけ（[jumpedFor]）。そのあと「戻る」で選ぶ画面に戻れる（要求のパーツが消えていて始められないときも）
 *
 * @param chosen    選ぶ画面で選んだモード（戻ると null）
 * @param jumpedFor 通常へ飛んだ開始要求の id
 */
internal fun shownMode(chosen: PlayMode?, request: GameSession.StartRequest?, jumpedFor: Long?): PlayMode? =
    if (request != null && !request.started && request.id != jumpedFor) PlayMode.NORMAL else chosen

/**
 * 積むタブの中身。開くと、まずモードを選ぶ画面（ホームのような、色の積み木と紙のカード）。
 * 選ぶとそのモードで遊ぶ画面になり、上の「‹ 戻る」か端末の「戻る」で選ぶ画面に戻る（選ぶ画面での「戻る」はホームへ）。
 *
 * どのモードを選んでいるかは、この画面の中だけで覚える（タブを移ってから戻ると、遊んでいた画面の続き。
 * アプリを開き直すと、また選ぶ画面から）。前に遊んだモードをスマホに残して、そこから始めることはしない
 */
@Composable
fun StackTab(onGoInventory: () -> Unit, onGoGallery: () -> Unit) {
    var chosen by rememberSaveable { mutableStateOf<PlayMode?>(null) }
    var jumpedFor by rememberSaveable { mutableStateOf<Long?>(null) }
    val request = GameSession.startRequest
    // 最初のコマから通常を出す（選ぶ画面が一瞬見えないように）。覚えるのは次の LaunchedEffect
    val mode = shownMode(chosen, request, jumpedFor)
    LaunchedEffect(request) {
        if (request != null && !request.started && request.id != jumpedFor) {
            chosen = PlayMode.NORMAL
            jumpedFor = request.id
        }
    }
    val records = RoadCollectorApp.from(LocalContext.current).stackRecords

    when (mode) {
        PlayMode.NORMAL -> {
            BackHandler { chosen = null }
            ModePlayPage(PlayMode.NORMAL, onBack = { chosen = null }) {
                StackScreen(onGoInventory = onGoInventory, onGoGallery = onGoGallery)
            }
            // 積み切って塔が止まり、パーツが残ったら（「サーバに送信」できる結果）、通常の最高を更新する。
            // 積む画面と同じ ViewModel（この画面の行き先ごとに 1 つ）を見るだけで、積む画面には手を入れない
            val vm: StackViewModel = viewModel()
            val canUpload = vm.canUpload
            LaunchedEffect(vm.runId, canUpload) {
                if (canUpload) vm.result?.let { records.submitNormal(it.tower.height) }
            }
        }
        // 3 分チャレンジ・毎日 1 つずつ積むは、まだ選べない（[PlayMode.ready]）
        null, PlayMode.CHALLENGE, PlayMode.DAILY -> {
            val best by records.normalBest.collectAsStateWithLifecycle()
            ModeSelectScreen(normalBest = best, onPick = { chosen = it })
        }
    }
}

/**
 * モードを選ぶ画面（ゲームのタイトル画面のように）。ホームと同じ組み方：
 * - 上の部分：台に積んだパーツの絵、大きな画面名「みちをつむ」（ほかの画面と同じ積み木。押すと動く）、「遊び方を選んでください」
 * - 下に、モードの行（ホームの行と同じ形。左に色の積み木とアイコン、右に紙のカード：名前・一言・自分の記録）
 * 行はふつうの大きさのままいちばん下に置き、余った高さは上の部分を大きくして埋める（[HeaderFillLayout]）。
 * 小さい画面や文字を大きくしている人の画面で入りきらないときは、画面ごと上下に動かせる
 */
@Composable
private fun ModeSelectScreen(normalBest: Float?, onPick: (PlayMode) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = PAGE_TOP, bottom = PAGE_BOTTOM)) {
                HeaderFillLayout(
                    minHeight = viewport - PAGE_TOP - PAGE_BOTTOM,
                    maxScale = HEADER_SCALE_MAX,
                    gap = HEADER_ROWS_GAP,
                    header = { scale -> ModeTitle(scale) },
                    rows = {
                        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                            PlayMode.entries.forEach { mode ->
                                ModeRow(
                                    mode = mode,
                                    record = if (mode == PlayMode.NORMAL) normalRecord(normalBest) else null,
                                    onClick = { onPick(mode) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * モードを選ぶ画面の上の部分：台に積んだパーツの絵・画面名「みちをつむ」・一言。まん中にそろえる。
 * 中の大きさ（dp・sp）を [scale] 倍にする（画面に高さが余るとき、行ではなくここを大きくして埋める）
 */
@Composable
private fun ModeTitle(scale: Float) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StackArt(modifier = Modifier.size(width = ART_WIDTH, height = ART_HEIGHT))
            Spacer(Modifier.height(10.dp))
            BlockWord("みちをつむ", blockDp = TITLE_BLOCK)
            Text(
                "遊び方を選んでください",
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * 台に積んだパーツの絵（飾り。読み上げない）。積む画面と同じ紺の台に、題字の色の道のパーツを 3 つ：
 * 下に横たわった棒（黄）、その上に橋のような形（青緑）、いちばん上に少し傾いた L（オレンジ）。
 * パーツは、コレクションの道と同じく、濃い縁取りを描いてから本体を重ねる
 */
@Composable
private fun StackArt(modifier: Modifier = Modifier) {
    // 台の色は積む画面と同じ（ダークでは、暗い地の上でも見える明るさ）
    val platform = if (Fieldbook.dark) Color(0xFF7A7FA6) else Pop.Navy
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        // 絵は 176×112 の目盛りで描き、場所の大きさに合わせる
        val u = size.width / 176f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val width = 10f * u
        fun part(color: Color, points: List<Offset>) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, Pop.side(color), style = Stroke(width + 3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // 台
        drawRoundRect(platform, topLeft = p(34f, 96f), size = Size(108f * u, 14f * u), cornerRadius = CornerRadius(2f * u))
        // 横たわった棒
        part(Pop.Yellow, listOf(p(46f, 87f), p(130f, 87f)))
        // 橋のような形
        part(Pop.Teal, listOf(p(60f, 79f), p(60f, 60f), p(114f, 60f), p(114f, 79f)))
        // 少し傾いた L
        rotate(degrees = -8f, pivot = p(92f, 38f)) {
            part(Pop.Orange, listOf(p(70f, 51f), p(104f, 51f), p(104f, 22f)))
        }
    }
}

/** 通常のカードに出す自分の記録（「自分の最高 2.40 m」。まだ無ければその一言） */
private fun normalRecord(best: Float?): String =
    if (best == null) "まだ記録はありません" else "自分の最高 %.2f m".format(best)

/**
 * モードの行 1 つ。左に色の積み木（モードのアイコン）、右に紙のカード（名前・一言・記録・›）。
 * 押せるモードは行のどこを押しても始まり、ほかのボタンと同じ押し心地で縮む。
 * まだ作っていないモードは、全体をうすくして「準備中」の札を出し、押せない（読み上げでも「準備中」と伝える）
 */
@Composable
private fun ModeRow(mode: PlayMode, record: String?, onClick: () -> Unit) {
    val look = lookOf(mode)
    val source = remember { MutableInteractionSource() }
    val action = if (mode.ready) {
        Modifier
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
            .alpha(NOT_READY_ALPHA)
            .clearAndSetSemantics {
                contentDescription = "${mode.label}、準備中。${mode.note}"
                disabled()
            }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().then(action),
    ) {
        Box(modifier = Modifier.width(BLOCK_COLUMN), contentAlignment = Alignment.Center) {
            // 題字と同じ色の積み木に、モードのアイコン。題字の積み木のように少し傾ける
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer { rotationZ = look.tiltDeg }
                    .size(width = BLOCK_SIZE, height = BLOCK_SIZE + BLOCK_DEPTH)
                    .popBlock(face = look.face, corner = 14.dp, depth = BLOCK_DEPTH),
            ) {
                val iconModifier = Modifier.size(ICON_SIZE)
                when (val icon = look.icon) {
                    null -> TabIcon(dest = Dest.Stack, selected = true, animate = false, tint = Pop.Letter, modifier = iconModifier)
                    else -> Icon(icon, contentDescription = null, tint = Pop.Letter, modifier = iconModifier)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
                .heightIn(min = CARD_MIN_HEIGHT)
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 「3 分」「5 本」の数と単位が別の行に分かれないよう、空白は折り返さない空白にする（notes）
                Text(
                    notes(mode.label),
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Fieldbook.Ink,
                    style = noteStyle,
                )
                Text(
                    notes(mode.note),
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Fieldbook.Muted,
                    // 文字を大きくして 2 行になるときは、言葉の切れ目で折り返す
                    style = noteStyle,
                )
                if (record != null) {
                    Text(
                        notes(record),
                        fontFamily = Pop.Rounded,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Pop.MineInk,
                        style = noteStyle,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // まだ作っていないモードは、記録の代わりに「準備中」の札（右の › の場所に置くと、小さい画面で名前が細切れに折り返すので下に）
                if (!mode.ready) {
                    PopTag("準備中", face = Pop.Navy, fontSize = 12.sp, tiltDeg = -2f, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (mode.ready) {
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
 * モードで遊ぶ画面の枠：上に「‹ 戻る」（モードを選ぶ画面へ。「みんな」のランキングと同じ黄色い札）と、右に今のモードの札。
 * その下に、モードの画面（積む画面など）を残りの高さ全部で置く
 */
@Composable
private fun ModePlayPage(mode: PlayMode, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
        ) {
            PopChip("‹ 戻る", face = Pop.Yellow, text = Pop.InkOnYellow, fontSize = 14.sp, onClick = onBack)
            Spacer(Modifier.weight(1f))
            PopTag(mode.label, face = lookOf(mode).tagFace, fontSize = 12.sp, tiltDeg = 2f)
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/**
 * モードの見た目：積み木の面の色・アイコン（null なら積むタブのアイコン）・傾き・遊んでいるときの札の色。
 * 色は題字の積み木の色から（通常＝オレンジ、3 分チャレンジ＝青緑、毎日＝紺）。アイコンの字の色はどれも題字の字と同じ
 */
private class ModeLook(val face: Color, val icon: ImageVector?, val tiltDeg: Float, val tagFace: Color)

private fun lookOf(mode: PlayMode): ModeLook = when (mode) {
    PlayMode.NORMAL -> ModeLook(Pop.Orange, null, -4f, Pop.OrangeDeep)
    PlayMode.CHALLENGE -> ModeLook(Pop.Teal, AppIcons.Stopwatch, 3f, Pop.TealDeep)
    PlayMode.DAILY -> ModeLook(Pop.Navy, AppIcons.Calendar, -2f, Pop.Navy)
}

/** ページの上と下の余白（ホームと同じ） */
private val PAGE_TOP = 20.dp
private val PAGE_BOTTOM = 20.dp

/** 画面に高さが余るとき、上の部分を大きくする上限（ふつうの大きさの何倍まで）と、上の部分と行のあいだ（いちばん狭いとき） */
private const val HEADER_SCALE_MAX = 1.8f
private val HEADER_ROWS_GAP = 22.dp

/** 上の部分の絵の大きさと、画面名の積み木 1 つの大きさ（ほかの画面の見出しは 26dp。タイトルなので大きく） */
private val ART_WIDTH = 176.dp
private val ART_HEIGHT = 112.dp
private val TITLE_BLOCK = 40.dp

/** 行と行の間 */
private val ROW_GAP = 14.dp

/** 左の積み木の列の幅と、積み木の大きさ・下にのぞく側面・中のアイコンの大きさ（ホームの行より一回り小さく） */
private val BLOCK_COLUMN = 84.dp
private val BLOCK_SIZE = 68.dp
private val BLOCK_DEPTH = 5.dp
private val ICON_SIZE = 38.dp

/** 右の紙のカードの、いちばん低い高さ（積み木と同じくらい） */
private val CARD_MIN_HEIGHT = 80.dp

/** まだ作っていないモードの行のうすさ */
private const val NOT_READY_ALPHA = 0.55f
