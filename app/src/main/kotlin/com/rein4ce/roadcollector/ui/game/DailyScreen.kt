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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.Dp
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
import com.rein4ce.roadcollector.core.daily.DailyTower
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.physics.Grip
import com.rein4ce.roadcollector.core.physics.PartSize
import com.rein4ce.roadcollector.core.physics.StackWorld
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER
import com.rein4ce.roadcollector.ui.common.SkyText
import com.rein4ce.roadcollector.ui.common.Daypart
import com.rein4ce.roadcollector.ui.common.PIECE_OUTLINE_DP
import com.rein4ce.roadcollector.ui.common.PieceColors
import com.rein4ce.roadcollector.ui.common.PiecePalette
import com.rein4ce.roadcollector.ui.common.drawPiece
import com.rein4ce.roadcollector.ui.common.drawWorldShapes
import com.rein4ce.roadcollector.ui.common.shownSky
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
 * - キャンバス：保存した塔を、止まったときの形のまま固めて出す（決定 70）。狙っている間は、出てくる高さが入るところまで視点を上げる
 * - 操作：今日まだ積んでいなければ、今日歩いた道（見本は除く）のカードから 1 つ選んで、塔を描く所をドラッグして位置を決め、
 *   指を離すと落ちる。「回す」で時計回りに回す（ふつうの積む画面と同じ。決定 67）。
 *   カードは一度に 3 枚まで。それより多ければ「ほかの道」で入れ替える（決定 70）。
 *   今日のパーツが台から落ちたら、同じパーツで置き直す（1 日 3 回まで。決定 70）。
 *   今日もう積んだ・置き直しを使い切ったら「また明日」。今日歩いた道が無ければ、歩くと積めることを伝える
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
    val places by repository.places.collectAsStateWithLifecycle()
    // 積み木の色（決定 74）：色相は歩いたときの天気、明るさは歩いた時間帯。コレクションから消したパーツで天気が分からなければ、
    // 積んだときのすべりやすさ（塔に残っている）から天気を戻す
    val pieceColorsOf: (Part) -> PieceColors = remember(weathers, places, vm.tower) {
        val known = HashMap<String, PieceColors>()
        val lookup: (Part) -> PieceColors = { part ->
            known.getOrPut(part.id) {
                val sky = repository.weatherOf(part.id, weathers)?.sky ?: vm.tower.blocks.firstOrNull { it.part.id == part.id }?.grip?.shownSky
                PiecePalette.colors(sky, Daypart.of(part, repository.placeOf(part.id, places)?.startedAt))
            }
        }
        lookup
    }

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
                    DailyDays(vm.tower.days, compact)
                }
                DailyHeight(vm.height, compact)
            }

            // 2 段目：キャンバス
            DailyCanvas(vm, pieceColorsOf, Modifier.fillMaxWidth().weight(1f).clipToBounds())

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
                        !vm.canPlaceToday -> Note(
                            // 文の切れ目で改行する（そのままだと「あ／したまた」のように語の途中で折り返していた）
                            "今日の道は ${DailyTower.MAX_RETRIES} 回置き直しても、台に残りませんでした。\nあしたまた、その日に歩いた道を 1 つ積めます",
                        )
                        todays.isEmpty() -> Note("今日歩いた道がまだありません。歩いて道を集めると（デモ散歩でも）、1 つ積めます")
                        else -> {
                            if (vm.fallsToday > 0) // 1 行に収まるように短く（前は「今日は…（置き直しは 1 日 3 回まで）」で、最後の 2 字だけ次の行に落ちていた）
                            Note("台から ${vm.fallsToday} 回落ちました（置き直しは ${DailyTower.MAX_RETRIES} 回まで）")
                            Note("今日歩いた道から 1 つ選んで積みましょう（1 日に 1 つ）")
                            TodayChoices(todays, colorsOf = pieceColorsOf, onPick = { part -> vm.choose(part, gripOf(part)) })
                        }
                    }
                    DailyViewModel.Phase.Aiming -> {
                        val part = vm.current
                        // 置き直している間は、何回目かを出す（落ちた回数＝今の置き直しの回数。決定 70）
                        if (vm.fallsToday > 0) {
                            Note(
                                (if (vm.lastFell) "台から落ちました。" else "") +
                                    "置き直し ${vm.fallsToday} 回目（${DailyTower.MAX_RETRIES} 回まで）",
                            )
                        }
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
                        // 回すのは時計回りだけで、押している間なめらかに回る（きざみは無い。決定 71。ui/game/RotateButtons.kt）
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

/** 今日の道のカードを、一度に並べる枚数（決定 70） */
private const val CHOICES_PER_PAGE = 3

/** 今日の道のカードの幅（画面が狭ければ、3 枚が入るように細くする） */
private val CARD_WIDTH = 112.dp

/** 今日の道のカードの間 */
private val CARD_GAP = 10.dp

/**
 * 今日の道のカード。一度に [CHOICES_PER_PAGE] 枚まで並べ、それより多ければ「ほかの道」で次の [CHOICES_PER_PAGE] 枚に入れ替える
 * （決定 70。最後まで行ったら最初に戻る。前は横に流して見る列だった）。並びは新しい順のまま
 */
@Composable
private fun TodayChoices(todays: List<Part>, colorsOf: (Part) -> PieceColors, onPick: (Part) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = (todays.size + CHOICES_PER_PAGE - 1) / CHOICES_PER_PAGE
    // 道が減ったとき（積んだ・コレクションから消した）にページが無くならないよう、今の数で回す
    val current = if (pages == 0) 0 else page % pages
    val shown = todays.drop(current * CHOICES_PER_PAGE).take(CHOICES_PER_PAGE)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = minOf(CARD_WIDTH, (maxWidth - CARD_GAP * (CHOICES_PER_PAGE - 1)) / CHOICES_PER_PAGE)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP, Alignment.CenterHorizontally),
        ) {
            for (part in shown) key(part.id) { TodayPartCard(part, colorsOf(part), width, onClick = { onPick(part) }) }
        }
    }
    if (pages > 1) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // ふつうの積む画面の「やり直す」と同じ、紙の面に青緑の縁の小さな札
            PopChip("ほかの道", face = Fieldbook.Card, text = Pop.TealLine, edge = Pop.TealLine, onClick = { page = (current + 1) % pages })
            SkyText("${current + 1} / $pages", color = Fieldbook.Muted, fontSize = 13.sp)
        }
    }
}

/** 操作の段の一言（太い丸ゴシック・紙の色の縁取り。天気の上に直接書くので） */
@Composable
private fun Note(text: String) {
    SkyText(text, color = Fieldbook.Ink, fontSize = 14.sp, textAlign = TextAlign.Center)
}

/**
 * 積んだ日数（「12 日ぶん」）。数字はオレンジの大きな太い丸ゴシック、「日ぶん」は墨（90 秒チャレンジの残り時間と同じ形）。
 * 前は 15sp の 1 行の字で小さかった（B の希望で大きくした）。大きさは dp で決め、文字の大きさの設定では大きくしない（上の行に収めるため）
 */
@Composable
private fun DailyDays(days: Int, compact: Boolean) {
    val density = LocalDensity.current
    fun fixed(dp: Float) = with(density) { dp.dp.toSp() }
    val number = if (Fieldbook.dark) Pop.Orange else Pop.OrangeDeep
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$days 日ぶん" },
    ) {
        SkyText("$days", color = number, fontSize = fixed(if (compact) 24f else 30f), maxLines = 1, softWrap = false)
        SkyText("日ぶん", color = Fieldbook.Ink, fontSize = fixed(if (compact) 13f else 15f), modifier = Modifier.padding(start = 4.dp, bottom = if (compact) 3.dp else 5.dp))
    }
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
private fun TodayPartCard(part: Part, colors: PieceColors, width: Dp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${part.displayName}、${part.distanceM} メートル。積む" }
            .padding(8.dp),
    ) {
        Canvas(Modifier.size(56.dp)) {
            // 積んだときと同じ色と線（決定 74）
            drawPiece(part, Offset(size.width / 2, size.height / 2), size.width * 0.8f, colors, outlinePx = PIECE_OUTLINE_DP.dp.toPx())
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
private fun DailyCanvas(vm: DailyViewModel, colorsOf: (Part) -> PieceColors, modifier: Modifier = Modifier) {
    val platform = if (Fieldbook.dark) Color(0xFF7A7FA6) else Color(0xFF4A4E69)
    // 塔の高さの目盛（通常のモードと同じ。HeightRuler.kt。決定 75）
    val ruler = rememberHeightRuler()
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
        // 目盛（塔と同じ変換なので、視点が上がっても塔の高さとずれない）。積み木の下になるように先に描く
        drawHeightRuler(viewport, ruler)
        // 積み木は、天気と時間帯の色で塗り、自分の色を暗くした線で囲む（通常の積む画面と同じ。決定 74）
        val outlinePx = PIECE_OUTLINE_DP.dp.toPx()
        drawWorldShapes(vm.shapes, platform, outlinePx, { sx(it) }, { sy(it) }, viewport.scale, colorsOf)
        val current = vm.current
        if (vm.phase == DailyViewModel.Phase.Aiming && current != null) {
            drawPiece(
                part = current,
                center = Offset(sx(vm.ghostX.toDouble()), sy(vm.spawnY().toDouble())),
                scalePx = viewport.scale * PartSize.of(current.distanceM).toFloat(),
                colors = colorsOf(current),
                outlinePx = outlinePx,
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
