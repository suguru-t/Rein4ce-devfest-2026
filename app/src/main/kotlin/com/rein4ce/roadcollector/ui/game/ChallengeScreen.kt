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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import com.rein4ce.roadcollector.core.challenge.ChallengeStyle
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.data.ChallengeStorage
import com.rein4ce.roadcollector.data.rankOf
import com.rein4ce.roadcollector.ui.common.PressButton
import com.rein4ce.roadcollector.ui.common.PressOutlinedButton
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.color
import com.rein4ce.roadcollector.ui.common.drawPart
import kotlinx.coroutines.isActive
import java.util.Locale


/** 確かめてから行うこと（どちらも、その回は記録に残さない） */
private enum class ChallengeConfirm { RESTART, ABANDON }

/**
 * 3分チャレンジの画面。通常のモードと同じく、上から「情報（残り時間・高さ・次の形）」「キャンバス」「操作」の 3 段。
 *
 * - キャンバス：縮尺は変えず（通常のモードと同じ 1 m の大きさ）、台はキャンバスの下のほう（上から 78%）。塔が高くなったら、
 *   次の形が出てくる高さが入るところまで、視点を少しずつ（0.4 秒かけて）上げる（StackViewport.follow）。引かない
 * - 毎コマ変わるのは塔の図形（キャンバスの描画の中だけで読む）と、秒が変わったときの残り時間（時計の字だけ）。
 *   画面全体は、操作したときと次の形に進んだときだけ組み立て直す（#78 のレビュー）
 * - 「やり直す」「中断」は確かめの窓を出し、出している間は時計と物理を止める
 * - 地は塗らない。ほかの画面と同じ方眼の紙と今の天気（決定 59）
 *
 * @param leading 上の行の左に置くもの。遊び方を選ぶ画面（ModeSelect.kt の ModePlayPage）から「‹ 戻る」と今のモードの札が来る。
 *   compact は小さい画面（積む画面の高さが 600dp 未満。札を出さず「‹ 戻る」だけにする）
 */
@Composable
internal fun ChallengeScreen(leading: @Composable (compact: Boolean) -> Unit = {}) {
    val directory = LocalContext.current.applicationContext.filesDir
    val factory = remember(directory) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChallengeStorage(directory).let { storage -> ChallengeViewModel(storage::update, loadRecords = storage::ranking) } as T
        }
    }
    val vm: ChallengeViewModel = viewModel(factory = factory)
    val frame = vm.frame
    var confirming by rememberSaveable { mutableStateOf<ChallengeConfirm?>(null) }

    // フレームのループ。画面が前に出ている間だけ回す（裏・別のタブ・通常のモードでは時計も物理も止まる）。
    // 確かめの窓を出している間も止める。結果の保存は ViewModel が持つので、ここで止めても保存は続く
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm.generation, lifecycle, confirming) {
        if (vm.frame == null || confirming != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
            // 始める前の説明の画面でも、上の行に「‹ 戻る」（遊び方を選ぶ画面へ）
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) { leading(compact) }
                Box(Modifier.weight(1f)) { ChallengeIntro(vm) }
            }
            return@BoxWithConstraints
        }
        val ink = MaterialTheme.colorScheme.onBackground
        // 操作の段は中身の高さだけ（上限は画面の 45%。超える中身はスクロール）。前は中身にかかわらず 45% を取っていて、
        // 台が画面のまん中あたりに来て、下が大きく空いていた（#78 のレビュー）。1 回の中では縮めない（キャンバスがガタつかないように）
        val controlsMaxHeight = maxHeight * CONTROLS_MAX_FRACTION
        val density = LocalDensity.current
        var controlsMinHeightDp by rememberSaveable(vm.generation) { mutableStateOf(0f) }
        Column(Modifier.fillMaxSize()) {
            // 1 段目：情報。上の行の左に「‹ 戻る」（leading）、右に主役の残り時間（通常のモードの「模型の高さ」と同じ大きさ。headlineSmall）。
            // 次の行に「模型の高さ」と「次」の形。「‹ 戻る」の行を別に足さず、残り時間と同じ行に置く（塔を描く所を狭くしないように）
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading(compact)
                ChallengeClock(vm, ink)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("模型の高さ %.2f m".format(Locale.JAPAN, frame.towerHeight), style = MaterialTheme.typography.bodyMedium, color = ink)
                if (!frame.terminal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("次 ", style = MaterialTheme.typography.bodyMedium, color = ink)
                        Canvas(Modifier.size(22.dp)) {
                            drawPart(frame.next, Offset(size.width / 2, size.height / 2), size.width * 0.7f)
                        }
                        Text(" ${frame.next.name ?: ""}", style = MaterialTheme.typography.bodyMedium, color = ink)
                    }
                }
            }

            // 2 段目：キャンバス
            ChallengeCanvas(vm, frame, Modifier.fillMaxWidth().weight(1f).clipToBounds())

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
                            RunControls(onRestart = { confirming = ChallengeConfirm.RESTART }, onAbandon = { confirming = ChallengeConfirm.ABANDON })
                        }
                        ChallengeRun.Phase.FALLING -> {
                            // 狙うときの「◀ ⟲ 置く ⟳ ▶」の行と同じ高さを「落下中…」で取っておき、下の「やり直す」「中断」を
                            // 動かさない（前は「置く」があった場所に「中断」が来て、続けて「置く」を押すと確かめの窓が開いていた）
                            Box(Modifier.fillMaxWidth().heightIn(min = AIM_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                                Text("落下中…", style = MaterialTheme.typography.bodyMedium, color = ink)
                            }
                            RunControls(onRestart = { confirming = ChallengeConfirm.RESTART }, onAbandon = { confirming = ChallengeConfirm.ABANDON })
                        }
                        ChallengeRun.Phase.FINALIZING ->
                            Text("時間です。塔が落ち着くのを待っています…", style = MaterialTheme.typography.bodyMedium, color = ink, textAlign = TextAlign.Center)
                        ChallengeRun.Phase.FINISHED, ChallengeRun.Phase.UNRESOLVED -> ChallengeResult(vm, frame)
                    }
                }
            }
        }
    }

    confirming?.let { which ->
        val restart = which == ChallengeConfirm.RESTART
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (restart) "やり直しますか？" else "中断しますか？") },
            text = { Text(if (restart) "この回は記録に残さず、はじめから始めます。" else "この回は記録に残さず、はじめの画面に戻ります。") },
            confirmButton = {
                PressTextButton(onClick = {
                    confirming = null
                    if (restart) vm.restart() else vm.abandon()
                }) { Text(if (restart) "やり直す" else "中断する") }
            },
            dismissButton = {
                PressTextButton(onClick = { confirming = null }) { Text("続ける") }
            },
        )
    }
}

/** 残り時間（「残り 02:37」）。秒が変わったときだけ、この字だけを組み立て直す。残り 10 秒からは赤 */
@Composable
private fun ChallengeClock(vm: ChallengeViewModel, ink: Color) {
    val seconds = vm.remainingSeconds
    val minutes = seconds / 60
    val rest = seconds % 60
    Text(
        "残り " + String.format(Locale.ROOT, "%02d:%02d", minutes, rest),
        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
        color = if (seconds <= 10) LastSecondsRed else ink,
        modifier = Modifier.semantics { contentDescription = "残り $minutes 分 $rest 秒" },
    )
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
    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = "塔。模型の高さ %.2f メートル".format(Locale.JAPAN, frame.towerHeight) },
    ) {
        val viewport = StackViewport(size.width, size.height, zoom = 1f, cameraY = cameraY.value)
        fun at(x: Double, y: Double) = Offset(viewport.toScreenX(x.toFloat()), viewport.toScreenY(y.toFloat()))
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
            // 狙っている形の高さは、自動で落ちるときは毎コマ下がるので、描画の中だけで読む（vm.ghostY）
            drawPart(frame.current, at(frame.x, vm.ghostY.toDouble()), viewport.scale, Math.toRadians(frame.angle).toFloat(), alpha = 0.45f)
        }
    }
}

/**
 * 狙う操作：「◀ ⟲ 15° 置く ⟳ 15° ▶」を 1 行（通常のモードの並びにそろえる）。◀▶ は 1 回 [MOVE_STEP_CHALLENGE_M]
 */
@Composable
private fun AimControls(vm: ChallengeViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressOutlinedButton(
            onClick = { vm.move(-MOVE_STEP_CHALLENGE_M) },
            modifier = Modifier.width(ARROW_BUTTON_WIDTH).semantics { contentDescription = "左へ 10cm" },
            contentPadding = PaddingValues(0.dp),
        ) { Text("◀") }
        // 回転は押し続けると回り続ける（#114。15° きざみのままなので、決まり（記録の ID）は変わらない）
        RotateButton(
            RotateDirection.LEFT,
            sessionKey = vm.generation,
            onRotate = { vm.rotate(it.toDouble()) },
            modifier = Modifier.weight(1.2f),
            contentPadding = AimButtonPadding,
        )
        PressButton(
            onClick = { vm.place() },
            modifier = Modifier.weight(1f),
            contentPadding = AimButtonPadding,
        ) { Text("置く") }
        RotateButton(
            RotateDirection.RIGHT,
            sessionKey = vm.generation,
            onRotate = { vm.rotate(it.toDouble()) },
            modifier = Modifier.weight(1.2f),
            contentPadding = AimButtonPadding,
        )
        PressOutlinedButton(
            onClick = { vm.move(MOVE_STEP_CHALLENGE_M) },
            modifier = Modifier.width(ARROW_BUTTON_WIDTH).semantics { contentDescription = "右へ 10cm" },
            contentPadding = PaddingValues(0.dp),
        ) { Text("▶") }
    }
}

/** 遊んでいる間の「やり直す」「中断」（どちらも確かめてから。その回は記録に残さない） */
@Composable
private fun RunControls(onRestart: () -> Unit, onAbandon: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PressTextButton(onClick = onRestart) { Text("やり直す") }
        PressTextButton(onClick = onAbandon) { Text("中断") }
    }
}

/** 結果のカード：記録と順位、保存のようす、「もう一度」、この端末の記録 */
@Composable
private fun ChallengeResult(vm: ChallengeViewModel, frame: ChallengeFrame) {
    val score = frame.score
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (score != null) {
                Text("記録 %.2f m".format(Locale.JAPAN, score.height), style = MaterialTheme.typography.titleLarge)
                if (vm.ranking.any { it.runId == score.runId }) {
                    Text("この端末で ${rankOf(score.height, vm.ranking)} 位", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("揺れが止まりませんでした", style = MaterialTheme.typography.titleLarge)
                Text("塔が落ち着かなかったので、記録には残しません。", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            SaveState(vm)
            Spacer(Modifier.height(12.dp))
            PressButton(onClick = { vm.start() }, enabled = vm.canStart) { Text("もう一度") }
            Spacer(Modifier.height(8.dp))
            Records(vm.ranking, highlight = score?.runId, style = vm.style)
        }
    }
}

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
        PressTextButton(onClick = vm::refreshRecords) { Text("再試行") }
    }
}

/** 始める前の画面：説明、「3分チャレンジを始める」、この端末の記録 */
@Composable
private fun ChallengeIntro(vm: ChallengeViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("3分チャレンジ", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "3 分間で、決まった 5 種類の形をどれだけ高く積めるか。画面を離れると一時停止します。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        StylePicker(vm.style, onPick = vm::selectStyle)
        Spacer(Modifier.height(8.dp))
        Text(
            vm.style.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PressButton(onClick = { vm.start() }, enabled = vm.canStart) { Text("3分チャレンジを始める") }
        SaveState(vm)
        Spacer(Modifier.height(16.dp))
        Records(vm.ranking, highlight = null, style = vm.style)
    }
}

/** この端末の記録（3分チャレンジ）の上位 10 件。「みんな」のランキングと取り違えないよう「この端末の記録」と呼ぶ */
@Composable
private fun Records(ranking: List<ChallengeScore>, highlight: String?, style: ChallengeStyle) {
    // 自分で落とすと自動で落ちるは、決まりが違うので記録を分けて出す（決定 65）
    Text("この端末の記録（3分チャレンジ・${style.label}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
    if (ranking.isEmpty()) {
        Text("まだ記録がありません", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        return
    }
    ranking.take(10).forEach { record ->
        val mine = record.runId == highlight
        Text(
            "${rankOf(record.height, ranking)} 位　%.2f m".format(Locale.JAPAN, record.height),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mine) FontWeight.Bold else null,
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 遊び方の名前（選ぶボタンと、記録の見出し） */
private val ChallengeStyle.label: String
    get() = when (this) {
        ChallengeStyle.MANUAL -> "自分で落とす"
        ChallengeStyle.AUTO -> "自動で落ちる"
    }

/** 遊び方の説明（始める前の画面） */
private val ChallengeStyle.description: String
    get() = when (this) {
        ChallengeStyle.MANUAL -> "◀▶ と回転で狙って「置く」で落とす。"
        ChallengeStyle.AUTO -> "形が少しずつ下がってくる。下がっている間に ◀▶ と回転で合わせ、塔か台に触れたら落ちる（「置く」ですぐ落とせる）。"
    }

/**
 * 遊び方を選ぶ 2 つのボタン（自分で落とす・自動で落ちる）。選んでいる方は色の付いたボタン、もう一方は枠だけ。
 * 読み上げでも、どちらを選んでいるか分かるようにする（Role.RadioButton）
 */
@Composable
private fun StylePicker(selected: ChallengeStyle, onPick: (ChallengeStyle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        ChallengeStyle.entries.forEach { style ->
            val chosen = style == selected
            val modifier = Modifier.semantics {
                this.selected = chosen
                role = Role.RadioButton
            }
            if (chosen) {
                PressButton(onClick = {}, modifier = modifier) { Text(style.label) }
            } else {
                PressOutlinedButton(onClick = { onPick(style) }, modifier = modifier) { Text(style.label) }
            }
        }
    }
}

/** ◀▶ で 1 回に動かす量 [m]（チャレンジの決まり） */
private const val MOVE_STEP_CHALLENGE_M = 0.1

/** ◀▶ のボタンの幅 */
private val ARROW_BUTTON_WIDTH = 48.dp

/** 狙う操作の行の高さ（ボタンの押せる高さ 48dp）。落下中もこの高さを取っておく */
private val AIM_ROW_HEIGHT = 48.dp

/** 回転・置くのボタンの中の余白（1 行に入るように狭める。通常のモードと同じ） */
private val AimButtonPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)

/** 残り 10 秒からの時計の色 */
private val LastSecondsRed = Color(0xFFD64545)

/** 操作の段の高さの上限（画面の高さに対する割合） */
private const val CONTROLS_MAX_FRACTION = 0.45f

/** これより低い画面（端末のバーとタブバーを除いた高さ）では、上の行の「‹ 戻る」の隣にモードの札を出さない（積む画面と同じ） */
private val COMPACT_HEIGHT = 600.dp

/** カメラを上げるときにかける時間 [ms]（通常のモードと同じ） */
private const val CAMERA_ANIMATION_MS = 400
