package com.rein4ce.roadcollector.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.data.ThemeMode
import com.rein4ce.roadcollector.data.WeatherMotion
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 設定（採集画面の右上の歯車から開く）。選ぶとすぐに効き、スマホの中に保存する。
 * - 画面の明るさ：端末に合わせる・ライト・ダーク
 * - 背景の天気の動き：なめらか・ふつう・止める・出さない
 * - 採集：記録中は画面を消さない、AI に名前を考えてもらう
 * - みんなのタワー：ニックネーム（作者名）
 * - アプリについて
 *
 * 閉じるのは、枠の下の丸い「×」か、枠の外を押したとき（戻るでも閉じる）
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    val theme by settings.themeMode.collectAsStateWithLifecycle()
    val motion by settings.weatherMotion.collectAsStateWithLifecycle()
    val keepOn by settings.keepScreenOn.collectAsStateWithLifecycle()
    val aiNaming by settings.aiNaming.collectAsStateWithLifecycle()
    val nickname by settings.nickname.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    var editingNickname by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    FramedDialog(title = "設定", onDismiss = onDismiss) {
        Section("画面の明るさ") {
            Column(modifier = Modifier.selectableGroup()) {
                for (m in ThemeMode.entries) {
                    Choice(
                        label = m.label,
                        // 端末の設定がいまどちらかも出す（選んだときにどちらになるか分かるように）
                        note = if (m == ThemeMode.SYSTEM) (if (systemDark) "いまはダーク" else "いまはライト") else null,
                        selected = m == theme,
                        onSelect = { settings.setThemeMode(m) },
                    )
                }
            }
        }
        Section("背景の天気の動き") {
            Column(modifier = Modifier.selectableGroup()) {
                for (m in WeatherMotion.entries) {
                    Choice(label = m.label, note = m.note, selected = m == motion, onSelect = { settings.setWeatherMotion(m) })
                }
            }
        }
        Section("採集") {
            Toggle(
                label = "記録中は画面を消さない",
                note = "地図を見ながら歩くとき用。電池を多く使う",
                checked = keepOn,
                onChange = settings::setKeepScreenOn,
            )
            Toggle(
                label = "AI に名前を考えてもらう",
                note = "パーツの形・距離・日時をサーバに送る。歩いた場所は送らない",
                checked = aiNaming,
                onChange = settings::setAiNaming,
            )
        }
        Section("みんなのタワー") {
            LinkRow(
                label = "ニックネーム",
                value = nickname.ifBlank { "名無し" },
                note = "送ったタワーに作者名として出る（だれでも見られる）",
                onClick = { editingNickname = true },
            )
        }
        Section(null, divider = false) {
            LinkRow(label = "アプリについて", value = null, note = "版・外に送るもの・使っている素材", onClick = { showAbout = true })
        }
    }

    if (editingNickname) {
        NicknameDialog(
            current = nickname,
            onSave = { settings.setNickname(it); editingNickname = false },
            onDismiss = { editingNickname = false },
        )
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

/** ひとまとまりの項目。[title] があれば上に小さな見出し、[divider] なら下に区切りの線（いちばん下は付けない） */
@Composable
private fun Section(title: String?, divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (title != null) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Fieldbook.Muted,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
        }
        content()
        if (divider) HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = Fieldbook.GridMajor)
    }
}

/** 1 つだけ選ぶ項目の 1 行（行ごと押せる） */
@Composable
private fun Choice(label: String, note: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // 行ごと押せる（selectable）
            colors = RadioButtonDefaults.colors(selectedColor = Fieldbook.Survey, unselectedColor = Fieldbook.Muted),
        )
        Spacer(Modifier.width(8.dp))
        Labels(label, note, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** オン・オフの項目の 1 行（行ごと押せる） */
@Composable
private fun Toggle(label: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Labels(label, note, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null, // 行ごと押せる（toggleable）
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Fieldbook.Survey,
                uncheckedThumbColor = Fieldbook.Muted,
                uncheckedTrackColor = Fieldbook.Card,
                uncheckedBorderColor = Fieldbook.Muted,
            ),
        )
    }
}

/** 押すと別の画面が開く項目の 1 行。右に今の値と「›」 */
@Composable
private fun LinkRow(label: String, value: String?, note: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick, role = Role.Button),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Labels(label, note, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 15.sp, color = Fieldbook.Muted, maxLines = 1)
        }
        Text("　›", fontSize = 18.sp, color = Fieldbook.Muted)
    }
}

/** 項目の名前と、その下の小さな説明 */
@Composable
private fun Labels(label: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 16.sp, color = Fieldbook.Ink)
        if (note != null) Text(note, style = noteStyle, fontSize = 12.sp, color = Fieldbook.Muted)
    }
}
