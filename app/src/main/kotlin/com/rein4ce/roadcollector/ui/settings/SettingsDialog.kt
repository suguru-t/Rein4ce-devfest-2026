package com.rein4ce.roadcollector.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.RoadCollectorApp
import com.rein4ce.roadcollector.data.ThemeMode
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 設定（採集画面の右上の歯車から開く）。いまは画面の明るさ（端末に合わせる・ライト・ダーク）だけ。
 * 選ぶとすぐにアプリ全体の色が変わり、この画面もその色で見える。選んだものはスマホの中に保存する
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val settings = RoadCollectorApp.from(LocalContext.current).settings
    val mode by settings.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        containerColor = Fieldbook.Paper,
        titleContentColor = Fieldbook.Ink,
        textContentColor = Fieldbook.Ink,
        title = { Text("設定", fontWeight = FontWeight.Bold) },
        text = {
            Column {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる", color = Fieldbook.Survey, fontWeight = FontWeight.Bold) }
        },
    )
}
