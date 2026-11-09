package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/** ポップな枠の角の丸さ（「みんな」の紙片・カードより一回り大きい窓なので、少し大きく） */
private val POP_CORNER = 18.dp

/**
 * 見出しの帯と「×」の付いた枠（設定・採集の「パーツになりませんでした」）：野帳の紙の上に灰色の帯を敷いてまん中に見出し、枠の下に丸い「×」。
 * 中身が長いときは枠の中だけ動かす（「×」はいつも見える）。
 *
 * @param onDismiss 戻るボタン・枠の外を押したとき
 * @param onClose 枠の下の「×」を押したとき（ふつうは [onDismiss] と同じ）
 * @param onBack あれば、枠の下の丸いボタンを「×」ではなく「＜」にして、押すとこれを呼ぶ
 *   （設定の中の「画面の明るさ」などから、設定の一覧に戻る。戻るボタンも閉じるボタンと同じ場所にそろえる）
 * @param pop 「みんな」・コレクションと同じポップな見た目にする（Pop.kt）：枠と下の丸いボタンを、
 *   下に側面がのぞくブロックの面にし、角を大きく丸める。見出しは灰色の帯の代わりに、紺の名札のブロックに白い字で載せる
 *   （「みんな」の断面図の名札の台と同じ考え方）。コレクションの拡大表示で使う（設定などは野帳の見た目のまま）
 * @param nameTag [pop] のとき、見出しを紺の名札のブロックに載せるか（パーツの名前など、そのものの名前を出すとき）。
 *   false なら、名札にせず丸ゴシックの字だけにして、下に題字の積み木と同じ 4 色の短い線を引く（設定・使い方・名前を付ける窓。
 *   名札はボタンのように見えて押せそうに見えたため。決定 56）
 * @param backdrop 枠の紙の上・見出しと中身の下に敷くもの（`Modifier.matchParentSize()` で枠いっぱいに敷く）。
 *   コレクションの拡大表示で、そのパーツを歩いた日の天気を降らせるのに使う。中身を動かしても、敷いたものは動かない
 * @param footer あれば、枠の中のいちばん下に、中身とは別に置くもの（ボタンなど）。中身が長くて動かすときも、ここは動かず
 *   いつも見える（小さい画面や文字を大きくしている人の画面で、ボタンが枠の下に隠れないように）。コレクションの拡大表示で使う
 * @param scrollContent false なら枠の側ではスクロールさせず、枠に入る高さまでを中身に渡す。
 *   中身の側で、入る大きさに縮める（コレクションの拡大表示：小さい画面では図を小さくして、注記もボタンも 1 画面に入れる。
 *   それでも入らないときは、中身の側でスクロールさせる）
 */
@Composable
fun FramedDialog(
    title: String,
    onDismiss: () -> Unit,
    onClose: () -> Unit = onDismiss,
    onBack: (() -> Unit)? = null,
    pop: Boolean = false,
    nameTag: Boolean = true,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .then(
                        if (pop) {
                            Modifier
                                .popBlock(face = Fieldbook.Paper, corner = POP_CORNER, depth = 5.dp)
                                .clip(RoundedCornerShape(POP_CORNER))
                        } else {
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Fieldbook.Paper)
                        },
                    ),
            ) {
                backdrop?.invoke(this)
                Column {
                    if (pop && !nameTag) {
                        // 見出しは字だけ（押せそうに見えないように）。設定などの決まった言葉なので、丸ゴシック
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 2.dp),
                        ) {
                            Text(
                                title,
                                fontFamily = Pop.Rounded,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = Fieldbook.Ink,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(Modifier.height(8.dp))
                            TitleColorLine()
                        }
                    } else if (pop) {
                        // 見出しは名札のブロック。見出し（パーツの名前など）は人や AI が付ける字なので、丸ゴシック（使う字だけの切り出し）
                        // ではなく端末の字体の極太にする。読みやすいよう、札は傾けない
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 4.dp),
                        ) {
                            Text(
                                title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Pop.Letter,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier
                                    .popBlock(face = Pop.Navy, corner = 10.dp, depth = 4.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .semantics { heading() },
                            )
                        }
                    } else {
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
                        }
                    }
                    // 見出しが変わったら（設定 → アプリについて など）、いちばん上から見せる
                    val scroll = remember(title) { ScrollState(0) }
                    Column(
                        modifier = Modifier
                            // 見出しと下に置くもの（footer）を先に測り、残りの高さで中身を動かす
                            .weight(1f, fill = false)
                            .then(if (scrollContent) Modifier.verticalScroll(scroll) else Modifier)
                            // 左右・下は 24dp。上は、中身の最初の見出しや行の上の余白と合わせて 24dp ほどになるように
                            // （下に置くものがあれば、下の余白はそちらに付ける）
                            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = if (footer != null) 0.dp else 24.dp),
                        content = content,
                    )
                    if (footer != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                            content = footer,
                        )
                    }
                }
            }
            // 枠の下の丸いボタン：ふつうは「×」で閉じる。戻る先があるときは「＜」で戻る
            Spacer(Modifier.height(16.dp))
            if (onBack != null) {
                RoundButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, label = "戻る", pop = pop, onClick = onBack)
            } else {
                RoundButton(Icons.Filled.Close, label = "閉じる", pop = pop, onClick = onClose)
            }
        }
    }
}

/**
 * 枠の下に出す、丸いボタン（「×」か「＜」。押せる大きさは 48dp）。暗い幕の上でも見えるよう、紙の色の丸に縁を付ける。
 * [pop] なら、下に側面がのぞく丸いブロックにして、波紋の代わりに縮む動きだけで応える
 */
@Composable
private fun RoundButton(icon: ImageVector, label: String, pop: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .pressFeel(source)
            .then(
                if (pop) {
                    Modifier.popBlock(face = Fieldbook.Paper, corner = 24.dp, depth = 3.dp, edge = Fieldbook.CardEdge)
                } else {
                    Modifier.clip(CircleShape).background(Fieldbook.Paper).border(1.dp, Fieldbook.CardEdge, CircleShape)
                },
            )
            .clickable(
                interactionSource = source,
                indication = if (pop) null else LocalIndication.current,
                onClick = onClick,
                role = Role.Button,
                onClickLabel = label,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Fieldbook.Ink, modifier = Modifier.size(24.dp))
    }
}

/** 見出しの下の飾りの線：題字の積み木と同じ 4 色（オレンジ・青緑・黄・紺）の短い線を、少しすき間をあけて並べる */
@Composable
private fun TitleColorLine() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (color in listOf(Pop.Orange, Pop.Teal, Pop.Yellow, Pop.Navy)) {
            Box(Modifier.size(width = 14.dp, height = 4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}
