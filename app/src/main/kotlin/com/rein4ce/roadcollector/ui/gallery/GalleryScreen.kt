package com.rein4ce.roadcollector.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.rein4ce.roadcollector.ui.common.paperBand
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import com.rein4ce.roadcollector.ui.settings.NicknameDialog
import java.util.Locale
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.PopButton
import com.rein4ce.roadcollector.ui.common.PopChip
import com.rein4ce.roadcollector.ui.common.PopTag
import com.rein4ce.roadcollector.ui.common.PopRefreshButton
import com.rein4ce.roadcollector.ui.common.BlockWord
import com.rein4ce.roadcollector.ui.common.SECTION_CORNER

/**
 * みんなのタワー（C）。地は「測量野帳」（Fieldbook.kt）の方眼紙で、画面の部品は題字「ミチツミ」の積み木に合わせた
 * ポップな見た目（ui/common/Pop.kt：太い丸ゴシック、アイコンの 4 色、下に濃い色がのぞくブロックの面）。記憶に残る要素は**断面図**（TowerSection.kt）：
 * 物差し（高い順＝高さ、多い順＝パーツ数）ごとに、**1 番のタワーと自分のいちばん良いタワーの 2 基**を同じ縮尺で
 * 1 本の台に並べ、左に標尺、いちばん高い記録に赤鉛筆の線。絵がそのまま差を語る。
 *
 * - 主役は「あなたの最高（最多）」と「最長（最多）まで あと いくつ」
 * - 物差しごとに色を持つ（高い順はオレンジ、多い順は青緑、新しい順は紺）。自分は道の色（オレンジ）、記録は赤鉛筆
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
        // 画面名（題字と同じ積み木）と、右にリロードのブロック
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BlockWord("みんなのタワー")
            Spacer(Modifier.weight(1f))
            PopRefreshButton(refreshing = gallery.refreshing, onClick = { gallery.refresh() })
        }

        GalleryHero(state = state, cmp = cmp, sort = sort, reduceMotion = reduceMotion)

        // 状態の行：読み込み中・つながらない・更新中・取り直せなかった、のときだけ出す（ランキングと共通）
        GalleryStatusRow(gallery)

        when (state) {
            TowerGallery.State.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pop.Teal)
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
                            // weight は fill=false にして（fill=true だと高さが決め打ちの制約になり、heightIn(max) が効かない）、
                            // 上限を掛けたあとに fillMaxHeight で「残りか上限の小さいほう」の高さを取る
                            // （Canvas の中身は Spacer なので、高さが決まった制約でないと 0 になる）。
                            // とても低くなったら（80dp を切る）描かず、下の 2 行だけになる
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = SECTION_HEIGHT)
                                .fillMaxHeight()
                                .paperBand(bleedX = 8.dp, bleedY = 6.dp)
                                // ブロックの面（下に側面がのぞく）。中身は TowerSection が同じ角の丸さで描く
                                .popBlock(face = Fieldbook.Paper, corner = SECTION_CORNER, depth = 4.dp, edge = Fieldbook.CardEdge),
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
                                Text(
                                    "名前を付けると、次に送るタワーから名前が出ます ›",
                                    fontFamily = Pop.Rounded,
                                    fontWeight = FontWeight.Black,
                                    color = Pop.TealLine,
                                    fontSize = 12.sp,
                                    modifier = Modifier.paperBand(bleedX = 6.dp, bleedY = 2.dp),
                                )
                            }
                        }
                    }
                }
                // 自分のタワーだけの一覧（送っていなければ「まだ送っていません」の案内が出る）は二番手の白いブロック、
                // ランキングは主役のオレンジのブロック。高さは下限だけ決める（文字を大きくして 2 行になっても切れない）
                val mineCount = towers.count(isMine)
                PopButton(
                    label = "あなたのタワー（$mineCount 個）",
                    onClick = onMine,
                    face = Fieldbook.Card,
                    text = Pop.TealLine,
                    edge = Pop.TealLine,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                PopButton(
                    label = "ランキングを見る（${towers.size} 個）",
                    onClick = onRanking,
                    face = Pop.OrangeDeep,
                    text = Pop.Letter,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
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
private fun GalleryHero(state: TowerGallery.State, cmp: Comparison, sort: TowerSort, reduceMotion: Boolean) {
    val isLoaded = state is TowerGallery.State.Loaded
    val isError = state is TowerGallery.State.Error
    val myTower = if (cmp.mineIsTop) cmp.top else cmp.mine
    val myValue = myTower?.let { sort.valueOf(it) }
    val topValue = cmp.top?.let { sort.valueOf(it) } ?: 0f
    val none = isLoaded && myTower == null
    // 数字はコレクションの合計距離と同じく、メーターのように 0 から回って上がる（開いたとき・物差しを変えたとき。
    // 取り直して値が変わったときは今の値から）。アニメーションを切っている人には回さず、すぐ出す。
    // 物差しが変わったら、メーターを作り直す（同じものを使い回すと、0 に戻す前の 1 コマに、前の値が新しい単位で出るため。#68 のレビュー）
    val meter = remember(sort) { Animatable(if (reduceMotion) myValue ?: 0f else 0f) }
    LaunchedEffect(sort, myValue) {
        if (myValue == null) return@LaunchedEffect
        if (reduceMotion) meter.snapTo(myValue)
        else meter.animateTo(myValue, tween(durationMillis = 1_100, easing = FastOutSlowInEasing))
    }
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
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.paperBand(), verticalAlignment = Alignment.Bottom) {
            Text(
                label,
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(end = 10.dp, bottom = if (none) 0.dp else 8.dp),
            )
            if (!none) {
                // 数字は物差しの色（高い順はオレンジ、多い順は青緑）。字なので、紙の上で読める濃い色
                Text(
                    if (myValue != null) sort.format(meter.value) else "—",
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 40.sp,
                    color = sortInk(sort),
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    sort.unit,
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = sortInk(sort),
                    modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        when {
            // あと少し：黄色の札。自分が 1 番：自分の色（道のオレンジ）の札
            isLoaded && myValue != null && cmp.mineIsTop -> PopChip("★ $sub", face = Pop.OrangeDeep, text = Pop.Letter, tiltDeg = -2f)
            isLoaded && myValue != null -> PopChip(sub, face = Pop.Yellow, text = Pop.InkOnYellow, tiltDeg = -2f)
            else -> Text(
                sub,
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = if (isError) Fieldbook.Danger else Fieldbook.Muted,
                modifier = Modifier.paperBand(),
            )
        }
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
        // 左の小さな積み木：自分のは道の色（オレンジ）、ほかの人のは紺
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .graphicsLayer { rotationZ = if (mine) -6f else 5f }
                .size(14.dp)
                .popBlock(face = if (mine) Pop.Orange else Pop.Navy, corner = 4.dp, depth = 2.dp),
        )
        // 幅を決めずに、左は呼び名と注記（作者だけ「…」で詰める）、右は高さ。文字を大きくしても切れない
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                label,
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = if (mine) Pop.MineInk else Fieldbook.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 本数・作者・日時（太い丸ゴシック。並びに使っている値は濃い墨）
            TowerMeta(tower, sort, modifier = Modifier.padding(top = 1.dp, bottom = 2.dp))
        }
        Text(
            "$heightText m",
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
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
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = 40.sp,
                    color = Fieldbook.Ink,
                )
                Text("m", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Fieldbook.Ink, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
                if (mine) {
                    PopTag("あなたのタワー", face = Pop.OrangeDeep, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 4.dp, edge = Fieldbook.CardEdge)
                    .clip(RoundedCornerShape(SECTION_CORNER)),
            ) {
                if (sketch != null) TowerSketch(sketch, modifier = Modifier.fillMaxSize().padding(12.dp))
            }
            Spacer(Modifier.height(10.dp))
            // 紙片では物差しに関係なく並べる（どの値も塗らない）。並びは高い順として渡す（高さは上の大きな数字）
            TowerMeta(tower, TowerSort.HEIGHT, partsLabel = true)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                PopButton(
                    label = "共有ページを開く",
                    onClick = { uriHandler.openUri(url) },
                    face = Pop.OrangeDeep,
                    text = Pop.Letter,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                PopButton(
                    label = "リンクをコピー",
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ミチツミ", url))
                        // Android 13 以降は端末が「コピーしました」を出すので、それより前だけ自分で知らせる
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "リンクをコピーしました", Toast.LENGTH_SHORT).show()
                    },
                    face = Fieldbook.Card,
                    text = Pop.TealLine,
                    edge = Pop.TealLine,
                    fontSize = 14.sp,
                )
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
            .popBlock(face = Fieldbook.Card, corner = SECTION_CORNER, depth = 3.dp, edge = Fieldbook.CardEdge)
            .padding(14.dp),
    ) { content() }
}

/** 断面図の高さ */
private val SECTION_HEIGHT = 260.dp
