package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 背景（方眼紙と天気）の上に直接書く字：字のまわりに紙の色の縁取り（ステッカーの白い縁のような）を付けて描く。
 * ダークモードのくもり・霧の雲は明るい灰色で、その上では薄い色の字（「全部で ○ 個」など）やオレンジの数字が沈んで読めないため
 * （#90・#102 のレビュー）。札で囲むと見出しより目立つので、囲まずに字の形のまま浮かせる。ライトでも、明るい雲や太陽の光の上ではっきりする。
 * 縁取りは同じ字を太い線で先に描いて作る（その字は読み上げから外す）
 * 「みんな」（ui/gallery）と積む画面（ui/game）で使う（決定 59）
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
    overflow: TextOverflow = TextOverflow.Clip,
) {
    SkyText(AnnotatedString(text), color, fontSize, modifier, fontFamily, fontWeight, maxLines, softWrap, textAlign, overflow)
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
    overflow: TextOverflow = TextOverflow.Clip,
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
            overflow = overflow,
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
            overflow = overflow,
        )
    }
}
