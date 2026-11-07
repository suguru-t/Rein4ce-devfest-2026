package com.rein4ce.roadcollector.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.data.ThemeMode
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 設定（採集画面の右上の歯車から開く）。いまは画面の明るさ（端末に合わせる・ライト・ダーク）だけ。
 * 選ぶとすぐにアプリ全体の色が変わり、この画面もその色で見える。選んだものはスマホの中に保存する。
 * 閉じるのは、枠の下の丸い「×」か、枠の外を押したとき（戻るでも閉じる）
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    val mode by settings.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 枠（野帳の紙）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Fieldbook.Paper)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text("設定", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
                Spacer(Modifier.height(16.dp))
                Text("画面の明るさ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Muted)
                Column(modifier = Modifier.selectableGroup()) {
                    for (m in ThemeMode.entries) {
                        val selected = m == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = selected, onClick = { settings.setThemeMode(m) }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null, // 行ごと押せる（selectable）
                                colors = RadioButtonDefaults.colors(selectedColor = Fieldbook.Survey, unselectedColor = Fieldbook.Muted),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(m.label, fontSize = 16.sp, color = Fieldbook.Ink)
                                if (m == ThemeMode.SYSTEM) {
                                    // 端末の設定がいまどちらかも出す（選んだときにどちらになるか分かるように）
                                    Text(
                                        if (systemDark) "いまはダーク" else "いまはライト",
                                        style = noteStyle,
                                        fontSize = 12.sp,
                                        color = Fieldbook.Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 枠の下の「×」で閉じる
            Spacer(Modifier.height(16.dp))
            CloseButton(onClick = onDismiss)
        }
    }
}

/** 枠の下に出す、丸い「×」のボタン（押せる大きさは 48dp）。暗い幕の上でも見えるよう、紙の色の丸に縁を付ける */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Fieldbook.Paper)
            .border(1.dp, Fieldbook.CardEdge, CircleShape)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = "閉じる"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Close, contentDescription = "閉じる", tint = Fieldbook.Ink, modifier = Modifier.size(24.dp))
    }
}
