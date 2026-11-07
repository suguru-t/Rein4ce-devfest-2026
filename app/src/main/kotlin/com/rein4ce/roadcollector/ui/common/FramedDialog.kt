package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 見出しの帯と「×」の付いた枠（設定・採集の「パーツになりませんでした」）：野帳の紙の上に灰色の帯を敷いてまん中に見出し、枠の下に丸い「×」。
 * 中身が長いときは枠の中だけ動かす（「×」はいつも見える）。
 *
 * @param onDismiss 戻るボタン・枠の外を押したとき
 * @param onClose 枠の下の「×」を押したとき（ふつうは [onDismiss] と同じ）
 * @param onBack あれば、見出しの帯の左に「＜」を出す（設定の中の「アプリについて」などから、設定に戻る）
 */
@Composable
fun FramedDialog(
    title: String,
    onDismiss: () -> Unit,
    onClose: () -> Unit = onDismiss,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Fieldbook.Paper),
            ) {
                Box(modifier = Modifier.fillMaxWidth().background(Fieldbook.Band)) {
                    Text(
                        title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Fieldbook.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 14.dp)
                            .semantics { heading() },
                    )
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "戻る", tint = Fieldbook.Ink)
                        }
                    }
                }
                // 見出しが変わったら（設定 → アプリについて など）、いちばん上から見せる
                val scroll = remember(title) { ScrollState(0) }
                Column(
                    modifier = Modifier
                        .verticalScroll(scroll)
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                    content = content,
                )
            }
            // 枠の下の「×」で閉じる
            Spacer(Modifier.height(16.dp))
            CloseButton(onClick = onClose)
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
