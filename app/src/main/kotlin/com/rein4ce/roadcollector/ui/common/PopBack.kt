package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * 積む画面の 3 つのモードと、ランキング（「みんな」）の画面の上の行に置く「‹ 戻る」と、遊び方の札（決定 76・80）。
 * 前はランキングだけ小さな札（字 14sp・12sp）だったので、積む画面と同じ大きさにそろえるため、ここにまとめた
 */

/**
 * 「‹ 戻る」の黄色い積み木。前は小さな札（字 14sp・見た目の高さ 28dp ほど）で押しにくかったので（B の希望）、字を大きくし（16dp。[small] なら 15dp）、
 * 見た目の高さ 42dp・幅 88dp 以上の積み木にした（押せる範囲も同じ大きさ）。上の行に収めるため、文字の大きさの設定では大きくしない
 *
 * @param onBack 押したとき。ボタンのまん中（アプリの画面の左上から）を渡す。戻った先の画面はここから大きくなって出てくる（決定 78。[ZoomInFrom]）
 * @param small 小さい画面か、幅の狭い端末（[isNarrowScreen]）。一回り小さくする
 * @param onClickLabel 読み上げで伝える、押すと何をするか
 */
@Composable
internal fun PopBackButton(onBack: (center: Offset) -> Unit, small: Boolean, onClickLabel: String) {
    val source = remember { MutableInteractionSource() }
    val size = with(LocalDensity.current) { (if (small) 15.dp else 16.dp).toSp() }
    // 置かれた所のまん中。押したときに渡すだけなので、変わっても描き直さない（State にしない）
    val center = remember { arrayOf(Offset.Unspecified) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .onGloballyPositioned { center[0] = it.boundsInRoot().center }
            .pressFeel(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = onClickLabel) { onBack(center[0]) }
            .popBlock(face = Pop.Yellow, corner = 50.dp, depth = 3.dp)
            .sizeIn(minWidth = if (small) 80.dp else 88.dp, minHeight = if (small) 40.dp else 42.dp)
            .padding(horizontal = 14.dp),
    ) {
        Text("‹ 戻る", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = size, color = Pop.InkOnYellow, maxLines = 1)
    }
}

/**
 * 遊び方の札（「通常」「90 秒チャレンジ」「毎日 1 つずつ積む」）。遊び方の色の積み木に白い字で、少し傾ける。
 * 「‹ 戻る」と同じ高さ・同じ字の大きさ（[small] なら一回り小さく）。押すものではないので、読み上げでは字だけ
 */
@Composable
internal fun PopModeTag(label: String, face: Color, small: Boolean) {
    val size = with(LocalDensity.current) { (if (small) 14.dp else 16.dp).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer { rotationZ = 2f }
            .popBlock(face = face, corner = 10.dp, depth = 3.dp)
            .heightIn(min = if (small) 36.dp else 42.dp)
            .padding(horizontal = 8.dp),
    ) {
        Text(label, fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = size, color = Pop.Letter, maxLines = 1)
    }
}

/** 幅の狭い端末（400dp 未満）か。「‹ 戻る」と遊び方の札を一回り小さくする（右に残り時間などが並んでも、幅が足りなくならないように） */
@Composable
internal fun isNarrowScreen(): Boolean = LocalConfiguration.current.screenWidthDp < NARROW_WIDTH_DP

/** これより幅の狭い端末では、「‹ 戻る」と遊び方の札を一回り小さくする [dp] */
private const val NARROW_WIDTH_DP = 400
