package com.rein4ce.roadcollector.ui.inventory

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.core.weather.WalkWeather
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.data.applyPartFilter
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.WalkNote
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.paperBand
import com.rein4ce.roadcollector.ui.common.walkDate
import com.rein4ce.roadcollector.ui.weather.BackdropFocus
import com.rein4ce.roadcollector.ui.weather.WeatherCredit
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * B 担当：コレクション（手持ちパーツ）の一覧。状態は 3 つ（写真アプリ・iPhone のホーム画面と同じ形）：
 *
 * - 普段：カードをタップすると大きく見られて、そこから名前を付けられる（RenameDialog.kt）
 * - 選択中（右上の「選択」か、下の「積むパーツを選ぶ」で入る）：カードの左上に丸が出て、タップか丸で選ぶ（最大 5 本）。
 *   丸には積む順の数字が入る。下の「選んだ n 本で積む」で積む画面へ。「キャンセル」か戻るで、選択を消して普段に戻る
 * - 並べ替え中（普段の状態で長押し）：カードが小さく揺れ、長押しのまま動かすと並べ替え、左上の「×」で削除（確認あり）。
 *   タップしても何も起きない。「完了」か戻るで普段に戻る
 *
 * 見た目は「測量野帳」（Fieldbook.kt）。
 */
@Composable
fun InventoryScreen(onGoStack: () -> Unit) {
    val context = LocalContext.current
    val repository = RoadCollectorApp.from(context).partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    val isLoaded by repository.isLoaded.collectAsStateWithLifecycle()
    val savedOrder by repository.order.collectAsStateWithLifecycle()
    // 歩いた場所（拡大表示の背景の地図に使う）。見本の場所は SampleParts から
    val places by repository.places.collectAsStateWithLifecycle()
    val weathers by repository.weather.collectAsStateWithLifecycle()
    // 並びは InventoryOrder.kt のルール。保存の順番には頼らない
    val shown = remember(parts, savedOrder) { inventoryOrder(parts, savedOrder) }
    // 画面に出している並び。ドラッグ中はこれだけを動かし、指を離したときに保存する
    var arranged by remember { mutableStateOf(shown) }
    // 並びと絞り込み（設定に保存。「パーツを選ぶ」の窓と共通）とお気に入り。画面に出すのは、いつもの順（arranged）を絞り込んで並べ替えたもの
    val settings = RoadCollectorApp.from(context).settings
    val filter by settings.partFilter.collectAsStateWithLifecycle()
    val favorites by repository.favorites.collectAsStateWithLifecycle()
    val visible = remember(arranged, filter, weathers, favorites) {
        applyPartFilter(arranged, filter, { repository.weatherOf(it.id, weathers)?.sky }, favorites)
    }
    // 持っているパーツの天気（天気の絞り込みで選べるもの）
    val skies = remember(parts, weathers) { parts.mapNotNullTo(HashSet()) { repository.weatherOf(it.id, weathers)?.sky } }
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
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val sampleCount = parts.count { SampleParts.byId(it.id) != null }
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(GameSession.startRequest?.partIds.orEmpty()) } }
    // 手持ちに実在する選択だけを数える（前回の選択に、削除済みのパーツが残っていることがあるため）
    val validSelected = selectedIds.filter { id -> parts.any { it.id == id } }
    // 選択中か（積むパーツを選んでいる）。前回の選択は残してあり、「選択」を押すとそのまま出る。
    // 「キャンセル」か戻るで、選択を消して普段に戻る（写真アプリと同じ）。
    // キャンセルはこの画面の選択だけを消し、新しい開始要求を発行しない。
    // 別のタブから戻ったときは、前回の開始要求のIDを選択の初期値にする。
    var selecting by remember { mutableStateOf(false) }
    // 「ランダムに選ぶ」の個数と、選んだあとの一言（手で選び直したら消す）
    var pickCount by remember { mutableIntStateOf(GameSession.MAX_PARTS) }
    var pickNote by remember { mutableStateOf<String?>(null) }
    fun cancelSelecting() {
        selectedIds.clear()
        pickNote = null
        selecting = false
    }
    BackHandler(enabled = selecting) { cancelSelecting() }
    fun toggleSelect(id: String) {
        pickNote = null
        if (id in selectedIds) {
            selectedIds.remove(id)
        } else if (validSelected.size < GameSession.MAX_PARTS) {
            selectedIds.add(id)
        }
    }
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
        // 見出し：歩いた距離の合計をメーターのように大きく出す（開くたびに 0 から上がり、増えたり減ったりすると、数字が回って新しい値まで動く）。
        // 右上に「選択」（選択中は「キャンセル」）。並べ替え中は出さない（見出しの右に「並べ替え中」と出る）
        Row(verticalAlignment = Alignment.Top) {
            CollectionMeter(
                totalM = parts.filter { SampleParts.byId(it.id) == null }.sumOf { it.distanceM },
                walks = parts.size - sampleCount,
                samples = sampleCount,
                isLoaded = isLoaded,
                editing = editing,
                reduceMotion = reduceMotion,
                modifier = Modifier.weight(1f),
            )
            if (!editing && isLoaded && parts.isNotEmpty()) {
                SelectButton(
                    selecting = selecting,
                    onClick = { if (selecting) cancelSelecting() else selecting = true },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    editing -> notes("長押しのまま動かして並べ替え", "× で削除")
                    selecting -> notes("タップで積むパーツを選ぶ", "${GameSession.MAX_PARTS} 本まで")
                    // 並べ替え（長押しのドラッグ）は、いつもの順で絞り込んでいないときだけ（ほかの並びで動かしても意味が分からないため）
                    !filter.isDefault -> notes("タップで大きく見る", "並べ替えは「いつもの順」のときだけ")
                    else -> notes("タップで大きく見る", "長押しで並べ替え")
                },
                style = noteStyle,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                // 字の幅だけ紙の色を敷く（ui/common/PaperBand。見出しの字が方眼の線にかぶって読みにくいため）
                modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start).paperBand(),
            )
            // 消した見本だけを足し戻す。採集したパーツと選択には触らない。
            // 読み込み中は出さない（parts が空なので全部の見本が「消えた」ように見え、押すとわざと消した見本まで戻るため）
            if (!editing && !selecting && isLoaded && missingSamples > 0) {
                TextButton(onClick = { repository.restoreMissingSamples() }) {
                    Text("見本を戻す", color = Fieldbook.Survey, fontSize = 13.sp)
                }
            }
        }

        // 並びと絞り込みの行。並べ替え中は出さない（並べ替えは、いつもの順で絞り込んでいないときだけなので）
        if (!editing && isLoaded && parts.isNotEmpty()) {
            PartFilterBar(filter = filter, onChange = settings::setPartFilter, skies = skies)
        }

        // 選択中だけ：積むパーツを「おまかせ」で選ぶ（Issue #44）。見本も選ぶ対象に入れる（見本も積めるパーツで、
        // 採集が少ないうちでも 5 本そろうため）。選択だけを置き換え、ゲームは始めない。並び順・進行中のゲームも変えない
        if (selecting && !editing && isLoaded) {
            RandomPickRow(
                // コレクションが空なら選べないので押せなくする（「0 本なので全部選びました」と出さないように）
                enabled = visible.isNotEmpty(),
                count = pickCount,
                onCountChange = { pickCount = it },
                note = pickNote,
                onPick = {
                    // 絞り込んでいれば、出ているパーツの中から選ぶ
                    val picked = pickRandom(visible.map { it.id }, pickCount)
                    selectedIds.clear()
                    selectedIds.addAll(picked)
                    pickNote = randomPickNote(picked.size, pickCount)
                },
            )
        }

        val gridState = rememberLazyGridState()
        // 並びや絞り込みを変えたら、いちばん上から見せる（リストは、それまで上に見えていたカードを上に残そうとするので、
        // 長い順にしても、いちばん長いパーツが上の見えない所に行ってしまう）
        LaunchedEffect(filter) { gridState.scrollToItem(0) }
        val haptics = LocalHapticFeedback.current
        val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
            // ドラッグ中：指の下に来たカードと場所を入れ替える（ほかのカードはよけるように動く）
            arranged = arranged.moved(from.index, to.index)
        }
        if (isLoaded && parts.isNotEmpty() && visible.isEmpty()) {
            // 絞り込んだら何も残らなかった
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("当てはまるパーツがありません", fontSize = 14.sp, color = Fieldbook.Muted, modifier = Modifier.paperBand())
                TextButton(onClick = { settings.setPartFilter(filter.copy(sky = null, favoritesOnly = false)) }) {
                    Text("絞り込みを外す", color = Fieldbook.Survey, fontWeight = FontWeight.Bold)
                }
            }
        } else LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.weight(1f),
            // 並べ替え中の「×」がカードの左上からはみ出すので、上と左右に少し余白
            contentPadding = PaddingValues(start = 6.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visible, key = { it.id }) { part ->
                // ReorderableItem が、並びが変わったときにカードを滑らかに動かす（animateItem）
                ReorderableItem(reorderState, key = part.id) { isDragging ->
                    val order = validSelected.indexOf(part.id).takeIf { it >= 0 }?.plus(1)
                    val index = arranged.indexOfFirst { it.id == part.id }
                    fun moveTo(to: Int) = saveArranged(arranged.moved(index, to))
                    PartCard(
                        part = part,
                        sky = repository.weatherOf(part.id, weathers)?.sky,
                        order = order,
                        isSample = SampleParts.byId(part.id) != null,
                        favorite = part.id in favorites,
                        onToggleFavorite = { repository.setFavorite(part.id, part.id !in favorites) },
                        isDragging = isDragging,
                        editing = editing,
                        selecting = selecting,
                        // 隣どうしで逆向きに揺らすと、iPhone のようにばらばらに揺れて見える
                        jiggleDirection = if (index % 2 == 0) 1f else -1f,
                        reduceMotion = reduceMotion,
                        // 5 本選んだら、まだ選んでいないカードの丸は押せない
                        canSelect = order != null || validSelected.size < GameSession.MAX_PARTS,
                        // タップ：普段は大きく見る、選択中は選ぶ／外す。並べ替え中は何もしない
                        // （長押しで並べ替え中に入ったあとに届くタップもここで止まる）
                        onClick = {
                            when {
                                editing -> Unit
                                selecting -> toggleSelect(part.id)
                                else -> zoomedId = part.id
                            }
                        },
                        onToggleSelect = { toggleSelect(part.id) },
                        onDeleteClick = { pendingDelete = part },
                        // 選択中は長押しで並べ替えに入らない（選ぶのと混ざらないように）。
                        // いつもの順で絞り込んでいないときだけ（ほかの並びや絞り込みの途中で動かしても、意味が分からないため）
                        dragHandle = Modifier.longPressDraggableHandle(
                            enabled = !selecting && filter.isDefault,
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
                                arranged = inventoryOrder(repository.parts.value, repository.order.value)
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

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                when {
                    editing -> editing = false
                    !selecting -> selecting = true
                    else -> {
                        GameSession.requestStart(validSelected)
                        onGoStack()
                    }
                }
            },
            enabled = editing || !selecting || validSelected.isNotEmpty(),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Fieldbook.Survey,
                contentColor = Color.White,
                disabledContainerColor = Fieldbook.GridMajor,
                disabledContentColor = Fieldbook.Muted,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                when {
                    editing -> "完了"
                    !selecting -> "積むパーツを選ぶ"
                    validSelected.isEmpty() -> "積むパーツを選んでください"
                    else -> "選んだ ${validSelected.size} 本で積む"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    // 大きく見ている間は、背景にそのパーツを歩いたときの天気を降らせる（ui/weather/WeatherLayer）
    DisposableEffect(zoomedId) {
        BackdropFocus.partId.value = zoomedId
        onDispose { BackdropFocus.partId.value = null }
    }

    // 名前を付けている間は、大きく見る画面を閉じておく（付け終わったら、新しい名前で戻る）
    val zoomed = zoomedId?.let { id -> parts.firstOrNull { it.id == id } }
    val renaming = renamingId?.let { id -> parts.firstOrNull { it.id == id } }
    // 設定（採集画面の歯車）で AI の名前付けをオフにしていたら、名前を付ける窓に「AI に考えてもらう」を出さない
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
            onConfirm = {
                repository.remove(part.id)
                // 選択draftだけから外す。進行中のrunは保持し、次の開始時に現在のIDを解決する。
                selectedIds.remove(part.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * コレクション画面の見出し。小さな「コレクション」の下に、歩いた距離の合計（見本は含めない）を大きく出す。
 * 画面を開くたびに、メーターのように 0 から数字が回って合計まで上がる（「みんな」の見出しと同じ）。
 * 開いている間に合計が変わったら（記録した・消した）、今の値から新しい値まで動く。アニメーションを切っている人には動かさない。
 * 読み込み中は parts が空なので、数字は出さない（読み終わってから 0 から上がる）。
 */
@Composable
private fun CollectionMeter(
    totalM: Int,
    walks: Int,
    samples: Int,
    isLoaded: Boolean,
    editing: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // メーターの値。画面に出るたびに 0 から始まり（覚えておかない）、読み終わったら合計まで回って上がる
    val meter = remember { Animatable(0f) }
    LaunchedEffect(isLoaded, totalM) {
        if (!isLoaded) return@LaunchedEffect
        if (reduceMotion) {
            meter.snapTo(totalM.toFloat())
        } else {
            meter.animateTo(totalM.toFloat(), tween(durationMillis = 1_100, easing = FastOutSlowInEasing))
        }
    }
    val shownM = meter.value
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                val total = totalDistance(totalM)
                contentDescription = if (isLoaded) "コレクション。これまでに集めた道 $walks 本、合計 ${total.number} ${total.unit.spoken}。見本 $samples" else "コレクション。読み込み中"
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 見出しの字は、行ごとに字の幅だけ紙の色を敷く（方眼の線にかぶって読みにくいため。ui/common/PaperBand）。
            // 見出し全体に敷くと、数字の右のすき間など字のない所まで方眼が消えて不自然になる
            Text(
                "コレクション",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.paperBand(),
            )
            Spacer(Modifier.weight(1f))
            if (editing) {
                Text("並べ替え中", fontFamily = Fieldbook.Data, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Survey)
            }
        }
        // 1 km 未満は m、1 km からは km で出す（totalDistance）。
        // 回っている途中の値も切り捨てる（四捨五入だと、1,999 m に向かう途中の 1999.6 が一瞬「2.0 km」になって、歩いた分より多く見える）
        val shown = totalDistance(shownM.toInt())
        Row(modifier = Modifier.paperBand(), verticalAlignment = Alignment.Bottom) {
            Text(
                if (isLoaded) narrowPoint(shown.number) else AnnotatedString("—"),
                fontFamily = Fieldbook.Data,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = Fieldbook.Ink,
            )
            Text(
                if (isLoaded) shown.unit.label else DistanceUnit.M.label,
                fontFamily = Fieldbook.Data,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            )
        }
        Text(
            if (isLoaded) notes("これまでに集めた道 $walks 本", "見本 $samples") else "読み込み中…",
            style = noteStyle,
            fontSize = 13.sp,
            color = Fieldbook.Ink,
            modifier = Modifier.paperBand(),
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

/**
 * 小数点の前後を詰める。メーターの数字は等幅の字なので、そのままだと「.」も数字 1 文字分の幅を取って、前後が空いて見えるため。
 * 字体は変えずに字間だけ詰める（「.」だけ別の字体にすると、行の高さが変わって、横の単位の位置が km のときだけずれることがある）
 */
private fun narrowPoint(number: String): AnnotatedString = buildAnnotatedString {
    val i = number.indexOf('.')
    if (i < 0) {
        append(number)
    } else {
        append(number.substring(0, i))
        withStyle(SpanStyle(letterSpacing = POINT_LETTER_SPACING)) { append('.') }
        append(number.substring(i + 1))
    }
}

/** 小数点の字間。等幅の字の 1 文字分（およそ 0.6 文字の高さ）のうち、半分ほどを詰める（左右に半分ずつ詰まる） */
private val POINT_LETTER_SPACING = (-0.3).em

/**
 * カードを押して開く拡大表示。設定などと同じ枠（見出しの帯にパーツの名前、枠の下に丸い「×」）に、大きな図と注記を出す。
 * 枠の中のいちばん下の「名前を付ける」から名前を付けられる
 */
@Composable
private fun ZoomDialog(part: Part, place: PartPlace?, weather: WalkWeather?, onRename: () -> Unit, onDismiss: () -> Unit) {
    val isSample = SampleParts.byId(part.id) != null
    FramedDialog(title = part.displayName, onDismiss = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))) {
                // 図の背景に地図を敷くのは、歩いた場所が分かっていて、地図のキーがあり、図と同じ縮尺まで地図を
                // 拡大できるときだけ（とても短い散歩は、地図をいちばん拡大しても合わない）。無ければ今までどおり紙だけ
                val mapPlace = place?.takeIf { BuildConfig.MAPS_API_KEY.isNotBlank() && mapCanMatch(it, figureDp = maxWidth.value) }
                if (mapPlace != null) {
                    // 地図は図と同じ縮尺・位置（PlaceMap.kt）。道・見当・北・縮尺はその上に重ねる。
                    // 図の下に細い帯を足して、Google のロゴを道や縮尺に隠されない場所に置く
                    PlaceMap(mapPlace, belowDp = MAP_LOGO_STRIP_DP, modifier = Modifier.matchParentSize())
                }
                Column {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        // 図の範囲だけ紙の色を半分透かして重ね、地図をうすくする（下の帯の Google のロゴにはかけない）
                        if (mapPlace != null) Box(Modifier.matchParentSize().background(Fieldbook.Paper.copy(alpha = MAP_FADE)))
                        FieldbookPart(part, modifier = Modifier.matchParentSize(), sky = weather?.sky)
                    }
                    if (mapPlace != null) Spacer(Modifier.height(MAP_LOGO_STRIP_DP.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            // 歩いた長さ・日付・天気（記録のあとの窓・カードと同じ形。ui/common/WalkNote）。
            // 歩いたときの天気が分かっていれば、字でも出す（道の色だけでは分からない人もいるので）
            WalkNote(
                distanceM = part.distanceM,
                date = if (isSample) "見本" else walkDate(part.walkedAt),
                weather = weather?.sky?.label,
            )
            // 歩いたときの天気は Open-Meteo から取っているので、出どころを添える（見本の天気は作り物なので付けない）
            if (weather != null && !isSample) WeatherCredit(modifier = Modifier.padding(top = 2.dp))
            // 名前を付ける（付け直す）。閉じるのは、ほかの窓と同じく枠の下の「×」
            TextButton(onClick = onRename, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = Fieldbook.Survey, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("名前を付ける", color = Fieldbook.Survey, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DeleteDialog(part: Part, sky: Sky?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                FieldbookPart(part, modifier = Modifier.size(132.dp), sky = sky)
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
            TextButton(onClick = onConfirm) { Text("削除", color = Fieldbook.Danger, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("やめる", color = Fieldbook.Muted) }
        },
    )
}

/**
 * パーツ 1 本のカード。方眼の上に置いた白い紙片に、図と注記を載せる（選んだときは青い破線の枠）。
 * - タップ（[onClick]）：普段は大きく見る、選択中は選ぶ／外す
 * - 選択中は左上に丸を出し、押すと選ぶ／外す（[onToggleSelect]）。選ぶと青く塗られ、積む順の数字が入る（写真アプリの複数選択と同じ形）
 * @param order 選んだ順（＝積む順）。選んでいなければ null
 * @param canSelect 選べるか（5 本選んだあとの、まだ選んでいないカードは false）
 * @param editing 並べ替え中なら、揺らして左上に「×」を出す
 * @param selecting 選択中なら、左上に選ぶ丸を出し、選んだカードを青い破線で囲む
 */
@Composable
private fun PartCard(
    part: Part,
    sky: Sky?,
    order: Int?,
    isSample: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    isDragging: Boolean,
    editing: Boolean,
    selecting: Boolean,
    jiggleDirection: Float,
    reduceMotion: Boolean,
    canSelect: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onDeleteClick: () -> Unit,
    dragHandle: Modifier,
    a11yActions: List<CustomAccessibilityAction>,
) {
    // 選んだ印（丸の数字・破線）は選択中だけ出す
    val selected = order != null && selecting && !editing
    // 主役は形そのもの。分類（L字など）は出さない
    val date = if (isSample) "見本" else walkDate(part.walkedAt)
    // 並べ替え中の揺れ（±1.2°）。持ち上げているカードは揺らさない
    val angle = if (editing && !isDragging && !reduceMotion) {
        val wobble = rememberInfiniteTransition(label = "jiggle")
        wobble.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 140, easing = LinearEasing), RepeatMode.Reverse),
            label = "jiggle",
        ).value * 1.2f * jiggleDirection
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // ドラッグ中は少し持ち上げる（大きく・影・白地）。並べ替え中は小さく揺らす
            .graphicsLayer {
                val s = if (isDragging) 1.05f else 1f
                scaleX = s
                scaleY = s
                rotationZ = angle
                shadowElevation = if (isDragging) 10.dp.toPx() else 0f
                shape = CardShape
            }
            // 1 本ずつ、方眼の上に置いた白い紙片にして、隣との境目を分かりやすくする。
            // 選んだパーツは青い破線、持ち上げているパーツは真っ白
            .then(
                when {
                    isDragging -> Modifier.background(Fieldbook.Card, CardShape)
                    selected -> Modifier
                        .background(Fieldbook.Card, CardShape)
                        .dashedOutline(Fieldbook.Survey, width = 2.dp, corner = 4.dp)
                    else -> Modifier
                        .background(Fieldbook.Card, CardShape)
                        .border(1.dp, Fieldbook.CardEdge, CardShape)
                },
            )
            // 読み上げでも「何番目に積むか」が伝わるように
            .semantics {
                if (selected) stateDescription = "$order 番目に積む"
                customActions = a11yActions
            }
            .then(dragHandle)
            .clickable(
                enabled = !editing,
                // 読み上げ（TalkBack）で、タップの意味が伝わるように
                onClickLabel = when {
                    !selecting -> "大きく見る"
                    order != null -> "選択を外す"
                    else -> "選ぶ"
                },
                onClick = onClick,
            )
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Box {
            // 道の色は歩いたときの天気で変わる。読み上げでは天気を言葉で伝える
            FieldbookPart(
                part,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics {
                    if (sky != null) contentDescription = "${sky.label}の日に歩いた道"
                },
                sky = sky,
            )
            if (selecting && !editing) {
                SelectMark(
                    order = order,
                    enabled = canSelect,
                    name = part.displayName,
                    onToggle = onToggleSelect,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (editing && !isDragging) {
                // 左上の「×」：押すと削除の確認（iPhone の並べ替え中と同じ位置）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-8).dp, y = (-8).dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Fieldbook.Ink)
                        .border(BorderStroke(2.dp, Fieldbook.Paper), CircleShape)
                        .clickable(onClickLabel = "削除") { onDeleteClick() }
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
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        }
    }
}

/**
 * カードの右下のお気に入り。入っていないときは灰色の ☆、押すと黄色の ★（もう一度押すと外れる）。
 * 見た目は小さいが、押せる範囲は縦 44dp（名前と注記の 2 行の右端ぶん）。読み上げではチェックボックスとして伝える
 */
@Composable
private fun FavoriteToggle(favorite: Boolean, name: String, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = FavoriteToggleWidth, height = 44.dp)
            .toggleable(value = favorite, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = "「$name」をお気に入りに入れる"
                stateDescription = if (favorite) "お気に入り" else "お気に入りではない"
            },
    ) {
        // 星の大きさは文字の大きさの設定で変えない（押せる範囲は決まっているので、大きくするとはみ出す）
        val starSize = with(LocalDensity.current) { 20.dp.toSp() }
        Text(
            if (favorite) "★" else "☆",
            color = if (favorite) FavoriteStar else Fieldbook.Muted,
            fontSize = starSize,
            lineHeight = starSize,
        )
    }
}

/** カードの右下のお気に入りの幅（名前と注記は、この分だけ右を空ける） */
private val FavoriteToggleWidth = 34.dp

/** 拡大表示で、地図の Google のロゴを置くために図の下に足す帯の高さ [dp] */
private const val MAP_LOGO_STRIP_DP = 28f

/**
 * 見出しの右上の「選択」ボタン（選択中は「キャンセル」）。写真アプリと同じ位置・言葉。
 * 野帳に合わせて、測量の青の細い縁の小さな丸いボタンにする。
 */
@Composable
private fun SelectButton(selecting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            if (selecting) "キャンセル" else "選択",
            color = if (selecting) Color.White else Fieldbook.Survey,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(if (selecting) Fieldbook.Survey else Fieldbook.Card, shape)
                .border(1.dp, Fieldbook.Survey, shape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/**
 * カードの左上の「選ぶ」丸。選んでいないときは白い丸に細い縁、選ぶと測量の青で塗って積む順の数字を入れる。
 * 見た目は 26dp だが、押せる範囲は 44dp（指で押しやすいように）。
 * 読み上げではチェックボックスとして「選ぶ」「n 番目に積む」を伝える。
 */
@Composable
internal fun SelectMark(order: Int?, enabled: Boolean, name: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val selected = order != null
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = "「$name」を積むのに選ぶ"
                stateDescription = if (order != null) "$order 番目に積む" else if (enabled) "選んでいない" else "${GameSession.MAX_PARTS} 本選んだので選べない"
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) Fieldbook.Survey else Fieldbook.Card.copy(alpha = 0.92f))
                .border(
                    BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) Color.White else if (enabled) Fieldbook.Muted else Fieldbook.GridMajor),
                    CircleShape,
                ),
        ) {
            if (order != null) {
                // 丸の大きさは決まっている（26dp）ので、数字は文字の大きさの設定で大きくしない（大きくすると丸からはみ出す）。
                // 行の高さも同じ大きさにする（行の高さだけ大きくなると、数字が下にずれて切れる）
                val numberSize = with(LocalDensity.current) { 14.dp.toSp() }
                Text("$order", color = Color.White, fontSize = numberSize, lineHeight = numberSize, fontWeight = FontWeight.Bold, fontFamily = Fieldbook.Data)
            }
        }
    }
}

/** パーツ 1 本ぶんの紙片の形（角を少しだけ丸める） */
private val CardShape = RoundedCornerShape(4.dp)

/**
 * 選択中に出す「ランダムに選ぶ」の行。左にボタン、右に個数（1〜[GameSession.MAX_PARTS] 本）。
 * 選んだあとは、その下に一言（何本選んだか。足りなかったときはそのこと）を出し、読み上げでも知らせる
 */
@Composable
private fun RandomPickRow(enabled: Boolean, count: Int, onCountChange: (Int) -> Unit, note: String?, onPick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPick, enabled = enabled) {
                Text("ランダムに選ぶ", color = if (enabled) Fieldbook.Survey else Fieldbook.Mark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            StepButton("−", "1 本減らす", enabled = count > 1) { onCountChange(count - 1) }
            Text(
                notes("$count 本"),
                fontFamily = Fieldbook.Data,
                fontSize = 13.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.semantics { contentDescription = "ランダムに選ぶ数、$count 本" },
            )
            StepButton("+", "1 本増やす", enabled = count < GameSession.MAX_PARTS) { onCountChange(count + 1) }
        }
        if (note != null) {
            Text(
                note,
                style = noteStyle,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** 個数を増やす・減らすボタン（押せる大きさは 48dp） */
@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (enabled) Fieldbook.Survey else Fieldbook.Mark)
    }
}


/** お気に入りの ★ の色（黄色。ライトでもダークでも、紙の上で見える） */
internal val FavoriteStar = Color(0xFFE0A100)

