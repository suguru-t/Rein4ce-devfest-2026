package com.rein4ce.roadcollector.ui.collect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.core.geo.Projection
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.sample.SampleTracks
import com.rein4ce.roadcollector.ui.TitleIntroState
import com.rein4ce.roadcollector.ui.common.FieldbookDropdownMenu
import com.rein4ce.roadcollector.ui.common.MenuHeading
import com.rein4ce.roadcollector.ui.common.PressButton
import com.rein4ce.roadcollector.ui.common.PressIconButton
import com.rein4ce.roadcollector.ui.common.PressOutlinedButton
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.WalkNote
import com.rein4ce.roadcollector.ui.common.rememberHaptics
import com.rein4ce.roadcollector.ui.common.walkDate
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.FieldbookPart
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.inventory.niceLength
import com.rein4ce.roadcollector.ui.settings.SettingsDialog
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
        // 見出し：左に題字「ミチツミ」（道積。積み木のブロックを積んでいる途中の絵）、右に歩いた長さと設定の歯車。
        // 題字はロゴなので文字の大きさの設定では大きくしない（dp で決める）。長さの字は折り返さない。
        // 歯車がはみ出さないよう、長さの字は残りの幅に入れ、入りきらないときだけ小さくする
        var showSettings by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 題字は押すと動く（TitleBlocks.kt）。落ちてくる「ミ」が歩いた長さや歯車の上を通るよう、手前に描く
            // 開いたときのタイトル（ui/TitleIntro.kt）が、ここに飛んできて収まる。そのために場所を知らせ、
            // 収まるまでは隠しておく（飛んでくる題字と、ここの題字が二重に見えないように）
            TitleBlocks(
                modifier = Modifier
                    .zIndex(1f)
                    .padding(start = 4.dp, end = 12.dp)
                    // タイトルが終わったら、もう知らせない（余計な更新をしない）
                    .onGloballyPositioned { if (!TitleIntroState.done) TitleIntroState.anchor = it.boundsInRoot() }
                    .graphicsLayer { alpha = if (TitleIntroState.done) 1f else 0f },
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
            // 歩いた長さと歯車のあいだを少しあける（くっついて見えないように）
            Spacer(Modifier.width(10.dp))
            // 押すと歯車がくるっと半回転して止まってから、設定の窓が開く（窓が歯車の半分にかかるので、先に回して見せる）。
            // 回っている間に押しても回り足さない（続けて押しても、1 回目の 0.4 秒後に開く。#77 のレビュー）。
            // 次に押したときは同じ向きへ回り足す（歯車の歯は 8 つなので、半回転で止まっても形は同じ）。
            // 端末の設定でアニメーションを切っている人には回さず、すぐ開く（Compose が設定に従う）
            val gearTurn = remember { Animatable(0f) }
            val gearScope = rememberCoroutineScope()
            val gearJob = remember { mutableStateOf<Job?>(null) }
            // 押したときの縮みと振動（#76 の押し心地）に、回転を重ねる
            PressIconButton(onClick = {
                if (gearJob.value?.isActive != true) {
                    gearJob.value = gearScope.launch {
                        gearTurn.animateTo(gearTurn.targetValue + 180f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
                        showSettings = true
                    }
                }
            }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "設定",
                    tint = Fieldbook.Ink,
                    modifier = Modifier.graphicsLayer { rotationZ = gearTurn.value },
                )
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
    LaunchedEffect(last) {
        if (last != null) cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(last.lat, last.lng)))
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
    // 向いている方位（扇）。デモ散歩の再生中は、スマホの向きと見本の道は関係がないので出さない
    val showIdleDot = idle != null && !playingDemo && (!recording || last == null)
    val heading = rememberCompassHeading(
        enabled = !playingDemo && (showIdleDot || (recording && last != null)),
        lat = last?.lat ?: idle?.lat,
        lng = last?.lng ?: idle?.lng,
    )
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
            val nowState = rememberMarkerState()
            nowState.position = LatLng(last.lat, last.lng)
            if (recording) {
                // 記録中：今いる場所に青い点（記録していない間と同じ印）。歩いている動きは見出しの足跡で見せ、地図は点と扇だけにする（見やすさのため）。
                // 位置は、点から点へ飛ばさず、なめらかに動かす
                val target = LatLng(last.lat, last.lng)
                var shown by remember { mutableStateOf(target) }
                LaunchedEffect(target) {
                    val from = shown
                    animate(0f, 1f, animationSpec = tween(WALK_GLIDE_MS, easing = LinearEasing)) { t, _ ->
                        shown = LatLng(from.latitude + (target.latitude - from.latitude) * t, from.longitude + (target.longitude - from.longitude) * t)
                    }
                }
                nowState.position = shown
                // 点の下に、スマホの向いている方位の扇（デモ散歩の再生中は出さない）
                if (!playingDemo) BeamMarker(heading, shown)
                MarkerComposable(Fieldbook.dark, state = nowState, anchor = Offset(0.5f, 0.5f), zIndex = 5f, title = "いまの場所") {
                    MyDotMarker()
                }
            } else if (points.size >= 2) {
                // 止めたあと：最後の点に青い旗（ゴール）
                // （歩き始めの近くなら、左右を反転して左へ傾ける）
                MarkerComposable(Fieldbook.dark, spread, state = nowState, anchor = FlagAnchor, zIndex = 5f, title = "ゴール") {
                    FlagMarker(GoalFlagColor, mirrored = spread, tilt = if (spread) -FLAG_SPREAD_DEGREES else 0f)
                }
            }
        }
        // 記録していない間：今いる場所に青い点と、向いている方位の扇。位置のずれの大きさを薄い円で
        if (showIdleDot && idle != null) {
            val here = LatLng(idle.lat, idle.lng)
            if (idle.accuracyM > 0f) {
                Circle(
                    center = here,
                    radius = idle.accuracyM.toDouble(),
                    fillColor = Fieldbook.Survey.copy(alpha = 0.12f),
                    strokeColor = Fieldbook.Survey.copy(alpha = 0.3f),
                    strokeWidth = 2f,
                    zIndex = 3f,
                )
            }
            BeamMarker(heading, here)
            val dotState = rememberMarkerState()
            dotState.position = here
            MarkerComposable(Fieldbook.dark, state = dotState, anchor = Offset(0.5f, 0.5f), zIndex = 4.5f, title = "いまの場所") {
                MyDotMarker()
            }
        }
    }
}

/**
 * [at] に、向いている方位の扇を敷く（足跡・青い点の下）。方位が分からない（センサーが無い・止めている）ときは出さない。
 * 方位は 1 秒に何度も変わるので、ここだけで読む（地図の中身全体を組み立て直さないように）
 */
@Composable
private fun BeamMarker(heading: State<Float?>, at: LatLng) {
    val deg = heading.value ?: return
    val state = rememberMarkerState()
    state.position = at
    MarkerComposable(
        Fieldbook.dark,
        state = state,
        anchor = Offset(0.5f, 0.5f),
        flat = true,
        rotation = deg,
        zIndex = 3.5f,
        title = "向いている方向",
    ) {
        HeadingBeamMarker()
    }
}

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
        // ゴールが歩き始めの近く（画面の上で旗の幅くらい）なら、2 本が重なって見えないよう左右に開く
        val spread = !recording && (goalAt - startAt).getDistance() < FlagWidth.toPx()
        val tilt = if (spread) FLAG_SPREAD_DEGREES else 0f
        if (!recording) drawFlag(goalAt, GoalFlagColor, mirrored = spread, tilt = -tilt)
        drawFlag(startAt, StartFlagColor, tilt = tilt)
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
