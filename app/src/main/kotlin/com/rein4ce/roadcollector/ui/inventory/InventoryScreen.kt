package com.rein4ce.roadcollector.ui.inventory

import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.GameSession
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * B 担当：手持ちパーツ一覧。タップで選択（最大 5 本）→「積む」へ。
 *
 * 長押しで「並べ替え中」になる（iPhone のホーム画面の「アプリを並べ替え中」と同じ）：
 * - カードが小さく揺れ、左上の「×」で削除（確認あり）
 * - 長押しのまま動かすと並べ替え（素早く払えばスクロール）。長押しで並べ替え中に入るときも、そのまま動かせる
 * - タップしても選択は変わらない。「完了」か戻るで普段の状態に戻る
 *
 * 名前の右の虫めがねで、パーツを大きく見られる（普段も並べ替え中も）。
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
    val missingSamples = SampleParts.all.count { s -> parts.none { it.id == s.id } }
    // 削除の確認ダイアログを出しているパーツ（null なら出していない）
    var pendingDelete by remember { mutableStateOf<Part?>(null) }
    // 虫めがねで大きく見ているパーツ（null なら見ていない）
    var zoomed by remember { mutableStateOf<Part?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fieldbookGrid()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("手持ち", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    editing -> "並べ替え中"
                    // 読み込み中は parts が空なので、件数は出さない
                    !isLoaded -> "読み込み中…"
                    else -> "採集 ${parts.size - sampleCount} ・ 見本 $sampleCount"
                },
                fontFamily = Fieldbook.Data,
                fontSize = 12.sp,
                fontWeight = if (editing) FontWeight.Bold else FontWeight.Normal,
                color = if (editing) Fieldbook.Survey else Fieldbook.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (editing) {
                    "長押しのまま動かして並べ替え ・ × で削除"
                } else {
                    "タップで選ぶ（${GameSession.MAX_PARTS} 本まで）・長押しで並べ替え／削除"
                },
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.weight(1f),
            )
            // 消した見本だけを足し戻す。採集したパーツと選択には触らない。
            // 読み込み中は出さない（parts が空なので全部の見本が「消えた」ように見え、押すとわざと消した見本まで戻るため）
            if (!editing && isLoaded && missingSamples > 0) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        // 隣どうしで逆向きに揺らすと、iPhone のようにばらばらに揺れて見える
                        jiggleDirection = if (index % 2 == 0) 1f else -1f,
                        reduceMotion = reduceMotion,
                        onClick = {
                            // 並べ替え中のタップでは選択を変えない（長押しで並べ替え中に入ったあとに届くタップもここで止まる）
                            if (editing) return@PartCard
                            if (part.id in selectedIds) {
                                selectedIds.remove(part.id)
                            } else if (validSelected.size < GameSession.MAX_PARTS) {
                                selectedIds.add(part.id)
                            }
                        },
                        onDeleteClick = { pendingDelete = part },
                        onZoom = { zoomed = part },
                        dragHandle = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                draggingId = part.id
                                dragStartIndex = arranged.indexOfFirst { it.id == part.id }
                                editing = true // 長押しで並べ替え中に入る（そのまま動かせば並べ替え）
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                val now = arranged.indexOfFirst { it.id == part.id }
                                draggingId = null
                                if (now != dragStartIndex) saveArranged(arranged)
                            },
                        ),
                        // ドラッグできない読み上げ（TalkBack）の人のための操作
                        a11yActions = listOfNotNull(
                            CustomAccessibilityAction("先頭へ") { moveTo(0); true }.takeIf { index > 0 },
                            CustomAccessibilityAction("前へ") { moveTo(index - 1); true }.takeIf { index > 0 },
                            CustomAccessibilityAction("後ろへ") { moveTo(index + 1); true }.takeIf { index < arranged.size - 1 },
                            CustomAccessibilityAction("削除") { pendingDelete = part; true },
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (editing) {
                    editing = false
                } else {
                    val chosen = validSelected.mapNotNull { id -> parts.firstOrNull { it.id == id } }
                    GameSession.selected = chosen
                    onGoStack()
                }
            },
            enabled = editing || validSelected.isNotEmpty(),
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
                    validSelected.isEmpty() -> "積むパーツを選んでください"
                    else -> "選んだ ${validSelected.size} 本で積む"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    zoomed?.let { part -> ZoomDialog(part, onDismiss = { zoomed = null }) }

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

/** 虫めがねで開く拡大表示。削除の確認と同じ野帳の紙に、大きな図と注記を出す */
@Composable
private fun ZoomDialog(part: Part, onDismiss: () -> Unit) {
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
                FieldbookPart(part, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
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
 * パーツ 1 本のカード。枠は付けず、方眼の上に図と注記だけを置く（選んだときだけ白地と破線の枠）。
 * @param order 選んだ順（＝積む順）。選んでいなければ null
 * @param editing 並べ替え中なら、揺らして左上に「×」を出す（選んだ順の ①② は出さない）
 */
@Composable
private fun PartCard(
    part: Part,
    order: Int?,
    isSample: Boolean,
    isDragging: Boolean,
    editing: Boolean,
    jiggleDirection: Float,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onZoom: () -> Unit,
    dragHandle: Modifier,
    a11yActions: List<CustomAccessibilityAction>,
) {
    val selected = order != null
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
            }
            .then(
                when {
                    isDragging -> Modifier.background(Color.White)
                    selected && !editing -> Modifier.background(Fieldbook.SelectedPaper).dashedOutline(Fieldbook.Survey)
                    else -> Modifier
                },
            )
            // 読み上げでも「何番目に積むか」が伝わるように（①② は見た目だけなので、下で読み上げから外している）
            .semantics {
                if (order != null && !editing) stateDescription = "$order 番目に積む"
                customActions = a11yActions
            }
            .then(dragHandle)
            .clickable(
                enabled = !editing,
                // 読み上げ（TalkBack）で、タップの意味が伝わるように
                onClickLabel = if (selected) "選択を外す" else "選択",
                onClick = onClick,
            )
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Box {
            FieldbookPart(part, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            if (order != null && !editing) {
                Text(
                    Fieldbook.orderMark(order),
                    color = Fieldbook.Survey,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp).clearAndSetSemantics {},
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                part.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            // 虫めがね：押すと大きく見られる（タップの「選ぶ」とは別）
            IconButton(onClick = onZoom, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "「${part.displayName}」を大きく見る",
                    tint = Fieldbook.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            "L=${part.distanceM} m ・ 頂点 ${part.points.size} ・ $date",
            fontFamily = Fieldbook.Data,
            fontSize = 10.5.sp,
            color = Fieldbook.Muted,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** 採集日。見本はカードの側で「見本」と出すので、ここには来ない。日時の無いパーツ（古いデータなど）は「—」 */
private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))
