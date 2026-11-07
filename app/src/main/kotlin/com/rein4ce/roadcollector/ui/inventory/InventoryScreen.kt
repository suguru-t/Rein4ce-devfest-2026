package com.rein4ce.roadcollector.ui.inventory

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.PartPlace
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.GameSession
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // 並びは InventoryOrder.kt のルール。保存の順番には頼らない
    val shown = remember(parts, savedOrder) { inventoryOrder(parts, savedOrder) }
    // 画面に出している並び。ドラッグ中はこれだけを動かし、指を離したときに保存する
    var arranged by remember { mutableStateOf(shown) }
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
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(GameSession.selected.map { it.id }) } }
    // 手持ちに実在する選択だけを数える（前回の選択に、削除済みのパーツが残っていることがあるため）
    val validSelected = selectedIds.filter { id -> parts.any { it.id == id } }
    // 選択中か（積むパーツを選んでいる）。前回の選択は残してあり、「選択」を押すとそのまま出る。
    // 「キャンセル」か戻るで、選択を消して普段に戻る（写真アプリと同じ）。
    // ただし消すのはこの画面の選択（selectedIds）だけ。積む画面に渡した選択（GameSession.selected）には触らない
    // （触ると、積んでいる途中のゲームが最初からやり直しになる）。なので、キャンセルしたあと別のタブから戻ると、
    // selectedIds が GameSession.selected から作り直されて、「選択」で積む画面に渡した選択がまた出る
    var selecting by remember { mutableStateOf(false) }
    fun cancelSelecting() {
        selectedIds.clear()
        selecting = false
    }
    BackHandler(enabled = selecting) { cancelSelecting() }
    fun toggleSelect(id: String) {
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
        // 見出し：歩いた距離の合計をメーターのように大きく出す（増えたり減ったりすると、数字が回って新しい値まで動く）。
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
                    editing -> "長押しのまま動かして並べ替え ・ × で削除"
                    selecting -> "タップで積むパーツを選ぶ（${GameSession.MAX_PARTS} 本まで）"
                    else -> "タップで大きく見る ・ 長押しで並べ替え"
                },
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.weight(1f),
            )
            // 消した見本だけを足し戻す。採集したパーツと選択には触らない。
            // 読み込み中は出さない（parts が空なので全部の見本が「消えた」ように見え、押すとわざと消した見本まで戻るため）
            if (!editing && !selecting && isLoaded && missingSamples > 0) {
                TextButton(onClick = { repository.restoreMissingSamples() }) {
                    Text("見本を戻す", color = Fieldbook.Survey, fontSize = 13.sp)
                }
            }
        }

        val gridState = rememberLazyGridState()
        val haptics = LocalHapticFeedback.current
        val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
            // ドラッグ中：指の下に来たカードと場所を入れ替える（ほかのカードはよけるように動く）
            arranged = arranged.moved(from.index, to.index)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.weight(1f),
            // 並べ替え中の「×」がカードの左上からはみ出すので、上と左右に少し余白
            contentPadding = PaddingValues(start = 6.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(arranged, key = { it.id }) { part ->
                // ReorderableItem が、並びが変わったときにカードを滑らかに動かす（animateItem）
                ReorderableItem(reorderState, key = part.id) { isDragging ->
                    val order = validSelected.indexOf(part.id).takeIf { it >= 0 }?.plus(1)
                    val index = arranged.indexOfFirst { it.id == part.id }
                    fun moveTo(to: Int) = saveArranged(arranged.moved(index, to))
                    PartCard(
                        part = part,
                        order = order,
                        isSample = SampleParts.byId(part.id) != null,
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
                        // 選択中は長押しで並べ替えに入らない（選ぶのと混ざらないように）
                        dragHandle = Modifier.longPressDraggableHandle(
                            enabled = !selecting,
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
                            CustomAccessibilityAction("先頭へ") { moveTo(0); true }.takeIf { index > 0 },
                            CustomAccessibilityAction("前へ") { moveTo(index - 1); true }.takeIf { index > 0 },
                            CustomAccessibilityAction("後ろへ") { moveTo(index + 1); true }.takeIf { index < arranged.size - 1 },
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
                        val chosen = validSelected.mapNotNull { id -> parts.firstOrNull { it.id == id } }
                        GameSession.selected = chosen
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

    // 名前を付けている間は、大きく見る画面を閉じておく（付け終わったら、新しい名前で戻る）
    val zoomed = zoomedId?.let { id -> parts.firstOrNull { it.id == id } }
    val renaming = renamingId?.let { id -> parts.firstOrNull { it.id == id } }
    if (renaming != null) {
        RenameDialog(
            part = renaming,
            onSave = { name ->
                repository.rename(renaming.id, name)
                renamingId = null
            },
            onDismiss = { renamingId = null },
        )
    } else if (zoomed != null) {
        ZoomDialog(
            zoomed,
            place = repository.placeOf(zoomed.id, places),
            onRename = { renamingId = zoomed.id },
            onDismiss = { zoomedId = null },
        )
    }

    pendingDelete?.let { part ->
        DeleteDialog(
            part = part,
            onConfirm = {
                repository.remove(part.id)
                // この画面の選択からは外す。積む画面に渡した選択（GameSession.selected）には触らない。
                // 触ると、積んでいる途中のゲームが最初からやり直しになるため
                selectedIds.remove(part.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * コレクション画面の見出し。小さな「コレクション」の下に、歩いた距離の合計（見本は含めない）を大きく出す。
 * 合計が変わると、メーターのように数字が回って新しい値まで動く（アニメーションを切っている人には動かさない）。
 * 読み込み中は parts が空なので、数字は出さない。
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
    // メーターの値。合計が変わったら、数字が回って新しい値まで動く
    val shownM by animateFloatAsState(
        targetValue = totalM.toFloat(),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
        label = "meter",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (isLoaded) "コレクション。これまでに集めた道 $walks 本、合計 $totalM メートル。見本 $samples" else "コレクション。読み込み中"
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "コレクション",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Fieldbook.Muted,
            )
            Spacer(Modifier.weight(1f))
            if (editing) {
                Text("並べ替え中", fontFamily = Fieldbook.Data, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Survey)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (isLoaded) "%,d".format(shownM.roundToInt()) else "—",
                fontFamily = Fieldbook.Data,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = Fieldbook.Ink,
            )
            Text(
                "m",
                fontFamily = Fieldbook.Data,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            )
        }
        Text(
            if (isLoaded) "これまでに集めた道 $walks 本 ・ 見本 $samples" else "読み込み中…",
            fontSize = 13.sp,
            color = Fieldbook.Ink,
        )
    }
}

/** 虫めがねで開く拡大表示。削除の確認と同じ野帳の紙に、大きな図と注記を出す。ここから名前を付けられる */
@Composable
private fun ZoomDialog(part: Part, place: PartPlace?, onRename: () -> Unit, onDismiss: () -> Unit) {
    val isSample = SampleParts.byId(part.id) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = Fieldbook.Paper,
        titleContentColor = Fieldbook.Ink,
        textContentColor = Fieldbook.Ink,
        title = { Text(part.displayName, fontWeight = FontWeight.Bold) },
        text = {
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
                            FieldbookPart(part, modifier = Modifier.matchParentSize())
                        }
                        if (mapPlace != null) Spacer(Modifier.height(MAP_LOGO_STRIP_DP.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "L=${part.distanceM} m ・ 頂点 ${part.points.size} ・ " + if (isSample) "見本" else formatDate(part.walkedAt),
                    fontFamily = Fieldbook.Data,
                    fontSize = 12.sp,
                    color = Fieldbook.Muted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる", color = Fieldbook.Survey, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onRename) { Text("名前を付ける", color = Fieldbook.Survey) }
        },
    )
}

@Composable
private fun DeleteDialog(part: Part, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                FieldbookPart(part, modifier = Modifier.size(132.dp))
                Text(part.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isSample) "見本は「見本を戻す」でいつでも戻せます。" else "採集したパーツは、削除すると元に戻せません。",
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
    order: Int?,
    isSample: Boolean,
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
    val date = if (isSample) "見本" else formatDate(part.walkedAt)
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
                    isDragging -> Modifier.background(Color.White, CardShape)
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
            FieldbookPart(part, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
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
        BasicText(
            part.displayName,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
        )
        Text(
            "L=${part.distanceM} m・頂点 ${part.points.size}・$date",
            fontFamily = Fieldbook.Data,
            fontSize = 10.5.sp,
            color = Fieldbook.Muted,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

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
private fun SelectMark(order: Int?, enabled: Boolean, name: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val selected = order != null
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = "「$name」を積むのに選ぶ"
                stateDescription = if (order != null) "$order 番目に積む" else if (enabled) "選んでいない" else "5 本選んだので選べない"
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) Fieldbook.Survey else Color.White.copy(alpha = 0.92f))
                .border(
                    BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) Color.White else if (enabled) Fieldbook.Muted else Fieldbook.GridMajor),
                    CircleShape,
                ),
        ) {
            if (order != null) {
                Text("$order", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Fieldbook.Data)
            }
        }
    }
}

/** パーツ 1 本ぶんの紙片の形（角を少しだけ丸める） */
private val CardShape = RoundedCornerShape(4.dp)

/** 採集日。見本はカードの側で「見本」と出すので、ここには来ない。日時の無いパーツ（古いデータなど）は「—」 */
private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))
