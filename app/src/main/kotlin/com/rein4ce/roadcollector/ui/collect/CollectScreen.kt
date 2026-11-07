package com.rein4ce.roadcollector.ui.collect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.R
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.sample.SampleTracks
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.FieldbookPart
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.inventory.niceLength
import com.rein4ce.roadcollector.ui.settings.SettingsDialog
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
    // 最近 30 秒の速さ。上限に近づいたら（9 割から。8 割だとジョギング中ずっと出てしまう）状態行で知らせる。
    // 上限を超えた状態が続くと TrackRecorder が記録を自動で止める
    val recentSpeed = remember(points) { vm.recentSpeedMps(points) }
    val nearLimit = isRecording && recentSpeed != null && recentSpeed > vm.walkMaxSpeedMps * 0.9

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
        // 見出し：左に題字「ミチツミ」（道積。積み木のブロックを積んでいる途中の絵）、右に歩いた長さと設定の歯車。
        // 題字はロゴなので文字の大きさの設定では大きくしない（dp で決める）。長さの字は折り返さない。
        // 歯車がはみ出さないよう、長さの字は残りの幅に入れ、入りきらないときだけ小さくする
        var showSettings by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleBlocks(modifier = Modifier.padding(start = 4.dp, end = 12.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BasicText(
                    "L=${distance.toInt()} m",
                    style = TextStyle(fontFamily = Fieldbook.Data, fontWeight = FontWeight.Bold, color = Fieldbook.Ink),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 18.sp, stepSize = 1.sp),
                )
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "設定", tint = Fieldbook.Ink)
            }
        }
        if (showSettings) SettingsDialog(onDismiss = { showSettings = false })

        // 状態：記録中・再生中のときだけ、地図の左上に札（丸と文）を重ねる。停止中は何も出さない。
        // 地図の上に重ねるので、出たり消えたりしても地図は動かず、停止中に題字と地図のあいだが空かない
        val playing = vm.playingLabel
        val status = when {
            playing != null -> StatusLine("${playing}を再生中", Fieldbook.Survey)
            nearLimit -> StatusLine("速めです（${CollectViewModel.kmh(recentSpeed!!)}）・${CollectViewModel.kmhInt(vm.walkMaxSpeedMps)} km で止まります", RecordingRed)
            isRecording -> StatusLine("記録中（画面を閉じても続きます）", RecordingRed)
            else -> null
        }
        Spacer(Modifier.height(4.dp))

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
                        // 左上は状態の札が出るので、左下に置く
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                }
                if (status != null) StatusChip(status, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
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
                    PartNameLine(o)
                    o.note?.let { note ->
                        // 速すぎて自動で止めたときの説明。赤鉛筆の色で目立たせる
                        Text(note, fontSize = 12.sp, color = RecordingRed, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Text(
                        "L=${o.part.distanceM} m ・ 頂点 ${o.part.points.size} ・ ε ${"%.0f".format(o.stats.epsilonM)} m",
                        fontFamily = Fieldbook.Data,
                        fontSize = 11.sp,
                        color = Fieldbook.Muted,
                    )
                    o.stats.fastestMps?.let { v ->
                        Text(
                            "最高 ${CollectViewModel.kmh(v)}（30 秒の窓）",
                            fontFamily = Fieldbook.Data,
                            fontSize = 11.sp,
                            color = Fieldbook.Muted,
                        )
                    }
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
        // 設定と同じ形：灰色の帯の見出しと、枠の下の丸い「×」で閉じる（枠の外を押しても閉じる）
        is CollectViewModel.Outcome.Rejected -> FramedDialog(title = "パーツになりませんでした", onDismiss = { vm.dismissOutcome() }) {
            Text(o.reason, style = noteStyle, fontSize = 15.sp, color = Fieldbook.Ink, modifier = Modifier.padding(top = 10.dp))
        }
        null -> Unit
    }
}

/** 記録中の赤。野帳の赤鉛筆のイメージで、少し落ち着いた色 */
private val RecordingRed = Color(0xFFC0392B)

/**
 * 題字「ミチツミ」の字。アプリのアイコンのように見えるよう、太い丸ゴシック M PLUS Rounded 1c Black（OFL）を
 * この 3 文字だけに切り出して同梱している（出どころとライセンスは app/fonts/）
 */
/** 題字のブロックの上の字の色（起動画面の題字と同じ） */
private val TitleLetter = Color(0xFFF3F6F0)

private val TitleRounded = FontFamily(Font(R.font.mplus_rounded_michitsumi, FontWeight.Black))

/** 題字のブロック 1 つ。[tiltDeg] は傾き、[liftDp] は持ち上げ（負で上）。色はアプリのアイコン（ic_launcher_foreground）の 4 色 */
private class TitleBlock(val char: Char, val color: Color, val tiltDeg: Float, val liftDp: Float)

/**
 * 題字「ミチツミ」を、積み木を積んでいる途中の絵にする。
 * 1 文字ずつ色の付いたブロックに白抜きで載せ、少しずつ傾けて並べる。最後の 1 つは浮かせて（影も濃く）「置く直前」に見せる
 */
private val TitleBlocks = listOf(
    TitleBlock('ミ', Color(0xFFE07A2A), tiltDeg = -5f, liftDp = 0f),
    TitleBlock('チ', Color(0xFF2A9D8F), tiltDeg = 3f, liftDp = 1f),
    TitleBlock('ツ', Color(0xFFF2C14E), tiltDeg = -2f, liftDp = 0f),
    TitleBlock('ミ', Color(0xFF4A4E69), tiltDeg = 8f, liftDp = -9f),
)

@Composable
private fun TitleBlocks(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "ミチツミ" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (block in TitleBlocks) {
            val lifted = block.liftDp < 0f
            Box(
                modifier = Modifier
                    // 浮かせた分の場所を上に取っておく（傾けた角が見出しの外にはみ出さないように少し余裕も）
                    .padding(top = 10.dp)
                    .size(32.dp)
                    .graphicsLayer {
                        rotationZ = block.tiltDeg
                        translationY = with(density) { block.liftDp.dp.toPx() }
                    }
                    .shadow(if (lifted) 6.dp else 2.dp, shape, clip = false)
                    .background(block.color, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    block.char.toString(),
                    fontFamily = TitleRounded,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { 21.dp.toSp() },
                    // 題字はロゴなので、ダークモードでも白い字のまま（紙の色に合わせて暗くしない）
                    color = TitleLetter,
                )
            }
        }
    }
}

private data class StatusLine(val text: String, val color: Color)

/**
 * 地図の左上に重ねる状態の札（記録中・再生中・速めです）。紙の色の札に、色の丸と文。
 * 少しあとから出たり変わったりするので、読み上げでも知らせる
 */
@Composable
private fun StatusChip(status: StatusLine, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Fieldbook.Paper.copy(alpha = 0.93f))
            .border(1.dp, Fieldbook.CardEdge, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(status.color))
        Spacer(Modifier.width(8.dp))
        Text(status.text, fontSize = 12.sp, color = Fieldbook.Ink)
    }
}

/**
 * 「パーツができました」の名前の行。AI に名前を考えてもらっている間は「名前を考え中…」、届いたら名前を出す。
 * 付かなかったら「○ m の散歩」のまま、あとで付けられることを添える（decisions.md の 5・7）
 */
@Composable
private fun PartNameLine(o: CollectViewModel.Outcome.Created) {
    val asking = o.naming == CollectViewModel.Naming.ASKING
    // 名前は少しあとから届くので、読み上げでも知らせる
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (asking) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Fieldbook.Muted)
            Text(
                if (asking) "名前を考え中…" else o.part.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (asking) Fieldbook.Muted else Fieldbook.Ink,
            )
        }
        if (o.naming == CollectViewModel.Naming.FAILED) {
            Text(
                notes("名前はコレクションから", "あとで付けられます"),
                style = noteStyle,
                fontSize = 11.sp,
                color = Fieldbook.Muted,
            )
        }
    }
}

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
            border = BorderStroke(1.5.dp, if (enabled) Fieldbook.Survey else Fieldbook.GridMajor),
            modifier = Modifier.height(52.dp),
        ) {
            Text("デモ散歩", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        // メニューも野帳の紙にする（Material の標準は薄紫の面で、画面から浮いて見える）。
        // 見本は 1 本ずつ小さな図を添えて、名札（棒・L字…）と名前で選べるようにする
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(6.dp),
            containerColor = Fieldbook.Paper,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Fieldbook.CardEdge),
        ) {
            MenuHeading("見本の散歩")
            SampleTracks.all.forEach { track ->
                val part = SampleParts.byId(track.id)
                DropdownMenuItem(
                    leadingIcon = { if (part != null) PartThumb(part, size = 30.dp) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                track.expectedKind.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Fieldbook.Muted,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(track.name, fontSize = 15.sp, color = Fieldbook.Ink)
                        }
                    },
                    colors = MenuDefaults.itemColors(textColor = Fieldbook.Ink, leadingIconColor = Fieldbook.Ink),
                    contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
                    onClick = { open = false; onPick(track) },
                )
            }
            HorizontalDivider(color = Fieldbook.GridMajor, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            MenuHeading("実機で録ったもの")
            DropdownMenuItem(
                leadingIcon = {
                    // 書類の小さな印：野帳の線で描く
                    Canvas(Modifier.size(30.dp)) {
                        val w = 1.5.dp.toPx()
                        val l = size.width * 0.3f
                        val t = size.height * 0.15f
                        val r = size.width * 0.7f
                        val b = size.height * 0.85f
                        val fold = size.width * 0.12f
                        val path = Path().apply {
                            moveTo(l, t); lineTo(r - fold, t); lineTo(r, t + fold); lineTo(r, b); lineTo(l, b); close()
                            moveTo(r - fold, t); lineTo(r - fold, t + fold); lineTo(r, t + fold)
                        }
                        drawPath(path, Fieldbook.Ink, style = Stroke(width = w, join = StrokeJoin.Round))
                        for (k in 1..3) {
                            val y = t + (b - t) * (0.4f + 0.18f * k)
                            drawLine(Fieldbook.Mark, Offset(l + fold, y), Offset(r - fold, y), w)
                        }
                    }
                },
                text = { Text("ファイルから再生", fontSize = 15.sp, color = Fieldbook.Ink) },
                trailingIcon = { Text("GPX / JSON", fontFamily = Fieldbook.Data, fontSize = 11.sp, color = Fieldbook.Muted) },
                colors = MenuDefaults.itemColors(textColor = Fieldbook.Ink, leadingIconColor = Fieldbook.Ink, trailingIconColor = Fieldbook.Muted),
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                onClick = { open = false; onPickFile() },
            )
        }
    }
}

/** メニューの小さな見出し（野帳の注記の字。コレクションの「コレクション」と同じ作り） */
@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Fieldbook.Muted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** パーツの小さな図（メニューの見本用）。コレクションのカードと同じ、縁取りした道の色 */
@Composable
private fun PartThumb(part: Part, size: Dp) {
    Canvas(Modifier.size(size)) {
        val scalePx = this.size.minDimension * 0.78f
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val edgePx = 1.2.dp.toPx()
        drawPart(part.copy(thickness = part.thickness + 2 * edgePx / scalePx), center, scalePx, color = Fieldbook.RoadCasing)
        drawPart(part, center, scalePx, color = Fieldbook.Road)
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
        // ダークモードなら、地図も暗い色にする
        mapColorScheme = if (Fieldbook.dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
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
