package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.data.PartFilter
import com.rein4ce.roadcollector.data.PartSort
import com.rein4ce.roadcollector.ui.common.FieldbookDropdownMenu
import com.rein4ce.roadcollector.ui.common.PressTextButton
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.common.notes
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.isNarrowScreen
import com.rein4ce.roadcollector.ui.common.popBlock

/**
 * 並びと絞り込みの行：［並び：いつもの順 ▾］［天気：すべて ▾］［☆ お気に入り］。
 * コレクションの上に置く（[PartFilter] は設定に保存して、アプリを閉じても覚えておく）。
 *
 * - 並び：いつもの順・新しい順・古い順・長い順・短い順
 * - 天気：すべてか、1 つの天気。持っているパーツに無い天気は選べない（選んでも何も出ないため）
 * - お気に入り：押すと、お気に入りだけを出す（もう一度押すと全部）
 *
 * 札は「みんな」の並びの札と同じ丸いブロック。いつもの順・絞り込みなし以外を選んでいる札は塗る（何かで絞っていることが分かるように。
 * 並び・天気は青緑、お気に入りは ★ と同じ黄色）。
 * 小さい画面や大きい文字では、札が次の行に折り返す
 *
 * @param skies 持っているパーツの天気（選べる天気）
 * @param enabled 押せるか（コレクションの並べ替え中は、行の場所は残したまま押せなくする）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartFilterBar(filter: PartFilter, onChange: (PartFilter) -> Unit, skies: Set<Sky>, modifier: Modifier = Modifier, enabled: Boolean = true) {
    // 丸ゴシックは字の幅が広いので、札の字・間を少し詰めて、ふつうの文字の大きさなら 3 つが 1 行に収まるようにする
    // 幅の狭い端末（400dp 未満）では、札の間と札の左右の余白を少し詰めて、3 つを 1 行に収める（前の 12sp でも入らず、お気に入りが 2 行目に落ちていた。#134 の C のレビュー）
    val narrow = isNarrowScreen()
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 6.dp)) {
        // 並び
        Box {
            var open by remember { mutableStateOf(false) }
            FilterTag("並び：${filter.sort.label}", active = filter.sort != PartSort.CUSTOM, menu = true, enabled = enabled) { open = true }
            FieldbookDropdownMenu(expanded = open, onDismissRequest = { open = false }, heading = "並び") {
                for (sort in PartSort.entries) {
                    MenuRow(label = sort.label, selected = sort == filter.sort, enabled = true) {
                        onChange(filter.copy(sort = sort))
                        open = false
                    }
                }
            }
        }
        // 天気
        Box {
            var open by remember { mutableStateOf(false) }
            FilterTag("天気：${filter.sky?.label ?: "すべて"}", active = filter.sky != null, menu = true, enabled = enabled) { open = true }
            FieldbookDropdownMenu(expanded = open, onDismissRequest = { open = false }, heading = "歩いた日の天気") {
                MenuRow(label = "すべて", selected = filter.sky == null, enabled = true) {
                    onChange(filter.copy(sky = null))
                    open = false
                }
                // 画面に出す天気の 4 つ（晴れ・くもり・雨・雪。決定 73）。霧のパーツはくもり、霧雨・雷雨のパーツは雨で出る
                val shownSkies = skies.mapTo(HashSet()) { it.shown }
                for (sky in Sky.SHOWN) {
                    MenuRow(label = sky.label, selected = sky == filter.sky?.shown, enabled = sky in shownSkies || sky == filter.sky?.shown, sky = sky) {
                        onChange(filter.copy(sky = sky))
                        open = false
                    }
                }
            }
        }
        // お気に入り（入る・切れるの札なので、読み上げではスイッチとして、入っているかどうかも伝える）
        FilterTag(
            if (filter.favoritesOnly) "★ お気に入り" else "☆ お気に入り",
            active = filter.favoritesOnly,
            menu = false,
            enabled = enabled,
            switch = true,
            activeFace = Pop.Yellow,
            activeText = Pop.InkOnYellow,
        ) { onChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
    }
}

/**
 * 並びと絞り込みの札。「みんな」の並びの札（ui/gallery/RankingScreen の SortTabs）と同じ、下に側面がのぞく丸いブロック：
 * ふだんは白い面に青緑の縁と字、選んでいるときは色の面に字を載せ、少し傾けて積み木を置いたように見せる。
 * 見た目は小さいが、押せる範囲は高さ 48dp（指で押しやすいように）。
 * 押したときは、ほかのボタンと同じ押し心地（ui/common/PressFeel）で、丸い札の形だけが縮む（押せる範囲の四角に波紋は出さない）
 *
 * @param active いつもの順・絞り込みなし以外を選んでいるか（[activeFace] で塗る）
 * @param menu 押すと選ぶ一覧が開く（右に ▾）
 * @param switch 入る・切れるの札か（読み上げではスイッチとして伝える）
 */
@Composable
private fun FilterTag(
    text: String,
    active: Boolean,
    menu: Boolean,
    enabled: Boolean,
    switch: Boolean = false,
    activeFace: Color = Pop.TealDeep,
    activeText: Color = Pop.Letter,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .pressFeel(source)
            .then(
                if (switch) {
                    Modifier.toggleable(
                        value = active,
                        interactionSource = source,
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = { onClick() },
                    )
                } else {
                    Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                },
            )
            // 絞り込んでいる札だけ「絞り込み中」と伝える（スイッチは入っているかどうかを自分で伝える）
            .semantics { if (active && !switch) stateDescription = "絞り込み中" },
    ) {
        Text(
            if (menu) "$text ▾" else text,
            color = if (active) activeText else Pop.TealLine,
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            // 前は 12sp。大きくすると 3 つが 1 行に入らなくなるので 13sp まで（決定 80）。
            // 幅の狭い端末（400dp 未満）では 13sp でも入らないので、前の 12sp のまま（#134 の C のレビュー）
            fontSize = if (isNarrowScreen()) 12.sp else 13.sp,
            maxLines = 1,
            modifier = Modifier
                .graphicsLayer { rotationZ = if (active) -2f else 0f }
                .popBlock(
                    face = if (active) activeFace else Fieldbook.Card,
                    corner = 50.dp,
                    depth = if (active) 4.dp else 2.dp,
                    edge = if (active) null else Pop.TealLine,
                )
                .padding(horizontal = if (isNarrowScreen()) 8.dp else 10.dp, vertical = 6.dp),
        )
    }
}

/** 一覧の 1 行。選んでいるものに印（✓）、天気なら小さなアイコン（パーツの図と同じ絵）を左に付ける */
@Composable
private fun MenuRow(label: String, selected: Boolean, enabled: Boolean, sky: Sky? = null, onClick: () -> Unit) {
    val isSelected = selected
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        // 選んでいるものは、色と太字だけでなく、読み上げでも分かるように（1 つだけ選ぶので、ラジオボタンと同じ扱い。#84 のレビュー）
        modifier = Modifier.semantics {
            role = Role.RadioButton
            this.selected = isSelected
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sky != null) {
                    Canvas(Modifier.size(20.dp)) { drawWeatherIcon(sky, center = center, size = size.minDimension) }
                    Box(Modifier.width(8.dp))
                }
                Text(
                    label,
                    // 選んでいるものは、札と同じ測量の青の太字
                    color = when {
                        !enabled -> Fieldbook.Mark
                        selected -> Fieldbook.Survey
                        else -> Fieldbook.Ink
                    },
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        },
        // ✓ は目で見る印。読み上げは上の「選択済み」で足りるので、読ませない
        trailingIcon = {
            if (selected) Text("✓", color = Fieldbook.Survey, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
        },
    )
}

/**
 * 並びや絞り込みを変えたら、一覧をいちばん上から見せる。変えたときだけで、画面に入った最初の 1 回は動かさない
 * （ほかのタブから戻ったときに、見ていた位置が残るように。#75 のレビュー）。コレクションと選ぶ窓で使う
 */
@Composable
internal fun ScrollToTopOnChange(filter: PartFilter, state: LazyGridState) {
    val last = remember { arrayOfNulls<PartFilter>(1) }
    LaunchedEffect(filter) {
        val before = last[0]
        last[0] = filter
        if (before != null && before != filter) state.scrollToItem(0)
    }
}
