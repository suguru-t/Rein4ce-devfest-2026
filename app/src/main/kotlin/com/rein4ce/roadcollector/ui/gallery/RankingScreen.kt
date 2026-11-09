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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import com.rein4ce.roadcollector.ui.common.pressFeel
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.dashedOutline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.PopChip
import com.rein4ce.roadcollector.ui.common.PopTag
import com.rein4ce.roadcollector.ui.common.PopRefreshButton
import com.rein4ce.roadcollector.ui.common.SkyText
import com.rein4ce.roadcollector.ui.common.BlockWord
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER

/**
 * ランキング：全部のタワーを紙片（カード）で 1 列に、[PAGE_SIZE] 個ずつ。「みんな」の「ランキングを見る」から来て、「‹ 戻る」で戻る。
 * [mineOnly] なら「あなたのタワー」：自分のタワーだけを同じ形で並べる。番号はその中での順番（1、2、3）で、
 * 「最長」「最多」の札は付けない（全部の中での 1 番ではないので）。送っていなければ案内。
 * 見た目は「みんな」の最初の画面と同じポップなブロック（ui/common/Pop.kt）。
 * 順位の積み木（1〜3 位は金・銀・銅）、タワーの絵（一覧の中で同じ縮尺）、高さ（主役）、1 番の値を 100% にした横棒（物差しの色）、パーツ数・作者・日時。
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

    // 「みんな」の最初の画面と同じ余白・同じ見出し（積み木）。上に「‹ 戻る」の黄色い札、その下に画面名と個数とリロード
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        PopChip("‹ 戻る", face = Pop.Yellow, text = Pop.InkOnYellow, fontSize = 14.sp, onClick = onBack)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BlockWord(if (mineOnly) "あなたのタワー" else "ランキング")
            Spacer(Modifier.weight(1f))
            // 天気の雲の上に直接書くので、紙の色の縁取りを付ける（ダークのくもりで沈まないように）
            SkyText(
                if (mineOnly) "${listed.size} 個" else "全部で ${towers.size} 個",
                color = Fieldbook.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
            PopRefreshButton(refreshing = gallery.refreshing, onClick = { gallery.refresh() })
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
                    .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
                    .padding(14.dp),
            ) {
                Text("まだ送っていません。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                Text("積んで「サーバに送信」すると、ここに並びます。", fontSize = 12.sp, color = Fieldbook.Muted)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 4.dp),
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
 * 並びの切り替え。選択肢を横に並べ、1 つずつ丸いブロックの札にする。物差しごとに色を持ち（高い順はオレンジ、多い順は青緑、
 * 新しい順は紺。見出しの数字や横棒と同じ色）、選んでいる札はその色で塗って白い字・下に側面がのぞく。選んでいない札は白い面に色の縁。
 * 読み上げではラジオボタンの組として伝える。押し心地（#76）はほかのボタンとそろえ、札の形だけが縮む（波紋は出さない）
 */
@Composable
internal fun SortTabs(sort: TowerSort, options: List<TowerSort>, onSelect: (TowerSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { s ->
            val selected = s == sort
            val source = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .pressFeel(source)
                    .selectable(
                        selected = selected,
                        interactionSource = source,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(s) },
                    )
                    .semantics { contentDescription = s.spoken },
            ) {
                Text(
                    s.label,
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (selected) Pop.Letter else sortInk(s),
                    maxLines = 1,
                    modifier = Modifier
                        .popBlock(
                            face = if (selected) sortFace(s) else Fieldbook.Card,
                            corner = 50.dp,
                            depth = if (selected) 4.dp else 2.dp,
                            edge = if (selected) null else sortInk(s),
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
            // ブロックの紙片（下に側面がのぞく）。押したときの波紋は角の丸い面の中だけ
            .popBlock(face = Fieldbook.Card, corner = CARD_CORNER, depth = 3.dp, edge = if (mine) Pop.Orange else Fieldbook.CardEdge)
            .clip(CardShape)
            .clickable(onClickLabel = "詳しく見る", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = (if (mine) "あなたのタワー。" else "") +
                    "${sort.label}で $rank 番目。$heightText メートル。パーツ ${tower.partCount} 本、$author、${formatDate(tower.createdAt)}"
            }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) { RankMark(rank) }
        // タワーの絵（野帳の図）。コレクションのカードと同じ「紙の上の図」の見せ方
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 80.dp)
                // 範囲の見積もりが外れて絵がはみ出しても、枠で切れるだけにする（隣の順位や文字の上に描かない）
                .clip(SketchShape)
                .background(Fieldbook.Paper, SketchShape)
                .then(if (sketch == null) Modifier.dashedOutline(Fieldbook.CardEdge, corner = 8.dp) else Modifier.border(1.dp, Fieldbook.CardEdge, SketchShape)),
        ) {
            if (sketch != null) TowerSketch(sketch, modifier = Modifier.fillMaxSize().padding(4.dp), referenceHeight = referenceHeight)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 高さと札。狭い端末で文字を大きくしていて入らないときは、札が次の行に折り返す（押しつぶさない。#66 のレビュー）
            FlowRow(verticalArrangement = Arrangement.Bottom, itemVerticalAlignment = Alignment.Bottom) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(heightText, fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 26.sp, color = Fieldbook.Ink, maxLines = 1, softWrap = false)
                    // 単位は数字の 6 割ほど（見出しの「2.68 m」と同じ釣り合い）。数字の下端にそろえる
                    Text("m", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Fieldbook.Ink, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                }
                // 全部の中で 1 番のタワーには、物差しに合わせた札（高い順は「最長」、多い順は「最多」）。記録の札は紺
                if (rank == 1 && showTopTag) PopTag(sort.topLabel, face = Pop.Navy, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                // 自分のタワーには「あなた」の札（自分の色＝道のオレンジを、白い字が読める濃さにしたもの）
                if (mine) PopTag("あなた", face = Pop.OrangeDeep, tiltDeg = 3f, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            }
            // 物差しの横棒：この一覧の 1 番を 100% にする（高い順・新しい順は高さ、多い順はパーツ数）。色は物差しの色
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Fieldbook.GridMajor, RoundedCornerShape(3.dp))) {
                Box(modifier = Modifier.fillMaxWidth(ratio.coerceIn(0.02f, 1f)).height(6.dp).background(sortInk(sort), RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.height(7.dp))
            // 注記：本数・作者・日時（太い丸ゴシック。作者は「…」で詰める）。幅 360dp の端末では 1 行に入らないので、日時は 2 行目に。
            // 並びの物差しになっている値（多い順なら本数、新しい順なら日時）は濃い墨
            TowerMeta(tower, sort, partsLabel = true, dateOnNewLine = true)
        }
    }
}

/**
 * 順位の積み木。1〜3 位は金（黄）・銀・銅のブロックに墨の数字、4 位からは白い面に細い縁だけ。左右交互に少し傾ける。
 * ブロックの大きさは決まっているので、数字は文字の大きさの設定で大きくしない（大きくするとはみ出す）
 */
@Composable
private fun RankMark(rank: Int, modifier: Modifier = Modifier) {
    val face = when (rank) {
        1 -> Pop.Yellow
        2 -> Pop.Silver
        3 -> Pop.Bronze
        else -> null
    }
    val numberSize = with(LocalDensity.current) { (if (rank >= 100) 11.dp else 15.dp).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { rotationZ = if (rank % 2 == 1) -5f else 4f }
            .size(width = 30.dp, height = 33.dp)
            .popBlock(face = face ?: Fieldbook.Card, corner = 8.dp, depth = 3.dp, edge = if (face == null) Fieldbook.Mark else null),
    ) {
        Text(
            "$rank",
            color = if (face != null) Pop.InkOnYellow else Fieldbook.Muted,
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            fontSize = numberSize,
            lineHeight = numberSize,
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
        state is TowerGallery.State.Loading -> StatusLine("サーバから読み込み中…", Pop.Teal)
        state is TowerGallery.State.Error -> StatusLine("サーバに接続できません", Fieldbook.Danger)
        gallery.refreshing -> StatusLine("更新中…", Pop.Teal)
        gallery.refreshFailed -> StatusLine("取り直せませんでした（前の一覧のまま）", Fieldbook.Danger)
        else -> null
    }
    val announce = state is TowerGallery.State.Error || gallery.refreshFailed
    Row(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .wrapContentWidth(Alignment.Start)
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status != null) {
            // 小さな積み木（傾けた四角）の印
            Box(modifier = Modifier.graphicsLayer { rotationZ = 12f }.size(10.dp).clip(RoundedCornerShape(3.dp)).background(status.color))
            Spacer(Modifier.width(8.dp))
            SkyText(status.text, color = Fieldbook.Muted, fontSize = 12.sp)
        }
    }
}

private data class StatusLine(val text: String, val color: Color)

/**
 * 下の「前の 5 個」「次の 5 個」と、いま出している範囲（「1〜5 / 12」）。一度に [PAGE_SIZE] 個までしか出さないので、
 * それより多いときだけ出す。端のページでは、その向きのボタンを押せなくする
 */
@Composable
internal fun PagerRow(page: Int, pages: Int, total: Int, onPage: (Int) -> Unit) {
    val first = page * PAGE_SIZE + 1
    val last = minOf((page + 1) * PAGE_SIZE, total)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // めくれるときは黄色の札、端で押せないときは白い面に薄い縁
        val canPrev = page > 0
        val canNext = page < pages - 1
        PopChip(
            "‹ 前の $PAGE_SIZE 個",
            face = if (canPrev) Pop.Yellow else Fieldbook.Card,
            text = if (canPrev) Pop.InkOnYellow else Fieldbook.Mark,
            edge = if (canPrev) null else Fieldbook.CardEdge,
            depth = if (canPrev) 3.dp else 1.dp,
            enabled = canPrev,
            onClick = { onPage(page - 1) },
        )
        SkyText(
            "$first〜$last / $total",
            color = Fieldbook.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .wrapContentWidth(Alignment.CenterHorizontally)
                // 縁取りの字と本来の字の 2 つが中にあるので、外側で 1 つにまとめて、説明だけを読ませる（#115 のレビュー）
                .clearAndSetSemantics { contentDescription = "$total 個のうち $first から $last 番目" },
        )
        PopChip(
            "次の $PAGE_SIZE 個 ›",
            face = if (canNext) Pop.Yellow else Fieldbook.Card,
            text = if (canNext) Pop.InkOnYellow else Fieldbook.Mark,
            edge = if (canNext) null else Fieldbook.CardEdge,
            depth = if (canNext) 3.dp else 1.dp,
            enabled = canNext,
            onClick = { onPage(page + 1) },
        )
    }
}

/** タワー 1 個ぶんの紙片の形（ブロックの面と同じ角の丸さ）と、紙片の中の絵の枠 */
private val CARD_CORNER = 14.dp
private val CardShape = RoundedCornerShape(CARD_CORNER)
private val SketchShape = RoundedCornerShape(8.dp)

internal fun formatDate(epochMs: Long): String =
    // 日付だけ（時刻は出さない）。歩いてすぐ積んで送ると、送った時刻から歩いた時刻が分かってしまうため。
    // 一覧は 1 週間ごとに入れ替える予定なので、日付で足りる（#92・決定 47。アプリも送る前に日付に丸める）
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))
