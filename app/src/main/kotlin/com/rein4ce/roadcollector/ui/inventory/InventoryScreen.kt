package com.rein4ce.roadcollector.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.WalkNote
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.common.walkDate
import com.rein4ce.roadcollector.ui.weather.BackdropFocus
import com.rein4ce.roadcollector.ui.weather.WeatherCredit
import com.rein4ce.roadcollector.ui.weather.ZoomWeather
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.LocalIndication
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.common.BlockWord
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.PopButton
import com.rein4ce.roadcollector.ui.common.PopChip
import com.rein4ce.roadcollector.ui.common.PopTag
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER
import com.rein4ce.roadcollector.ui.common.popBlock

/**
 * B 担当：コレクション（手持ちパーツ）の一覧。状態は 2 つ（iPhone のホーム画面と同じ形）：
 *
 * - 普段：カードをタップすると大きく見られて、そこから名前を付けられる（RenameDialog.kt）
 * - 並べ替え中（普段の状態で長押し）：カードが小さく揺れ、長押しのまま動かすと並べ替え、左上の「×」で削除（確認あり）。
 *   タップしても何も起きない。「完了」か戻るで普段に戻る
 *
 * 積むパーツを選ぶ「選択」はやめた（積む画面の通常モードは、いつもコレクションの全部のパーツから出すため。決定 62）。
 *
 * 見た目は「測量野帳」（Fieldbook.kt）。
 */
@Composable
fun InventoryScreen() {
    val context = LocalContext.current
    val repository = RoadCollectorApp.from(context).partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    val isLoaded by repository.isLoaded.collectAsStateWithLifecycle()
    // 歩いた場所（拡大表示の背景の地図に使う）。見本の場所は SampleParts から
    val places by repository.places.collectAsStateWithLifecycle()
    val weathers by repository.weather.collectAsStateWithLifecycle()
    // 並びは InventoryOrder.kt のルール。保存の順番には頼らない
    val shown = rememberArrangedParts(repository)
    // 画面に出している並び。ドラッグ中はこれだけを動かし、指を離したときに保存する
    var arranged by remember { mutableStateOf(shown) }
    // 並びと絞り込み（設定に保存）とお気に入り。画面に出すのは、いつもの順（arranged）を絞り込んで並べ替えたもの
    val settings = RoadCollectorApp.from(context).settings
    val filtering = rememberPartFiltering(repository, settings)
    val filter = filtering.filter
    val favorites = filtering.favorites
    val visible = remember(arranged, filtering) { filtering.apply(arranged) }
    // ドラッグしているパーツの id と、ドラッグを始めたときの位置
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(shown) { if (draggingId == null) arranged = shown }
    fun saveArranged(list: List<Part>) {
        arranged = list
        repository.setOrder(list.map { it.id })
    }
    // 並べ替え中か。画面を離れると普段の状態に戻る
    var editing by remember { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }
    // 端末の設定でアニメーションを切っている人には、カードを揺らさない
    val reduceMotion = rememberReduceMotion()
    val sampleCount = parts.count { SampleParts.byId(it.id) != null }
    val missingSamples = SampleParts.all.count { s -> parts.none { it.id == s.id } }
    // 削除の確認ダイアログを出しているパーツ（null なら出していない）
    var pendingDelete by remember { mutableStateOf<Part?>(null) }
    // 大きく見ているパーツと、名前を付けているパーツの id（null なら開いていない）。
    // id で持つのは、名前を付けたあと、大きく見る画面に新しい名前で戻るため
    var zoomedId by remember { mutableStateOf<String?>(null) }
    var renamingId by remember { mutableStateOf<String?>(null) }

    // 方眼の地はアプリ全体（AppNav の Scaffold）で 1 枚敷いているので、ここでは描かない（二重になって端で線がずれる）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        // 画面名（題字「ミチツミ」と同じ積み木。「みんな」と同じ形）と、並べ替え中は右に「並べ替え中」の札。
        // 行の高さは 48dp に決めておく（札が出たときに下の一覧がずれると、長押しでつかんだカードが指の下からずれるため）
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            BlockWord("コレクション")
            Spacer(Modifier.weight(1f))
            if (editing) {
                PopTag("並べ替え中", face = Pop.Navy, fontSize = 12.sp, tiltDeg = 2f)
            }
        }
        // 歩いた距離の合計（見出しのメーター）。値は画面で持つ（下の見出しは一覧といっしょに動くので、見出しの中で持つと、
        // 画面の外に出て戻るたびに 0 から回り直す）
        val totalM = parts.filter { SampleParts.byId(it.id) == null }.sumOf { it.distanceM }
        val meter = rememberMeter(totalM, isLoaded, reduceMotion)

        val gridState = rememberLazyGridState()
        // 並びや絞り込みを変えたら、いちばん上から見せる（リストは、それまで上に見えていたカードを上に残そうとするので、
        // 長い順にしても、いちばん長いパーツが上の見えない所に行ってしまう）。
        // 変えたときだけ：画面に入った最初の 1 回は動かさない（ほかのタブから戻ったときに、見ていた位置が残るように。#75 のレビュー）
        ScrollToTopOnChange(filter, gridState)
        val haptics = rememberHaptics()
        val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
            // ドラッグ中：指の下に来たカードと場所を入れ替える（ほかのカードはよけるように動く）。
            // 一覧のいちばん上は見出しなので、何番目かではなく id で探す（見出しは並べ替えの相手にならない）
            val f = arranged.indexOfFirst { it.id == from.key }
            val t = arranged.indexOfFirst { it.id == to.key }
            if (f >= 0 && t >= 0) arranged = arranged.moved(f, t)
        }
        // メーター・注記・並びと絞り込みは、一覧のいちばん上に置いて、カードといっしょに動かす。
        // 上に止めておくと、小さい画面や文字を大きくしている人の画面では、見出しだけで半分ほどになり、カードが 1 段も見えないため
        // （画面名と「選択」の行だけは止めておく）
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.weight(1f),
            // 並べ替え中の「×」がカードの左上からはみ出すので、左右に少し余白（上は見出しがあるので要らない）
            contentPadding = PaddingValues(start = GRID_START, end = GRID_END, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = HEADER_KEY) {
                // 見出しは一覧の左右の余白の外まで広げて、上の画面名と左をそろえる
                Column(modifier = Modifier.outdent(start = GRID_START, end = GRID_END)) {
                    // 歩いた距離の合計をメーターのように大きく出す（開くたびに 0 から上がり、増えたり減ったりすると、数字が回って新しい値まで動く）
                    CollectionMeter(
                        meter = meter,
                        totalM = totalM,
                        walks = parts.size - sampleCount,
                        samples = sampleCount,
                        isLoaded = isLoaded,
                    )
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                editing -> notes("長押しのまま動かして並べ替え", "× で削除")
                                // 並べ替え（長押しのドラッグ）は、いつもの順で絞り込んでいないときだけ（ほかの並びで動かしても意味が分からないため）
                                !filter.isDefault -> notes("タップで大きく見る", "並べ替えは「いつもの順」のときだけ")
                                else -> notes("タップで大きく見る", "長押しで並べ替え")
                            },
                            fontFamily = Pop.Rounded,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = Fieldbook.Muted,
                            modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start),
                        )
                        // 消した見本だけを足し戻す。採集したパーツには触らない。
                        // 読み込み中は出さない（parts が空なので全部の見本が「消えた」ように見え、押すとわざと消した見本まで戻るため）
                        if (!editing && isLoaded && missingSamples > 0) {
                            PopChip(
                                "見本を戻す",
                                face = Fieldbook.Card,
                                text = Pop.TealLine,
                                edge = Pop.TealLine,
                                fontSize = 12.sp,
                                depth = 2.dp,
                                onClick = { repository.restoreMissingSamples() },
                            )
                        }
                    }

                    // 並びと絞り込みの行。並べ替え中は見えなくして押せなくする（並べ替えは、いつもの順で絞り込んでいないときだけなので）。
                    // 行の場所は残す：消すと一覧が上に詰まり、長押しでつかんだカードが指の下からずれるため（#75 のレビュー）
                    if (isLoaded && parts.isNotEmpty()) {
                        PartFilterBar(
                            filter = filter,
                            onChange = settings::setPartFilter,
                            skies = filtering.skies,
                            enabled = !editing,
                            modifier = Modifier
                                .graphicsLayer { alpha = if (editing) 0f else 1f }
                                .then(if (editing) Modifier.clearAndSetSemantics {} else Modifier),
                        )
                    }

                }
            }
            if (isLoaded && parts.isNotEmpty() && visible.isEmpty()) {
                // 絞り込んだら何も残らなかった（見出しは残すので、上の並びと絞り込みからも外せる）
                item(key = EMPTY_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = EMPTY_KEY) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("当てはまるパーツがありません", fontSize = 14.sp, color = Fieldbook.Muted)
                        PressTextButton(onClick = { settings.setPartFilter(filter.copy(sky = null, favoritesOnly = false)) }) {
                            Text("絞り込みを外す", color = Fieldbook.Survey, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { part ->
                // ReorderableItem が、並びが変わったときにカードを滑らかに動かす（animateItem）
                ReorderableItem(reorderState, key = part.id) { isDragging ->
                    val index = arranged.indexOfFirst { it.id == part.id }
                    fun moveTo(to: Int) = saveArranged(arranged.moved(index, to))
                    PartCard(
                        part = part,
                        sky = repository.weatherOf(part.id, weathers)?.sky,
                        startedAt = repository.placeOf(part.id, places)?.startedAt,
                        isSample = SampleParts.byId(part.id) != null,
                        favorite = part.id in favorites,
                        onToggleFavorite = { repository.setFavorite(part.id, part.id !in favorites) },
                        isDragging = isDragging,
                        editing = editing,
                        // 隣どうしで逆向きに揺らすと、iPhone のようにばらばらに揺れて見える
                        jiggleDirection = if (index % 2 == 0) 1f else -1f,
                        reduceMotion = reduceMotion,
                        // タップ：普段は大きく見る。並べ替え中は何もしない
                        // （長押しで並べ替え中に入ったあとに届くタップもここで止まる）
                        onClick = { if (!editing) zoomedId = part.id },
                        onDeleteClick = { pendingDelete = part },
                        // 長押しで並べ替えに入るのは、いつもの順で絞り込んでいないときだけ（ほかの並びや絞り込みの途中で動かしても、意味が分からないため）
                        dragHandle = Modifier.longPressDraggableHandle(
                            enabled = filter.isDefault,
                            onDragStarted = {
                                draggingId = part.id
                                dragStartIndex = arranged.indexOfFirst { it.id == part.id }
                                editing = true // 長押しで並べ替え中に入る（そのまま動かせば並べ替え）
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                val now = arranged.indexOfFirst { it.id == part.id }
                                draggingId = null
                                if (now != dragStartIndex) repository.setOrder(arranged.map { it.id })
                                // ドラッグ中は上の LaunchedEffect(shown) が並びを書き換えないので、その間にコレクションが
                                // 変わっていても（デモ散歩がちょうど終わった等）画面に出ない。離したときに最新の内容で並べ直す
                                arranged = repository.arrangedNow()
                            },
                        ),
                        // ドラッグできない読み上げ（TalkBack）の人のための操作
                        a11yActions = listOfNotNull(
                            CustomAccessibilityAction("先頭へ") { moveTo(0); true }.takeIf { filter.isDefault && index > 0 },
                            CustomAccessibilityAction("前へ") { moveTo(index - 1); true }.takeIf { filter.isDefault && index > 0 },
                            CustomAccessibilityAction("後ろへ") { moveTo(index + 1); true }.takeIf { filter.isDefault && index < arranged.size - 1 },
                            CustomAccessibilityAction("名前を付ける") { renamingId = part.id; true },
                            CustomAccessibilityAction("削除") { pendingDelete = part; true },
                        ),
                    )
                }
            }
        }

        // 並べ替え中だけ、いちばん下に「完了」（「みんな」の「ランキングを見る」と同じ、オレンジのブロック）。
        // 高さは 48dp 以上（文字を大きくして 2 行になっても切れないように）
        if (editing) {
            Spacer(Modifier.height(8.dp))
            PopButton(
                label = "完了",
                onClick = { editing = false },
                face = Pop.OrangeDeep,
                text = Pop.Letter,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // 大きく見ている間だけ、背景にそのパーツを歩いたときの天気を降らせる（一覧のときは今の天気。ui/weather/WeatherLayer）
    DisposableEffect(zoomedId) {
        BackdropFocus.partId.value = zoomedId
        onDispose { BackdropFocus.partId.value = null }
    }

    // 名前を付けている間は、大きく見る画面を閉じておく（付け終わったら、新しい名前で戻る）
    val zoomed = zoomedId?.let { id -> parts.firstOrNull { it.id == id } }
    val renaming = renamingId?.let { id -> parts.firstOrNull { it.id == id } }
    // 設定（ホームの歯車）で AI の名前付けをオフにしていたら、名前を付ける窓に「AI に考えてもらう」を出さない
    val aiNaming by RoadCollectorApp.from(context).settings.aiNaming.collectAsStateWithLifecycle()
    if (renaming != null) {
        RenameDialog(
            part = renaming,
            onSave = { name ->
                repository.rename(renaming.id, name)
                renamingId = null
            },
            onDismiss = { renamingId = null },
            aiOn = aiNaming,
        )
    } else if (zoomed != null) {
        ZoomDialog(
            zoomed,
            place = repository.placeOf(zoomed.id, places),
            weather = repository.weatherOf(zoomed.id, weathers),
            onRename = { renamingId = zoomed.id },
            onDismiss = { zoomedId = null },
        )
    }

    pendingDelete?.let { part ->
        DeleteDialog(
            part = part,
            sky = repository.weatherOf(part.id, weathers)?.sky,
            startedAt = repository.placeOf(part.id, places)?.startedAt,
            onConfirm = {
                repository.remove(part.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * 見出しのメーターの値。画面を開くたびに 0 から始まり（覚えておかない）、読み終わったら合計まで回って上がる。
 * 見出しは一覧といっしょに動く（画面の外に出ると作り直される）ので、値は画面の側で持つ（戻るたびに 0 から回り直さないように）
 */
@Composable
internal fun rememberMeter(totalM: Int, isLoaded: Boolean, reduceMotion: Boolean): Animatable<Float, AnimationVector1D> {
    val meter = remember { Animatable(0f) }
    LaunchedEffect(isLoaded, totalM) {
        if (!isLoaded) return@LaunchedEffect
        if (reduceMotion) {
            meter.snapTo(totalM.toFloat())
        } else {
            meter.animateTo(totalM.toFloat(), tween(durationMillis = 1_100, easing = FastOutSlowInEasing))
        }
    }
    return meter
}

/**
 * 一覧（LazyVerticalGrid）の中に置いたものを、一覧の左右の余白（[start]・[end]）の外まで広げる。
 * 一覧のいちばん上に置いた見出しを、一覧の外にある画面名と左右そろえるのに使う
 */
private fun Modifier.outdent(start: Dp, end: Dp): Modifier = layout { measurable, constraints ->
    val s = start.roundToPx()
    val extra = s + end.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout((placeable.width - extra).coerceIn(constraints.minWidth, constraints.maxWidth), placeable.height) {
        placeable.placeRelative(-s, 0)
    }
}

/** 一覧の左右の余白。並べ替え中にカードの左上からはみ出す「×」の分 */
private val GRID_START = 6.dp
private val GRID_END = 2.dp

/** 一覧のいちばん上の見出しと、絞り込んで何も残らなかったときの一言の key（パーツの id とは重ならない） */
private const val HEADER_KEY = "inventory-header"
private const val EMPTY_KEY = "inventory-empty"

/**
 * コレクション画面の見出し（画面名の積み木の下）。歩いた距離の合計（見本は含めない）を大きく出す（「みんな」の「あなたの最高」と同じ形）。
 * 画面を開くたびに、メーターのように 0 から数字が回って合計まで上がる（「みんな」の見出しと同じ）。
 * 開いている間に合計が変わったら（記録した・消した）、今の値から新しい値まで動く。アニメーションを切っている人には動かさない。
 * 読み込み中は parts が空なので、数字は出さない（読み終わってから 0 から上がる）。
 * 数字の動きは [rememberMeter] で、画面の側で持つ。ホーム（ui/HomeScreen.kt）の見出しでも同じものを出す
 *
 * @param showSamples 見本の本数（「・ 見本 7」）も出すか。ホームでは出さない（見本はコレクションの中の話なので）
 * @param horizontalAlignment 2 行の寄せ方。コレクションは左寄せ、ホームはまん中（題字・一言・ボタンがまん中にそろうので）
 */
@Composable
internal fun CollectionMeter(
    meter: Animatable<Float, AnimationVector1D>,
    totalM: Int,
    walks: Int,
    samples: Int,
    isLoaded: Boolean,
    modifier: Modifier = Modifier,
    showSamples: Boolean = true,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val shownM = meter.value
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                val total = totalDistance(totalM)
                // 画面名は、すぐ上の積み木（BlockWord）が見出しとして読むので、ここでは言わない（2 回続かないように。#90 の A のレビュー）
                val sampleNote = if (showSamples) "。見本 $samples" else ""
                contentDescription = if (isLoaded) "これまでに集めた道 $walks 本、合計 ${total.number} ${total.unit.spoken}$sampleNote" else "読み込み中"
            },
    ) {
        Spacer(Modifier.height(4.dp))
        // 1 km 未満は m、1 km からは km で出す（totalDistance）。
        // 回っている途中の値も切り捨てる（四捨五入だと、1,999 m に向かう途中の 1999.6 が一瞬「2.0 km」になって、歩いた分より多く見える）
        val shown = totalDistance(shownM.toInt())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "歩いた合計",
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(end = 10.dp, bottom = 8.dp),
            )
            // 数字は道の色（オレンジ）。字なので、紙の上で読める濃い色（ダークでは明るいオレンジ）
            Text(
                if (isLoaded) shown.number else "—",
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
                color = MeterInk,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                if (isLoaded) shown.unit.label else DistanceUnit.M.label,
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = MeterInk,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            )
        }
        Text(
            when {
                !isLoaded -> "読み込み中…"
                showSamples -> notes("これまでに集めた道 $walks 本", "見本 $samples")
                else -> "これまでに集めた道 $walks 本"
            },
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            color = Fieldbook.Ink,
        )
    }
}

/** 見出しのメーターに出す合計距離。数字と単位に分けて持つ（単位は小さく出すため） */
internal data class TotalDistance(val number: String, val unit: DistanceUnit)

/**
 * 合計距離の単位。画面に出す文字と、読み上げ（TalkBack）の言葉を一緒に持つ。
 * 画面の文字を変えても（たとえば「㎞」にしても）、読み上げが別の単位にならないように
 */
internal enum class DistanceUnit(val label: String, val spoken: String) {
    M("m", "メートル"),
    KM("km", "キロメートル"),
}

/**
 * 合計距離 [m] を、見出しのメーターに出す形にする。
 * - 1 km 未満は m のまま（834 m）
 * - 1 km からは km にして小数 1 桁（2,115 m → 2.1 km）。0.1 km に満たない分は切り捨てる。
 *   車の距離計と同じく、歩いた分より多くは見せないため（999 m の次が 1.0 km、1,999 m は 1.9 km）
 * - 桁区切りの「,」は使わない。言語の設定によらず、小数点は「.」
 */
internal fun totalDistance(m: Int): TotalDistance =
    if (m < 1_000) {
        TotalDistance("$m", DistanceUnit.M)
    } else {
        val tenths = m / 100
        TotalDistance("${tenths / 10}.${tenths % 10}", DistanceUnit.KM)
    }

/** 見出しの合計の数字の色：道のオレンジ。紙の上で読めるよう、ライトは濃いオレンジ、ダークは明るいオレンジ */
private val MeterInk: Color get() = if (Fieldbook.dark) Pop.Orange else Pop.OrangeDeep

/**
 * カードを押して開く拡大表示。設定などと同じ枠（見出しの帯にパーツの名前、枠の下に丸い「×」）を、一覧と同じポップな見た目にして、
 * 大きな図と注記を出す。図は「みんな」の紙片の図と同じブロックの面に載せる。
 * 枠の中のいちばん下の「名前を付ける」から名前を付けられる
 */
@Composable
private fun ZoomDialog(part: Part, place: PartPlace?, weather: WalkWeather?, onRename: () -> Unit, onDismiss: () -> Unit) {
    val isSample = SampleParts.byId(part.id) != null
    // 「もう一度歩く」：道を歩いた順になぞる（WalkReplay.kt）。窓を閉じると止まる
    val replay = rememberWalkReplay()
    FramedDialog(
        title = part.displayName,
        onDismiss = onDismiss,
        pop = true,
        // 枠の中の高さを中身に渡して、図の大きさで枠に収める（入りきらないときだけ、中身の側でスクロールする。FitFigureLayout）
        scrollContent = false,
        // 歩いたときの天気を、窓の紙（図のまわり）にも降らせる。背景の天気は、窓とまわりの暗い幕で見えにくいため。
        // 図（地図と道）はふさがない
        backdrop = { ZoomWeather(weather, walkedAt = part.walkedAt, modifier = Modifier.matchParentSize()) },
        // もう一度歩くと、名前を付ける（付け直す）。どちらも「みんな」と同じブロックのボタン。
        // 主な操作の「もう一度歩く」は青緑の面、「名前を付ける」は白い面に青緑の縁（「リンクをコピー」と同じ）。
        // 枠の中のいちばん下に止めておく（小さい画面で図が長くなっても、ボタンが枠の下に隠れないように）。
        // 横に 2 つ同じ幅で並べ、文字を大きくして入らなければ、ボタンの中で 2 行に折り返す（縦に積むと、図の場所がなくなる）。
        // 高さは 2 つでそろえる。閉じるのは、ほかの窓と同じく枠の下の「×」
        footer = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                ReplayButton(replay, padding = ZOOM_BUTTON_PADDING, modifier = Modifier.weight(1f).fillMaxHeight())
                PopButton(
                    label = "名前を付ける",
                    onClick = onRename,
                    face = Fieldbook.Card,
                    text = Pop.TealLine,
                    edge = Pop.TealLine,
                    fontSize = 15.sp,
                    padding = ZOOM_BUTTON_PADDING,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        },
    ) {
        // 図と、その下の注記。図は正方形で、ふつうは枠の幅いっぱい。窓に入りきらないとき（小さい画面や、文字を大きくしている人の画面）は、
        // 注記と枠の下のボタンが見えるよう、残りの高さに入るまで図を小さくする（FitFigureLayout）。
        // 小さくする限り（MIN_FIGURE）まで縮めても入らないとき（文字をとても大きくしたときなど）だけ、中身をスクロールさせる
        // （図を最小のまま押し込むと、注記に重なって読めなくなるため。#97 の A のレビュー）
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            FitFigureLayout(
                // 枠の中で、中身に使える高さ（FramedDialog の scrollContent = false で、見出しとボタンを引いた残り）
                viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity,
                minFigure = MIN_FIGURE + MAP_LOGO_STRIP_DP.dp + FIGURE_DEPTH,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                figure = {
                    BoxWithConstraints(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxWidth()) {
                        // 図の一辺：渡された高さ（FitFigureLayout が、注記を引いた残りを渡す）に入る大きさ。
                        // [strip] は図の下の Google のロゴの帯（地図を敷くときだけ）
                        fun side(strip: Dp): Dp =
                            if (constraints.hasBoundedHeight) minOf(maxWidth, maxOf(maxHeight - strip - FIGURE_DEPTH, MIN_FIGURE)) else maxWidth
                        // 図の背景に地図を敷くのは、歩いた場所が分かっていて、地図のキーがあり、図と同じ縮尺まで地図を
                        // 拡大できるときだけ（とても短い散歩は、地図をいちばん拡大しても合わない）。無ければ今までどおり紙だけ
                        val mapPlace = place?.takeIf {
                            BuildConfig.MAPS_API_KEY.isNotBlank() && mapCanMatch(it, figureDp = side(MAP_LOGO_STRIP_DP.dp).value)
                        }
                        val side = side(if (mapPlace != null) MAP_LOGO_STRIP_DP.dp else 0.dp)
                        Box(
                            modifier = Modifier
                                .width(side)
                                .popBlock(face = Fieldbook.Card, corner = CARD_CORNER, depth = FIGURE_DEPTH, edge = Fieldbook.CardEdge)
                                .clip(RoundedCornerShape(CARD_CORNER)),
                        ) {
                            if (mapPlace != null) {
                                // 地図は図と同じ縮尺・位置（PlaceMap.kt）。道・見当・北・縮尺はその上に重ねる。
                                // 図の下に細い帯を足して、Google のロゴを道や縮尺に隠されない場所に置く
                                PlaceMap(mapPlace, belowDp = MAP_LOGO_STRIP_DP, modifier = Modifier.matchParentSize())
                            }
                            Column {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    // 図の範囲だけ紙の色を半分透かして重ね、地図をうすくする（下の帯の Google のロゴにはかけない）
                                    if (mapPlace != null) Box(Modifier.matchParentSize().background(Fieldbook.Paper.copy(alpha = MAP_FADE)))
                                    // なぞるところは図の描き直しの中だけで読む（毎コマ、窓の中身全体を組み立て直さないように）
                                    FieldbookPart(part, modifier = Modifier.matchParentSize(), sky = weather?.sky, startedAt = place?.startedAt, trace = { replay.frame })
                                }
                                if (mapPlace != null) Spacer(Modifier.height(MAP_LOGO_STRIP_DP.dp))
                            }
                        }
                    }
                },
                below = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(6.dp))
                        // 歩いた長さ・日付・天気（記録のあとの窓・カードと同じ形。ui/common/WalkNote）。
                        // 歩いたときの天気が分かっていれば、字でも出す（道の色だけでは分からない人もいるので）
                        // 再生している間は、この行に何倍速かを出す（図の中に出すと旗や道にかかる。注記は見えなくするだけで場所は残し、
                        // 下の「止める」ボタンが動かないようにする）
                        val speed = replaySpeed(part.distanceM)
                        val showSpeed = replay.playing && speed != null
                        Box(contentAlignment = Alignment.Center) {
                            WalkNote(
                                distanceM = part.distanceM,
                                date = if (isSample) "見本" else walkDate(part.walkedAt),
                                weather = weather?.sky?.label,
                                // 再生中は見えなくするだけでなく、読み上げからも外す（見えている「約 ○ 倍速」とそろえる。#94 の A のレビュー）
                                modifier = Modifier
                                    .graphicsLayer { alpha = if (showSpeed) 0f else 1f }
                                    .then(if (showSpeed) Modifier.clearAndSetSemantics {} else Modifier),
                            )
                            if (showSpeed) {
                                // 行の大きさは注記で決め、倍速の字では変えない（字の高さが少し違うので、再生を始めると
                                // 図の大きさが 1px 動いていた。#97 の A のレビュー）
                                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "約 $speed 倍速",
                                        fontFamily = Fieldbook.Data,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Fieldbook.Survey,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.wrapContentSize(unbounded = true),
                                    )
                                }
                            }
                        }
                        // 歩いたときの天気は Open-Meteo から取っているので、出どころを添える（見本の天気は作り物なので付けない）
                        if (weather != null && !isSample) WeatherCredit(modifier = Modifier.padding(top = 2.dp))
                    }
                },
            )
        }
    }
}

/**
 * 拡大表示の図（[figure]）と、その下の注記（[below]）を縦に並べる。注記を先に測り、図には [viewport] から注記を引いた残りの高さを
 * 最大として渡す（図の側で、その高さに入る大きさにする）。残りが [minFigure] より少なければ [minFigure] を渡すので、全体は
 * [viewport] より高くなる（外側のスクロールで見る）
 */
@Composable
private fun FitFigureLayout(
    viewport: Int,
    minFigure: Dp,
    figure: @Composable () -> Unit,
    below: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(figure, below), modifier = modifier) { (figureParts, belowParts), constraints ->
        val width = constraints.maxWidth
        val belowPlaced = belowParts.map { it.measure(Constraints(maxWidth = width)) }
        val belowHeight = belowPlaced.sumOf { it.height }
        val room = if (viewport == Constraints.Infinity) Constraints.Infinity else maxOf(viewport - belowHeight, minFigure.roundToPx())
        val figurePlaced = figureParts.map { it.measure(Constraints(maxWidth = width, maxHeight = room)) }
        layout(width, figurePlaced.sumOf { it.height } + belowHeight) {
            var y = 0
            (figurePlaced + belowPlaced).forEach {
                it.place((width - it.width) / 2, y)
                y += it.height
            }
        }
    }
}

@Composable
private fun DeleteDialog(part: Part, sky: Sky?, startedAt: Long?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val isSample = SampleParts.byId(part.id) != null
    // 採集画面の結果ダイアログと同じ、野帳の紙のダイアログ。どれを消すのか分かるように図も出す
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = Fieldbook.Paper,
        titleContentColor = Fieldbook.Ink,
        textContentColor = Fieldbook.Ink,
        title = { Text("パーツを削除", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                FieldbookPart(part, modifier = Modifier.size(132.dp), sky = sky, startedAt = startedAt)
                Text(part.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isSample) "見本は「見本を戻す」でいつでも戻せます。" else "記録したパーツは、削除すると元に戻せません。",
                    style = noteStyle,
                    fontSize = 13.sp,
                    color = Fieldbook.Muted,
                )
            }
        },
        confirmButton = {
            PressTextButton(onClick = onConfirm) { Text("削除", color = Fieldbook.Danger, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            PressTextButton(onClick = onDismiss) { Text("やめる", color = Fieldbook.Muted) }
        },
    )
}

/**
 * パーツ 1 本のカード。方眼の上に置いたブロックの面（「みんな」の紙片と同じ、下に側面がのぞく白い面）に、図と注記を載せる。
 * 押すと、ほかのボタンと同じ押し心地（ui/common/PressFeel）で縮む。
 * - タップ（[onClick]）：大きく見る
 * @param editing 並べ替え中なら、揺らして左上に「×」を出す
 */
@Composable
private fun PartCard(
    part: Part,
    sky: Sky?,
    startedAt: Long?,
    isSample: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    isDragging: Boolean,
    editing: Boolean,
    jiggleDirection: Float,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    dragHandle: Modifier,
    a11yActions: List<CustomAccessibilityAction>,
) {
    // 主役は形そのもの。分類（L字など）は出さない
    val date = if (isSample) "見本" else walkDate(part.walkedAt)
    // 並べ替え中の揺れ（±1.2°）。持ち上げているカードは揺らさない。
    // 揺れの値は、下の graphicsLayer の中（描くとき）で読む。ここで読むと、揺れが動くたび（1 秒に 60 回）に
    // カードの中身（図・名前・☆）まで組み立て直して描き直すので重い（並べ替え中に CPU が倍になっていた）
    val wobble: State<Float>? = if (editing && !isDragging && !reduceMotion) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 140, easing = LinearEasing), RepeatMode.Reverse),
            label = "jiggle",
        )
    } else {
        null
    }
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // ドラッグ中は少し持ち上げる（大きく・影）。並べ替え中は小さく揺らす
            .graphicsLayer {
                val s = if (isDragging) 1.05f else 1f
                scaleX = s
                scaleY = s
                rotationZ = (wobble?.value ?: 0f) * 1.2f * jiggleDirection
                shadowElevation = if (isDragging) 10.dp.toPx() else 0f
                shape = CardShape
            }
            .pressFeel(source)
            // 1 本ずつ、方眼の上に置いたブロックの面にして、隣との境目を分かりやすくする（縁は細い線、下に側面）
            .popBlock(face = Fieldbook.Card, corner = CARD_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
            .semantics { customActions = a11yActions }
            .then(dragHandle)
            .clickable(
                interactionSource = source,
                // 角の丸い面に四角い波紋が出ないよう、波紋は出さず、縮む動きだけで応える
                indication = null,
                enabled = !editing,
                // 読み上げ（TalkBack）で、タップの意味が伝わるように
                onClickLabel = "大きく見る",
                onClick = onClick,
            )
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Box {
            // 道の色は歩いたときの天気（色相）と時間帯（明るさ）で変わる（決定 74）。読み上げでは天気を言葉で伝える
            FieldbookPart(
                part,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics {
                    if (sky != null) contentDescription = "${sky.label}の日に歩いた道"
                },
                sky = sky,
                startedAt = startedAt,
            )
            if (editing && !isDragging) {
                // 左上の「×」：押すと削除の確認（iPhone の並べ替え中と同じ位置）
                val deleteSource = remember { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-8).dp, y = (-8).dp)
                        .size(28.dp)
                        .pressFeel(deleteSource)
                        .clip(CircleShape)
                        .background(Fieldbook.Ink)
                        .border(BorderStroke(2.dp, Fieldbook.Paper), CircleShape)
                        .clickable(interactionSource = deleteSource, indication = LocalIndication.current, onClickLabel = "削除") { onDeleteClick() }
                        .semantics { contentDescription = "「${part.displayName}」を削除" },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        // 名前は 10 文字まで（命名 API・名前を付けるダイアログと同じ）。長い名前は字を少し小さくして 1 行に収める。
        // それでも入らない幅の端末では「…」で切る（タップして大きく見れば全部見られる）
        // 名前と注記の 2 行。右下に、お気に入りの ☆（押すと黄色の ★）。字は ☆ の分だけ右を空ける
        Box {
        Column(modifier = Modifier.padding(end = FavoriteToggleWidth)) {
        BasicText(
            part.displayName,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
        )
        // 文字の大きさが 1 倍なら 1 行。入らない分は「…」にする（どのカードも同じ高さになる）。
        // 文字を大きくしている人には 1 行に入らないので、2 行に折り返す（1 行で切ると採集日が見えなくなる）。
        // どのカードも 2 行分の高さにして、横に並ぶカードの下端をそろえる。それでも入らない分は「…」にする。
        // 少しだけ大きい設定（1.1 倍など）では、折り返さなくても 1 行分空くが、高さをそろえるほうを取った
        val largeText = LocalDensity.current.fontScale > 1f
        // 歩いた長さ・日付・天気（記録のあとの窓・大きく見る窓と同じ形。ui/common/WalkNote）
        WalkNote(
            distanceM = part.distanceM,
            date = date,
            weather = sky?.label,
            fontSize = 10.5.sp,
            separator = "・",
            minLines = if (largeText) 2 else 1,
            maxLines = if (largeText) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        }
        FavoriteToggle(
            favorite = favorite,
            name = part.displayName,
            // 並べ替え中は押せない（揺れているカードの上で、間違えて押さないように）
            enabled = !editing,
            onToggle = onToggleFavorite,
            // 見た目は小さいが、押せる範囲は縦 44dp（名前と注記の 2 行の右端ぶん）
            modifier = Modifier.align(Alignment.CenterEnd).size(width = FavoriteToggleWidth, height = 48.dp),
        )
        }
    }
}

/** カードの右下のお気に入りの幅（名前と注記は、この分だけ右を空ける） */
private val FavoriteToggleWidth = 44.dp

/** 拡大表示で、地図の Google のロゴを置くために図の下に足す帯の高さ [dp] */
private const val MAP_LOGO_STRIP_DP = 28f

/** 拡大表示の図のブロックの厚み（下にのぞく側面） */
private val FIGURE_DEPTH = 4.dp

/** 拡大表示の図をこれより小さくはしない（それでも入らないときは、中身をスクロールさせる。FitFigureLayout） */
private val MIN_FIGURE = 160.dp

/**
 * 拡大表示の下の 2 つのボタンの内側の余白。左右をふつう（16dp）より狭くして、小さい画面で文字を大きくしていても
 * 「立ち止まる」が言葉の途中で折り返さないようにする
 */
private val ZOOM_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 10.dp)

/** パーツ 1 本ぶんの紙片の形（角を少しだけ丸める） */
/** カードの角の丸さ（「みんな」の断面図・紙片と同じ） */
private val CARD_CORNER = SECTION_CORNER
private val CardShape = RoundedCornerShape(CARD_CORNER)

/** お気に入りの ★ の色（黄色。ライトでもダークでも、紙の上で見える） */
internal val FavoriteStar = Color(0xFFE0A100)

