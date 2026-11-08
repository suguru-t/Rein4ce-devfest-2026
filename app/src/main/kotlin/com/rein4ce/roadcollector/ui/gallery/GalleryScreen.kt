package com.rein4ce.roadcollector.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.rein4ce.roadcollector.ui.common.PressButton
import com.rein4ce.roadcollector.ui.common.PressIconButton
import com.rein4ce.roadcollector.ui.common.PressOutlinedButton
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.settings.NicknameDialog
import java.util.Locale

/**
 * みんなのタワー（C）。見た目は「測量野帳」（Fieldbook.kt）で、記憶に残る要素は**断面図**（TowerSection.kt）：
 * 物差し（高い順＝高さ、多い順＝パーツ数）ごとに、**1 番のタワーと自分のいちばん良いタワーの 2 基**を同じ縮尺で
 * 1 本の台に並べ、左に標尺、いちばん高い記録に赤鉛筆の線。絵がそのまま差を語る。
 *
 * - 主役は「あなたの最高（最多）」と「最長（最多）まで あと いくつ」
 * - 青は押せるものだけ（切り替え・ボタン）。強調は道の色（あなた）と赤鉛筆（記録）の 2 本
 * - 全部のタワーは「ランキングを見る」で別の画面（RankingScreen.kt。紙片の一覧、5 個ずつ）。戻るで帰る
 * - タワーを押すと、下から紙片がせり上がる（アプリの中で完結）。外に出るのは「共有ページを開く」を押したときだけ
 * - 動きは開いたときに一度だけ（標尺が伸び、タワーが台から立つ）。端末でアニメーションを切っている人には動かさない
 */
@Composable
fun GalleryScreen(gallery: TowerGallery = RoadCollectorApp.from(LocalContext.current).gallery) {
    val context = LocalContext.current
    val app = RoadCollectorApp.from(context)
    val state = gallery.state
    val towers = (state as? TowerGallery.State.Loaded)?.towers.orEmpty()
    // 自分のタワー：サーバの答え（持ち主の印が同じ）か、この端末から送った記録（data/MyTowers）のどちらか
    val local by app.myTowers.ids.collectAsStateWithLifecycle()
    val isMine: (TowerSummary) -> Boolean = { it.mine || it.id in local }
    val nickname by app.settings.nickname.collectAsStateWithLifecycle()

    // 画面を開くたびに一覧を取り直す（タブを切り替えて戻ったときも）。起動時の先読み（TowerGallery.prefetch）が
    // まだ走っていれば止めずに待ち、その結果を先に出す
    LaunchedEffect(Unit) { gallery.refresh() }

    // 物差し（高い順・多い順）と、どの画面を開いているか（最初の画面・ランキング・あなたのタワー）。タブを行き来しても、画面を回しても残す
    var sort by rememberSaveable { mutableStateOf(TowerSort.HEIGHT) }
    // 「あなたのタワー」の並びは別に持つ（ここだけ新しい順も選べるので、断面図の物差しと混ぜない）
    var mineSort by rememberSaveable { mutableStateOf(TowerSort.NEWEST) }
    // ほかのタブ（記録・コレクション・積む）に移って戻ってきたときは、ランキングや「あなたのタワー」ではなく最初の画面から。
    // そのため rememberSaveable ではなく remember（タブを移るとこの画面は作り直されるので、最初の画面に戻る）
    var page by remember { mutableStateOf(GalleryPage.OVERVIEW) }
    BackHandler(enabled = page != GalleryPage.OVERVIEW) { page = GalleryPage.OVERVIEW }

    // 紙片を開いているタワーと、ニックネームの窓
    var opened by remember { mutableStateOf<TowerSummary?>(null) }
    var showNickname by remember { mutableStateOf(false) }

    when (page) {
        GalleryPage.OVERVIEW -> Overview(
            gallery = gallery,
            towers = towers,
            isMine = isMine,
            sort = sort,
            onSort = { sort = it },
            openedId = opened?.id,
            onOpen = { opened = it },
            onRanking = { page = GalleryPage.RANKING },
            onMine = { page = GalleryPage.MINE },
            nicknameMissing = nickname.isBlank(),
            onNickname = { showNickname = true },
        )
        GalleryPage.RANKING, GalleryPage.MINE -> RankingScreen(
            gallery = gallery,
            towers = towers,
            isMine = isMine,
            mineOnly = page == GalleryPage.MINE,
            sort = if (page == GalleryPage.MINE) mineSort else sort,
            onSort = { if (page == GalleryPage.MINE) mineSort = it else sort = it },
            onBack = { page = GalleryPage.OVERVIEW },
            onOpen = { opened = it },
        )
    }

    opened?.let { t ->
        TowerSheet(
            tower = t,
            sketch = gallery.sketches[t.id],
            mine = isMine(t),
            onDismiss = { opened = null },
        )
    }
    // ニックネームの窓を直接開く（設定の窓をまるごと開くより 1 回で済む）。設定の中のものと同じ窓
    if (showNickname) {
        NicknameDialog(
            current = nickname,
            onSave = { app.settings.setNickname(it); showNickname = false },
            onDismiss = { showNickname = false },
        )
    }
}

/** 「みんな」の中の画面。最初の画面（断面図）から、ランキングと「あなたのタワー」へ行き、戻るで帰る */
private enum class GalleryPage { OVERVIEW, RANKING, MINE }

/** 「みんな」の最初の画面：見出し、物差しの切り替え、断面図（1 番と自分）、2 基の行、「ランキングを見る」「あなたのタワー」 */
@Composable
private fun Overview(
    gallery: TowerGallery,
    towers: List<TowerSummary>,
    isMine: (TowerSummary) -> Boolean,
    sort: TowerSort,
    onSort: (TowerSort) -> Unit,
    openedId: String?,
    onOpen: (TowerSummary) -> Unit,
    onRanking: () -> Unit,
    onMine: () -> Unit,
    nicknameMissing: Boolean,
    onNickname: () -> Unit,
) {
    val state = gallery.state
    // 自分のかどうか（isMine）は見ている間に変わりうるので、remember せず毎回選ぶ（軽い処理）
    val cmp = comparison(towers, sort, isMine)
    val record = towers.maxOfOrNull { it.height } ?: 0f
    // 断面図に出す 2 基の絵を取る（一覧の JSON には置き方が入っていない）
    LaunchedEffect(cmp) { listOfNotNull(cmp.top, cmp.mine).forEach { gallery.loadSketch(it.id) } }

    // 開いたときの演出：標尺が伸び、タワーが台から立つ。アプリを立ち上げてから一度だけ。
    // アニメーションを切っている人には動かさない
    val reduceMotion = rememberReduceMotion()
    val intro = remember { Animatable(if (SectionIntro.played || reduceMotion) 1f else 0f) }
    LaunchedEffect(towers.isNotEmpty()) {
        if (towers.isNotEmpty() && intro.value < 1f) {
            intro.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
            SectionIntro.played = true
        }
    }

    // 方眼の地はアプリ全体（AppNav の Scaffold）に 1 枚だけ敷いてある（#23）。ここでは敷かない
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    ) {
        // 画面名（野帳にマーカーで線を引いたように）と、右にリロードの印
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "みんなのタワー",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.paperBand().marker(),
            )
            Spacer(Modifier.weight(1f))
            PressIconButton(onClick = { gallery.refresh() }, enabled = !gallery.refreshing) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "再読み込み",
                    tint = if (gallery.refreshing) Fieldbook.Mark else Fieldbook.Survey,
                )
            }
        }

        GalleryHero(state = state, cmp = cmp, sort = sort)

        // 状態の行：読み込み中・つながらない・更新中・取り直せなかった、のときだけ出す（ランキングと共通）
        GalleryStatusRow(gallery)

        when (state) {
            TowerGallery.State.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Fieldbook.Survey)
            }
            // 利用者向けの短い案内だけ。生のエラー文や開発者向けの説明は出さない（手元のサーバの使い方は README）
            is TowerGallery.State.Error -> Note {
                Text("少し待って、右上のリロードの印を押してください。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                Text("電波のない場所か、サーバが起動中かもしれません。", fontSize = 12.sp, color = Fieldbook.Muted)
            }
            is TowerGallery.State.Loaded -> if (towers.isEmpty()) {
                Note {
                    Text("まだタワーがありません。", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                    Text("積んで「サーバに送信」すると、ここに立ちます。", fontSize = 12.sp, color = Fieldbook.Muted)
                }
            } else {
                SortTabs(sort = sort, options = TowerSort.COMPARABLE, onSelect = onSort)
                // 左：その物差しで 1 番。右：自分のいちばん良いタワー（自分が 1 番なら、他人で 1 番のタワー。その順位を呼び名に）
                val slots = listOf(
                    SectionSlot(
                        tower = cmp.top,
                        caption = if (cmp.mineIsTop) "${sort.topLabel}・あなた" else sort.topLabel,
                        mine = cmp.mineIsTop,
                    ),
                    SectionSlot(
                        tower = cmp.mine,
                        caption = if (cmp.mineIsTop) "${cmp.otherRank ?: 2} 番目" else "あなた",
                        mine = !cmp.mineIsTop && cmp.mine != null,
                        emptyNote = "まだありません",
                    ),
                )
                // 残りの高さは、下のボタン 2 つが先に取り、残り全部をこの Box（断面図・2 行・案内）に渡す。
                // 断面図は Box の中で、2 行と案内を引いた残り（上限 SECTION_HEIGHT）まで伸びる。上限を超えて余った分は
                // Box の下の空き（案内とボタンのあいだ）になる。前は断面図と空きを Column の weight で 3 対 1 に分けていたので、
                // 足りないときも空きが先に 1/4 を取って断面図が縮みすぎていた（#68 のレビュー）
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TowerSection(
                            slots = slots,
                            sketches = gallery.sketches,
                            record = record,
                            selected = openedId,
                            progress = { intro.value },
                            onTap = onOpen,
                            // weight は fill=true にする（Canvas の中身は Spacer で、高さが決まった制約のときだけその高さを取る。
                            // fill=false だと 0 になる）。とても低くなったら（80dp を切る）描かず、下の 2 行だけになる
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(max = SECTION_HEIGHT)
                                .paperBand(bleedX = 8.dp, bleedY = 6.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        // 断面図の 2 基を 1 行ずつ（押すと紙片）
                        slots.forEach { s ->
                            val t = s.tower ?: return@forEach
                            TowerRow(label = s.caption, tower = t, mine = isMine(t), sort = sort, onClick = { onOpen(t) })
                        }
                        // ニックネームを付けていない自分のタワーがあるときだけ、一言。押すとニックネームの窓。
                        // 作者名は送るときにタワーに入るので、付けても今までのタワーは名無しのまま（文もそう書く）
                        if (nicknameMissing && towers.any { isMine(it) && it.author.isNullOrBlank() }) {
                            PressTextButton(onClick = onNickname, modifier = Modifier.padding(top = 2.dp)) {
                                Text("名前を付けると、次に送るタワーから名前が出ます", color = Fieldbook.Survey, fontSize = 12.sp)
                            }
                        }
                    }
                }
                // 自分のタワーだけの一覧（送っていなければ「まだ送っていません」の案内が出る）。
                // ボタンの高さは下限だけ決める（文字を大きくして 2 行になっても切れない）
                val mineCount = towers.count(isMine)
                PressOutlinedButton(
                    onClick = onMine,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Fieldbook.Survey),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Fieldbook.Survey),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("あなたのタワー（$mineCount 個）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                PressButton(
                    onClick = onRanking,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Fieldbook.Survey, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text("ランキングを見る（${towers.size} 個）", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 開いたときの演出を、アプリを立ち上げてから一度だけにするための覚え */
private object SectionIntro {
    var played = false
}

/**
 * 見出しの主役：物差しに合わせて「あなたの最高 2.30 m」「最長まで あと 1.15 m」（多い順なら「あなたの最多 4 本」「最多まで あと 1 本」）。
 * 自分が 1 番なら「あなたが最長です」。まだ送っていなければ、その旨と、送るとここに立つこと。
 * 字の行ごとに紙の色を敷く（背景に天気が出るため。決定 26）
 */
@Composable
private fun GalleryHero(state: TowerGallery.State, cmp: Comparison, sort: TowerSort) {
    val isLoaded = state is TowerGallery.State.Loaded
    val isError = state is TowerGallery.State.Error
    val myTower = if (cmp.mineIsTop) cmp.top else cmp.mine
    val myValue = myTower?.let { sort.valueOf(it) }
    val topValue = cmp.top?.let { sort.valueOf(it) } ?: 0f
    val none = isLoaded && myTower == null
    val label = if (none) "まだ送っていません" else if (sort == TowerSort.HEIGHT) "あなたの最高" else "あなたの最多"
    // つながらないときは、下の状態の行と同じ言葉にする（「読み込み中」のままだと食い違って見える）
    val sub = when {
        isError -> "サーバに接続できません"
        !isLoaded -> "読み込み中…"
        myValue == null -> "積んで「サーバに送信」すると、ここに立ちます"
        cmp.mineIsTop -> "あなたが${sort.topLabel}です"
        else -> "${sort.topLabel}まで あと ${sort.format(topValue - myValue)} ${sort.unit}"
    }
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = when {
                isError -> "$label。サーバに接続できません"
                !isLoaded -> "読み込み中"
                myValue == null -> "$label。$sub"
                else -> "$label ${sort.format(myValue)} ${if (sort == TowerSort.HEIGHT) "メートル" else "本"}。$sub"
            }
        },
    ) {
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.paperBand(), verticalAlignment = Alignment.Bottom) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(end = 10.dp, bottom = if (none) 0.dp else 7.dp),
            )
            if (!none) {
                Text(
                    myValue?.let { sort.format(it) } ?: "—",
                    fontFamily = Fieldbook.Data,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = Fieldbook.Ink,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    sort.unit,
                    fontFamily = Fieldbook.Data,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Fieldbook.Ink,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
        }
        Text(sub, style = noteStyle, fontSize = 13.sp, color = Fieldbook.Ink, modifier = Modifier.paperBand())
    }
}

/**
 * 断面図の下の 1 行：呼び名（最長・あなた）、高さ、パーツ数、作者、日時。紙片（カード）にはせず、薄い線で区切る。
 * 押すと紙片が開く。物差しに合わせて、多い順ならパーツ数を墨で
 */
@Composable
private fun TowerRow(label: String, tower: TowerSummary, mine: Boolean, sort: TowerSort, onClick: () -> Unit) {
    val author = tower.author?.takeIf { it.isNotBlank() } ?: "名無し"
    val heightText = "%.2f".format(Locale.US, tower.height)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClickLabel = "詳しく見る", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label。$heightText メートル。パーツ ${tower.partCount} 本、$author、${formatDate(tower.createdAt)}"
            }
            .drawBehind { drawLine(Fieldbook.GridMajor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .paperBand(bleedX = 4.dp, bleedY = 0.dp)
            .padding(horizontal = 4.dp),
    ) {
        // 幅を決めずに、左は呼び名と注記（作者だけ「…」で詰める）、右は高さ。文字を大きくしても切れない
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (mine) Fieldbook.RoadCasing else Fieldbook.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notes("${tower.partCount} 本") + " ・ ",
                    style = noteStyle,
                    fontSize = 12.sp,
                    fontWeight = if (sort == TowerSort.PARTS) FontWeight.Bold else FontWeight.Normal,
                    color = if (sort == TowerSort.PARTS) Fieldbook.Ink else Fieldbook.Muted,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(author, fontSize = 12.sp, color = Fieldbook.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" ・ " + notes(formatDate(tower.createdAt)), style = noteStyle, fontSize = 12.sp, color = Fieldbook.Muted, maxLines = 1, softWrap = false)
            }
        }
        Text(
            "$heightText m",
            fontFamily = Fieldbook.Data,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Fieldbook.Ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * タワー 1 個の紙片（下からせり上がる）。大きな絵、高さ、パーツ数、作者、日時。
 * ボタンは「共有ページを開く」（ブラウザ）と「リンクをコピー」。コピーしたら同じ言葉で知らせる
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TowerSheet(tower: TowerSummary, sketch: Tower?, mine: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val url = "${ApiClient.baseUrl}/towers/${tower.id}"
    val author = tower.author?.takeIf { it.isNotBlank() } ?: "名無し"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Fieldbook.Paper,
        contentColor = Fieldbook.Ink,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.2f".format(Locale.US, tower.height),
                    fontFamily = Fieldbook.Data,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = Fieldbook.Ink,
                )
                Text("m", fontFamily = Fieldbook.Data, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                if (mine) {
                    Text("あなたのタワー", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fieldbook.RoadCasing, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Fieldbook.Card)
                    .border(1.dp, Fieldbook.CardEdge, RoundedCornerShape(4.dp)),
            ) {
                if (sketch != null) TowerSketch(sketch, modifier = Modifier.fillMaxSize().padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                notes("パーツ ${tower.partCount} 本", author, formatDate(tower.createdAt)),
                style = noteStyle,
                fontFamily = Fieldbook.Data,
                fontSize = 13.sp,
                color = Fieldbook.Muted,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PressButton(
                    onClick = { uriHandler.openUri(url) },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Fieldbook.Survey, contentColor = Color.White),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("共有ページを開く", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                PressTextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ミチツミ", url))
                        // Android 13 以降は端末が「コピーしました」を出すので、それより前だけ自分で知らせる
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "リンクをコピーしました", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("リンクをコピー", color = Fieldbook.Survey, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
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
            .background(Fieldbook.Card, RoundedCornerShape(4.dp))
            .border(1.dp, Fieldbook.CardEdge, RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) { content() }
}

/** 断面図の高さ */
private val SECTION_HEIGHT = 260.dp
