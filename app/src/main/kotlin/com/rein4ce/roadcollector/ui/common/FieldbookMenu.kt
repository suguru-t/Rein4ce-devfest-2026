package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 野帳の紙のメニュー（押すと下に出る一覧）。Material の標準は白や薄紫の面で画面から浮いて見えるので、
 * 紙の色に細い縁を付け、上に小さな見出しを置く。記録画面の「デモ散歩」と、コレクション・選ぶ窓の並び・天気の札で使う
 *
 * @param heading 一覧の上に出す小さな見出し（null なら出さない）
 */
@Composable
fun FieldbookDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    heading: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(6.dp),
        containerColor = Fieldbook.Paper,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Fieldbook.CardEdge),
    ) {
        if (heading != null) MenuHeading(heading)
        content()
    }
}

/** メニューの上の小さな見出し（字間を少し空けた、うすい墨色の太字） */
@Composable
fun MenuHeading(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Fieldbook.Muted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}
