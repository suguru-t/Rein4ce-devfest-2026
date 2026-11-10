package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.modeFaceOr
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 通常モードの「ほかの道」のボタン（決定 62・93。前の呼び名は「チェンジ」）：狙っているパーツを、ほかのパーツにランダムに替える。
 * 遊び方の色の積み木に、シャッフルのアイコン（交差する 2 本の矢印。[AppIcons.Shuffle]）と「ほかの道」の字。
 * 前はアイコン（向きが逆の 2 本の矢印）だけで、初めての人には、押すと何が起きるか分かりにくかった（決定 93）。
 * 右下に、このゲームであと何回替えられるかの小さな札。替えられないとき（落下中・残り 0 回・パーツが 1 種類）は灰色にして押せない。
 * 字の大きさの設定では大きくしない（積む画面の情報の段に置くので、段が伸びて塔を描く所が狭くならないように）
 *
 * @param left あと何回替えられるか
 * @param label 「ほかの道」の字も出すか。小さい画面では、キャンバスの右上に重ねるのでアイコンだけにする
 */
@Composable
internal fun ChangeButton(enabled: Boolean, left: Int, onClick: () -> Unit, modifier: Modifier = Modifier, label: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    // 遊び方の塗り（通常はテラコッタ。決定 84）。押せないときは灰色
    val face = if (enabled) modeFaceOr(Pop.Orange) else Fieldbook.Mark
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "ほかの道に替える。あと $left 回"
                role = Role.Button
                if (enabled) onClick { onClick(); true } else disabled()
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                // 右下の回数の札がはみ出す分、右と下を空ける
                .padding(end = if (label) 6.dp else 0.dp, bottom = 2.dp)
                .height(43.dp)
                .then(if (label) Modifier else Modifier.width(40.dp))
                .popBlock(face = face, corner = 10.dp, depth = 3.dp)
                // 右は回数の札がかかる分も空けて、字が札の下に入らないようにする
                .padding(start = if (label) 12.dp else 0.dp, end = if (label) 18.dp else 0.dp),
        ) {
            Icon(AppIcons.Shuffle, contentDescription = null, tint = Pop.Letter, modifier = Modifier.size(if (label) 22.dp else 26.dp))
            if (label) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "ほかの道",
                    fontFamily = Pop.Rounded,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { 15.dp.toSp() },
                    color = Pop.Letter,
                    maxLines = 1,
                )
            }
        }
        // 残りの回数の札（右下）
        Text(
            "$left",
            fontFamily = Pop.Rounded,
            fontWeight = FontWeight.Black,
            fontSize = with(density) { 11.dp.toSp() },
            color = Pop.Letter,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .sizeIn(minWidth = 18.dp)
                .popBlock(face = if (enabled) Pop.Navy else Fieldbook.Muted, corner = 9.dp, depth = 2.dp)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
