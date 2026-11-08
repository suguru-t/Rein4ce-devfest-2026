package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.heading
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.gallery.Pop
import com.rein4ce.roadcollector.ui.gallery.PopButton
import com.rein4ce.roadcollector.ui.gallery.popBlock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.data.GameSession
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.WalkNote
import com.rein4ce.roadcollector.ui.common.notes
import kotlinx.coroutines.launch

/**
 * 積む画面から開く「積むパーツを選ぶ」窓（下から出る）。コレクションに戻らずに、その場で積むパーツを選び直せる。
 *
 * - 見た目はコレクションの一覧と同じポップな形（太い丸ゴシックの見出し、ブロックの札・カード・ボタン。ui/gallery/Pop.kt）
 * - コレクションと同じ並びで、小さなカード（野帳の図・名前・歩いた長さ）を 3 列に並べる。
 *   図には天気の色とアイコンが出るので、どれが大きいか・滑るかを見て選べる
 * - タップで選ぶ／外す（[GameSession.MAX_PARTS] 本まで）。図の右下の丸に積む順の数字が入る（コレクションの選択と同じ）
 * - 開いたときは、いま積んでいるパーツを選んだ状態にする（1 本だけ入れ替えるときに楽なように）
 * - 「選んだ n 本で積む」で、コレクションから選んだときと同じ開始要求（[GameSession.requestStart]）を出す。
 *   積む画面はそれを受け取って、新しいゲームを始める
 *
 * @param initial はじめに選んでおくパーツの id（積む順）
 * @param onStart 選んだパーツの id（積む順）で始める
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartPickerSheet(initial: List<String>, onDismiss: () -> Unit, onStart: (List<String>) -> Unit) {
    val repository = RoadCollectorApp.from(LocalContext.current).partRepository
    val weathers by repository.weather.collectAsStateWithLifecycle()
    val shown = rememberArrangedParts(repository)
    // 並びと絞り込みは、コレクションと共通（設定に保存）。お気に入りは、コレクションと同じ ☆ で入れたり外したりできる
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    // 並びと絞り込みとお気に入りは、コレクションと共通（InventoryOrder.kt の rememberPartFiltering）
    val filtering = rememberPartFiltering(repository, settings)
    val filter = filtering.filter
    val favorites = filtering.favorites
    val parts by repository.parts.collectAsStateWithLifecycle()
    val isLoaded by repository.isLoaded.collectAsStateWithLifecycle()
    val visible = remember(shown, filtering) { filtering.apply(shown) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial.distinct().take(GameSession.MAX_PARTS)) } }
    // 選んだあとに消されたパーツは数えない
    val have = shown.mapTo(HashSet()) { it.id }
    val valid = selected.filter { it in have }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Fieldbook.Paper,
        // 窓の上の端は、状態バー（時刻・カメラの穴）の下で止める。止めないと、パーツが多くて窓がいちばん上まで
        // 伸びたときに、窓を下げる取っ手がカメラの穴と重なる
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "積むパーツを選ぶ",
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            )
            Text(
                notes("タップで選ぶ", "${GameSession.MAX_PARTS} 本まで", "長く歩いた道ほど大きい"),
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
            )
            PartFilterBar(
                filter = filter,
                onChange = settings::setPartFilter,
                skies = filtering.skies,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // カードを並べる所だけ、端から端まで方眼紙にする（コレクションと同じく、方眼の上に紙片を置いた見た目）。
            // 見出しとボタンは、字に線がかからないよう無地の紙のまま。この窓はメイン画面とは別の窓なので、見出しの下の線を消す仕組みは使わない
            // 並びや絞り込みを変えたら、いちばん上から見せる（コレクションと同じ）
            val gridState = rememberLazyGridState()
            ScrollToTopOnChange(filter, gridState)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                // 一覧は、見出しとボタンを置いた残りの高さに収める（決まった高さにすると、小さい画面や大きい文字で
                // 窓に入りきらず、下の「選んだ n 本で積む」が切れて押せなくなる。#74 の A のレビュー）
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).fieldbookGrid(clearings = false),
            ) {
                // 絞り込んだら何も残らなかったとき（コレクションが空のときや読み込み中は出さない）。コレクションと同じく外せるように
                if (isLoaded && parts.isNotEmpty() && visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 24.dp),
                        ) {
                            Text("当てはまるパーツがありません", fontSize = 14.sp, color = Fieldbook.Muted)
                            PressTextButton(onClick = { settings.setPartFilter(filter.copy(sky = null, favoritesOnly = false)) }) {
                                Text("絞り込みを外す", color = Fieldbook.Survey, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                items(visible, key = { it.id }) { part ->
                    val index = valid.indexOf(part.id)
                    PickerCard(
                        part = part,
                        sky = repository.weatherOf(part.id, weathers)?.sky,
                        favorite = part.id in favorites,
                        onToggleFavorite = { repository.setFavorite(part.id, part.id !in favorites) },
                        order = if (index >= 0) index + 1 else null,
                        canSelect = index >= 0 || valid.size < GameSession.MAX_PARTS,
                        onToggle = {
                            if (part.id in selected) selected.remove(part.id)
                            else if (valid.size < GameSession.MAX_PARTS) selected.add(part.id)
                        },
                    )
                }
            }
            // 選んだうち、絞り込みで隠れているもの（開いたときに選んである、いま積んでいるパーツも含む）を知らせて、外せるようにする
            HiddenSelectionNote(
                hidden = valid.count { id -> visible.none { it.id == id } },
                onRemove = { selected.removeAll { id -> visible.none { it.id == id } } },
                onGrid = false,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            // コレクションのいちばん下のボタンと同じ、オレンジのブロック（押せないときは灰色）
            PopButton(
                label = if (valid.isEmpty()) "積むパーツを選んでください" else "選んだ ${valid.size} 本で積む",
                // 窓を閉じる動きが終わってから始める（Material3 の下から出る窓の作法。すぐ消すと、閉じる動き無しに消える）
                onClick = {
                    val picked = valid
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onStart(picked) }
                },
                face = if (valid.isNotEmpty()) Pop.OrangeDeep else Fieldbook.GridMajor,
                text = if (valid.isNotEmpty()) Pop.Letter else Fieldbook.Muted,
                enabled = valid.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 選ぶ窓の小さなカード：野帳の図（天気の色とアイコン）、名前、歩いた長さ。
 * コレクションのカードと同じブロックの面（下に側面）で、押すと縮む。
 * 選んだカードは、コレクションと同じ青い破線で囲み、図の右下の丸に積む順の数字を入れる
 */
@Composable
private fun PickerCard(
    part: Part,
    sky: Sky?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    order: Int?,
    canSelect: Boolean,
    onToggle: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .pressFeel(source)
            .popBlock(face = Fieldbook.Card, corner = PICKER_CARD_CORNER, depth = 2.dp, edge = if (order != null) null else Fieldbook.CardEdge)
            .then(if (order != null) Modifier.dashedOutline(Fieldbook.Survey, width = 2.dp, corner = PICKER_CARD_CORNER) else Modifier)
            // 角の丸い面に四角い波紋が出ないよう、波紋は出さず、縮む動きだけで応える
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = canSelect,
                onClickLabel = if (order != null) "選択を外す" else "選ぶ",
                onClick = onToggle,
            )
            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 6.dp),
    ) {
        FieldbookPart(
            part,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics {
                if (sky != null) contentDescription = "${sky.label}の日に歩いた道"
            },
            sky = sky,
        )
        // 名前と、歩いた長さ（足跡のマークを頭に。コレクションのカードの注記と同じ形。ui/common/WalkNote）。
        // 積むときの大きさは出さない（小さいカードでは字が切れるため。長さで分かり、大きさは積む画面の上の行に出る）
        // 右の列は 2 段：上（名前の横）に選ぶ丸、下（長さの横）にお気に入りの ☆（押すと黄色の ★）。
        // 選ぶ丸を図の上に置くと、小さい図では道や右下の角の印にかかるため、図の下へ出した
        Box {
            Column(modifier = Modifier.fillMaxWidth().padding(end = RIGHT_COLUMN)) {
                Text(
                    part.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Fieldbook.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                WalkNote(
                    distanceM = part.distanceM,
                    date = null,
                    weather = null,
                    fontSize = 10.sp,
                    separator = "・",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            // 右の列は字の 2 行に高さを合わせ（matchParentSize。カードの高さは字だけで決まる）、1 行ずつに分ける
            Column(modifier = Modifier.matchParentSize().wrapContentWidth(Alignment.End).width(RIGHT_COLUMN)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SelectMark(
                        order = order,
                        enabled = canSelect,
                        name = part.displayName,
                        onToggle = onToggle,
                        // 丸（26dp）は 1 行より少し大きいので、行からはみ出して置く（requiredSize）。押せる範囲は 30dp
                        // （窓のカードはどこを押しても選べるので、丸は印として見えればよい。下の ☆ と重なる所は ☆ が勝つ）
                        modifier = Modifier.requiredSize(30.dp),
                    )
                }
                FavoriteToggle(
                    favorite = favorite,
                    name = part.displayName,
                    enabled = true,
                    onToggle = onToggleFavorite,
                    // 行（10sp の注記、16dp ほど）より押せる範囲を上下に広げる。外すとカードを選んでしまうので（#84 のレビュー）。
                    // 上の丸と重なる所は、あとに置いた ☆ が勝つ
                    modifier = Modifier.weight(1f).fillMaxWidth().requiredHeight(28.dp),
                    starSize = 16.dp,
                )
            }
        }
    }
}

/** 窓のカードの、名前と長さの右の列（選ぶ丸とお気に入りの ☆）の幅 */
private val RIGHT_COLUMN = 32.dp

/** 窓のカードの角の丸さ（コレクションのカードより小さいので、角も少し小さく） */
private val PICKER_CARD_CORNER = 10.dp
