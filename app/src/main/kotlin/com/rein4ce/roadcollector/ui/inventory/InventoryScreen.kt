package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * B 担当：手持ちパーツ一覧。タップで選択（最大 5 本）→「積む」へ。
 * 長押しのまま動かすと並べ替え、動かさずに離すとメニュー（先頭へ・削除）。iPhone のホーム画面のアプリを動かすときと同じ。
 * 見た目は「測量野帳」（Fieldbook.kt）。
 */
@Composable
fun InventoryScreen(onGoStack: () -> Unit) {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
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
    // 長押しして動かさずに離すと、そのあとにタップ（選択の切り替え）も届いてしまうので、直後の 1 回だけ無視する
    var ignoreTapFor by remember { mutableStateOf<String?>(null) }
    var ignoreTapUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(shown) { if (draggingId == null) arranged = shown }
    fun saveArranged(list: List<Part>) {
        arranged = list
        repository.setOrder(list.map { it.id })
    }
    val sampleCount = parts.count { SampleParts.byId(it.id) != null }
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(GameSession.selected.map { it.id }) } }
    // 手持ちに実在する選択だけを数える（前回の選択に、削除済みのパーツが残っていることがあるため）
    val validSelected = selectedIds.filter { id -> parts.any { it.id == id } }
    val missingSamples = SampleParts.all.count { s -> parts.none { it.id == s.id } }
    // 削除の確認ダイアログを出しているパーツ（null なら出していない）
    var pendingDelete by remember { mutableStateOf<Part?>(null) }
    // 長押しのメニューを開いているパーツの id（null なら開いていない）
    var menuFor by remember { mutableStateOf<String?>(null) }

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
                // 読み込み中は parts が空なので、件数は出さない
                if (isLoaded) "採集 ${parts.size - sampleCount} ・ 見本 $sampleCount" else "読み込み中…",
                fontFamily = Fieldbook.Data,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "タップで選ぶ（${GameSession.MAX_PARTS} 本まで）・長押しで動かす／メニュー",
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.weight(1f),
            )
            // 消した見本だけを足し戻す。採集したパーツと選択には触らない。
            // 読み込み中は出さない（parts が空なので全部の見本が「消えた」ように見え、押すとわざと消した見本まで戻るため）
            if (isLoaded && missingSamples > 0) {
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
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
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
                        isFirst = index == 0,
                        isDragging = isDragging,
                        menuOpen = menuFor == part.id,
                        onMenuDismiss = { menuFor = null },
                        onMoveToTop = {
                            menuFor = null
                            moveTo(0)
                        },
                        onDelete = {
                            menuFor = null
                            pendingDelete = part
                        },
                        onClick = {
                            if (ignoreTapFor == part.id && SystemClock.uptimeMillis() < ignoreTapUntil) {
                                ignoreTapFor = null
                                return@PartCard
                            }
                            if (part.id in selectedIds) {
                                selectedIds.remove(part.id)
                            } else if (validSelected.size < GameSession.MAX_PARTS) {
                                selectedIds.add(part.id)
                            }
                        },
                        dragHandle = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                draggingId = part.id
                                dragStartIndex = arranged.indexOfFirst { it.id == part.id }
                                menuFor = null
                                ignoreTapFor = part.id
                                ignoreTapUntil = SystemClock.uptimeMillis() + 1_500L
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                val now = arranged.indexOfFirst { it.id == part.id }
                                draggingId = null
                                if (now != dragStartIndex) {
                                    saveArranged(arranged) // 動かした → その並びを保存（このあとタップは届かない）
                                    ignoreTapFor = null
                                } else {
                                    menuFor = part.id // 動かさずに離した → メニュー（続けて届くタップは無視する）
                                }
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
                val chosen = validSelected.mapNotNull { id -> parts.firstOrNull { it.id == id } }
                GameSession.selected = chosen
                onGoStack()
            },
            enabled = validSelected.isNotEmpty(),
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
                if (validSelected.isEmpty()) "積むパーツを選んでください" else "選んだ ${validSelected.size} 本で積む",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
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

/** 長押しメニューの 1 行：アイコンと字を同じ色で。押せないときは薄く */
@Composable
private fun FieldbookMenuItem(text: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = color,
            leadingIconColor = color,
            disabledTextColor = Fieldbook.Muted.copy(alpha = 0.45f),
            disabledLeadingIconColor = Fieldbook.Muted.copy(alpha = 0.45f),
        ),
    )
}

/**
 * パーツ 1 本のカード。枠は付けず、方眼の上に図と注記だけを置く（選んだときだけ白地と破線の枠）。
 * @param order 選んだ順（＝積む順）。選んでいなければ null
 */
@Composable
private fun PartCard(
    part: Part,
    order: Int?,
    isSample: Boolean,
    isFirst: Boolean,
    isDragging: Boolean,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onMoveToTop: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    dragHandle: Modifier,
    a11yActions: List<CustomAccessibilityAction>,
) {
    val selected = order != null
    // 主役は形そのもの。分類（L字など）は出さない
    val date = if (isSample) "見本" else formatDate(part.walkedAt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // ドラッグ中は少し持ち上げる（大きく・影・白地）
            .graphicsLayer {
                val s = if (isDragging) 1.05f else 1f
                scaleX = s
                scaleY = s
                shadowElevation = if (isDragging) 10.dp.toPx() else 0f
            }
            .then(
                when {
                    isDragging -> Modifier.background(Color.White)
                    selected -> Modifier.background(Fieldbook.SelectedPaper).dashedOutline(Fieldbook.Survey)
                    else -> Modifier
                },
            )
            // 読み上げでも「何番目に積むか」が伝わるように（①② は見た目だけなので、下で読み上げから外している）
            .semantics {
                if (order != null) stateDescription = "$order 番目に積む"
                customActions = a11yActions
            }
            .then(dragHandle)
            .clickable(
                // 読み上げ（TalkBack）で、タップの意味が伝わるように
                onClickLabel = if (selected) "選択を外す" else "選択",
                onClick = onClick,
            )
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Box {
            FieldbookPart(part, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            // 長押しのメニュー。野帳の紙と細い枠線で、ほかの画面とそろえる
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onMenuDismiss,
                shape = RoundedCornerShape(6.dp),
                containerColor = Fieldbook.Paper,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Fieldbook.Mark),
            ) {
                FieldbookMenuItem("先頭へ", Icons.Filled.KeyboardArrowUp, Fieldbook.Ink, enabled = !isFirst, onClick = onMoveToTop)
                HorizontalDivider(color = Fieldbook.GridMajor, modifier = Modifier.padding(horizontal = 10.dp))
                FieldbookMenuItem("削除…", Icons.Filled.Delete, Fieldbook.Danger, onClick = onDelete)
            }
            if (order != null) {
                Text(
                    Fieldbook.orderMark(order),
                    color = Fieldbook.Survey,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp).clearAndSetSemantics {},
                )
            }
        }
        Text(
            part.displayName,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Fieldbook.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
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
