package com.rein4ce.roadcollector.ui.collect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleTracks
import com.rein4ce.roadcollector.ui.common.PartThumbnail
import com.rein4ce.roadcollector.ui.theme.Orange
import kotlin.math.max

/**
 * A 担当：GPS 採集画面。
 * 地図は API キーがあれば Google Maps、無ければ軌跡だけを Canvas に描く（キー無しでもデモが動く）。
 */
@Composable
fun CollectScreen(
    onGoInventory: () -> Unit,
    vm: CollectViewModel = viewModel(),
) {
    val context = LocalContext.current
    val points by vm.points.collectAsStateWithLifecycle()
    val isRecording by vm.isRecording.collectAsStateWithLifecycle()
    val distance = remember(points) { vm.distanceM(points) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) vm.startRecording()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 状態カード
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isRecording) Color(0xFFD64545) else Color(0xFFB0B8C4)),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            vm.isDemoPlaying -> "デモ散歩を再生中"
                            isRecording -> "記録中（画面を閉じても続きます）"
                            else -> "停止中"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${points.size} 点 ・ 約 ${distance.toInt()} m",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 地図 or 軌跡キャンバス
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEFF2F5)),
        ) {
            if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
                TrackMap(points)
            } else {
                TrackCanvas(points, modifier = Modifier.fillMaxSize())
                Text(
                    text = "MAPS_API_KEY 未設定：軌跡のみ表示",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 操作
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRecording) {
                Button(onClick = { vm.stopAndConvert() }, modifier = Modifier.weight(1f)) {
                    Text("停止してパーツ化")
                }
            } else {
                Button(
                    onClick = {
                        if (LocationRecordingService.hasLocationPermission(context)) {
                            vm.startRecording()
                        } else {
                            permissionLauncher.launch(LocationRecordingService.requiredPermissions())
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("記録開始")
                }
                DemoMenuButton(enabled = !vm.isDemoPlaying, onPick = { vm.playDemo(it) })
            }
        }
    }

    // 結果ダイアログ
    when (val o = vm.outcome) {
        is CollectViewModel.Outcome.Created -> AlertDialog(
            onDismissRequest = { vm.dismissOutcome() },
            title = { Text("パーツができました") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    PartThumbnail(o.part, modifier = Modifier.size(140.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(o.part.displayName)
                    Text(
                        "GPS ${o.stats.rawPoints} 点 → フィルタ後 ${o.stats.filteredPoints} 点 → 頂点 ${o.stats.simplifiedPoints} 個",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissOutcome(); onGoInventory() }) { Text("手持ちを見る") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissOutcome() }) { Text("閉じる") }
            },
        )
        is CollectViewModel.Outcome.Rejected -> AlertDialog(
            onDismissRequest = { vm.dismissOutcome() },
            title = { Text("パーツになりませんでした") },
            text = { Text(o.reason) },
            confirmButton = { TextButton(onClick = { vm.dismissOutcome() }) { Text("閉じる") } },
        )
        null -> Unit
    }
}

@Composable
private fun DemoMenuButton(enabled: Boolean, onPick: (SampleTracks.SampleTrack) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) { Text("デモ散歩") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SampleTracks.all.forEach { track ->
                DropdownMenuItem(
                    text = { Text("${track.expectedKind.label}：${track.name}") },
                    onClick = { open = false; onPick(track) },
                )
            }
        }
    }
}

/** Google Maps に軌跡を描く（API キーがある場合） */
@Composable
private fun TrackMap(points: List<TrackPoint>) {
    val start = points.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: LatLng(35.7496, 139.8049)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, 16f)
    }
    val last = points.lastOrNull()
    LaunchedEffect(last) {
        if (last != null) cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(last.lat, last.lng)))
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
    ) {
        if (points.size >= 2) {
            Polyline(points = points.map { LatLng(it.lat, it.lng) }, color = Orange, width = 12f)
        }
    }
}

/** API キーが無いときの代替：軌跡をメートル座標にして枠に収めて描く */
@Composable
fun TrackCanvas(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val xy = Projection.toMeters(points)
        val minX = xy.minOf { it.x }
        val maxX = xy.maxOf { it.x }
        val minY = xy.minOf { it.y }
        val maxY = xy.maxOf { it.y }
        val span = max(maxX - minX, maxY - minY).coerceAtLeast(20.0)
        val pad = 24f
        val scale = ((size.minDimension - pad * 2) / span).toFloat()
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        val path = Path()
        xy.forEachIndexed { i, p ->
            val sx = size.width / 2 + ((p.x - cx) * scale).toFloat()
            val sy = size.height / 2 - ((p.y - cy) * scale).toFloat()
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }
        drawPath(path, color = Orange, style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val first = xy.first()
        drawCircle(
            color = Color(0xFF2A9D8F),
            radius = 9f,
            center = Offset(size.width / 2 + ((first.x - cx) * scale).toFloat(), size.height / 2 - ((first.y - cy) * scale).toFloat()),
        )
    }
}
