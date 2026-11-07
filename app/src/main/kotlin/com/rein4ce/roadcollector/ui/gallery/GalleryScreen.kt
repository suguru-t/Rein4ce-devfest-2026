package com.rein4ce.roadcollector.ui.gallery

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.paperBand
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.dashedOutline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 見た目は他の画面と同じ「測量野帳」（Fieldbook.kt）。見出しはコレクション（B）と同じ形：
 * 小さな「みんなのタワー」の下に、いちばん高いタワーの高さを大きく出し、その下にタワーの数。
 * 背景には採集画面と同じく今の天気が出る（ui/weather/WeatherLayer）ので、見出しの字の行ごとに紙の色を敷く（決定 26）。
 *
 * 方眼の上に、タワーを 1 基ずつ紙片として並べる。一度に出すのは [PAGE_SIZE] 基までで、多いときは下の「前へ」「次へ」でめくる。
 * 並びは見出しの下の札で選ぶ（高い順・新しい順・パーツ数）。カードの番号はいまの並びの上から 1、2、3 で、
 * 1 番には並びに合わせた札（最長・最新・最多）が付く。
 * タワーをタップすると、サーバの共有ページ（LINE / X に貼るのと同じもの）をブラウザで開く。
 */
@Composable
fun GalleryScreen(gallery: TowerGallery = RoadCollectorApp.from(LocalContext.current).gallery) {
    val uriHandler = LocalUriHandler.current
    val state = gallery.state
    val towers = (state as? TowerGallery.State.Loaded)?.towers.orEmpty()
    // このスマホから送信したタワーの id（data/MyTowers）。カードに「あなた」の札を出す
    val mine by RoadCollectorApp.from(LocalContext.current).myTowers.ids.collectAsStateWithLifecycle()

    // 画面を開くたびに一覧を取り直す（タブを切り替えて戻ったときも）。積んで送信した直後に来ても、押さずに新しいタワーが出る。
    // 起動時の先読み（TowerGallery.prefetch）がまだ走っていれば止めずに待ち、その結果を先に出す
    LaunchedEffect(Unit) { gallery.refresh() }

    // 並び順とページ。タブを行き来しても、画面を回しても残す
    var sort by rememberSaveable { mutableStateOf(TowerSort.HEIGHT) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val sorted = remember(towers, sort) { sortTowers(towers, sort) }
    val pages = pageCount(sorted.size)
    // 取り直してタワーが減り、見ていたページが無くなったら最後のページへ
    val current = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    LaunchedEffect(current) { page = current }
    val shown = pageOf(sorted, current)

    // 一覧の位置。並びやページを変えたら上から。取り直して新しいタワーが①に入ったとき、上のほうを見ていたなら①から見せる。
    // 一覧を残したまま取り直すので同じ LazyColumn が使われ続け、項目に key を付けていると Compose は
    // 「見えていた項目」を先頭に残す。何もしないと前の①が先頭のままで、新しい①は画面の上に隠れる（#49 のレビュー）
    val listState = rememberLazyListState()
    LaunchedEffect(sort, current) { listState.scrollToItem(0) }
    LaunchedEffect(shown.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    // 方眼の地はアプリ全体（AppNav の Scaffold）に 1 枚だけ敷いてある（#23）。ここでは敷かない
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        GalleryMeter(
            top = sorted.firstOrNull(),
            total = towers.size,
            sort = sort,
            isLoaded = state is TowerGallery.State.Loaded,
            modifier = Modifier.fillMaxWidth(),
        )

        // 案内の行：普段はタップの説明。裏で取り直しているときは青い点と「更新中」、取り直せなかったときは赤い点。
        // 右はリロードの印（押すと取り直す）
        val status = when {
            state is TowerGallery.State.Loading -> StatusLine("サーバから読み込み中…", Fieldbook.Survey)
            state is TowerGallery.State.Error -> StatusLine("サーバに接続できません", Fieldbook.Danger)
            gallery.refreshing -> StatusLine("更新中…", Fieldbook.Survey)
            gallery.refreshFailed -> StatusLine("取り直せませんでした（前の一覧のまま）", Fieldbook.Danger)
            else -> null
        }
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // 字の幅だけ紙の色を敷く（ui/common/PaperBand。字が方眼の線や天気にかぶって読みにくいため）
                modifier = Modifier
                    .weight(1f)
                    .wrapContentWidth(Alignment.Start)
                    .paperBand()
                    // 少しあとから変わるので、読み上げでも知らせる
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                if (status != null) {
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(status.color))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    status?.text ?: notes("タップで共有ページを開く", "LINE や X に貼れる"),
                    style = noteStyle,
                    fontSize = 12.sp,
                    color = Fieldbook.Muted,
                )
            }
            IconButton(onClick = { gallery.refresh() }, enabled = !gallery.refreshing) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "再読み込み",
                    tint = if (gallery.refreshing) Fieldbook.Mark else Fieldbook.Survey,
                )
            }
        }

        when (state) {
            TowerGallery.State.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Fieldbook.Survey)
            }
            // 利用者向けの短い案内だけ。生のエラー文や開発者向けの説明（手元のサーバの使い方）は出さない。
            // 手元のサーバにつなぐ方法は README「サーバの接続先」にある
            is TowerGallery.State.Error -> Note {
                Text("少し待って、右上のリロードの印を押してください。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                Text("電波のない場所か、サーバが起動中かもしれません。", fontSize = 12.sp, color = Fieldbook.Muted)
            }
            is TowerGallery.State.Loaded -> if (towers.isEmpty()) {
                Note {
                    Text("まだタワーがありません。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                    Text("積んで「サーバに送信」すると、ここに並びます。", fontSize = 12.sp, color = Fieldbook.Muted)
                }
            } else {
                SortRow(sort = sort, onSelect = { sort = it; page = 0 })
                val tallest = towers.maxOf { it.height }.coerceAtLeast(0.01f)
                // 5 基が「前へ」「次へ」の上にちょうど収まるよう、カードの間と上下の余白は詰めてある
                // （下の余白があると、5 基目の下の縁が「次へ」の行に隠れて切れて見えた）
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp),
                ) {
                    itemsIndexed(shown, key = { _, t -> t.id }, contentType = { _, _ -> "tower" }) { i, t ->
                        // 絵の中身はカードが見えたときに取る（一覧の JSON には入っていない）
                        LaunchedEffect(t.id) { gallery.loadSketch(t.id) }
                        TowerCard(
                            tower = t,
                            sketch = gallery.sketches[t.id],
                            // 番号はいまの並びの上から（2 ページ目は 6 から）。1 番には並びに合わせた札（最長・最新・最多）
                            rank = current * PAGE_SIZE + i + 1,
                            sort = sort,
                            // サーバの答え（持ち主の印が同じ）か、この端末から送った記録のどちらかなら「あなた」
                            mine = t.mine || t.id in mine,
                            ratio = t.height / tallest,
                            onClick = { uriHandler.openUri("${ApiClient.baseUrl}/towers/${t.id}") },
                        )
                    }
                }
                if (pages > 1) {
                    PagerRow(page = current, pages = pages, total = sorted.size, onPage = { page = it })
                }
            }
        }
    }
}

/**
 * 見出し。コレクションの見出し（CollectionMeter）と同じ形：小さな画面名の下に、並びに合わせた 1 番の値を大きく出し
 * （高い順は「最長 3.45 m」、新しい順は「最新 11/7 10:16」、パーツ数は「最多 5 本」）、その下に「合計 6 個」。読み込み中は数字を出さない。
 * 字の行ごとに紙の色を敷く（背景に天気が出るので、字が方眼や雨にかぶらないように。決定 26）
 */
@Composable
private fun GalleryMeter(top: TowerSummary?, total: Int, sort: TowerSort, isLoaded: Boolean, modifier: Modifier = Modifier) {
    // 並びごとに、1 番のタワーの「その値」を出す：高い順は高さ [m]、パーツ数は本数、新しい順は日時（数字は回さない）
    val target: Float = when (sort) {
        TowerSort.HEIGHT -> top?.height ?: 0f
        TowerSort.PARTS -> top?.partCount?.toFloat() ?: 0f
        TowerSort.NEWEST -> 0f
    }
    // 数字はメーターのように 0 から回って上がる（開くたび・並びを変えたとき・取り直して伸びたとき）。
    // アニメーションを切っている人には回さず、すぐ出す
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(sort, target) {
        if (reduceMotion) {
            shown.snapTo(target)
        } else {
            shown.snapTo(0f)
            shown.animateTo(target, tween(durationMillis = 1_100, easing = FastOutSlowInEasing))
        }
    }
    // 大きな字と、その右の小さな字（単位。新しい順は時刻）。読み上げ用の言い方も一緒に
    val big: String
    val small: String
    val spoken: String
    when (sort) {
        TowerSort.HEIGHT -> {
            big = if (top != null) "%.2f".format(Locale.US, shown.value) else "—"
            small = "m"
            spoken = "最長 ${top?.let { "%.2f".format(Locale.US, it.height) }} メートル"
        }
        TowerSort.PARTS -> {
            big = if (top != null) "${shown.value.roundToInt()}" else "—"
            small = "本"
            spoken = "最多 ${top?.partCount} 本"
        }
        TowerSort.NEWEST -> {
            big = top?.let { formatDay(it.createdAt) } ?: "—"
            small = top?.let { formatTime(it.createdAt) } ?: ""
            spoken = "最新 ${top?.let { formatDate(it.createdAt) }}"
        }
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = when {
                !isLoaded -> "みんなのタワー。読み込み中"
                top == null -> "みんなのタワー。まだタワーがありません"
                else -> "みんなのタワー。$spoken。合計 $total 個"
            }
        },
    ) {
        // 画面名：野帳にマーカーで線を引いたように、字の下半分に道の色（オレンジ）を透かして敷く
        Text(
            "みんなのタワー",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = Fieldbook.Ink,
            modifier = Modifier.paperBand().marker(),
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.paperBand(), verticalAlignment = Alignment.Bottom) {
            // 並びに合わせた札（最長・最新・最多）。1 番のカードにも同じ札が付く
            Tag(sort.topLabel, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp, bottom = 10.dp))
            Text(
                big,
                fontFamily = Fieldbook.Data,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = Fieldbook.Ink,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                small,
                fontFamily = Fieldbook.Data,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            )
        }
        Text(
            if (isLoaded) notes("合計 $total 個") else "読み込み中…",
            style = noteStyle,
            fontSize = 13.sp,
            color = Fieldbook.Ink,
            modifier = Modifier.paperBand(),
        )
    }
}

/** 野帳にマーカーで引いた線：字の下半分に、道の色（オレンジ）を透かして敷く。字の左右に少しはみ出す */
private fun Modifier.marker(): Modifier = drawBehind {
    val bleed = 4.dp.toPx()
    val top = size.height * 0.45f
    drawRoundRect(
        color = Fieldbook.Road.copy(alpha = 0.4f),
        topLeft = Offset(-bleed, top),
        size = Size(size.width + bleed * 2, size.height - top),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
}

/** 色で塗った小さな札（測量の青の「最長」、道の色の「あなた」）。白い字。見出しとカードで同じ形 */
@Composable
private fun Tag(text: String, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = Fieldbook.Survey) {
    Text(
        text,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 高さの順位の丸。1〜3 位は測量の青で塗って白い数字、4 位からは細い縁だけ（コレクションの「選ぶ丸」と同じ形）。
 * 丸の大きさは決まっているので、数字は文字の大きさの設定で大きくしない（大きくすると丸からはみ出す）
 */
@Composable
private fun RankMark(rank: Int, modifier: Modifier = Modifier) {
    val top = rank in 1..3
    val numberSize = with(LocalDensity.current) { (if (rank >= 100) 11.dp else 14.dp).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (top) Fieldbook.Survey else Color.Transparent)
            .border(1.5.dp, if (top) Fieldbook.Survey else Fieldbook.Muted, CircleShape),
    ) {
        Text(
            "$rank",
            color = if (top) Color.White else Fieldbook.Muted,
            fontFamily = Fieldbook.Data,
            fontSize = numberSize,
            lineHeight = numberSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 並び順を選ぶ札の列（高い順・新しい順・パーツ数）。コレクションの「選択」ボタンと同じ、測量の青の細い縁の小さな丸い札。
 * 選んでいる札は青く塗る。読み上げではラジオボタンの組として伝える
 */
@Composable
private fun SortRow(sort: TowerSort, onSelect: (TowerSort) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TowerSort.entries.forEach { s ->
            val selected = s == sort
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(s) })
                    .semantics { contentDescription = s.spoken },
            ) {
                Text(
                    s.label,
                    color = if (selected) Color.White else Fieldbook.Survey,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .background(if (selected) Fieldbook.Survey else Fieldbook.Card, shape)
                        .border(1.dp, Fieldbook.Survey, shape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * 下の「前へ」「次へ」と、いま出している範囲（「1〜5 / 12」）。一度に [PAGE_SIZE] 基までしか出さないので、
 * それより多いときだけ出す。端のページでは、その向きのボタンを押せなくする
 */
@Composable
private fun PagerRow(page: Int, pages: Int, total: Int, onPage: (Int) -> Unit) {
    val first = page * PAGE_SIZE + 1
    val last = minOf((page + 1) * PAGE_SIZE, total)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 0) {
            Text("‹ 前へ", color = if (page > 0) Fieldbook.Survey else Fieldbook.Mark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "$first〜$last / $total",
            fontFamily = Fieldbook.Data,
            fontSize = 12.sp,
            color = Fieldbook.Muted,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .paperBand()
                .semantics { contentDescription = "$total 個のうち $first から $last 番目" },
        )
        TextButton(onClick = { onPage(page + 1) }, enabled = page < pages - 1) {
            Text("次へ ›", color = if (page < pages - 1) Fieldbook.Survey else Fieldbook.Mark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * タワー 1 基ぶんの紙片：高さの順位、タワーの絵、高さ（主役）、高さの目盛り、パーツ数・作者・日時。
 * [sketch] はまだ取れていなければ null で、絵の場所に破線の枠だけ出す（取れたら差し替わる）
 */
@Composable
private fun TowerCard(tower: TowerSummary, sketch: Tower?, rank: Int, sort: TowerSort, mine: Boolean, ratio: Float, onClick: () -> Unit) {
    val author = tower.author?.takeIf { it.isNotBlank() } ?: "名無し"
    val heightText = "%.2f".format(Locale.US, tower.height)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Fieldbook.Card, CardShape)
            .border(1.dp, Fieldbook.CardEdge, CardShape)
            .clickable(onClickLabel = "共有ページを開く", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = (if (mine) "あなたのタワー。" else "") +
                    "${sort.label}で $rank 番目。$heightText メートル。パーツ ${tower.partCount} 本、$author、${formatDate(tower.createdAt)}"
            }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // 高さの順位の丸（幅 36dp の中に置く）
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) { RankMark(rank) }
        // タワーの絵（野帳の図）。コレクションのカードと同じ「紙の上の図」の見せ方
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 80.dp)
                // 範囲の見積もりが外れて絵がはみ出しても、枠で切れるだけにする（隣の順位や文字の上に描かない）
                .clip(SketchShape)
                .background(Fieldbook.Paper, SketchShape)
                .then(if (sketch == null) Modifier.dashedOutline(Fieldbook.CardEdge, corner = 3.dp) else Modifier.border(1.dp, Fieldbook.CardEdge, SketchShape)),
        ) {
            if (sketch != null) TowerSketch(sketch, modifier = Modifier.fillMaxSize().padding(4.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(heightText, fontFamily = Fieldbook.Data, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = Fieldbook.Ink)
                Text("m", fontFamily = Fieldbook.Data, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                // 1 番のタワーには、並びに合わせた札（高い順は見出しと同じ「最長」、新しい順は「最新」、パーツ数は「最多」）
                if (rank == 1) Tag(sort.topLabel, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                // このスマホから送信したタワーには、道の色（オレンジ）の「あなた」の札
                if (mine) Tag("あなた", fontSize = 10.sp, color = Fieldbook.Road, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
            }
            // 高さの目盛り：いちばん高いタワーを 100% にした横棒（測量の断面図のイメージ）
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Fieldbook.GridMajor, RoundedCornerShape(2.dp))) {
                Box(modifier = Modifier.fillMaxWidth(ratio.coerceIn(0.02f, 1f)).height(4.dp).background(Fieldbook.Road, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(4.dp))
            // 作者名が長くても日時が消えないよう、作者だけ「…」で詰める
            val metaStyle = TextStyle(fontFamily = Fieldbook.Data, fontSize = 10.5.sp, color = Fieldbook.Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("パーツ ${tower.partCount} 本 ・ ", style = metaStyle, maxLines = 1, softWrap = false)
                Text(author, style = metaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" ・ ${formatDate(tower.createdAt)}", style = metaStyle, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 説明や注意を書く紙片（一覧が出せないとき） */
@Composable
private fun Note(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(Fieldbook.Card, CardShape)
            .border(1.dp, Fieldbook.CardEdge, CardShape)
            .padding(12.dp),
    ) { content() }
}

private data class StatusLine(val text: String, val color: Color)

/** タワー 1 基ぶんの紙片の形（手持ちのカードと同じ角丸） */
private val CardShape = RoundedCornerShape(4.dp)

/** 紙片の中のタワーの絵の枠 */
private val SketchShape = RoundedCornerShape(3.dp)

private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(epochMs))

/** 見出しの「最新」用：日付だけ（11/7）と、時刻だけ（10:16） */
private fun formatDay(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))

private fun formatTime(epochMs: Long): String =
    if (epochMs <= 0L) "" else SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(epochMs))
