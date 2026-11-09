package com.rein4ce.roadcollector.ui.collect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.audio.Sfx
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.sample.SampleTracks
import com.rein4ce.roadcollector.ui.common.FieldbookDropdownMenu
import com.rein4ce.roadcollector.ui.common.MenuHeading
import com.rein4ce.roadcollector.ui.common.PressButton
import com.rein4ce.roadcollector.ui.common.PressOutlinedButton
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.WalkNote
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import com.rein4ce.roadcollector.ui.common.walkDate
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.FieldbookPart
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.BlockWord
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.rememberSfx
import com.rein4ce.roadcollector.ui.inventory.niceLength
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing

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
    val app = RoadCollectorApp.from(context)
    // パーツを歩いたときの天気（記録のあとに WeatherService が取ってきて保存する）。記録のあとの窓に出す
    val weathers by app.partRepository.weather.collectAsStateWithLifecycle()
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
        // 見出し：左に画面名「みちのきろく」（ほかの画面の名前と同じ積み木。ui/common/Pop.kt の BlockWord）、右に歩いた長さ。
        // 画面名の積み木は文字の大きさの設定では大きくしない（dp で決める）。長さの字は折り返さない。
        // 長さの字は残りの幅に入れ、入りきらないときだけ小さくする
        // （設定の歯車は、ホームの右上へ移した。決定 52。ui/settings/SettingsGearButton.kt）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 画面名は押すと動く（BlockWord）。落ちてくる最後の積み木が歩いた長さの上を通るよう、手前に描く
            // （開いたときのタイトルは、ここではなくホームの題字に収まる。ui/HomeScreen.kt）
            BlockWord(
                "みちのきろく",
                modifier = Modifier
                    .zIndex(1f)
                    // 左の端は、ほかの画面の名前と同じ所にそろえる
                    .padding(end = 12.dp),
            )
            // 歩いた長さ：「L=」の代わりに、下のタブの「採集」と同じ足跡のマーク
            val walkedM = distance.toInt()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "歩いた長さ $walkedM メートル" },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 足跡（と長さ）を押すと、足跡が 1 歩ずつ上へ歩く（遊びの仕掛け。読み上げには出さない）。
                // 押したら題字と同じ軽い振動を返す。歩いている途中に押しても、始めからやり直さない
                // （歩き始めるまでのわずかな間も「歩いている」と見るため、動きの isRunning ではなく Job で見る）
                val footSteps = remember { Animatable(0f) }
                val footScope = rememberCoroutineScope()
                val footJob = remember { mutableStateOf<Job?>(null) }
                val haptics = rememberHaptics()
                // 記録中（デモ散歩の再生中も）は、足跡がずっと歩き続ける（地図には足跡を出さず、ここで「歩いている」を見せる）。
                // 止めたら、アイコンと同じ並びに戻す
                LaunchedEffect(isRecording) {
                    footJob.value?.cancel()
                    if (isRecording) {
                        footJob.value = footScope.launch { footSteps.keepWalking() }
                    } else {
                        footJob.value = null
                        footSteps.snapTo(0f)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            if (footJob.value?.isActive != true) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                footJob.value = footScope.launch { footSteps.walkFootprints() }
                            }
                        }
                    },
                ) {
                    WalkingFootprints(steps = { footSteps.value }, tint = Fieldbook.Ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    BasicText(
                        "$walkedM m",
                        style = TextStyle(fontFamily = Fieldbook.Data, fontWeight = FontWeight.Bold, color = Fieldbook.Ink),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 18.sp, stepSize = 1.sp),
                    )
                }
            }
        }

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
                    TrackMap(points, recording = isRecording, playingDemo = playing != null)
                } else {
                    TrackCanvas(points, recording = isRecording, modifier = Modifier.fillMaxSize())
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

        // 操作：コレクションの「積むパーツを選ぶ」と同じ青・形（両端が丸い）・高さ（48dp）。
        // 文字を大きくして片方だけ 2 行になっても、並んだ 2 つの高さをそろえる（高い方に合わせる）
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 「記録開始」と「停止してパーツ化」は 1 つのボタンで、文字と押したときの動作だけを切り替える。
            // 別々のボタンにすると、押した瞬間に記録が始まって（止まって）ボタンが入れ替わり、
            // 離したあとの押し心地（バネで戻る動き。ui/common/PressFeel）が途中で消えてしまう
            SurveyButton(
                text = if (isRecording) "停止してパーツ化" else "記録開始",
                onClick = {
                    when {
                        isRecording -> vm.stopAndConvert()
                        LocationRecordingService.hasLocationPermission(context) -> vm.startRecording()
                        else -> permissionLauncher.launch(LocationRecordingService.requiredPermissions())
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (!isRecording) {
                DemoMenuButton(
                    enabled = !vm.isDemoPlaying,
                    onPick = { vm.playDemo(it) },
                    onPickFile = { fileLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }

    // 結果の窓：コレクションと同じ野帳の図（見当・北・縮尺）と注記
    when (val o = vm.outcome) {
        // 設定・拡大表示と同じ形：灰色の帯の見出し、枠の中のいちばん下に「コレクションを見る」、枠の下の丸い「×」で閉じる。
        // 見出しは「パーツができました」だと機械っぽいので、コレクションの「これまでに集めた道 ○ 本」とつながる言い方にする
        is CollectViewModel.Outcome.Created -> FramedDialog(title = "道を 1 本集めました", onDismiss = { vm.dismissOutcome() }) {
            // 窓が出たときに 1 回だけ、ごほうびの音（ド・ミ・ソ・ド）。名前が届いて中身が変わっても、画面を回しても鳴らし直さない
            val sfx = rememberSfx()
            var cheered by rememberSaveable(o.part.id) { mutableStateOf(false) }
            LaunchedEffect(o.part.id) {
                if (!cheered) {
                    cheered = true
                    sfx.play(Sfx.DONE)
                }
            }
            // 天気は記録のあとに取ってくるので、届いたら図（道の色・左上のアイコン）と注記に出す（届くまではいつもの色）
            val sky = app.partRepository.weatherOf(o.part.id, weathers)?.sky
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                FieldbookPart(o.part, modifier = Modifier.size(180.dp), sky = sky)
                Spacer(Modifier.height(4.dp))
                PartNameLine(o)
                o.note?.let { note ->
                    // 速すぎて自動で止めたときの説明。赤鉛筆の色で目立たせる
                    Text(note, fontSize = 12.sp, color = RecordingRed, modifier = Modifier.padding(vertical = 4.dp))
                }
                // 歩いた長さ・日付・天気（コレクションのカード・大きく見る窓と同じ形。ui/common/WalkNote）。
                // 天気は記録のあとに取ってくるので、届いたら足す（届くまでは出さない）。
                // 変換の数字（頂点・ε・GPS の点など）は、見ても分からないので出さない（開発用に Logcat へ。CollectViewModel.keep）
                WalkNote(
                    distanceM = o.part.distanceM,
                    date = walkDate(o.part.walkedAt),
                    weather = sky?.label,
                )
                PressTextButton(onClick = { vm.dismissOutcome(); onGoInventory() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("コレクションを見る", color = Fieldbook.Survey, fontWeight = FontWeight.Bold)
                }
            }
        }
        // 設定と同じ形：灰色の帯の見出しと、枠の下の丸い「×」で閉じる（枠の外を押しても閉じる）
        is CollectViewModel.Outcome.Rejected -> FramedDialog(title = "パーツになりませんでした", onDismiss = { vm.dismissOutcome() }) {
            Text(o.reason, style = noteStyle, fontSize = 15.sp, color = Fieldbook.Ink, modifier = Modifier.padding(top = 10.dp))
        }
        null -> Unit
    }
}

/** 記録中の赤。野帳の赤鉛筆のイメージで、少し落ち着いた色 */
private val RecordingRed = Color(0xFFC0392B)

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
        // 付かなかったときだけ、あとで付けられることを添える（届くまでに自分で名前を付けていたら出さない）
        if (o.naming == CollectViewModel.Naming.FAILED && !o.part.hasName) {
            Text(
                notes("名前はコレクションから", "あとで付けられます"),
                style = noteStyle,
                fontSize = 11.sp,
                color = Fieldbook.Muted,
            )
        }
    }
}

/** コレクションの「積む」ボタンと同じ見た目のボタン（両端が丸い Material の標準の形。「パーツを選ぶ」の窓のボタンとも同じ） */
@Composable
private fun SurveyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PressButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonDefaults.shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Fieldbook.Survey,
            contentColor = Color.White,
            disabledContainerColor = Fieldbook.GridMajor,
            disabledContentColor = Fieldbook.Muted,
        ),
        // 高さは「選んだ n 本で積む」と同じ 48dp（下限だけ決めて、文字を大きくして 2 行になっても切れないように）
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DemoMenuButton(
    enabled: Boolean,
    onPick: (SampleTracks.SampleTrack) -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        PressOutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            // 隣の「記録開始」と同じ、両端が丸い形
            shape = ButtonDefaults.outlinedShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Fieldbook.Survey, disabledContentColor = Fieldbook.Muted),
            border = BorderStroke(1.5.dp, if (enabled) Fieldbook.Survey else Fieldbook.GridMajor),
            modifier = Modifier.fillMaxHeight().heightIn(min = 48.dp),
        ) {
            Text("デモ散歩", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        // メニューも野帳の紙にする（Material の標準は薄紫の面で、画面から浮いて見える）。
        // 見本は 1 本ずつ小さな図を添えて、形の名前（棒・L字…）で選ぶ。名前の下に、パーツにしたときの長さを小さく添える
        // （棒と L字は 2 本ずつあり、図は同じ大きさにそろえて描くので、長さで見分ける。下の「ファイル」の行と同じ 2 段。#84 のレビュー）
        FieldbookDropdownMenu(expanded = open, onDismissRequest = { open = false }, heading = "見本の散歩") {
            SampleTracks.all.forEach { track ->
                val part = SampleParts.byId(track.id)
                DropdownMenuItem(
                    leadingIcon = { if (part != null) PartThumb(part, size = 30.dp) },
                    text = {
                        // 読み上げでは、見本の名前も言う（「棒、まっすぐ北へ、420 メートル」）
                        val spoken = listOfNotNull(track.expectedKind.label, track.name, part?.let { "${it.distanceM} メートル" })
                        Column(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken.joinToString("、") }) {
                            Text(track.expectedKind.label, fontSize = 15.sp, color = Fieldbook.Ink)
                            if (part != null) {
                                Text("${part.distanceM} m", fontFamily = Fieldbook.Data, fontSize = 11.sp, color = Fieldbook.Muted)
                            }
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
                // 「ファイル」の下に、読める形式を小さく添える（横に並べると、メニューの幅はいちばん長い行で決まるので、
                // 上の見本の行の右が空きすぎる。2 行にすれば、幅は見本の行と同じくらいに収まる）
                text = {
                    Column {
                        Text("ファイル", fontSize = 15.sp, color = Fieldbook.Ink)
                        Text("GPX / JSON", fontFamily = Fieldbook.Data, fontSize = 11.sp, color = Fieldbook.Muted)
                    }
                },
                colors = MenuDefaults.itemColors(textColor = Fieldbook.Ink, leadingIconColor = Fieldbook.Ink),
                contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
                onClick = { open = false; onPickFile() },
            )
        }
    }
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
private fun TrackMap(points: List<TrackPoint>, recording: Boolean, playingDemo: Boolean) {
    val start = points.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: LatLng(35.7496, 139.8049)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, 16f)
    }
    val last = points.lastOrNull()
    // 新しい点へ地図を動かす。なめらかに動かすのは、点が画面の中にあって、次の点まで時間があるときだけ。
    // - デモ散歩の再生中は、3 秒で道を流しきるので点が数十ミリ秒ごとに来る。なめらかに動かすと、動き出すたびに次の点で
    //   止められてほとんど進まず、指で地図をずらしていると歩く人が画面の外のままになる。そのため、すぐ移す
    // - 点が画面の外（指で地図をずらした、記録の最初の点が遠い）なら、すぐ移して、今いる場所をすぐ見せる
    LaunchedEffect(last) {
        if (last == null) return@LaunchedEffect
        val target = CameraUpdateFactory.newLatLng(LatLng(last.lat, last.lng))
        val onScreen = cameraPositionState.projection?.visibleRegion?.latLngBounds?.contains(LatLng(last.lat, last.lng)) == true
        if (playingDemo || !onScreen) cameraPositionState.move(target) else cameraPositionState.animate(target)
    }
    // 記録していない間（と、記録を始めて最初の点が届くまで）の今いる場所。デモ散歩の再生中は、本当の場所を混ぜない
    val idleState = rememberIdleFix(enabled = !playingDemo && (!recording || last == null))
    val idle = idleState.value
    // 軌跡が無いときは、今いる場所へ地図を動かす（Google マップと同じく、開くと自分の所が出る）。
    // まず、スマホが覚えている最後の位置で動かし（すぐ出る）、位置を聞き始めてから本当の位置が初めて届いたら、もう 1 回だけ動かす
    // （最後の位置は、電車で移動してきたあとなどは古い場所のことがあり、そのままだと青い点が画面の外に出てしまうため）。
    // その間に指で地図を動かしていたら、追いかけない。0：まだ、1：最後の位置で動かした、2：本当の位置で動かした（以後は動かさない）
    var centeredOnIdle by remember { mutableIntStateOf(0) }
    LaunchedEffect(idle != null, idle?.live) {
        if (idle == null || points.isNotEmpty()) return@LaunchedEffect
        val step = if (idle.live) 2 else 1
        if (centeredOnIdle >= step) return@LaunchedEffect
        val movedByHand = centeredOnIdle == 1 && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        centeredOnIdle = step
        if (!movedByHand) cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(idle.lat, idle.lng)))
    }
    // 人が今どちらを向いているか（記録していない間と記録中で同じものを使い、記録を始めたときに向きが飛ばないように）
    val facing = remember { WalkerFacing() }
    // 向いている方位（人のまわりの三角）。デモ散歩の再生中は、スマホの向きと見本の道は関係がないので出さない
    val showIdleDot = idle != null && !playingDemo && (!recording || last == null)
    val heading = rememberCompassHeading(
        enabled = !playingDemo && (showIdleDot || (recording && last != null)),
        lat = last?.lat ?: idle?.lat,
        lng = last?.lng ?: idle?.lng,
    )
    // 「今いる場所へ戻る」ボタン（#112・決定 72）。戻す所は、記録中（デモ散歩の再生中も）なら歩いている人、そうでなければ今いる場所（HereButton.kt）
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val here = hereTarget(points, recording, playingDemo, idle?.let { MapSpot(it.lat, it.lng) })
    // 位置の許可が無いときは、押すと許可を聞く（許可されたら、記録していない間の今いる場所が届き始め、地図もそこへ動く）
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val canAsk = here == null && !LocationRecordingService.hasLocationPermission(context)
    Box(Modifier.fillMaxSize()) {
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
            // スタート（最初の点）：赤い旗。止めたあと、ゴールが歩き始めの近くなら、2 本が重なって見えないよう左右に開く。
            // 近いかどうかは、今の地図の拡大で旗の幅が何 m になるかと比べる（拡大を変えると開いたり閉じたりする）。
            // 地図を動かすたびに中身を組み立て直さないよう、開くかどうかが変わったときだけ読み直す
            val firstPt = points.firstOrNull()
            val spread by remember(recording, firstPt, last, points.size) {
                derivedStateOf {
                    !recording && firstPt != null && last != null && points.size >= 2 &&
                        roughMeters(firstPt, last) < flagWidthMeters(firstPt.lat, cameraPositionState.position.zoom)
                }
            }
            if (firstPt != null) {
                val startState = rememberMarkerState()
                startState.position = LatLng(firstPt.lat, firstPt.lng)
                val tilt = if (spread) FLAG_SPREAD_DEGREES else 0f
                MarkerComposable(Fieldbook.dark, tilt, state = startState, anchor = FlagAnchor, zIndex = 4f, title = "スタート") {
                    FlagMarker(StartFlagColor, tilt = tilt)
                }
            }
            if (last != null) {
                if (recording) {
                    // 記録中：今いる場所に黄色の人（記録していない間と同じ印）。地図は旗・道・人だけにする（見やすさのため）。
                    // 位置は、点から点へ飛ばさず、なめらかに動かす。
                    // ただしデモ散歩の再生中は、地図と同じく、すぐ新しい点へ移す（点が数十ミリ秒ごとに来るので、なめらかに動かすと
                    // 動き出すたびに次の点で止められて十数点ぶん遅れ、地図を拡大していると人が画面の外に置いていかれる）
                    val target = LatLng(last.lat, last.lng)
                    var shown by remember { mutableStateOf(target) }
                    LaunchedEffect(target) {
                        if (playingDemo) {
                            shown = target
                            return@LaunchedEffect
                        }
                        val from = shown
                        animate(0f, 1f, animationSpec = tween(WALK_GLIDE_MS, easing = LinearEasing)) { t, _ ->
                            shown = LatLng(from.latitude + (target.latitude - from.latitude) * t, from.longitude + (target.longitude - from.longitude) * t)
                        }
                    }
                    // 黄色の人が歩いている向きを向いて歩き、まわりの三角がスマホの向いている方位を指す（デモ散歩の再生中は三角を出さない）
                    HereMarker(
                        at = shown,
                        heading = heading,
                        showPointer = !playingDemo,
                        walking = true,
                        walkingEast = movingEast(points),
                        camera = cameraPositionState,
                        facing = facing,
                        zIndex = 5f,
                    )
                } else if (points.size >= 2) {
                    // 止めたあと：最後の点に青い旗（ゴール）
                    // （歩き始めの近くなら、左右を反転して左へ傾ける）
                    val goalState = rememberMarkerState()
                    goalState.position = LatLng(last.lat, last.lng)
                    MarkerComposable(Fieldbook.dark, spread, state = goalState, anchor = FlagAnchor, zIndex = 5f, title = "ゴール") {
                        FlagMarker(GoalFlagColor, mirrored = spread, tilt = if (spread) -FLAG_SPREAD_DEGREES else 0f)
                    }
                }
            }
            // 記録していない間：今いる場所に止まった黄色の人と、向いている方位の三角。位置のずれの大きさを薄い青緑の円で
            if (showIdleDot && idle != null) {
                val here = LatLng(idle.lat, idle.lng)
                if (idle.accuracyM > 0f) {
                    Circle(
                        center = here,
                        radius = idle.accuracyM.toDouble(),
                        fillColor = Pop.Teal.copy(alpha = 0.12f),
                        strokeColor = Pop.Teal.copy(alpha = 0.35f),
                        strokeWidth = 2f,
                        zIndex = 3f,
                    )
                }
                HereMarker(
                    at = here,
                    heading = heading,
                    showPointer = true,
                    walking = false,
                    walkingEast = null,
                    camera = cameraPositionState,
                    facing = facing,
                    zIndex = 4.5f,
                )
            }
        }
        // 右下の拡大・縮小のボタン（地図に付いているもの）のすぐ上に置く（Google マップの現在地のボタンと同じ並び）
        HereButton(
            ready = here != null || canAsk,
            label = when {
                here != null -> "今いる場所へ戻る"
                canAsk -> "今いる場所へ戻る。位置の許可を聞きます"
                else -> "今いる場所を調べています"
            },
            onClick = {
                val target = here
                if (target != null) {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lng), hereZoom(cameraPositionState.position.zoom)),
                            HERE_ANIMATION_MS,
                        )
                    }
                } else if (canAsk) {
                    locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = HERE_BUTTON_END, bottom = HERE_BUTTON_BOTTOM),
        )
    }
}

/**
 * 「今いる場所へ戻る」ボタンの置き場所（地図の右下の端から）。地図に付いている拡大・縮小のボタン（右から約 11dp・下から約 89dp まで）の
 * すぐ上で、横のまん中をそろえる
 */
private val HERE_BUTTON_END = 6.dp
private val HERE_BUTTON_BOTTOM = 96.dp

/**
 * [at] に、今いる場所の黄色の人（[rememberWalkerIcon]）と、向いている方位の三角（[HeadingPointer]）を出す。
 * 方位は 1 秒に何度も変わるので、ここだけで読む（地図の中身全体を組み立て直さないように）。
 * - 三角は [showPointer] で、方位が分かる（センサーがある）ときだけ。地図に寝かせた印なので、地図を回しても方位のまま
 * - 人の向き：記録中（[walking]）で歩いている向き [walkingEast] が決まるときは、そちらを向く（人は「歩いている人」なので、
 *   スマホを横に向けて持って歩いても、歩いている向きと逆を向かないように）。そうでなければ、方位から地図の回転を引いた
 *   「画面の上の向き」で、東寄りなら右・西寄りなら左。どれも決まらなければ前の向き（[facing]）のまま
 * - [walking] なら 0.5 秒ごとに足を入れ替えて歩く（見出しの足跡と同じ速さ）。そうでないとき、端末で「動きを減らす」にしているときは、
 *   一歩踏み出した姿のまま止まる
 */
@Composable
private fun HereMarker(
    at: LatLng,
    heading: State<Float?>,
    showPointer: Boolean,
    walking: Boolean,
    walkingEast: Boolean?,
    camera: CameraPositionState,
    facing: WalkerFacing,
    zIndex: Float,
) {
    val deg = if (showPointer) heading.value else null
    // 地図の回転は、変わったときだけ読み直す（地図を動かす・拡大するたびに、毎コマ組み立て直さないように）
    val bearing by remember(camera) { derivedStateOf { camera.position.bearing } }
    val right = when {
        walking && walkingEast != null -> walkingEast
        deg != null -> facingRight(deg - bearing, facing.right)
        walkingEast != null -> walkingEast
        else -> facing.right
    }
    facing.right = right
    val reduceMotion = rememberReduceMotion()
    val step by produceState(0, walking && !reduceMotion) {
        value = 0
        if (walking && !reduceMotion) while (true) { delay(WALKER_STEP_MS); value++ }
    }
    val pose = if (step % 2 == 0) WalkerPose.STEP_A else WalkerPose.STEP_B
    if (deg != null) {
        val pointerState = rememberMarkerState()
        pointerState.position = at
        MarkerComposable(
            Fieldbook.dark,
            state = pointerState,
            anchor = Offset(0.5f, 0.5f),
            flat = true,
            rotation = deg,
            zIndex = POINTER_Z,
            title = "向いている方向",
        ) {
            HeadingPointer()
        }
    }
    // 人の印（絵は向きと姿ごとに 1 回だけ描いて使い回す。rememberWalkerIcon）
    val figureState = rememberMarkerState()
    figureState.position = at
    Marker(
        state = figureState,
        icon = rememberWalkerIcon(facingRight = right, pose = pose),
        anchor = WalkerAnchor,
        zIndex = zIndex,
        title = "いまの場所",
    )
}

/** 方位の三角の重なり順（道 1・2 より上、旗 4・人より下。人の頭や旗に隠れても、三角の先は外に出る） */
private const val POINTER_Z = 3.5f

/** 人が今どちらを向いているか（北・南のあたりで前の向きのままにするために覚えておく。描き直しの合図にはしない） */
private class WalkerFacing {
    var right = true
}

/** 記録中に、地図の人が足を入れ替える間隔 [ms]（見出しの足跡の 1 歩と同じくらい） */
private const val WALKER_STEP_MS = 500L

/**
 * API キーが無いときの代替：軌跡をメートル座標にして枠に収め、野帳の図として描く（縁取りの道・始点・北・縮尺）。
 */
@Composable
fun TrackCanvas(points: List<TrackPoint>, modifier: Modifier = Modifier, recording: Boolean = false) {
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
        // スタート（最初の点）：赤い旗。止めたあとは、最後の点に青い旗（ゴール）。記録中は今いる場所に青い点
        val first = xy.first()
        val lastPt = xy.last()
        val startAt = Offset(sx(first.x), sy(first.y))
        val goalAt = Offset(sx(lastPt.x), sy(lastPt.y))
        // ゴールが歩き始めの近く（画面の上で旗の幅くらい）なら、2 本が重なって見えないよう左右に開く（StartGoalMarks.kt）
        drawStartGoalFlags(startAt, goal = if (recording) null else goalAt)
        if (recording) {
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(sx(lastPt.x), sy(lastPt.y)))
            drawCircle(Fieldbook.Survey, radius = 4.5.dp.toPx(), center = Offset(sx(lastPt.x), sy(lastPt.y)))
        }

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

/** 記録中に、地図の今いる場所の点が、点から点へなめらかに動く時間 [ms] */
private const val WALK_GLIDE_MS = 400
