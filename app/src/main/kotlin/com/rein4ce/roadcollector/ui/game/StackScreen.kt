package com.rein4ce.roadcollector.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.data.WalkDistance
import com.rein4ce.roadcollector.ui.common.BlockWord
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER
import com.rein4ce.roadcollector.ui.common.PopChip
import com.rein4ce.roadcollector.ui.common.PopButton
import com.rein4ce.roadcollector.ui.common.PressButton
import com.rein4ce.roadcollector.ui.common.PressOutlinedButton
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.color
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.common.partSizeText
import com.rein4ce.roadcollector.ui.common.SkyText
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.arrangedNow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

/**
 * D 担当：積み上げゲーム画面（通常モード。決定 62・#108）。上から「情報（高さ・置いた数・落下）」「キャンバス」「操作（ボタン・終わりのカード）」の 3 段。
 * コレクションの全部のパーツからランダムに 1 つずつ出て、台から落ちたら終わり（[StackViewModel]）。
 *
 * キャンバスでは、ワールド座標（m、y 上向き、台の上面が y=0）をキャンバスに写す（[StackViewport]）：
 *   横 5 m がキャンバスの幅になる倍率で、台はキャンバスの下から 22% の位置。
 *   狙っている間と落下中は大きさを変えず、塔が高くなったら、次のパーツが出てくる高さが入るところまでカメラを上げる
 *   （台はキャンバスの下へ外れることがある）。置き切ったら、塔全体と台が入るように引いて見せる。
 *   キャンバスの左端には、台の上面を 0 にした模型の高さの目盛（「みんな」の断面図と同じ標尺）を重ねて描く
 *
 * @param leading 情報の段の 1 行目の左に置くもの。モードで遊ぶ画面（ModeSelect.kt の ModePlayPage）からは「‹ 戻る」と今のモードの札が来る
 *   （行を別に足すと、その分だけ塔を描く所が狭くなるため。#116）。[compact] は小さい画面（1 行に並べるので、幅を取らないものにする）。
 *   渡さなければ、画面名「みちをつむ」の積み木（[StackTitle]）
 */
@Composable
fun StackScreen(
    onGoInventory: () -> Unit,
    onGoGallery: () -> Unit,
    leading: @Composable (compact: Boolean) -> Unit = { compact -> StackTitle(compact) },
    vm: StackViewModel = viewModel(),
) {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val inventory by repository.parts.collectAsStateWithLifecycle()
    val isLoaded by repository.isLoaded.collectAsStateWithLifecycle()
    val weathers by repository.weather.collectAsStateWithLifecycle()

    // 出すパーツは、コレクションに見えている全部（arrangedNow。見本は設定の「見本のパーツを出す」に合わせて入る）
    LaunchedEffect(inventory, isLoaded) {
        // partsとisLoadedの収集順にかかわらず、開始境界ではRepositoryの現在値を使う。
        // 出どころは「使った道」を数えるため（終わったときに、置いたパーツだけで数える）
        vm.updateInventory(repository.arrangedNow(), repository.isLoaded.value, repository.origins.value)
    }

    // ゲームループ。画面が見えている間（STARTED 以上）だけ回す。アプリを裏に回すと止まり、戻ったときは
    // 前のフレームの時刻を捨てて数え直すので、止まっていた時間が一度に入らない。
    // タブを移ったときは、画面ごと LaunchedEffect が止まり、戻ると同じく数え直す（1 回の時間の上限は VM の tick でもかける）
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (last != 0L) vm.tick((now - last) / 1_000_000_000.0)
                    last = now
                }
            }
        }
    }

    if (vm.phase == StackViewModel.Phase.Idle) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 読み込み後は、VM が「始める」か「startMessage を出す」のどちらかになる。
            // パーツがあって案内もないのは、上の LaunchedEffect が動く前の一瞬だけ
            if (!isLoaded || (inventory.isNotEmpty() && vm.startMessage == null)) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("パーツを読み込み中…")
            } else {
                Text(vm.startMessage ?: "コレクションにパーツがありません")
                Spacer(Modifier.height(8.dp))
                PopButton("コレクションへ", onClick = onGoInventory, face = Pop.OrangeDeep, text = Pop.Letter)
            }
        }
        return
    }

    vm.startMessage?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissStartMessage,
            text = { Text(message) },
            confirmButton = {
                PressTextButton(onClick = {
                    vm.dismissStartMessage()
                    onGoInventory()
                }) { Text("コレクションへ") }
            },
            dismissButton = {
                PressTextButton(onClick = vm::dismissStartMessage) { Text("ゲームに戻る") }
            },
        )
    }

    // 操作の段の高さは、1 回のゲームの中では縮めない（狙う → 落下中 → 狙う で段の高さが変わると、
    // キャンバスの大きさが変わって台と塔がガタつくため）。新しいゲームでは測り直す。
    // rememberSaveable にして、タブを行き来しても測り直さない（ゲームは ViewModel に残っているため）
    val density = LocalDensity.current
    var controlsMinHeightDp by rememberSaveable(vm.runId) { mutableStateOf(0f) }
    // 情報の段も同じく、1 回のゲームの中では縮めない（いま置くパーツの大きさの行は狙っている間だけ出るので、
    // そのままだと置くたびに段の高さが変わり、キャンバスの大きさが変わって台と塔がガタつくため）
    var infoMinHeightDp by rememberSaveable(vm.runId) { mutableStateOf(0f) }

    // いま置くパーツのすべりやすさ。雨や雪の日の道は、置くと滑りやすい（摩擦が小さい）。
    // 急に滑って驚かないよう、置く前に知らせる（置くボタンにも同じ値を渡す）
    val sky = vm.current?.let { repository.weatherOf(it.id, weathers)?.sky }
    val grip = Grip.of(sky)

    // 上から「情報」「キャンバス」「操作」の 3 段に分ける。ボタンや完成カードがキャンバスに重ならないので、
    // 台と塔の下の方がボタンの裏に隠れない（#42）。キャンバスは残りの高さを全部使う
    // 地は塗らない。ほかの画面と同じく、アプリ全体の方眼の紙と今の天気（AppNav・ui/weather）が透けて見える（決定 59）
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 操作の段は画面の高さの [CONTROLS_MAX_FRACTION] まで。それより高くなる中身（小さい画面で文字を大きくしたときの
        // 完成カードなど）はスクロールさせ、キャンバスが潰れて塔が見えなくならないようにする
        val controlsMaxHeight = maxHeight * CONTROLS_MAX_FRACTION
        // 小さい画面（積む画面の高さが [COMPACT_HEIGHT] に満たない）では、高さの数字を一回り小さくする
        val compact = maxHeight < COMPACT_HEIGHT
        Column(modifier = Modifier.fillMaxSize()) {
            // 1 段目：情報（高さ・残り・落下）。その下に、いま置くパーツの大きさと、すべりやすいならその一言（置く前に知らせる）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = infoMinHeightDp.dp)
                    .onSizeChanged { size ->
                        val h = with(density) { size.height.toDp() }.value
                        if (h > infoMinHeightDp) infoMinHeightDp = h
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            ) {
                // 1 行目の左は [leading]（モードで遊ぶ画面では「‹ 戻る」とモードの札、渡されなければ画面名の積み木）。
                // 字は「みんな」・コレクションと同じ太い丸ゴシック。方眼の紙と天気の上に直接書くので、紙の色の縁取りを付ける（SkyText。決定 59）。
                // 小さい画面では、1 行目の左・高さ・残りと落下を 1 行に並べて、情報の段を低くする（塔を描く所が狭くなって、台が見えなくならないように。
                // #116 のレビュー）。ふつうの画面では、1 行目に左のものと残り・落下、2 行目に大きな高さ
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(end = 12.dp)) { leading(true) }
                        HeightMeter(vm.height.toFloat(), compact = true)
                        Spacer(Modifier.weight(1f))
                        PlacedAndFallen(vm.placedCount, vm.fallenCount, compact = true)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leading(false)
                        PlacedAndFallen(vm.placedCount, vm.fallenCount, compact = false)
                    }
                    // 高さの右に「チェンジ」（狙っているパーツを替える。決定 62）。高さの数字より少し下げ、下の「○ m の道」の行の右にかかる所に置く
                    // （ずらすのは見た目だけで、段の高さは変えない。右側は空いているので、下の行の字には重ならない）
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HeightMeter(vm.height.toFloat(), compact = false)
                        Spacer(Modifier.weight(1f))
                        ChangeButton(
                            enabled = vm.canChange,
                            left = vm.changesLeft,
                            onClick = vm::change,
                            modifier = Modifier.offset(y = CHANGE_BUTTON_DROP),
                        )
                    }
                }
                val current = vm.current
                if (vm.phase == StackViewModel.Phase.Placing && current != null) {
                    // 長い道ほど大きい（PartSize）。何 m の道だから何倍か、を出す。
                    // 「みんな」のタワーの注記の行と同じく、数字は墨、ほかは薄い墨。
                    // 「歩いた道」とは書かない（見本やデモ散歩のパーツもあり、完成カードの「使った道」は歩いた道だけを数えるため。#87 の A のレビュー）
                    val size = partSizeText(current.distanceM)
                    SkyText(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Fieldbook.Ink)) { append("${current.distanceM} m") }
                            append(" の道 ・ 大きさ ")
                            withStyle(SpanStyle(color = Fieldbook.Ink)) { append(size) }
                        },
                        color = Fieldbook.Muted,
                        // 小さい画面では、文字の大きさの設定で大きくしない（情報の段が伸びて、塔を描く所が狭くならないように）
                        fontSize = if (compact) with(density) { 13.dp.toSp() } else 14.sp,
                    )
                    val slippery = grip.label
                    if (sky != null && slippery != null) {
                        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                        SlipperyNote(sky, slippery, compact)
                    }
                }
            }

            // 2 段目：キャンバス。ドラッグはこの中だけで受け付け、はみ出した絵は切る（情報や操作の段の上に描かない）
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                StackCanvas(vm = vm, onRelease = { vm.place(grip) }, modifier = Modifier.fillMaxSize().clipToBounds())
                // 小さい画面では、情報の段の 1 行に高さと置いた数が並んで入らないので、「チェンジ」はキャンバスの右上に重ねる
                // （段を伸ばすと塔を描く所が狭くなるため）
                if (compact) {
                    ChangeButton(
                        enabled = vm.canChange,
                        left = vm.changesLeft,
                        onClick = vm::change,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 4.dp),
                    )
                }
            }

            // 3 段目：操作（ボタン・完成カード）
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
                    when (vm.phase) {
                        StackViewModel.Phase.Placing -> {
                            // 置き方の一言は 1 行に収める（2 行になると操作の段が高くなり、小さい画面で塔を描く所が狭くなるため。#116 のレビュー）。
                            // パーツの名前が長ければ、名前のほうを「…」で切る。名前は人や AI が付けた字なので、丸ゴシック（使う字だけの切り出し）
                            // ではなく端末の字体の太字にする（切り出しに無い字だけ端末の字体になって、1 つの名前の中で字体が混ざらないように）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.semantics(mergeDescendants = true) {},
                            ) {
                                SkyText(
                                    vm.current?.displayName ?: "",
                                    color = Fieldbook.Ink,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Default,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                // 入りきらない（文字をとても大きくした）ときは「…」で切れたと分かるようにする（#88 の C のレビュー）
                                SkyText(
                                    " ・ ドラッグして、指を離すと落ちる",
                                    color = Fieldbook.Muted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // 操作のボタンは「回す」の 1 つだけ（決定 67）。位置はキャンバスのドラッグで決め、指を離すと落ちる（aimByDrag）。
                            // 回すのは時計回りだけで、押し続けると 15° ずつ回り続ける（#114 の RotateButton）
                            RotateButton(
                                RotateDirection.RIGHT,
                                sessionKey = vm.runId,
                                onRotate = vm::rotate,
                                modifier = Modifier.width(TURN_BUTTON_MIN_WIDTH),
                                text = TURN_BUTTON_TEXT,
                                label = TURN_BUTTON_LABEL,
                            )
                            // 今のコレクションの全部のパーツで、はじめから（パーツを選ぶ機能はやめた。決定 62）
                            Spacer(Modifier.height(4.dp))
                            PopChip(
                                "やり直す",
                                face = Fieldbook.Card,
                                text = Pop.TealLine,
                                edge = Pop.TealLine,
                                onClick = { vm.restart(repository.arrangedNow(), repository.origins.value) },
                            )
                        }
                        StackViewModel.Phase.Settling -> {
                            // 段の高さは狙っているときのまま（controlsMinHeightDp）なので、ここでは文字だけ
                            SkyText("落下中…", color = Fieldbook.Muted, fontSize = 14.sp)
                        }
                        StackViewModel.Phase.Finished, StackViewModel.Phase.Unresolved -> {
                            vm.result?.let { result ->
                                ResultCard(
                                    vm = vm,
                                    result = result,
                                    unresolved = vm.phase == StackViewModel.Phase.Unresolved,
                                    onRetry = { vm.restart(repository.arrangedNow(), repository.origins.value) },
                                    onGoGallery = onGoGallery,
                                )
                            }
                        }
                        StackViewModel.Phase.Idle -> Unit
                    }
                }
            }
        }
    }
}

/**
 * 「雪の日の道 ・ とてもすべりやすい」。頭の丸は、コレクションで道に付く天気の色（雪は水色、雨は青）と同じ。
 * 字は道の色の縁取りの濃い色で、地の色（ライト・ダーク）の上でも読めるように、うすく色を敷いた札にする
 */
@Composable
private fun SlipperyNote(sky: Sky, label: String, compact: Boolean) {
    val (road, casing) = Fieldbook.roadColors(sky)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(road.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(road).border(1.dp, casing, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            "${sky.label}の日の道 ・ $label",
            style = MaterialTheme.typography.bodySmall,
            // ほかの字と同じ太い丸ゴシック（天気の名前とすべりやすさは、どちらもアプリの言葉）
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            // 小さい画面では、文字の大きさの設定で大きくしない（情報の段が伸びて、塔を描く所が狭くならないように）
            fontSize = if (compact) with(LocalDensity.current) { 12.dp.toSp() } else TextUnit.Unspecified,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 置き切ったときの結果のカード。表示も送信も、置き切った瞬間に固定した [StackViewModel.StackResult] を使う
 * （完成後に物理は止めているので、カードの高さと画面の塔と送る内容がそろう）。
 * 送れない結果（塔が止まったのを確かめられなかった・パーツが 1 つも残らなかった）は、送信ボタンを出さずに理由を書く。
 */
@Composable
private fun ResultCard(
    vm: StackViewModel,
    result: StackViewModel.StackResult,
    unresolved: Boolean,
    onRetry: () -> Unit,
    onGoGallery: () -> Unit,
) {
    // 終わりのカード：「みんな」・コレクションのカードと同じ紙の面（縁と下にのぞく側面）。字は太い丸ゴシック、ボタンはブロック（決定 67）
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val title = when {
            unresolved -> "揺れが止まりませんでした"
            result.remainingCount == 0 -> "全部落ちました"
            else -> "おわり！ 模型の高さ %.2f m".format(result.tower.height)
        }
        Text(title, fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Fieldbook.Ink, textAlign = TextAlign.Center)
        Text(
            "置いたパーツ ${result.usedCount} 個 ・ 台に残ったのは ${result.remainingCount} 個",
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            color = Fieldbook.Muted,
            textAlign = TextAlign.Center,
        )
        // 使った道（本当に歩いた道の距離）。1 行目はふつうの字、2 行目（数えなかったパーツの断り）は小さい字
        walkedLines(result.walked).forEachIndexed { i, line ->
            Text(
                line,
                style = if (i == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = Fieldbook.Ink,
                textAlign = TextAlign.Center,
            )
        }
        val reason = when {
            unresolved -> "塔が止まらず、高さを確定できないので、送信できません。もう一度試してください。"
            result.remainingCount == 0 -> "台にパーツが残っていないので、送信できません。"
            else -> null
        }
        reason?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Fieldbook.Muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        // 入りきらないボタンは次の行へ回す（文字を大きくしたとき、1 行に並べると「みんな」が画面の外に押し出されていた）
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PopButton("もう一度", onClick = onRetry, face = Pop.OrangeDeep, text = Pop.Letter)
            if (vm.canUpload) {
                // 送信済みなら押せない（押すたびに同じタワーが別の id で保存され、「みんな」に重複して並ぶため）
                PopButton(
                    when {
                        vm.uploading -> "送信中…"
                        vm.uploaded -> "送信済み"
                        else -> "サーバに送信"
                    },
                    onClick = { vm.upload() },
                    face = Fieldbook.Card,
                    text = Pop.TealLine,
                    edge = Pop.TealLine,
                    enabled = !vm.uploading && !vm.uploaded,
                    reserveLabels = listOf("サーバに送信", "送信中…", "送信済み"),
                )
            }
            PopButton("みんな", onClick = onGoGallery, face = Fieldbook.Card, text = galleryInk(), edge = galleryInk())
        }
        vm.uploadMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Fieldbook.Ink, textAlign = TextAlign.Center)
        }
    }
}

/**
 * キャンバス。ワールド座標（m）とキャンバス（px）の変換は [StackViewport] で行い、描く位置も、指で動かした量を m に直すのも、
 * 同じ変換（キャンバスの大きさと、いまのカメラ）を使う。カメラが動いても、指の動きとゴーストがずれない。
 *
 * カメラは VM の [StackViewModel.camera] に合わせる：狙っている間と落下中は、大きさを変えずに次のパーツが出てくる高さまで上げ
 * （[StackViewport.follow]）、置き切ったら塔全体と台が入るように引く（[StackViewport.overview]）。
 * camera はパーツが止まったときだけ変わるので、狙っている間と落下中にカメラは動かず、落ちていくパーツも追いかけない。
 * 動くときは経過時間で補間するので、画面の更新の速さ（30Hz・120Hz）によらず同じ速さで動く
 */
@Composable
private fun StackCanvas(vm: StackViewModel, onRelease: () -> Unit, modifier: Modifier = Modifier) {
    // 落下ラインと台の色。ダークモード（地が暗い）では、暗い地の上でも見える明るさにする
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val guide = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.13f)
    val platform = if (dark) Color(0xFF7A7FA6) else Color(0xFF4A4E69)

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 指を離したときに呼ぶ（置くパーツのすべりやすさは、画面が今のパーツから決めて渡す）。ジェスチャーは作り直さずに、いつも新しいものを呼ぶ
    val currentRelease by rememberUpdatedState(onRelease)
    val canvasWidth = canvasSize.width.toFloat()
    val canvasHeight = canvasSize.height.toFloat()
    val target = when (val camera = vm.camera) {
        is CameraTarget.Follow -> StackViewport.follow(canvasWidth, canvasHeight, camera.top)
        is CameraTarget.Overview -> StackViewport.overview(canvasWidth, canvasHeight, camera.focus)
    }
    val zoom = remember { Animatable(1f) }
    val cameraY = remember { Animatable(0f) }
    var cameraPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        if (!cameraPlaced) {
            // 最初に大きさが決まったとき（タブを戻ったときも）は、動かさずに合わせる
            zoom.snapTo(target.zoom)
            cameraY.snapTo(target.cameraY)
            cameraPlaced = true
        } else {
            val spec = tween<Float>(durationMillis = CAMERA_ANIMATION_MS, easing = FastOutSlowInEasing)
            launch { zoom.animateTo(target.zoom, spec) }
            launch { cameraY.animateTo(target.cameraY, spec) }
        }
    }

    // 模型の高さの目盛。「みんな」の断面図の標尺（TowerSection）と同じ字と色にする：太い丸ゴシック 9dp（図の一部なので、
    // 文字の大きさの設定では大きくしない）、「1m」。キャンバスの左端に重ねて描くので、塔の大きさは目盛が無いときと同じ
    val density = LocalDensity.current
    val rulerInk = Fieldbook.Muted
    val rulerMark = Fieldbook.Mark
    val rulerGrid = Fieldbook.GridMajor
    val rulerStyle = remember(rulerInk, density) {
        TextStyle(fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = with(density) { 9.dp.toSp() }, color = rulerInk)
    }
    val textMeasurer = rememberTextMeasurer()
    // 目盛の字は「0m」〜「99m」くらいしか出ないので、測った結果を覚えておき、カメラが動いている間も毎コマ測り直さない
    val rulerLabels = remember(rulerStyle) { HashMap<String, TextLayoutResult>() }
    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            // 横にドラッグして狙い、指を離すと落とす（決定 67。ui/game/AimGestures.kt）。落とすのは狙っている間だけ（VM の place が見る）
            .aimByDrag(
                key = vm,
                onDrag = { dx ->
                    // 描くときと同じ変換（いまのキャンバスの大きさとカメラ）で m に直す
                    val viewport = StackViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat(), zoom.value, cameraY.value)
                    vm.dragBy(viewport.dragToMeters(dx))
                },
                onRelease = { currentRelease() },
            ),
    ) {
        val viewport = StackViewport(size.width, size.height, zoom.value, cameraY.value)
        val scalePx = viewport.scale
        fun sx(x: Double) = viewport.toScreenX(x.toFloat())
        fun sy(y: Double) = viewport.toScreenY(y.toFloat())

        // 目盛（塔と同じ変換なので、カメラが上がっても引いても塔の高さとずれない）。パーツの下になるように先に描く
        drawHeightRuler(viewport, rulerMark, rulerGrid) { text -> rulerLabels.getOrPut(text) { textMeasurer.measure(text, rulerStyle) } }

        // 落下ライン
        drawLine(
            color = guide,
            start = Offset(0f, sy(-1.5)),
            end = Offset(size.width, sy(-1.5)),
            strokeWidth = 2f,
        )

        // 物理ワールドの図形
        for (s in vm.shapes) {
            val color = s.part?.kind?.color() ?: platform
            when (s) {
                is StackWorld.RenderShape.Poly -> {
                    val path = Path()
                    for (i in s.xs.indices) {
                        val px = sx(s.xs[i])
                        val py = sy(s.ys[i])
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, color)
                }
                is StackWorld.RenderShape.Disc -> {
                    drawCircle(color, radius = s.r.toFloat() * scalePx, center = Offset(sx(s.cx), sy(s.cy)))
                }
            }
        }

        // ゴースト（これから置くパーツ）
        val current = vm.current
        if (vm.phase == StackViewModel.Phase.Placing && current != null) {
            // 置いたときと同じ大きさで見せる（長く歩いた道ほど大きい。PartSize）
            drawPart(
                part = current,
                center = Offset(sx(vm.ghostX.toDouble()), sy(vm.spawnY().toDouble())),
                scalePx = scalePx * PartSize.of(current.distanceM).toFloat(),
                angleRad = Math.toRadians(vm.ghostAngleDeg.toDouble()).toFloat(),
                alpha = 0.45f,
            )
        }
    }
}

/**
 * 模型の高さの目盛を、キャンバスの左端に重ねて描く。「みんな」の断面図の標尺（TowerSection）と同じ形：
 * 縦の線の左に「1m」の字と短い目盛り、右へ伸びる薄い点線。20 cm の細かい目盛りは、間が空くときだけ（[heightTicks]）。
 * 縦の線は台の上面（0 m）から上だけ（台が下に外れているときは、キャンバスの下の端から）
 *
 * @param label 目盛りの字を測った結果（同じ字は覚えておいたものを使う）
 */
private fun DrawScope.drawHeightRuler(
    viewport: StackViewport,
    mark: Color,
    grid: Color,
    label: (String) -> TextLayoutResult,
) {
    val staffX = RULER_STAFF_DP.dp.toPx()
    val labelGap = maxOf(28.dp.toPx(), label("0m").size.height * 1.5f)
    val ticks = heightTicks(viewport, minorGapPx = 8.dp.toPx(), labelGapPx = labelGap)
    if (ticks.isEmpty()) return
    val bottom = min(size.height, viewport.toScreenY(0f))
    if (bottom > 0f) drawLine(mark, Offset(staffX, 0f), Offset(staffX, bottom), 1.dp.toPx())
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
    for (tick in ticks) {
        val y = tick.screenY
        val text = tick.label
        if (text == null) {
            drawLine(mark, Offset(staffX - 2.dp.toPx(), y), Offset(staffX, y), 1.dp.toPx())
            continue
        }
        drawLine(mark, Offset(staffX - 4.dp.toPx(), y), Offset(staffX, y), 1.dp.toPx())
        // 1 m ごとの薄い点線。台の上面（0 m）には台があるので引かない
        if (tick.meters > 0f) drawLine(grid, Offset(staffX, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
        val layout = label(text)
        val top = (y - layout.size.height / 2f).coerceIn(0f, (size.height - layout.size.height).coerceAtLeast(0f))
        drawText(layout, topLeft = Offset(staffX - 6.dp.toPx() - layout.size.width, top))
    }
}

/**
 * 完成カードの「使った道」の行。
 * - 歩いて記録したパーツがある：「使った道：合計 1.23 km」。ほかのパーツも混ざっていれば、数えなかったことを 2 行目に
 * - 1 本も無い（見本やデモ散歩だけで積んだ）：「合計 0.00 km」は出さず、距離が出ないわけを 1 行で。
 *   発表は見本とデモで積むので、毎回「0.00 km」が出て失敗したように見えないように（#87 のレビュー）
 */
internal fun walkedLines(walked: WalkDistance): List<String> = when {
    walked.walks > 0 -> listOfNotNull(
        "使った道：合計 %.2f km".format(Locale.JAPAN, walked.meters / 1000.0),
        if (walked.others > 0) "見本・デモ・歩いた記録か確かめられないパーツ ${walked.others} 個は距離に含めていません" else null,
    )
    walked.others == 0 -> emptyList()
    walked.unknown == 0 -> listOf("見本・デモのパーツなので、歩いた距離は出ません")
    else -> listOf("歩いた記録か確かめられないパーツなので、歩いた距離は出ません")
}

/** 目盛の縦の線の位置（キャンバスの左端から）[dp]。断面図の標尺の幅と同じ */
private const val RULER_STAFF_DP = 30f

/**
 * 画面名「みちをつむ」。コレクション・「みんな」・記録と同じく、題字「ミチツミ」と同じ積み木（ui/common/Pop.kt の BlockWord）で、押すと動く。
 * 積み木は文字の大きさの設定では大きくしない（dp で決める）。5 文字あるので、小さい画面（[compact]）では一回り小さくして、高さ・残りと同じ行に収める
 */
@Composable
internal fun StackTitle(compact: Boolean) {
    if (compact) BlockWord("みちをつむ", blockDp = 20.dp) else BlockWord("みちをつむ")
}

/**
 * いまの塔の高さ：「模型の高さ」と、大きな数字と「m」。「みんな」の「あなたの最高」と同じ形（太い丸ゴシック・オレンジの数字・紙の色の縁取り）。
 * 数字は、置いて高さが変わるたびに、前の値からメーターのように回って変わる（動きを減らす設定なら、すぐ変わる）。
 * 大きさは画面名の積み木と同じく dp で決め、文字の大きさの設定では大きくしない（もともと大きい字で、設定で大きくすると
 * 情報の段が伸びて、塔を描く所が狭くなるため。#116 のレビュー）
 *
 * @param compact 小さい画面。数字を一回り小さくする
 */
@Composable
internal fun HeightMeter(height: Float, compact: Boolean) {
    val density = LocalDensity.current
    // dp で決めた大きさを、文字の大きさの設定に左右されない sp に直す
    fun fixed(dp: Float) = with(density) { dp.dp.toSp() }
    val numberSize = fixed(if (compact) 26f else 40f)
    val labelSize = fixed(if (compact) 14f else 15f)
    val unitSize = fixed(if (compact) 14f else 18f)
    val reduceMotion = rememberReduceMotion()
    val shown by animateFloatAsState(
        targetValue = height,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "HeightMeter",
    )
    // ダークモードでは濃いオレンジが暗い紙に沈むので、明るいオレンジにする（「みんな」の高い順の字と同じ）
    val number = if (Fieldbook.dark) Pop.Orange else Pop.OrangeDeep
    Row(
        verticalAlignment = Alignment.Bottom,
        // 読み上げは、回っている途中の数字ではなく、今の高さを 1 回で
        modifier = Modifier.clearAndSetSemantics { contentDescription = "模型の高さ %.2f メートル".format(height) },
    ) {
        SkyText("模型の高さ", color = Fieldbook.Ink, fontSize = labelSize, modifier = Modifier.padding(end = 10.dp, bottom = if (compact) 6.dp else 8.dp))
        SkyText("%.2f".format(shown), color = number, fontSize = numberSize, maxLines = 1, softWrap = false)
        SkyText("m", color = number, fontSize = unitSize, modifier = Modifier.padding(start = 4.dp, bottom = if (compact) 5.dp else 7.dp))
    }
}

/** これより低い積む画面（端末のバーとタブバーを除いた高さ）では、高さの数字を一回り小さくする */
private val COMPACT_HEIGHT = 600.dp

/**
 * 「置いた ○ 個」と、その下に「落下 ○ 個」（右寄せ）。通常モードは終わりが無いので、残りの代わりに置いた数を出す。
 * 落下は 1 個目で終わり（決定 62）なので、出るのは終わる直前から
 *
 * @param compact 小さい画面。字を画面名の積み木と同じく dp で決め、文字の大きさの設定では大きくしない
 *   （2 行あるので、大きくすると情報の段が伸びて、塔を描く所が狭くなるため。#116 のレビュー）
 */
@Composable
private fun PlacedAndFallen(placed: Int, fallen: Int, compact: Boolean) {
    val size = if (compact) with(LocalDensity.current) { 13.dp.toSp() } else 15.sp
    Column(horizontalAlignment = Alignment.End) {
        SkyText("置いた $placed 個", color = Fieldbook.Ink, fontSize = size)
        // 0 個のときも場所だけは取っておく（初めて落ちたときに情報の段が伸びて、台がずれないように）
        val fallenModifier = if (fallen > 0) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
        SkyText("落下 $fallen 個", color = FALLEN_RED, fontSize = size, modifier = fallenModifier)
    }
}

/** 「回す」のボタンの幅（ボタンが 1 つだけなので、押しやすい幅にする。画面いっぱいには広げない） */
private val TURN_BUTTON_MIN_WIDTH = 220.dp

/** 「チェンジ」を、高さの行のまん中からどれだけ下げて置くか（ふつうの画面） */
private val CHANGE_BUTTON_DROP = 24.dp

/** 「落下 ○ 個」の赤 */
private val FALLEN_RED = Color(0xFFD64545)

/**
 * 終わりのカードの「みんな」の字と縁の色（「みんな」のタブの紺）。ダークモードでは紺が暗い紙に沈むので、明るい紺にする
 * （モードを選ぶ画面の台の色と同じ考え。「サーバに送信」の青緑の縁の札（`Pop.TealLine`）と同じく、明るさだけ変える）
 */
private fun galleryInk(): Color = if (Fieldbook.dark) Color(0xFF9DA2C8) else Pop.Navy

/** 操作の段の高さの上限（画面の高さに対する割合）。超える中身はスクロールさせる */
private const val CONTROLS_MAX_FRACTION = 0.45f

/** カメラを動かす（上げる・引く）ときにかける時間 [ms] */
private const val CAMERA_ANIMATION_MS = 400
