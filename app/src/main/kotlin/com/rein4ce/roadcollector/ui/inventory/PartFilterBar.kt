package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.data.PartFilter
import com.rein4ce.roadcollector.data.PartSort

/**
 * 並びと絞り込みの行：［並び：いつもの順 ▾］［天気：すべて ▾］［☆ お気に入り］。
 * コレクションと「パーツを選ぶ」の窓の上に置き、どちらでも同じ選び方になる（[PartFilter] は設定に保存して共通）。
 *
 * - 並び：いつもの順・新しい順・古い順・長い順・短い順
 * - 天気：すべてか、1 つの天気。持っているパーツに無い天気は選べない（選んでも何も出ないため）
 * - お気に入り：押すと、お気に入りだけを出す（もう一度押すと全部）
 *
 * いつもの順・絞り込みなし以外を選んでいる札は、測量の青で塗る（何かで絞っていることが分かるように）。
 * 小さい画面や大きい文字では、札が次の行に折り返す
 *
 * @param skies 持っているパーツの天気（選べる天気）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartFilterBar(filter: PartFilter, onChange: (PartFilter) -> Unit, skies: Set<Sky>, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 並び
        Box {
            var open by remember { mutableStateOf(false) }
            FilterTag("並び：${filter.sort.label}", active = filter.sort != PartSort.CUSTOM, menu = true) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Fieldbook.Card) {
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
            FilterTag("天気：${filter.sky?.label ?: "すべて"}", active = filter.sky != null, menu = true) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Fieldbook.Card) {
                MenuRow(label = "すべて", selected = filter.sky == null, enabled = true) {
                    onChange(filter.copy(sky = null))
                    open = false
                }
                for (sky in Sky.entries) {
                    MenuRow(label = sky.label, selected = sky == filter.sky, enabled = sky in skies || sky == filter.sky, sky = sky) {
                        onChange(filter.copy(sky = sky))
                        open = false
                    }
                }
            }
        }
        // お気に入り
        FilterTag(
            if (filter.favoritesOnly) "★ お気に入り" else "☆ お気に入り",
            active = filter.favoritesOnly,
            menu = false,
        ) { onChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
    }
}

/**
 * 並びと絞り込みの札。コレクションの右上の「選択」と同じ、測量の青の細い縁の丸い札。
 * 見た目は小さいが、押せる範囲は高さ 48dp（指で押しやすいように）
 *
 * @param active いつもの順・絞り込みなし以外を選んでいるか（青く塗る）
 * @param menu 押すと選ぶ一覧が開く（右に ▾）
 */
@Composable
private fun FilterTag(text: String, active: Boolean, menu: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (active) "絞り込み中" else "" },
    ) {
        Text(
            if (menu) "$text ▾" else text,
            color = if (active) Color.White else Fieldbook.Survey,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(shape)
                .background(if (active) Fieldbook.Survey else Fieldbook.Card, shape)
                .border(1.dp, Fieldbook.Survey, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 一覧の 1 行。選んでいるものに印（✓）、天気なら小さなアイコン（パーツの図と同じ絵）を左に付ける */
@Composable
private fun MenuRow(label: String, selected: Boolean, enabled: Boolean, sky: Sky? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        enabled = enabled,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sky != null) {
                    Canvas(Modifier.size(20.dp)) { drawWeatherIcon(sky, center = center, size = size.minDimension) }
                    Box(Modifier.width(8.dp))
                }
                Text(
                    label,
                    color = if (enabled) Fieldbook.Ink else Fieldbook.Mark,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        },
        trailingIcon = { if (selected) Text("✓", color = Fieldbook.Survey, fontWeight = FontWeight.Bold) },
    )
}
