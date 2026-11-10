package com.rein4ce.roadcollector.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rein4ce.roadcollector.core.challenge.ChallengeRun
import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.ChallengeStorage
import com.rein4ce.roadcollector.data.rankOf
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.ui.common.modeInkOr
import com.rein4ce.roadcollector.ui.common.modeFaceOr
import com.rein4ce.roadcollector.ui.common.PressButton
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER
import com.rein4ce.roadcollector.ui.common.PopChip
import com.rein4ce.roadcollector.ui.common.PopButton
import com.rein4ce.roadcollector.ui.common.PressOutlinedButton
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.color
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.common.SkyText
import kotlinx.coroutines.isActive
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import java.util.Locale



/**
 * 90 秒チャレンジ（前は 3 分チャレンジ。決定 75）の画面。通常のモードと同じく、上から「情報（残り時間・高さ・次の形）」「キャンバス」「操作」の 3 段。
 *
 * - 開いたらすぐ始まる（始めるボタンと、始める前の説明の画面は無い。決定 69）。遊び方は自分で落とすだけ
 *   （通常のモードと同じく、ドラッグして指を離すと落ちる。自動で落ちるはやめた）
 * - 画面を離れたら（「‹ 戻る」・ほかのタブ）、その回は捨てる。次に開いたときは、その前の段階を引き継がず新しい回から（決定 69）。
 *   アプリが裏に回ったときは、時計と物理を止めて、戻ったら続きから（画面を離れたことにはしない）
 * - 情報の段の「模型の高さ」は通常のモードと同じ大きな数字（HeightMeter）。「次」の形は、キャンバスの右上に重ねる紙の札（決定 75）
 * - 新しい回は、画面が切り替わってから 1 秒置いて「3・2・1」を数え、数え終えてから時計が動く（決定 76）
 * - キャンバス：縮尺は変えず（通常のモードと同じ 1 m の大きさ）、台はキャンバスの下のほう（上から 78%）。塔が高くなったら、
 *   次の形が出てくる高さが入るところまで、視点を少しずつ（0.4 秒かけて）上げる（StackViewport.follow）。引かない
 * - 毎コマ変わるのは塔の図形（キャンバスの描画の中だけで読む）と、秒が変わったときの残り時間（時計の字だけ）。
 *   画面全体は、操作したときと次の形に進んだときだけ組み立て直す（#78 のレビュー）
 * - 「やり直す」は確かめの窓を出し、出している間は時計と物理を止める（「中断」は「‹ 戻る」と同じになるのでやめた）
 * - 地は塗らない。ほかの画面と同じ方眼の紙と今の天気（決定 59）
 *
 * @param leading 上の行の左に置くもの。遊び方を選ぶ画面（ModeSelect.kt の ModePlayPage）から「‹ 戻る」と今のモードの札が来る。
 *   compact は小さい画面（積む画面の高さが 600dp 未満。札を出さず「‹ 戻る」だけにする）
 */
@Composable
internal fun ChallengeScreen(leading: @Composable (compact: Boolean) -> Unit = {}, onGoRanking: () -> Unit = {}) {
    val directory = LocalContext.current.applicationContext.filesDir
    val factory = remember(directory) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChallengeStorage(directory).let { storage ->
                    // 記録になった回の塔は、自動で「みんな」の 3 分チャレンジのランキングに送る（決定 77）
                    ChallengeViewModel(storage::update, loadRecords = storage::ranking, sendTower = { ApiClient.postChallengeTower(it) })
                } as T
        }
    }
    val vm: ChallengeViewModel = viewModel(factory = factory)
    val frame = vm.frame
    // 「やり直す」の確かめの窓を出しているところ
    var confirming by rememberSaveable { mutableStateOf(false) }

    // 開いたらすぐ始める（決定 69）。画面を離れて捨てた回のあとも、次に開いたときにここで新しい回が始まる
    LaunchedEffect(frame == null) {
        if (vm.frame == null) vm.start()
    }
    // 画面を離れたら（「‹ 戻る」・ほかのタブ）、その回を捨てる（決定 69）。画面の作り直し（ダークモードの切り替えなど）では捨てない
    val activity = LocalActivity.current
    DisposableEffect(vm) {
        onDispose { if (activity?.isChangingConfigurations != true) vm.leave() }
    }

    // フレームのループ。画面が前に出ている間だけ回す（裏・別のタブ・通常のモードでは時計も物理も止まる）。
    // 確かめの窓を出している間も止める。結果の保存は ViewModel が持つので、ここで止めても保存は続く
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm.generation, lifecycle, confirming) {
        if (vm.frame == null || confirming) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 新しい回は、始まる前に「3・2・1」を 1 秒ずつ出してから時計を動かす（決定 76）。数えている途中で裏に回ったら、戻ったときに 3 から数え直す。
            // 画面が切り替わってすぐに「3」が出ると早すぎるので、一呼吸（[COUNTDOWN_LEAD_MS]）置いてから数え始める（その間も時計と操作は止めたまま）
            if (vm.needsCountdown) {
                try {
                    delay(COUNTDOWN_LEAD_MS)
                    for (n in COUNTDOWN_FROM downTo 1) {
                        vm.showCountdown(n)
                        delay(COUNTDOWN_STEP_MS)
                    }
                    vm.finishCountdown()
                } finally {
                    // 途中で止められたとき（「やり直す」の確かめの窓・裏に回る・画面を回す）に、数字を残さない（#130 の C のレビュー）
                    vm.stopCountdown()
                }
            }
            vm.resume()
            try {
                while (isActive && vm.frame?.terminal == false) withFrameNanos { vm.tick() }
            } finally {
                vm.pause()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT
        if (frame == null) {
            // 開いてすぐ始まるまでの一瞬だけ。上の行の「‹ 戻る」だけを出しておく
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) { leading(compact) }
            return@BoxWithConstraints
        }
        // 操作の段は中身の高さだけ（上限は画面の 45%。超える中身はスクロール）。前は中身にかかわらず 45% を取っていて、
        // 台が画面のまん中あたりに来て、下が大きく空いていた（#78 のレビュー）。1 回の中では縮めない（キャンバスがガタつかないように）
        val controlsMaxHeight = maxHeight * CONTROLS_MAX_FRACTION
        val density = LocalDensity.current
        var controlsMinHeightDp by rememberSaveable(vm.generation) { mutableStateOf(0f) }
        Column(Modifier.fillMaxSize()) {
            // 1 段目：情報。上の行の左に「‹ 戻る」（leading）、右に主役の残り時間（通常のモードの「模型の高さ」と同じ形の、太い丸ゴシックの数字）。
            // 次の行に「模型の高さ」と「次」の形。「‹ 戻る」の行を別に足さず、残り時間と同じ行に置く（塔を描く所を狭くしないように）。
            // 字は通常のモードの情報の段と同じく、方眼紙の上に縁取りで浮かせる丸ゴシック（SkyText）。見出しは薄い墨、値は墨（決定 67）
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading(compact)
                ChallengeClock(vm, compact)
            }
            // 「模型の高さ」は通常のモードと同じ大きな数字（HeightMeter。置いて高さが変わるとメーターのように回る）
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                HeightMeter(frame.towerHeight.toFloat(), compact)
            }

            // 2 段目：キャンバス。右上に「次」の形の札を重ねる（決定 75。前は情報の段の右端に、背景なしで出していた）
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ChallengeCanvas(vm, frame, Modifier.fillMaxSize().clipToBounds())
                if (!frame.terminal) NextPiece(frame.next, compact, Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp))
                // 始まる前の「3・2・1」（決定 76）
                if (vm.countdown > 0) CountdownNumber(vm.countdown, Modifier.align(Alignment.Center))
            }

            // 3 段目：操作
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = controlsMaxHeight)
                    .onSizeChanged { size ->
                        val h = with(density) { size.height.toDp() }.value
                        if (h > controlsMinHeightDp) controlsMinHeightDp = h
                    }
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = controlsMinHeightDp.dp.coerceAtMost(controlsMaxHeight))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (frame.phase) {
                        ChallengeRun.Phase.AIMING -> {
                            AimControls(vm)
                            RunControls(onRestart = { confirming = true })
                        }
                        ChallengeRun.Phase.FALLING -> {
                            // 狙うときの「回す」の行と同じ高さを「落下中…」で取っておき、下の「やり直す」を動かさない
                            // （前は「置く」があった場所に「中断」が来て、続けて「置く」を押すと確かめの窓が開いていた）
                            AimHint(visible = false)
                            Box(Modifier.fillMaxWidth().heightIn(min = AIM_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                                SkyText("落下中…", color = Fieldbook.Muted, fontSize = 14.sp)
                            }
                            RunControls(onRestart = { confirming = true })
                        }
                        ChallengeRun.Phase.FINALIZING ->
                            SkyText("時間です。塔が落ち着くのを待っています…", color = Fieldbook.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        ChallengeRun.Phase.FINISHED, ChallengeRun.Phase.UNRESOLVED -> ChallengeResult(vm, frame, onGoRanking)
                    }
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("やり直しますか？") },
            text = { Text("この回は記録に残さず、はじめから始めます。") },
            confirmButton = {
                PressTextButton(onClick = {
                    confirming = false
                    vm.restart()
                }) { Text("やり直す") }
            },
            dismissButton = {
                PressTextButton(onClick = { confirming = false }) { Text("続ける") }
            },
        )
    }
}

/**
 * 始まる前の「3・2・1」の数（決定 76）。塔を描く所のまん中に、大きなオレンジの太い丸ゴシックで、紙の色の縁取り（SkyText）を付けて出す。
 * 数が変わるたびに、少し大きく出てから縮んで収まる（動きを減らす設定なら、動かさない）。読み上げでも数を伝える
 */
@Composable
private fun CountdownNumber(n: Int, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val scale = remember(n) { Animatable(if (reduceMotion) 1f else 1.6f) }
    LaunchedEffect(n) { if (!reduceMotion) scale.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing)) }
    val size = with(LocalDensity.current) { 96.dp.toSp() }
    SkyText(
        "$n",
        color = modeInkOr(if (Fieldbook.dark) Pop.Orange else Pop.OrangeDeep),
        fontSize = size,
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .semantics { liveRegion = LiveRegionMode.Assertive; contentDescription = "$n" },
    )
}

/** 「3・2・1」をいくつから数えるか */
private const val COUNTDOWN_FROM = 3

/** 「3・2・1」の 1 つの数を出しておく時間 [ms] */
private const val COUNTDOWN_STEP_MS = 1000L

/** 画面が切り替わってから「3」を出すまでの間 [ms]（前は切り替わってすぐに出ていて早すぎた。B の希望） */
private const val COUNTDOWN_LEAD_MS = 1000L

/**
 * 「次」の形の札（決定 69・75）。塔を描く所の右上に重ねる、紙のカード（「みんな」・コレクションのカードと同じ面・縁・側面）に、
 * 上から「次」の見出し・形の絵（大きめ）・形の名前。背景のカードがあるので、ほかの字や積み木と分かれて「次」だと分かる。
 * 大きさは dp で決め、文字の大きさの設定では大きくしない（塔を描く所に重ねるので、大きくすると塔が隠れる）
 */
@Composable
private fun NextPiece(next: Part, compact: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    fun fixed(dp: Float) = with(density) { dp.dp.toSp() }
    // 形が分かるように大きめに（決定 75。前は 40dp・小さい画面で 30dp）
    val icon = if (compact) 40.dp else 56.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .popBlock(face = Fieldbook.Card, corner = 12.dp, depth = 3.dp, edge = Fieldbook.CardEdge)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "次の形 ${next.name ?: ""}" },
    ) {
        Text("次", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = fixed(if (compact) 12f else 13f), color = Fieldbook.Muted)
        Canvas(Modifier.size(icon)) {
            // 置いたときと同じ向き（決まった形の色）
            drawPart(next, Offset(size.width / 2, size.height / 2), size.width * 0.75f)
        }
        Text(next.name ?: "", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = fixed(if (compact) 12f else 13f), color = Fieldbook.Ink, maxLines = 1, softWrap = false)
    }
}

/**
 * 残り時間（「残り 02:37」）。秒が変わったときだけ、この字だけを組み立て直す。
 * 通常のモードの「模型の高さ」（HeightMeter）と同じ形：見出しは墨、数字はオレンジの太い丸ゴシックで、方眼紙の上に縁取りで浮かせる（決定 67）。
 * 残り 10 秒からは数字を赤にする。大きさは dp で決め、文字の大きさの設定では大きくしない（「‹ 戻る」と同じ行に入れるため）
 */
@Composable
private fun ChallengeClock(vm: ChallengeViewModel, compact: Boolean) {
    val seconds = vm.remainingSeconds
    val minutes = seconds / 60
    val rest = seconds % 60
    val density = LocalDensity.current
    fun fixed(dp: Float) = with(density) { dp.dp.toSp() }
    // ダークモードでは濃いオレンジが暗い紙に沈むので、明るいオレンジにする（HeightMeter と同じ）
    val number = when {
        seconds <= 10 -> LastSecondsRed
        else -> modeInkOr(if (Fieldbook.dark) Pop.Orange else Pop.OrangeDeep)
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "残り $minutes 分 $rest 秒" },
    ) {
        SkyText("残り", color = Fieldbook.Ink, fontSize = fixed(if (compact) 13f else 15f), modifier = Modifier.padding(end = 8.dp, bottom = if (compact) 3.dp else 5.dp))
        // 数字は幅をそろえる（tnum）。秒が変わるたびに字の幅が変わって、位置が揺れないように
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) {
            SkyText(
                String.format(Locale.ROOT, "%02d:%02d", minutes, rest),
                color = number,
                fontSize = fixed(if (compact) 24f else 30f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * キャンバス。ワールド座標（m、y 上向き、台の上面が y=0）からの変換は通常のモードと同じ [StackViewport]（zoom は 1 のまま）。
 * 塔の図形は描画の中だけで読む（毎コマ変わっても、描き直すだけで組み立て直さない）
 */
@Composable
private fun ChallengeCanvas(vm: ChallengeViewModel, frame: ChallengeFrame, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val guide = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.13f)
    val platform = if (dark) Color(0xFF7A7FA6) else Color(0xFF4A4E69)
    // 模型の高さの目盛（通常のモードと同じ。HeightRuler.kt。決定 75）
    val ruler = rememberHeightRuler()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val target = StackViewport.follow(canvasSize.width.toFloat(), canvasSize.height.toFloat(), frame.cameraTop.toFloat())
    val cameraY = remember(vm.generation) { Animatable(0f) }
    var cameraPlaced by remember(vm.generation) { mutableStateOf(false) }
    LaunchedEffect(target.cameraY, canvasSize, vm.generation) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        if (!cameraPlaced) {
            // 最初に大きさが決まったとき（タブを戻ったときも）は、動かさずに合わせる
            cameraY.snapTo(target.cameraY)
            cameraPlaced = true
        } else {
            cameraY.animateTo(target.cameraY, tween(durationMillis = CAMERA_ANIMATION_MS, easing = FastOutSlowInEasing))
        }
    }
    // 横にドラッグすると、前の ◀▶ と同じ 10 cm きざみで動く（決定 67）。遊べるのは自分で落とすだけなので（決定 69）、
    // 指を離すとその位置で落ちる（通常のモードと同じ）。落とすのは狙っている間だけ（core の place が見る）
    val steps = remember(vm.generation) { DragSteps(MOVE_STEP_CHALLENGE_M) }
    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .aimByDrag(
                key = vm.generation,
                // 「3・2・1」の間は狙えない（決定 76）
                canAim = { vm.canAim },
                onDrag = { dx ->
                    val viewport = StackViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat(), zoom = 1f, cameraY = cameraY.value)
                    val n = steps.add(viewport.dragToMeters(dx).toDouble())
                    if (n != 0) vm.move(n * MOVE_STEP_CHALLENGE_M)
                },
                onRelease = {
                    steps.reset()
                    vm.place()
                },
            )
            .semantics {
                contentDescription = "塔。模型の高さ %.2f メートル".format(Locale.JAPAN, frame.towerHeight)
                // 読み上げ（TalkBack）ではドラッグできないので、キャンバスの操作で左右に動かし、落とせるようにする（前の ◀▶ と「置く」の代わり）
                // 「3・2・1」の間（とその前）は出さない（押しても何も起きないのに、読み上げで「した」と伝わるため。#130 の C のレビュー）
                if (vm.canAim) {
                    customActions = listOf(
                        CustomAccessibilityAction("左へ 10 cm") { vm.move(-MOVE_STEP_CHALLENGE_M); true },
                        CustomAccessibilityAction("右へ 10 cm") { vm.move(MOVE_STEP_CHALLENGE_M); true },
                        CustomAccessibilityAction("ここで落とす") { vm.place(); true },
                    )
                }
            },
    ) {
        val viewport = StackViewport(size.width, size.height, zoom = 1f, cameraY = cameraY.value)
        fun at(x: Double, y: Double) = Offset(viewport.toScreenX(x.toFloat()), viewport.toScreenY(y.toFloat()))
        // 目盛（塔と同じ変換なので、視点が上がっても塔の高さとずれない）。形の下になるように先に描く
        drawHeightRuler(viewport, ruler)
        // 落下ライン（通常のモードと同じ。ここより下に落ちた形は消える）
        drawLine(guide, Offset(0f, viewport.toScreenY(-1.5f)), Offset(size.width, viewport.toScreenY(-1.5f)), strokeWidth = 2f)
        for (shape in vm.shapes) {
            val color = shape.part?.kind?.color() ?: platform
            when (shape) {
                is StackWorld.RenderShape.Poly -> {
                    val path = Path()
                    shape.xs.indices.forEach { i ->
                        val p = at(shape.xs[i], shape.ys[i])
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path, color)
                }
                is StackWorld.RenderShape.Disc -> drawCircle(color, (shape.r * viewport.scale).toFloat(), at(shape.cx, shape.cy))
            }
        }
        if (frame.phase == ChallengeRun.Phase.AIMING) {
            // 狙っている形の高さ（vm.ghostY）は描画の中だけで読む。自分で落とすときは出てくる高さのまま
            drawPart(frame.current, at(frame.x, vm.ghostY.toDouble()), viewport.scale, Math.toRadians(frame.angle).toFloat(), alpha = 0.45f)
        }
    }
}

/**
 * 狙う操作：「回す」のボタン 1 つだけ（決定 67。通常のモードと同じ）。位置はキャンバスのドラッグで決め（10 cm きざみ）、
 * 指を離すと落ちる（[ChallengeCanvas]）。回すのは時計回りだけで、押している間なめらかに回る（きざみは無い。決定 71）。
 * 行の高さは [AIM_ROW_HEIGHT]（落下中の「落下中…」と同じ。下の「やり直す」が動かないように）
 */
@Composable
private fun AimControls(vm: ChallengeViewModel) {
    // 「置く」のボタンが無いので、落とし方を一言で知らせる
    AimHint()
    Box(Modifier.fillMaxWidth().heightIn(min = AIM_ROW_HEIGHT), contentAlignment = Alignment.Center) {
        RotateButton(
            RotateDirection.RIGHT,
            sessionKey = vm.generation,
            onRotate = { vm.rotate(it.toDouble()) },
            modifier = Modifier.width(TURN_BUTTON_WIDTH),
            text = TURN_BUTTON_TEXT,
            label = TURN_BUTTON_LABEL,
        )
    }
}

/**
 * 狙う操作の上の一言（決定 67）。遊べるのは自分で落とすだけなので「指を離すと落ちる」（決定 69）。
 * 落下中も同じ字を見えなくして置き、行の高さをそろえる（[visible] が false）
 */
@Composable
private fun AimHint(visible: Boolean = true) {
    // 通常のモードの置き方の一言と同じ字（縁取りの丸ゴシック・薄い墨）
    SkyText(
        "ドラッグして、指を離すと落ちる",
        color = Fieldbook.Muted,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = if (visible) Modifier.padding(bottom = 4.dp) else Modifier.padding(bottom = 4.dp).alpha(0f).clearAndSetSemantics {},
    )
}

/**
 * 遊んでいる間の「やり直す」（確かめてから。その回は記録に残さない）。
 * 「中断」はやめた（上の「‹ 戻る」で同じことになる。画面を離れたら、その回は捨てる。決定 69）
 */
@Composable
private fun RunControls(onRestart: () -> Unit) {
    // 通常のモードの「やり直す」と同じ、紙の面に青緑の縁の小さな札（決定 67）
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
        PopChip("やり直す", face = Fieldbook.Card, text = modeInkOr(Pop.TealLine), edge = modeInkOr(Pop.TealLine), onClick = onRestart)
    }
}

/** 結果のカード：記録と順位、保存のようす、「みんな」に送ったようす、「もう一度」と「ランキング」、この端末の記録 */
@Composable
private fun ChallengeResult(vm: ChallengeViewModel, frame: ChallengeFrame, onGoRanking: () -> Unit) {
    val score = frame.score
    // 通常のモードの終わりのカードと同じ、紙の面（縁と下にのぞく側面）。字は太い丸ゴシック、ボタンはブロック（決定 67）
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (score != null) {
            Text("記録 %.2f m".format(Locale.JAPAN, score.height), fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Fieldbook.Ink)
            if (vm.ranking.any { it.runId == score.runId }) {
                Text("この端末で ${rankOf(score.height, vm.ranking)} 位", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 13.sp, color = Fieldbook.Muted)
            }
        } else {
            Text("揺れが止まりませんでした", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Fieldbook.Ink)
            Text("塔が落ち着かなかったので、記録には残しません。", style = MaterialTheme.typography.bodyMedium, color = Fieldbook.Muted, textAlign = TextAlign.Center)
        }
        SaveState(vm)
        if (score != null) SendState(vm)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PopButton("もう一度", onClick = { vm.start() }, face = modeFaceOr(Pop.OrangeDeep), text = Pop.Letter, enabled = vm.canStart)
            // 「みんな」の 3 分チャレンジのランキングへ（通常の終わりのカードの「ランキング」と同じ紺の縁。決定 77）
            PopButton("ランキング", onClick = onGoRanking, face = Fieldbook.Card, text = modeInkOr(rankingInk()), edge = modeInkOr(rankingInk()))
        }
        Spacer(Modifier.height(8.dp))
        Records(vm.ranking, highlight = score?.runId)
    }
}

/**
 * 「みんな」に送ったようす（決定 77）：送っています・ランキングに出しました・送れませんでした（「もう一度送る」）。
 * 記録になった回の塔は、結果が出たら自動で送る
 */
@Composable
private fun SendState(vm: ChallengeViewModel) {
    when (vm.sendState) {
        ChallengeViewModel.SendState.IDLE -> Unit
        ChallengeViewModel.SendState.SENDING -> Text("ランキングに送っています…", style = MaterialTheme.typography.bodySmall, color = Fieldbook.Muted)
        ChallengeViewModel.SendState.SENT -> Text("ランキングに出しました", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 13.sp, color = modeInkOr(Pop.TealLine))
        ChallengeViewModel.SendState.FAILED -> {
            Text("ランキングに送れませんでした", style = MaterialTheme.typography.bodySmall, color = Fieldbook.Muted)
            PopChip("もう一度送る", face = Fieldbook.Card, text = modeInkOr(Pop.TealLine), edge = modeInkOr(Pop.TealLine), onClick = vm::resend)
        }
    }
}

/** 終わりのカードの「ランキング」の字と縁の色（「みんな」のタブの紺。ダークモードでは明るい紺。通常の終わりのカードと同じ） */
@Composable
private fun rankingInk(): Color = if (Fieldbook.dark) Color(0xFF9DA2C8) else Pop.Navy

/** 記録の保存のようす（保存中・保存できなかった）。保存できなかったら「再試行」 */
@Composable
private fun SaveState(vm: ChallengeViewModel) {
    if (vm.saving) Text("記録を保存しています…", style = MaterialTheme.typography.bodySmall)
    if (vm.saveError) {
        Text(
            if (vm.resultUnsaved) "記録を保存できませんでした。このまま次に進むと、この回の記録は残りません。" else "記録を読み込めませんでした。",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        PopChip("再試行", face = Fieldbook.Card, text = modeInkOr(Pop.TealLine), edge = modeInkOr(Pop.TealLine), onClick = vm::refreshRecords)
    }
}

/**
 * この端末の記録（90 秒チャレンジ）の上位 10 件。「みんな」のランキングと取り違えないよう「この端末の記録」と呼ぶ。
 * 遊べるのは自分で落とすだけなので、その決まりの記録だけを出す（決定 69。ほかの決まりの記録は消さずに残っている）
 */
@Composable
private fun Records(ranking: List<ChallengeScore>, highlight: String?) {
    Text("この端末の記録（90 秒チャレンジ）", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Fieldbook.Ink)
    if (ranking.isEmpty()) {
        Text("まだ記録がありません", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        return
    }
    ranking.take(10).forEach { record ->
        val mine = record.runId == highlight
        Text(
            "${rankOf(record.height, ranking)} 位　%.2f m".format(Locale.JAPAN, record.height),
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = if (mine) Pop.MineInk else Fieldbook.Ink,
        )
    }
}

/** ドラッグで動かすきざみ [m]（前の ◀▶ の 1 回と同じ。チャレンジの決まり。決定 67）。読み上げの「左へ」「右へ」も同じ量 */
private const val MOVE_STEP_CHALLENGE_M = 0.1

/** 「回す」のボタンの幅（通常のモードと同じ。画面いっぱいには広げない） */
private val TURN_BUTTON_WIDTH = 220.dp

/** 狙う操作の行の高さ（「回す」のボタンの押せる高さ 48dp と、積み木の下にのぞく側面 4dp）。落下中もこの高さを取っておく */
private val AIM_ROW_HEIGHT = 52.dp

/** 残り 10 秒からの時計の色 */
private val LastSecondsRed = Color(0xFFD64545)

/** 操作の段の高さの上限（画面の高さに対する割合） */
private const val CONTROLS_MAX_FRACTION = 0.45f

/** これより低い画面（端末のバーとタブバーを除いた高さ）では、上の行の「‹ 戻る」の隣にモードの札を出さない（積む画面と同じ） */
private val COMPACT_HEIGHT = 600.dp

/** カメラを上げるときにかける時間 [ms]（通常のモードと同じ） */
private const val CAMERA_ANIMATION_MS = 400
