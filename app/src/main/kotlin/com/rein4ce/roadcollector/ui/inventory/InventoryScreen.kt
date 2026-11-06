package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.GameSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * B 担当：手持ちパーツ一覧。タップで選択（最大 5 本）→「積む」へ。長押しで削除。
 * 見た目は「測量野帳」（Fieldbook.kt）。
 */
@Composable
fun InventoryScreen(onGoStack: () -> Unit) {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    // 並びは「採集したパーツを新しい順 → 見本を決まった順」。保存の順番には頼らない
    val shown = remember(parts) {
        val (samples, collected) = parts.partition { SampleParts.byId(it.id) != null }
        collected.sortedByDescending { it.walkedAt } +
            samples.sortedBy { s -> SampleParts.all.indexOfFirst { it.id == s.id } }
    }
    val sampleCount = parts.count { SampleParts.byId(it.id) != null }
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(GameSession.selected.map { it.id }) } }
    // 手持ちに実在する選択だけを数える（前回の選択に、削除済みのパーツが残っていることがあるため）
    val validSelected = selectedIds.filter { id -> parts.any { it.id == id } }
    val missingSamples = SampleParts.all.count { s -> parts.none { it.id == s.id } }
    // 削除の確認ダイアログを出しているパーツ（null なら出していない）
    var pendingDelete by remember { mutableStateOf<Part?>(null) }

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
                "採集 ${parts.size - sampleCount} ・ 見本 $sampleCount",
                fontFamily = Fieldbook.Data,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "タップで選ぶ（${GameSession.MAX_PARTS} 本まで）・長押しで消す",
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.weight(1f),
            )
            // 消した見本だけを足し戻す。採集したパーツと選択には触らない
            if (missingSamples > 0) {
                TextButton(onClick = { repository.restoreMissingSamples() }) {
                    Text("見本を戻す", color = Fieldbook.Survey, fontSize = 13.sp)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { part ->
                val order = validSelected.indexOf(part.id).takeIf { it >= 0 }?.plus(1)
                PartCard(
                    part = part,
                    order = order,
                    isSample = SampleParts.byId(part.id) != null,
                    onClick = {
                        if (part.id in selectedIds) {
                            selectedIds.remove(part.id)
                        } else if (validSelected.size < GameSession.MAX_PARTS) {
                            selectedIds.add(part.id)
                        }
                    },
                    onLongClick = { pendingDelete = part },
                )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("パーツを削除") },
        text = {
            Text(
                "「${part.displayName}」を削除します。" +
                    if (isSample) "見本は「見本を戻す」でいつでも戻せます。"
                    else "採集したパーツは元に戻せません。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/**
 * パーツ 1 本のカード。枠は付けず、方眼の上に図と注記だけを置く（選んだときだけ白地と破線の枠）。
 * @param order 選んだ順（＝積む順）。選んでいなければ null
 */
@Composable
private fun PartCard(part: Part, order: Int?, isSample: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val selected = order != null
    // 主役は形そのもの。分類（L字など）は出さない
    val date = if (isSample) "見本" else formatDate(part.walkedAt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(Fieldbook.SelectedPaper).dashedOutline(Fieldbook.Survey) else Modifier)
            .combinedClickable(
                // 読み上げ（TalkBack）で、タップと長押しの意味が伝わるように
                onClickLabel = if (selected) "選択を外す" else "選択",
                onLongClickLabel = "削除",
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Box {
            FieldbookPart(part, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            if (order != null) {
                Text(
                    Fieldbook.orderMark(order),
                    color = Fieldbook.Survey,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
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

private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "見本" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))
