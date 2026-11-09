package com.rein4ce.roadcollector.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER
import com.rein4ce.roadcollector.ui.common.SkyText
import com.rein4ce.roadcollector.ui.common.PopChip
import com.rein4ce.roadcollector.ui.common.color
import com.rein4ce.roadcollector.ui.common.drawPart
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 「毎日 1 つずつ積む」の画面（決定 61・#106）。ふつうの積む画面と同じく、上から「情報」「キャンバス」「操作」の 3 段。
 *
 * - 情報：上の行の左に「‹ 戻る」とモードの札（[leading]）、右に積んだ日数。その下に塔の高さ（ふつうの積む画面と同じ形）
 * - キャンバス：保存した塔を、止まったときの形のまま出す。狙っている間は、出てくる高さが入るところまで視点を上げる
 * - 操作：今日まだ積んでいなければ、今日歩いた道（見本は除く）のカードから 1 つ選んで、塔を描く所をドラッグして位置を決め、
 *   指を離すと落ちる。「回す」で時計回りに回す（ふつうの積む画面と同じ。決定 67）。
 *   今日もう積んだら「また明日」。今日歩いた道が無ければ、歩くと積めることを伝える
 *
 * @param leading 上の行の左に置くもの（遊び方を選ぶ画面の ModePlayPage から「‹ 戻る」と札が来る）
 */
@Composable
internal fun DailyScreen(leading: @Composable (compact: Boolean) -> Unit = {}) {
    val app = RoadCollectorApp.from(LocalContext.current)
    val vm: DailyViewModel = viewModel(factory = remember { viewModelFactory { initializer { DailyViewModel(app.dailyTower) } } })
    val repository = app.partRepository
    val parts by repository.parts.collectAsStateWithLifecycle()
    val weathers by repository.weather.collectAsStateWithLifecycle()

    // 画面に戻るたびに今日の日付を見直す（0 時をまたいで開いたままでも、次の日の 1 つを積めるように）
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshDay() }
    }
    // 落ちている間だけ、物理を進める
    LaunchedEffect(vm.phase) {
        if (vm.phase != DailyViewModel.Phase.Settling) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive && vm.phase == DailyViewModel.Phase.Settling) {
            withFrameNanos { now ->
                vm.tick((now - last) / 1e9)
                last = now
            }
        }
    }

    // 今日歩いた道（見本と、もう塔にあるものは除く）。新しい順
    val todays = remember(parts, vm.day, vm.tower) {
        val inTower = vm.tower.blocks.map { it.part.id }.toSet()
        parts.filter { SampleParts.byId(it.id) == null && it.id !in inTower && dayOf(it.walkedAt) == vm.day }
            .sortedByDescending { it.walkedAt }
    }
    fun gripOf(part: Part) = Grip.of(repository.weatherOf(part.id, weathers)?.sky)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT
        Column(Modifier.fillMaxSize()) {
            // 1 段目：情報
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    leading(compact)
                    Spacer(Modifier.weight(1f))
                    SkyText("${vm.tower.days} 日ぶん", color = Fieldbook.Ink, fontSize = 15.sp)
                }
                DailyHeight(vm.height, compact)
            }

            // 2 段目：キャンバス
            DailyCanvas(vm, Modifier.fillMaxWidth().weight(1f).clipToBounds())

            // 3 段目：操作
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (vm.phase) {
                    DailyViewModel.Phase.Loading -> Unit
                    DailyViewModel.Phase.Viewing -> when {
                        vm.placedToday -> Note("今日の 1 つは積みました。あしたまた、その日に歩いた道を 1 つ積めます")
                        todays.isEmpty() -> Note("今日歩いた道がまだありません。歩いて道を集めると（デモ散歩でも）、1 つ積めます")
                        else -> {
                            if (vm.lastFell) Note("さっきの道は台から落ちました。今日の道から、もう一度 1 つ選べます")
                            Note("今日歩いた道から 1 つ選んで積みましょう（1 日に 1 つ）")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            ) {
                                items(todays, key = { it.id }) { part -> TodayPartCard(part, onClick = { vm.choose(part, gripOf(part)) }) }
                            }
                        }
                    }
                    DailyViewModel.Phase.Aiming -> {
                        val part = vm.current
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // パーツの名前は人や AI が付けた字なので、端末の字体（丸ゴシックの切り出しに無い字が混ざらないように）
                            SkyText(
                                part?.displayName ?: "",
                                color = Fieldbook.Ink,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            SkyText(" ・ ドラッグして、指を離すと落ちる", color = Fieldbook.Muted, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                        // 操作のボタンは「回す」の 1 つだけ（ふつうの積む画面と同じ。決定 67）。位置はキャンバスのドラッグで決め、指を離すと落ちる。
                        // 回すのは時計回りだけで、押し続けると 15° ずつ回り続ける（#114・決定 64。ui/game/RotateButtons.kt）
                        RotateButton(
                            RotateDirection.RIGHT,
                            sessionKey = part?.id ?: "",
                            onRotate = vm::rotate,
                            modifier = Modifier.width(TURN_BUTTON_WIDTH),
                            text = TURN_BUTTON_TEXT,
                            label = TURN_BUTTON_LABEL,
                        )
                        // ふつうの積む画面の「やり直す」と同じ、紙の面に青緑の縁の小さな札
                        PopChip("選び直す", face = Fieldbook.Card, text = Pop.TealLine, edge = Pop.TealLine, onClick = vm::cancelChoice)
                    }
                    DailyViewModel.Phase.Settling -> SkyText("落下中…", color = Fieldbook.Muted, fontSize = 14.sp)
                }
                if (vm.saveFailed) Note("塔を保存できませんでした。もう一度積むと、保存し直します")
            }
        }
    }
}

/** 「回す」のボタンの幅（ふつうの積む画面と同じ。画面いっぱいには広げない） */
private val TURN_BUTTON_WIDTH = 220.dp

/** 操作の段の一言（太い丸ゴシック・紙の色の縁取り。天気の上に直接書くので） */
@Composable
private fun Note(text: String) {
    SkyText(text, color = Fieldbook.Ink, fontSize = 14.sp, textAlign = TextAlign.Center)
}

/** 塔の高さ：「塔の高さ」と、大きな数字と「m」（ふつうの積む画面の高さと同じ形。dp で決め、文字の大きさの設定では大きくしない） */
@Composable
private fun DailyHeight(height: Float, compact: Boolean) {
    val density = LocalDensity.current
    fun fixed(dp: Float) = with(density) { dp.dp.toSp() }
    val number = if (Fieldbook.dark) Pop.Orange else Pop.OrangeDeep
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "塔の高さ %.2f メートル".format(height) },
    ) {
        SkyText("塔の高さ", color = Fieldbook.Ink, fontSize = fixed(if (compact) 14f else 15f), modifier = Modifier.padding(end = 10.dp, bottom = if (compact) 6.dp else 8.dp))
        SkyText("%.2f".format(height), color = number, fontSize = fixed(if (compact) 26f else 40f), maxLines = 1, softWrap = false)
        SkyText("m", color = number, fontSize = fixed(if (compact) 14f else 18f), modifier = Modifier.padding(start = 4.dp, bottom = if (compact) 5.dp else 7.dp))
    }
}

/**
 * 今日歩いた道のカード（押すと、それを積む）。コレクションのカードと同じ紙の面に、道の形・名前・距離。
 * 名前は人や AI が付けた字なので端末の字体
 */
@Composable
private fun TodayPartCard(part: Part, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(112.dp)
            .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${part.displayName}、${part.distanceM} メートル。積む" }
            .padding(8.dp),
    ) {
        Canvas(Modifier.size(56.dp)) {
            drawPart(part, Offset(size.width / 2, size.height / 2), size.width * 0.8f)
        }
        Spacer(Modifier.height(4.dp))
        Text(part.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Fieldbook.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${part.distanceM} m", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Fieldbook.Muted)
    }
}

/**
 * キャンバス：ふつうの積む画面と同じ変換（[StackViewport]）。カメラは [DailyViewModel.camera] に合わせて、0.4 秒かけて動かす。
 * 狙っている間は、これから置くパーツ（ゴースト）をうすく出し、横のドラッグで動かして、指を離すと落とす（決定 67。ui/game/AimGestures.kt）
 */
@Composable
private fun DailyCanvas(vm: DailyViewModel, modifier: Modifier = Modifier) {
    val platform = if (Fieldbook.dark) Color(0xFF7A7FA6) else Color(0xFF4A4E69)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val target = when (val camera = vm.camera) {
        is CameraTarget.Follow -> StackViewport.follow(canvasSize.width.toFloat(), canvasSize.height.toFloat(), camera.top)
        is CameraTarget.Overview -> StackViewport.overview(canvasSize.width.toFloat(), canvasSize.height.toFloat(), camera.focus)
    }
    val zoom = remember { Animatable(1f) }
    val cameraY = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        if (!placed) {
            zoom.snapTo(target.zoom)
            cameraY.snapTo(target.cameraY)
            placed = true
        } else {
            val spec = tween<Float>(durationMillis = 400, easing = FastOutSlowInEasing)
            launch { zoom.animateTo(target.zoom, spec) }
            launch { cameraY.animateTo(target.cameraY, spec) }
        }
    }
    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = "毎日 1 つずつ積む塔。高さ %.2f メートル".format(vm.height) }
            .aimByDrag(
                key = vm,
                canAim = { vm.phase == DailyViewModel.Phase.Aiming },
                onDrag = { dx ->
                    val viewport = StackViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat(), zoom.value, cameraY.value)
                    vm.dragBy(viewport.dragToMeters(dx))
                },
                // 狙っている間だけ落とす（VM の place が見る。カードを選ぶ前に触っても何もしない）
                onRelease = { vm.place() },
            ),
    ) {
        val viewport = StackViewport(size.width, size.height, zoom.value, cameraY.value)
        fun sx(x: Double) = viewport.toScreenX(x.toFloat())
        fun sy(y: Double) = viewport.toScreenY(y.toFloat())
        for (s in vm.shapes) {
            val color = s.part?.kind?.color() ?: platform
            when (s) {
                is StackWorld.RenderShape.Poly -> {
                    val path = Path()
                    for (i in s.xs.indices) {
                        if (i == 0) path.moveTo(sx(s.xs[i]), sy(s.ys[i])) else path.lineTo(sx(s.xs[i]), sy(s.ys[i]))
                    }
                    path.close()
                    drawPath(path, color)
                }
                is StackWorld.RenderShape.Disc -> drawCircle(color, radius = s.r.toFloat() * viewport.scale, center = Offset(sx(s.cx), sy(s.cy)))
            }
        }
        val current = vm.current
        if (vm.phase == DailyViewModel.Phase.Aiming && current != null) {
            drawPart(
                part = current,
                center = Offset(sx(vm.ghostX.toDouble()), sy(vm.spawnY().toDouble())),
                scalePx = viewport.scale * PartSize.of(current.distanceM).toFloat(),
                angleRad = Math.toRadians(vm.ghostAngleDeg.toDouble()).toFloat(),
                alpha = 0.45f,
            )
        }
    }
}

/** 歩いた時刻の日付（端末の時刻。"2026-11-10"） */
private fun dayOf(walkedAt: Long): String = Instant.ofEpochMilli(walkedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** これより低い画面では、上の行の札を出さず、高さの数字を小さくする（積む画面と同じ） */
private val COMPACT_HEIGHT = 600.dp
