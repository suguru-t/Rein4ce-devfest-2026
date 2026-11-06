package com.rein4ce.roadcollector.ui.collect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleTracks
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.FieldbookPart
import com.rein4ce.roadcollector.ui.inventory.niceLength
import kotlin.math.max

/**
 * A 担当：GPS 採集画面。見た目は手持ち画面と同じ「測量野帳」（Fieldbook.kt）。
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

    // GPX / JSON ファイルを選ぶ（MIME は端末ごとにまちまちなので絞らない）
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.playFile(uri) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) vm.startRecording()
    }

    // 方眼の地はアプリ全体（AppNav の Scaffold）で 1 枚敷いているので、ここでは描かない（二重になって端で線がずれる）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        // 見出し：左に画面名、右に測量の数値（手持ちの「採集 n ・ 見本 n」と同じ位置・字）
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("採集", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                "L=${distance.toInt()} m ・ ${points.size} 点",
                fontFamily = Fieldbook.Data,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // 状態行：記録中だけ赤い点。文は短く、注記の字で
        val playing = vm.playingLabel
        val status = when {
            playing != null -> StatusLine("${playing}を再生中", Fieldbook.Survey)
            isRecording -> StatusLine("記録中（画面を閉じても続きます）", RecordingRed)
            else -> StatusLine("停止中・「記録開始」で歩き始める", Fieldbook.Mark)
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(status.color),
            )
            Spacer(Modifier.width(8.dp))
            Text(status.text, fontSize = 12.sp, color = if (isRecording) Fieldbook.Ink else Fieldbook.Muted)
        }

        // 図：地図 or 軌跡。四隅の見当を地図の外側に描く（地図は Android の View なので、上には描けない）
        val hasMap = BuildConfig.MAPS_API_KEY.isNotBlank()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fieldbookCorners()
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    // 枠線は地図の端が分かるように地図あり版だけ。地図なし版は手持ちのカードと同じく見当だけ
                    .then(if (hasMap) Modifier.border(1.dp, Fieldbook.GridMajor, RoundedCornerShape(4.dp)) else Modifier),
            ) {
                if (hasMap) {
                    TrackMap(points)
                } else {
                    TrackCanvas(points, modifier = Modifier.fillMaxSize())
                    Text(
                        "MAPS_API_KEY 未設定：軌跡のみ",
                        fontFamily = Fieldbook.Data,
                        fontSize = 10.sp,
                        color = Fieldbook.Muted,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 操作：手持ちの「積む」ボタンと同じ青・角・高さ
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRecording) {
                SurveyButton(
                    text = "停止してパーツ化",
                    onClick = { vm.stopAndConvert() },
                    modifier = Modifier.weight(1f),
                )
            } else {
                SurveyButton(
                    text = "記録開始",
                    onClick = {
                        if (LocationRecordingService.hasLocationPermission(context)) {
                            vm.startRecording()
                        } else {
                            permissionLauncher.launch(LocationRecordingService.requiredPermissions())
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                DemoMenuButton(
                    enabled = !vm.isDemoPlaying,
                    onPick = { vm.playDemo(it) },
                    onPickFile = { fileLauncher.launch(arrayOf("*/*")) },
                )
            }
        }
    }

    // 結果ダイアログ：手持ちと同じ野帳の図（見当・北・縮尺）と注記
    when (val o = vm.outcome) {
        is CollectViewModel.Outcome.Created -> AlertDialog(
            onDismissRequest = { vm.dismissOutcome() },
            containerColor = Fieldbook.Paper,
            titleContentColor = Fieldbook.Ink,
            textContentColor = Fieldbook.Ink,
            title = { Text("パーツができました", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    FieldbookPart(o.part, modifier = Modifier.size(180.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(o.part.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                    Text(
                        "L=${o.part.distanceM} m ・ 頂点 ${o.part.points.size} ・ ε ${"%.0f".format(o.stats.epsilonM)} m",
                        fontFamily = Fieldbook.Data,
                        fontSize = 11.sp,
                        color = Fieldbook.Muted,
                    )
                    Text(
                        "GPS ${o.stats.rawPoints} 点 → フィルタ後 ${o.stats.filteredPoints} 点",
                        fontFamily = Fieldbook.Data,
                        fontSize = 11.sp,
                        color = Fieldbook.Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissOutcome(); onGoInventory() }) {
                    Text("コレクションを見る", color = Fieldbook.Survey, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissOutcome() }) { Text("閉じる", color = Fieldbook.Muted) }
            },
        )
        is CollectViewModel.Outcome.Rejected -> AlertDialog(
            onDismissRequest = { vm.dismissOutcome() },
            containerColor = Fieldbook.Paper,
            titleContentColor = Fieldbook.Ink,
            textContentColor = Fieldbook.Ink,
            title = { Text("パーツになりませんでした", fontWeight = FontWeight.Bold) },
            text = { Text(o.reason) },
            confirmButton = {
                TextButton(onClick = { vm.dismissOutcome() }) { Text("閉じる", color = Fieldbook.Survey, fontWeight = FontWeight.Bold) }
            },
        )
        null -> Unit
    }
}

/** 記録中の赤。野帳の赤鉛筆のイメージで、少し落ち着いた色 */
private val RecordingRed = Color(0xFFC0392B)

private data class StatusLine(val text: String, val color: Color)

/** 手持ちの「積む」ボタンと同じ見た目のボタン */
@Composable
private fun SurveyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Fieldbook.Survey,
            contentColor = Color.White,
            disabledContainerColor = Fieldbook.GridMajor,
            disabledContentColor = Fieldbook.Muted,
        ),
        modifier = modifier.height(52.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DemoMenuButton(
    enabled: Boolean,
    onPick: (SampleTracks.SampleTrack) -> Unit,
    onPickFile: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Fieldbook.Survey, disabledContentColor = Fieldbook.Muted),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, if (enabled) Fieldbook.Survey else Fieldbook.GridMajor),
            modifier = Modifier.height(52.dp),
        ) {
            Text("デモ散歩", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SampleTracks.all.forEach { track ->
                DropdownMenuItem(
                    text = { Text("${track.expectedKind.label}：${track.name}") },
                    onClick = { open = false; onPick(track) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("ファイルから再生（GPX / JSON）") },
                onClick = { open = false; onPickFile() },
            )
        }
    }
}

/** 図の四隅の見当（手持ちのカードと同じ形。枠の外側、余白 10dp の中に描く） */
private fun Modifier.fieldbookCorners(): Modifier = drawBehind {
    val a = 2.dp.toPx()
    val len = 12.dp.toPx()
    val w = 1.5.dp.toPx()
    val c = Fieldbook.Mark
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(c, Offset(x, y), Offset(x + dx, y), w)
        drawLine(c, Offset(x, y), Offset(x, y + dy), w)
    }
    corner(a, a, len, len)
    corner(size.width - a, a, -len, len)
    corner(a, size.height - a, len, -len)
    corner(size.width - a, size.height - a, -len, -len)
}

/** Google Maps に軌跡を描く（API キーがある場合）。地図の道路のように、暗い縁取りの上にオレンジを重ねる */
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
            val latLngs = points.map { LatLng(it.lat, it.lng) }
            Polyline(points = latLngs, color = Fieldbook.RoadCasing, width = 16f, zIndex = 1f)
            Polyline(points = latLngs, color = Fieldbook.Road, width = 10f, zIndex = 2f)
        }
        // 今いる場所（最後の点）：青い点
        if (last != null) {
            Circle(
                center = LatLng(last.lat, last.lng),
                radius = 4.0,
                fillColor = Fieldbook.Survey,
                strokeColor = Color.White,
                strokeWidth = 4f,
                zIndex = 3f,
            )
        }
    }
}

/**
 * API キーが無いときの代替：軌跡をメートル座標にして枠に収め、野帳の図として描く（縁取りの道・始点・北・縮尺）。
 */
@Composable
fun TrackCanvas(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Fieldbook.Data, fontSize = 10.sp, color = Fieldbook.Ink)
    Canvas(modifier = modifier) {
        // 北（軌跡はメートル座標で y が北向きなので、常に上）
        val nx = size.width - 14.dp.toPx()
        drawText(measurer, "N", topLeft = Offset(nx - 4.dp.toPx(), 8.dp.toPx()), style = label.copy(fontWeight = FontWeight.Bold))
        drawPath(
            Path().apply {
                moveTo(nx, 22.dp.toPx())
                lineTo(nx - 4.dp.toPx(), 30.dp.toPx())
                lineTo(nx + 4.dp.toPx(), 30.dp.toPx())
                close()
            },
            Fieldbook.Ink,
        )
        if (points.size < 2) return@Canvas

        val xy = Projection.toMeters(points)
        val minX = xy.minOf { it.x }
        val maxX = xy.maxOf { it.x }
        val minY = xy.minOf { it.y }
        val maxY = xy.maxOf { it.y }
        val span = max(maxX - minX, maxY - minY).coerceAtLeast(20.0)
        val pad = 28.dp.toPx()
        val scale = ((size.minDimension - pad * 2) / span).toFloat() // px / m
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        fun sx(x: Double) = size.width / 2 + ((x - cx) * scale).toFloat()
        fun sy(y: Double) = size.height / 2 - ((y - cy) * scale).toFloat()

        val path = Path()
        xy.forEachIndexed { i, p -> if (i == 0) path.moveTo(sx(p.x), sy(p.y)) else path.lineTo(sx(p.x), sy(p.y)) }
        val road = 8.dp.toPx()
        val casing = road + 2 * 1.5.dp.toPx()
        drawPath(path, color = Fieldbook.RoadCasing, style = Stroke(width = casing, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color = Fieldbook.Road, style = Stroke(width = road, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // 始点：測量の基準点のように、二重丸
        val first = xy.first()
        drawCircle(Fieldbook.Ink, radius = 5.dp.toPx(), center = Offset(sx(first.x), sy(first.y)))
        drawCircle(Fieldbook.Paper, radius = 2.5.dp.toPx(), center = Offset(sx(first.x), sy(first.y)))
        // 今いる場所（最後の点）：青い点（地図あり版と同じ）
        val lastPt = xy.last()
        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(sx(lastPt.x), sy(lastPt.y)))
        drawCircle(Fieldbook.Survey, radius = 4.5.dp.toPx(), center = Offset(sx(lastPt.x), sy(lastPt.y)))

        // 縮尺：幅の 1/4 くらいを切りのいい長さに
        val barM = niceLength(size.width * 0.25f / scale)
        val barPx = barM * scale
        val x0 = 12.dp.toPx()
        val y0 = size.height - 12.dp.toPx()
        drawLine(Fieldbook.Ink, Offset(x0, y0), Offset(x0 + barPx, y0), 2.dp.toPx())
        drawLine(Fieldbook.Ink, Offset(x0, y0 - 4.dp.toPx()), Offset(x0, y0 + 4.dp.toPx()), 1.5.dp.toPx())
        drawLine(Fieldbook.Ink, Offset(x0 + barPx, y0 - 4.dp.toPx()), Offset(x0 + barPx, y0 + 4.dp.toPx()), 1.5.dp.toPx())
        drawText(measurer, if (barM >= 1000) "${barM / 1000} km" else "$barM m", topLeft = Offset(x0 + barPx + 4.dp.toPx(), y0 - 8.dp.toPx()), style = label)
    }
}
