package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.ui.common.PartThumbnail
import com.rein4ce.roadcollector.ui.theme.Orange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * B 担当：手持ちパーツ一覧。タップで選択（最大 5 個）→「積む」へ。長押しで削除。
 */
@Composable
fun InventoryScreen(onGoStack: () -> Unit) {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    // 並びは「採集したパーツを新しい順 → サンプルを決まった順」。保存の順番には頼らない
    val shown = remember(parts) {
        val (samples, collected) = parts.partition { SampleParts.byId(it.id) != null }
        collected.sortedByDescending { it.walkedAt } +
            samples.sortedBy { s -> SampleParts.all.indexOfFirst { it.id == s.id } }
    }
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(GameSession.selected.map { it.id }) } }
    // 手持ちに実在する選択だけを数える（前回の選択に、削除済みのパーツが残っていることがあるため）
    val validSelected = selectedIds.filter { id -> parts.any { it.id == id } }
    val missingSamples = SampleParts.all.count { s -> parts.none { it.id == s.id } }
    // 削除の確認ダイアログを出しているパーツ（null なら出していない）
    var pendingDelete by remember { mutableStateOf<Part?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("手持ちパーツ ${parts.size} 個", style = MaterialTheme.typography.titleLarge)
            // 消したサンプルだけを足し戻す。採集したパーツと選択には触らない
            if (missingSamples > 0) {
                TextButton(onClick = { repository.restoreMissingSamples() }) { Text("サンプルを戻す") }
            }
        }
        Text(
            "タップで選択（最大 ${GameSession.MAX_PARTS} 個）・長押しで削除",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it.id }) { part ->
                val selected = part.id in selectedIds
                PartCard(
                    part = part,
                    selected = selected,
                    onClick = {
                        if (selected) {
                            selectedIds.remove(part.id)
                        } else if (validSelected.size < GameSession.MAX_PARTS) {
                            selectedIds.add(part.id)
                        }
                    },
                    onLongClick = { pendingDelete = part },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val chosen = validSelected.mapNotNull { id -> parts.firstOrNull { it.id == id } }
                GameSession.selected = chosen
                onGoStack()
            },
            enabled = validSelected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("選んだ ${validSelected.size} 個で積む")
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
                    if (isSample) "サンプルは「サンプルを戻す」でいつでも戻せます。"
                    else "採集したパーツは元に戻せません。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

@Composable
private fun PartCard(part: Part, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val border = if (selected) Modifier.border(3.dp, Orange, RoundedCornerShape(12.dp)) else Modifier
    // 主役は形そのもの。分類（L字など）は出さない。
    // 名前がないパーツは見出しが「705 m の散歩」になるので、下の行に距離を重ねて出さない
    val details = listOfNotNull(
        "${part.distanceM} m".takeIf { part.hasName },
        formatDate(part.walkedAt),
    ).joinToString(" ・ ")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(border)
            .combinedClickable(
                // 読み上げ（TalkBack）で、タップと長押しの意味が伝わるように
                onClickLabel = if (selected) "選択を外す" else "選択",
                onLongClickLabel = "削除",
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            PartThumbnail(part, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Text(part.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5B6675),
            )
        }
    }
}

private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "サンプル" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))
