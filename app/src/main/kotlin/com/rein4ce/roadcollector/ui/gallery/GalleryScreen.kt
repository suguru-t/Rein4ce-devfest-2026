package com.rein4ce.roadcollector.ui.gallery

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.net.ApiClient
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.inventory.dashedOutline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 見た目は他の画面と同じ「測量野帳」（Fieldbook.kt）。
 * 方眼の上に、タワーを 1 基ずつ紙片として並べる。高さの順位表なので、上から順に ①②③ を振る。
 * タワーをタップすると、サーバの共有ページ（LINE / X に貼るのと同じもの）をブラウザで開く。
 */
@Composable
fun GalleryScreen(gallery: TowerGallery = RoadCollectorApp.from(LocalContext.current).gallery) {
    val uriHandler = LocalUriHandler.current
    val state = gallery.state
    val towers = (state as? TowerGallery.State.Loaded)?.towers.orEmpty()

    // 画面を開くたびに一覧を取り直す（タブを切り替えて戻ったときも）。積んで送信した直後に来ても、押さずに新しいタワーが出る。
    // 起動時の先読み（TowerGallery.prefetch）がまだ走っていれば止めずに待ち、その結果を先に出す
    LaunchedEffect(Unit) { gallery.refresh() }

    // 方眼の地はアプリ全体（AppNav の Scaffold）に 1 枚だけ敷いてある（#23）。ここでは敷かない
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        // 見出し：左に画面名、右に測量の数値（他の画面と同じ位置・字）
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("みんなのタワー", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
            Spacer(Modifier.weight(1f))
            Text(
                if (state is TowerGallery.State.Loaded) "${towers.size} 基" else "—",
                fontFamily = Fieldbook.Data,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // 状態行：読み込み中は青、つながらないときは赤。右に「再読み込み」
        val status = when (state) {
            TowerGallery.State.Loading -> StatusLine("サーバから読み込み中…", Fieldbook.Survey)
            is TowerGallery.State.Error -> StatusLine("サーバに接続できません", Fieldbook.Danger)
            // 一覧を出したまま裏で取り直しているときは青い点と「更新中」、取り直せなかったときは赤い点で分かるようにする
            is TowerGallery.State.Loaded -> when {
                gallery.refreshing -> StatusLine("更新中… ・ ${serverHost()}", Fieldbook.Survey)
                gallery.refreshFailed -> StatusLine("取り直せませんでした（前の一覧のまま）", Fieldbook.Danger)
                else -> StatusLine("高さの順位表 ・ ${serverHost()}", Fieldbook.Mark)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(status.color))
            Spacer(Modifier.width(8.dp))
            Text(status.text, fontSize = 12.sp, color = Fieldbook.Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { gallery.refresh() }, enabled = !gallery.refreshing) {
                Text("再読み込み", color = Fieldbook.Survey, fontSize = 13.sp)
            }
        }

        when (state) {
            TowerGallery.State.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Fieldbook.Survey)
            }
            // 利用者向けの短い案内だけ。生のエラー文や開発者向けの説明（手元のサーバの使い方）は出さない。
            // 手元のサーバにつなぐ方法は README「サーバの接続先」にある
            is TowerGallery.State.Error -> Note {
                Text("少し待って「再読み込み」を押してください。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                Text("電波のない場所か、サーバが起動中かもしれません。", fontSize = 12.sp, color = Fieldbook.Muted)
            }
            is TowerGallery.State.Loaded -> if (towers.isEmpty()) {
                Note {
                    Text("まだタワーがありません。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                    Text("積んで「サーバに送信」すると、ここに並びます。", fontSize = 12.sp, color = Fieldbook.Muted)
                }
            } else {
                val tallest = towers.maxOf { it.height }.coerceAtLeast(0.01f)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                ) {
                    itemsIndexed(towers, key = { _, t -> t.id }, contentType = { _, _ -> "tower" }) { i, t ->
                        // 絵の中身はカードが見えたときに取る（一覧の JSON には入っていない）
                        LaunchedEffect(t.id) { gallery.loadSketch(t.id) }
                        TowerCard(
                            tower = t,
                            sketch = gallery.sketches[t.id],
                            rank = i + 1,
                            ratio = t.height / tallest,
                            onClick = { uriHandler.openUri("${ApiClient.baseUrl}/towers/${t.id}") },
                        )
                    }
                }
            }
        }
    }
}

/**
 * タワー 1 基ぶんの紙片：順位、タワーの絵、高さ（主役）、高さの目盛り、パーツ数・作者・日時。
 * [sketch] はまだ取れていなければ null で、絵の場所に破線の枠だけ出す（取れたら差し替わる）
 */
@Composable
private fun TowerCard(tower: TowerSummary, sketch: Tower?, rank: Int, ratio: Float, onClick: () -> Unit) {
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
                contentDescription = "$rank 位。高さ $heightText メートル。パーツ ${tower.partCount} 本、$author、${formatDate(tower.createdAt)}"
            }
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        // 順位。9 位までは選んだ順の ①② と同じ丸数字、10 位からは丸数字がないので測量の字で数字だけ（幅 36dp に収める）
        if (rank <= 9) {
            Text(
                Fieldbook.orderMark(rank),
                color = if (rank <= 3) Fieldbook.Survey else Fieldbook.Muted,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
            )
        } else {
            Text(
                "$rank",
                fontFamily = Fieldbook.Data,
                color = Fieldbook.Muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(36.dp),
            )
        }
        // タワーの絵（野帳の図）。コレクションのカードと同じ「紙の上の図」の見せ方
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 64.dp)
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

/** 状態行に出すサーバ名。URL 全体は長いのでホスト名だけ */
private fun serverHost(): String = ApiClient.baseUrl.removePrefix("https://").removePrefix("http://").substringBefore('/')

private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(epochMs))
