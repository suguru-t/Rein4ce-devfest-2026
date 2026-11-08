package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rein4ce.roadcollector.ui.common.PressButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.rein4ce.roadcollector.data.applyPartFilter
import com.rein4ce.roadcollector.ui.common.WalkNote
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import kotlinx.coroutines.launch

/**
 * 積む画面から開く「積むパーツを選ぶ」窓（下から出る）。コレクションに戻らずに、その場で積むパーツを選び直せる。
 *
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
    val filter by settings.partFilter.collectAsStateWithLifecycle()
    val favorites by repository.favorites.collectAsStateWithLifecycle()
    val parts by repository.parts.collectAsStateWithLifecycle()
    val visible = remember(shown, filter, weathers, favorites) {
        applyPartFilter(shown, filter, { repository.weatherOf(it.id, weathers)?.sky }, favorites)
    }
    val skies = remember(parts, weathers) { parts.mapNotNullTo(HashSet()) { repository.weatherOf(it.id, weathers)?.sky } }
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                notes("タップで選ぶ", "${GameSession.MAX_PARTS} 本まで", "長く歩いた道ほど大きい"),
                style = noteStyle,
                fontSize = 12.sp,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
            )
            PartFilterBar(
                filter = filter,
                onChange = settings::setPartFilter,
                skies = skies,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // カードを並べる所だけ、端から端まで方眼紙にする（コレクションと同じく、方眼の上に紙片を置いた見た目）。
            // 見出しとボタンは、字に線がかからないよう無地の紙のまま。この窓はメイン画面とは別の窓なので、見出しの下の線を消す仕組みは使わない
            // 並びや絞り込みを変えたら、いちばん上から見せる（コレクションと同じ）
            val gridState = rememberLazyGridState()
            LaunchedEffect(filter) { gridState.scrollToItem(0) }
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
                if (visible.isEmpty()) {
                    // 絞り込んだら何も残らなかった
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "当てはまるパーツがありません",
                            fontSize = 14.sp,
                            color = Fieldbook.Muted,
                            modifier = Modifier.padding(vertical = 32.dp).wrapContentWidth(Alignment.CenterHorizontally),
                        )
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
            Spacer(Modifier.height(12.dp))
            PressButton(
                // 窓を閉じる動きが終わってから始める（Material3 の下から出る窓の作法。すぐ消すと、閉じる動き無しに消える）
                onClick = {
                    val picked = valid
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onStart(picked) }
                },
                enabled = valid.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Fieldbook.Survey, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
            ) {
                Text(if (valid.isEmpty()) "積むパーツを選んでください" else "選んだ ${valid.size} 本で積む", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 選ぶ窓の小さなカード：野帳の図（天気の色とアイコン）、名前、歩いた長さ。
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
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = Modifier
            .background(Fieldbook.Card, shape)
            .then(
                if (order != null) Modifier.dashedOutline(Fieldbook.Survey, width = 2.dp, corner = 4.dp)
                else Modifier.border(1.dp, Fieldbook.CardEdge, shape),
            )
            .clickable(enabled = canSelect, onClickLabel = if (order != null) "選択を外す" else "選ぶ", onClick = onToggle)
            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 6.dp),
    ) {
        Box {
            FieldbookPart(
                part,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics {
                    if (sky != null) contentDescription = "${sky.label}の日に歩いた道"
                },
                sky = sky,
            )
            SelectMark(
                order = order,
                enabled = canSelect,
                name = part.displayName,
                onToggle = onToggle,
                // 小さい図では、左下の縮尺の字と近くならないよう、丸を角へ少し寄せる
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp),
            )
        }
        // 名前と、歩いた長さ（足跡のマークを頭に。コレクションのカードの注記と同じ形。ui/common/WalkNote）。
        // 積むときの大きさは出さない（小さいカードでは字が切れるため。長さで分かり、大きさは積む画面の上の行に出る）
        // 右に、コレクションのカードと同じお気に入りの ☆（押すと黄色の ★）。押せる範囲は、名前と注記の 2 行の右端ぶん
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Column(modifier = Modifier.weight(1f)) {
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
            FavoriteToggle(
                favorite = favorite,
                name = part.displayName,
                enabled = true,
                onToggle = onToggleFavorite,
                modifier = Modifier.width(26.dp).fillMaxHeight(),
                starSize = 16.dp,
                // 星は下の行（足跡と長さ）の高さに置く
                alignment = Alignment.BottomCenter,
            )
        }
    }
}
