package com.rein4ce.roadcollector.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import com.rein4ce.roadcollector.ui.common.modeFaceOr
import com.rein4ce.roadcollector.ui.common.AppIcons
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.common.pressFeel
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 通常モードの「チェンジ」のボタン（決定 62）：狙っているパーツを、ほかのパーツにランダムに替える。
 * 「みんな」のリロードと同じ形の積み木（通常の色のオレンジ）に、向きが逆の 2 本の矢印（入れ替え。[AppIcons.Swap]）。
 * 右下に、このゲームであと何回替えられるかの小さな札。替えられないとき（落下中・残り 0 回・パーツが 1 種類）は灰色にして押せない。
 * 字の大きさの設定では大きくしない（積む画面の情報の段に置くので、段が伸びて塔を描く所が狭くならないように）
 *
 * @param left あと何回替えられるか
 */
@Composable
internal fun ChangeButton(enabled: Boolean, left: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    // 遊び方の塗り（通常はテラコッタ。決定 84）。押せないときは灰色
    val face = if (enabled) modeFaceOr(Pop.Orange) else Fieldbook.Mark
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "チェンジ。ほかの道に替える。あと $left 回"
                role = Role.Button
                if (enabled) onClick { onClick(); true } else disabled()
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 40.dp, height = 43.dp)
                .popBlock(face = face, corner = 10.dp, depth = 3.dp),
        ) {
            Icon(AppIcons.Swap, contentDescription = null, tint = Pop.Letter, modifier = Modifier.size(26.dp))
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
