package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalDensity
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
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.paperBand
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.dashedOutline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ランキング：全部のタワーを紙片（カード）で 1 列に、[PAGE_SIZE] 個ずつ。「みんな」の「ランキングを見る」から来て、「‹ 戻る」で戻る。
 * [mineOnly] なら「あなたのタワー」：自分のタワーだけを同じ形で並べる。番号はその中での順番（1、2、3）で、
 * 「最長」「最多」の札は付けない（全部の中での 1 番ではないので）。送っていなければ案内。
 * 順位の丸（1〜3 位は測量の青で塗る）、タワーの絵（一覧の中で同じ縮尺）、高さ（主役）、1 番の値を 100% にした横棒、パーツ数・作者・日時。
 * 1 番には「最長」「最多」の札、自分のタワーには「あなた」の札。カードを押すと紙片（TowerSheet）が開く。
 * 読み込み中・取り直せなかったときの知らせと、リロードの印は「みんな」の最初の画面と同じ（[GalleryStatusRow]）
 *
 * @param mineOnly 自分のタワーだけを出す（「あなたのタワー」）
 * @param sort   並び（高い順・多い順。あなたのタワーでは新しい順も）
 */
@Composable
internal fun RankingScreen(
    gallery: TowerGallery,
    towers: List<TowerSummary>,
    isMine: (TowerSummary) -> Boolean,
    mineOnly: Boolean,
    sort: TowerSort,
    onSort: (TowerSort) -> Unit,
    onBack: () -> Unit,
    onOpen: (TowerSummary) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    // 番号はこの一覧の中での順番（「あなたのタワー」なら自分のタワーの中で 1、2、3）。
    // 自分のかどうか（isMine）は見ている間に変わりうるので、remember の鍵にせず毎回選び直す（軽い処理）
    val sorted = remember(towers, sort) { sortTowers(towers, sort) }
    val listed = if (mineOnly) sorted.filter(isMine) else sorted
    val pages = pageCount(listed.size)
    val current = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    // 取り直してタワーが減り、見ていたページが無くなったら最後のページへ。
    // 一覧がまだ無いとき（読み込み中・取れなかったとき）は書き戻さない（見ていたページが 1 ページ目に戻ってしまう。#64 のレビュー）
    LaunchedEffect(current, listed.isEmpty()) { if (listed.isNotEmpty()) page = current }
    val shown = pageOf(listed, current)
    // 絵の縮尺は、この一覧でいちばん高いタワーが枠に入る大きさにそろえる（絵そのものが高さの差を語る）。
    // 横棒は、この一覧の 1 番を 100% にする
    val referenceHeight = sketchHeightFor(listed.maxOfOrNull { it.height } ?: 1f)
    val topValue = listed.maxOfOrNull { sort.valueOf(it) }?.coerceAtLeast(0.01f) ?: 1f

    val listState = rememberLazyListState()
    LaunchedEffect(sort, current) { listState.scrollToItem(0) }
    // 取り直して新しいタワーが 1 番に入ったとき、上のほうを見ていたなら 1 番から見せる。項目に key を付けていると Compose は
    // 「見えていた項目」を先頭に残すので、何もしないと新しい 1 番が画面の上に隠れる（#49 のレビュー）
    LaunchedEffect(shown.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    // 「みんな」の最初の画面と同じ余白・同じ見出しの字（マーカー線）。上に「‹ 戻る」、その下に画面名と個数
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(start = 0.dp, end = 8.dp),
            modifier = Modifier.heightIn(min = 36.dp),
        ) {
            Text("‹ 戻る", color = Fieldbook.Survey, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.paperBand(bleedX = 8.dp, bleedY = 4.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                if (mineOnly) "あなたのタワー" else "ランキング",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.paperBand().marker(),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (mineOnly) "${listed.size} 個" else "全部で ${towers.size} 個",
                fontFamily = Fieldbook.Data,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(bottom = 2.dp).paperBand(),
            )
            IconButton(onClick = { gallery.refresh() }, enabled = !gallery.refreshing) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "再読み込み",
                    tint = if (gallery.refreshing) Fieldbook.Mark else Fieldbook.Survey,
                )
            }
        }
        // 読み込み中・つながらない・取り直せなかった、のときの知らせ（最初の画面と同じ）
        GalleryStatusRow(gallery)
        // 「あなたのタワー」では新しい順も選べる（自分の記録を追う画面なので）
        SortTabs(sort = sort, options = if (mineOnly) TowerSort.entries else TowerSort.COMPARABLE, onSelect = { onSort(it); page = 0 })
        if (mineOnly && listed.isEmpty()) {
            // まだ送っていない（送ったタワーの印は、サーバの持ち主の印か、この端末の記録）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(Fieldbook.Card, CardShape)
                    .border(1.dp, Fieldbook.CardEdge, CardShape)
                    .padding(12.dp),
            ) {
                Text("まだ送っていません。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                Text("積んで「サーバに送信」すると、ここに並びます。", fontSize = 12.sp, color = Fieldbook.Muted)
            }
        }
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
                    rank = current * PAGE_SIZE + i + 1,
                    sort = sort,
                    mine = isMine(t),
                    // 「最長」「最多」の札は全部の中での 1 番だけ（あなたのタワーの 1 番には付けない）
                    showTopTag = !mineOnly,
                    referenceHeight = referenceHeight,
                    ratio = sort.valueOf(t) / topValue,
                    onClick = { onOpen(t) },
                )
            }
        }
        if (pages > 1) {
            PagerRow(page = current, pages = pages, total = listed.size, onPage = { page = it })
        }
    }
}

/**
 * 並びの切り替え。野帳のインデックス（見出しのつまみ）のように、字だけを横に並べ、選んでいるものは測量の青に下線。
 * 読み上げではラジオボタンの組として伝える
 */
@Composable
internal fun SortTabs(sort: TowerSort, options: List<TowerSort>, onSelect: (TowerSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { s ->
            val selected = s == sort
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(s) })
                    .semantics { contentDescription = s.spoken }
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    s.label,
                    color = if (selected) Fieldbook.Survey else Fieldbook.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .paperBand(bleedX = 8.dp, bleedY = 4.dp)
                        .drawBehind {
                            if (selected) {
                                drawRoundRect(
                                    color = Fieldbook.Survey,
                                    topLeft = Offset(0f, size.height + 2.dp.toPx()),
                                    size = Size(size.width, 2.dp.toPx()),
                                    cornerRadius = CornerRadius(1.dp.toPx()),
                                )
                            }
                        },
                )
            }
        }
    }
}

/**
 * タワー 1 個ぶんの紙片：順位、タワーの絵（一覧の中で同じ縮尺。高いタワーほど大きい）、高さ（主役）、パーツ数・作者・日時。
 * [sketch] はまだ取れていなければ null で、絵の場所に破線の枠だけ出す（取れたら差し替わる）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TowerCard(
    tower: TowerSummary,
    sketch: Tower?,
    rank: Int,
    sort: TowerSort,
    mine: Boolean,
    showTopTag: Boolean,
    referenceHeight: Float,
    ratio: Float,
    onClick: () -> Unit,
) {
    val author = tower.author?.takeIf { it.isNotBlank() } ?: "名無し"
    val heightText = "%.2f".format(Locale.US, tower.height)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Fieldbook.Card, CardShape)
            .border(1.dp, Fieldbook.CardEdge, CardShape)
            .clickable(onClickLabel = "詳しく見る", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = (if (mine) "あなたのタワー。" else "") +
                    "${sort.label}で $rank 番目。$heightText メートル。パーツ ${tower.partCount} 本、$author、${formatDate(tower.createdAt)}"
            }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
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
            if (sketch != null) TowerSketch(sketch, modifier = Modifier.fillMaxSize().padding(4.dp), referenceHeight = referenceHeight)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 高さと札。狭い端末で文字を大きくしていて入らないときは、札が次の行に折り返す（押しつぶさない。#66 のレビュー）
            FlowRow(verticalArrangement = Arrangement.Bottom, itemVerticalAlignment = Alignment.Bottom) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(heightText, fontFamily = Fieldbook.Data, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = Fieldbook.Ink, maxLines = 1, softWrap = false)
                    // 単位は数字の 6 割ほど（見出しの「2.68 m」と同じ釣り合い）。数字の下端にそろえる
                    Text("m", fontFamily = Fieldbook.Data, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
                }
                // 全部の中で 1 番のタワーには、物差しに合わせた札（高い順は「最長」、多い順は「最多」）
                if (rank == 1 && showTopTag) Tag(sort.topLabel, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                // 自分のタワーには「あなた」の札（道の濃い縁の色。オレンジの地に白い字は読みにくい）
                if (mine) Tag("あなた", fontSize = 10.sp, color = Fieldbook.RoadCasing, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
            }
            // 物差しの横棒：この一覧の 1 番を 100% にする（高い順・新しい順は高さ、多い順はパーツ数）
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Fieldbook.GridMajor, RoundedCornerShape(2.dp))) {
                Box(modifier = Modifier.fillMaxWidth(ratio.coerceIn(0.02f, 1f)).height(4.dp).background(Fieldbook.Road, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(5.dp))
            // 注記：等幅だと字間が開いて見えるので、本文の字。幅 360dp の端末では 1 行に入らないので、日時は 2 行目に。
            // 作者名が長くても本数が消えないよう、作者だけ「…」で詰める。並びの物差しになっている値（多い順なら本数、新しい順なら日時）は墨で
            val dimStyle = TextStyle(fontSize = 12.sp, color = Fieldbook.Muted)
            val inkStyle = dimStyle.copy(color = Fieldbook.Ink, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(notes("パーツ ${tower.partCount} 本") + " ・ ", style = if (sort == TowerSort.PARTS) inkStyle else dimStyle, maxLines = 1, softWrap = false)
                Text(author, style = dimStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Text(notes(formatDate(tower.createdAt)), style = if (sort == TowerSort.NEWEST) inkStyle else dimStyle, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * 順位の丸。1〜3 位は測量の青で塗って白い数字、4 位からは細い縁だけ（コレクションの「選ぶ丸」と同じ形）。
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
 * 一覧の状態の行：読み込み中・つながらない・更新中・取り直せなかった、のときだけ出す（ふつうは空の行）。
 * 「みんな」の最初の画面とランキングの両方で使う。
 * 読み上げ（TalkBack）に知らせるのは、つながらない・取り直せなかったときだけ（「更新中…」はタブを開くたびに出るので、毎回読み上げない。#64 のレビュー）
 */
@Composable
internal fun GalleryStatusRow(gallery: TowerGallery) {
    val state = gallery.state
    val status = when {
        state is TowerGallery.State.Loading -> StatusLine("サーバから読み込み中…", Fieldbook.Survey)
        state is TowerGallery.State.Error -> StatusLine("サーバに接続できません", Fieldbook.Danger)
        gallery.refreshing -> StatusLine("更新中…", Fieldbook.Survey)
        gallery.refreshFailed -> StatusLine("取り直せませんでした（前の一覧のまま）", Fieldbook.Danger)
        else -> null
    }
    val announce = state is TowerGallery.State.Error || gallery.refreshFailed
    Row(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .wrapContentWidth(Alignment.Start)
            .then(if (status != null) Modifier.paperBand() else Modifier)
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status != null) {
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(status.color))
            Spacer(Modifier.width(8.dp))
            Text(status.text, style = noteStyle, fontSize = 12.sp, color = Fieldbook.Muted)
        }
    }
}

private data class StatusLine(val text: String, val color: Color)

/** 色で塗った小さな札（測量の青の「最長」「最多」、道の濃い縁の色の「あなた」）。白い字 */
@Composable
internal fun Tag(text: String, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = Fieldbook.Survey) {
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
 * 下の「前の 5 個」「次の 5 個」と、いま出している範囲（「1〜5 / 12」）。一度に [PAGE_SIZE] 個までしか出さないので、
 * それより多いときだけ出す。端のページでは、その向きのボタンを押せなくする
 */
@Composable
internal fun PagerRow(page: Int, pages: Int, total: Int, onPage: (Int) -> Unit) {
    val first = page * PAGE_SIZE + 1
    val last = minOf((page + 1) * PAGE_SIZE, total)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 0) {
            Text("‹ 前の $PAGE_SIZE 個", color = if (page > 0) Fieldbook.Survey else Fieldbook.Mark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
            Text("次の $PAGE_SIZE 個 ›", color = if (page < pages - 1) Fieldbook.Survey else Fieldbook.Mark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 野帳にマーカーで引いた線：字の下半分に、道の色（オレンジ）を透かして敷く。字の左右に少しはみ出す（画面名に使う） */
internal fun Modifier.marker(): Modifier = drawBehind {
    val bleed = 4.dp.toPx()
    val top = size.height * 0.45f
    drawRoundRect(
        color = Fieldbook.Road.copy(alpha = 0.4f),
        topLeft = Offset(-bleed, top),
        size = Size(size.width + bleed * 2, size.height - top),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
}

/** タワー 1 個ぶんの紙片の形（手持ちのカードと同じ角丸）と、紙片の中の絵の枠 */
private val CardShape = RoundedCornerShape(4.dp)
private val SketchShape = RoundedCornerShape(3.dp)

internal fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(epochMs))
