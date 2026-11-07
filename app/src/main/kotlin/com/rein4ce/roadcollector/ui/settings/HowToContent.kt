package com.rein4ce.roadcollector.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.Dest
import com.rein4ce.roadcollector.ui.common.FramedDialog
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 初めて開いたときに 1 回だけ出す使い方（「×」か枠の外で閉じると、次からは出さない）。
 * 中身は設定の「使い方」と同じ。最後に、設定からいつでも見られることを添える
 */
@Composable
fun HowToDialog(onClose: () -> Unit) {
    FramedDialog(title = "使い方", onDismiss = onClose) {
        HowToContent(firstTime = true)
    }
}

/**
 * アプリの使い方（設定の枠の中で切り替えて出す。初めて開いたときにも出す）。
 * 下のタブの 4 つを、タブと同じアイコンを添えて順に説明する。文は「です・ます」で、1 つ 2 行くらいに収める
 *
 * @param firstTime 初めて開いたときに出すとき。設定からいつでも見られることを最後に添える
 */
@Composable
internal fun ColumnScope.HowToContent(firstTime: Boolean = false) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Step(Dest.Collect.iconOutline, "採集", "歩いた道が、積み木のパーツになります。「デモ散歩」でも試せます。")
        Step(Dest.Inventory.iconOutline, "コレクション", "集めたパーツが並びます。タップで大きく見て、名前を付けられます。")
        Step(Dest.Stack.iconOutline, "積む", "コレクションで選んだパーツを、動かして回し、「置く」で積みます。")
        Step(Dest.Gallery.iconOutline, "みんな", "完成したタワーを送ると、みんなの高さの順位表に並びます。")
    }
    Text(
        "ひとこと",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Fieldbook.Muted,
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp).semantics { heading() },
    )
    Tip("パーツになるのは歩きとジョギングだけです。乗り物で動いた道は入りません。")
    Tip("歩いた場所はスマホの中だけに残り、サーバには送りません。")
    if (firstTime) {
        Tip("この説明は、設定（右上の歯車）の「使い方」でいつでも見られます。")
    }
}

/** 1 つの画面の説明：左にタブと同じアイコン、右に画面の名前と説明 */
@Composable
private fun Step(icon: ImageVector, name: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Fieldbook.Card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Fieldbook.Ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink, modifier = Modifier.semantics { heading() })
            Text(detail, style = noteStyle, fontSize = 13.sp, color = Fieldbook.Ink)
        }
    }
}

/** 補足の 1 行（「・」付き） */
@Composable
private fun Tip(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("・", fontSize = 13.sp, color = Fieldbook.Muted)
        Text(text, style = noteStyle, fontSize = 13.sp, color = Fieldbook.Ink)
    }
}
