package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

// 「みんな」だけで使うポップな部品（物差しの色・タワーの注記の行）。コレクションと共通の部品は ui/common/Pop.kt

/** 物差しの色の面（白い字を載せる）：高い順はオレンジ、多い順は青緑、新しい順は紺 */
internal fun sortFace(sort: TowerSort): Color = when (sort) {
    TowerSort.HEIGHT -> Pop.OrangeDeep
    TowerSort.PARTS -> Pop.TealDeep
    TowerSort.NEWEST -> Pop.Navy
}

/** 物差しの色の字（紙の上に直接書く）。ダークモードでは濃い色が紙に沈むので、明るい色にする */
internal fun sortInk(sort: TowerSort): Color = if (!Fieldbook.dark) sortFace(sort) else when (sort) {
    TowerSort.HEIGHT -> Pop.Orange
    TowerSort.PARTS -> Pop.Teal
    TowerSort.NEWEST -> Color(0xFFB9BCD0)
}

/**
 * 背景（方眼紙と天気）の上に直接書く字：字のまわりに紙の色の縁取り（ステッカーの白い縁のような）を付けて描く。
 * ダークモードのくもり・霧の雲は明るい灰色で、その上では薄い色の字（「全部で ○ 個」など）やオレンジの数字が沈んで読めないため
 * （#90・#102 のレビュー）。札で囲むと見出しより目立つので、囲まずに字の形のまま浮かせる。ライトでも、明るい雲や太陽の光の上ではっきりする。
 * 縁取りは同じ字を太い線で先に描いて作る（その字は読み上げから外す）
 */
@Composable
internal fun SkyText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = Pop.Rounded,
    fontWeight: FontWeight = FontWeight.Black,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    textAlign: TextAlign? = null,
) {
    SkyText(AnnotatedString(text), color, fontSize, modifier, fontFamily, fontWeight, maxLines, softWrap, textAlign)
}

/**
 * [SkyText] の、一部だけ色や字体を変えた字（[SpanStyle] を付けた文字列）。
 * 縁取りは、どの部分も紙の色で描く（字の色の指定は縁取りでは使わない）
 */
@Composable
internal fun SkyText(
    text: AnnotatedString,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = Pop.Rounded,
    fontWeight: FontWeight = FontWeight.Black,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    textAlign: TextAlign? = null,
) {
    val outline = with(LocalDensity.current) { 4.dp.toPx() }
    // 縁取りの字は、部分ごとの色を外して紙の色だけにする（字体や太さの指定は、字の形がそろうように残す）
    val outlineText = remember(text) {
        AnnotatedString(
            text.text,
            spanStyles = text.spanStyles.map { it.copy(item = it.item.copy(color = Color.Unspecified)) },
        )
    }
    Box(modifier) {
        Text(
            outlineText,
            color = Fieldbook.Paper,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            maxLines = maxLines,
            softWrap = softWrap,
            textAlign = textAlign,
            style = LocalTextStyle.current.copy(drawStyle = Stroke(width = outline, join = StrokeJoin.Round)),
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text,
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            maxLines = maxLines,
            softWrap = softWrap,
            textAlign = textAlign,
        )
    }
}

/**
 * タワーの注記の行：本数・作者・日時を「・」でつなぐ。本数と日時は太い丸ゴシックで、前の細い字より一回り大きく（13sp）。
 * 囲みはしない（札で囲むと、高さや呼び名より目立ってしまうため）。並びの物差しに使っている値
 * （多い順なら本数、新しい順なら日時）だけ墨の字にし、ほかは薄い墨。
 * 作者は人が入れる字で、丸ゴシックの切り出しに入っていないので、端末の字体の太字（長ければ「…」で詰める）
 *
 * @param partsLabel 本数に「パーツ」を付けるか（ランキングと紙片。断面図の下の行は短く「5 本」）
 * @param dateOnNewLine 日時を 2 行目に回すか（幅の狭いランキングの紙片）
 */
@Composable
internal fun TowerMeta(
    tower: TowerSummary,
    sort: TowerSort,
    modifier: Modifier = Modifier,
    partsLabel: Boolean = false,
    dateOnNewLine: Boolean = false,
) {
    val author = tower.author?.takeIf { it.isNotBlank() } ?: "名無し"
    val parts: @Composable () -> Unit = {
        MetaText(if (partsLabel) "パーツ ${tower.partCount} 本" else "${tower.partCount} 本", strong = sort == TowerSort.PARTS)
    }
    val date: @Composable () -> Unit = {
        MetaText(formatDate(tower.createdAt), strong = sort == TowerSort.NEWEST)
    }
    val dot: @Composable () -> Unit = { MetaText(" ・ ", strong = false) }
    val authorText: @Composable RowScope.() -> Unit = {
        Text(
            author,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Fieldbook.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    if (dateOnNewLine) {
        Column(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                parts()
                dot()
                authorText()
            }
            date()
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            parts()
            dot()
            authorText()
            dot()
            date()
        }
    }
}

/** 注記の字（太い丸ゴシック、13sp）。[strong] なら墨、そうでなければ薄い墨 */
@Composable
private fun MetaText(label: String, strong: Boolean) {
    Text(
        label,
        fontFamily = Pop.Rounded,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        color = if (strong) Fieldbook.Ink else Fieldbook.Muted,
        maxLines = 1,
        softWrap = false,
    )
}
