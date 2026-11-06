package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.ui.common.PartThumbnail
import com.rein4ce.roadcollector.ui.theme.Orange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * B 担当：手持ちパーツ一覧。タップで選択（最大 5 個）→「積む」へ。
 */
@Composable
fun InventoryScreen(onGoStack: () -> Unit) {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(GameSession.selected.map { it.id }) } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("手持ちパーツ ${parts.size} 個", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { repository.resetToSamples(); selectedIds.clear() }) { Text("サンプルに戻す") }
        }
        Text(
            "タップで選択（最大 ${GameSession.MAX_PARTS} 個）",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(parts, key = { it.id }) { part ->
                val selected = part.id in selectedIds
                PartCard(
                    part = part,
                    selected = selected,
                    onClick = {
                        if (selected) {
                            selectedIds.remove(part.id)
                        } else if (selectedIds.size < GameSession.MAX_PARTS) {
                            selectedIds.add(part.id)
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val chosen = selectedIds.mapNotNull { id -> parts.firstOrNull { it.id == id } }
                GameSession.selected = chosen
                onGoStack()
            },
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("選んだ ${selectedIds.size} 個で積む")
        }
    }
}

@Composable
private fun PartCard(part: Part, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Modifier.border(3.dp, Orange, RoundedCornerShape(12.dp)) else Modifier
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(border)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            PartThumbnail(part, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Text(part.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                "${part.kind.label} ・ ${part.distanceM} m ・ ${formatDate(part.walkedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5B6675),
            )
        }
    }
}

private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "サンプル" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))
