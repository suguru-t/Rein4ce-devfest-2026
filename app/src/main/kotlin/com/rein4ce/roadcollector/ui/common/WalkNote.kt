package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.inventory.Fieldbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * パーツの注記：歩いた長さ・日付・天気。記録のあとの窓・コレクションのカード・大きく見る窓で同じ形にする。
 * 例：（足跡のマーク）436 m ・ 11/7 ・ 晴れ
 *
 * - 「L=」の代わりに、下のタブの「記録」と同じ足跡のマークを頭に付ける（読み上げでは「歩いた長さ」）
 * - 天気がまだ分からない（取ってくる前・取れなかった）ときは出さない
 * - マークと数字、数字と単位は別の行に分けない（[notes]・[noteStyle]。マークのあとは折り返さない空白）
 *
 * @param date 日付（[walkDate]）。見本は「見本」
 * @param weather 天気の名前（「晴れ」など）。分からなければ null
 * @param separator つなぎの字。カードの狭い注記は空白なしの「・」
 */
@Composable
fun WalkNote(
    distanceM: Int,
    date: String,
    weather: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    separator: String = " ・ ",
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val color = Fieldbook.Muted
    val text = buildAnnotatedString {
        appendInlineContent(FOOTPRINT, "歩いた長さ")
        append(NBSP)
        append(notes(*listOfNotNull("$distanceM m", date, weather).toTypedArray(), separator = separator))
    }
    Text(
        text,
        style = noteStyle,
        fontFamily = Fieldbook.Data,
        fontSize = fontSize,
        color = color,
        minLines = minLines,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = mapOf(
            FOOTPRINT to InlineTextContent(Placeholder(1.1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(AppIcons.Footprints, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
            },
        ),
        modifier = modifier,
    )
}

/** パーツの日付（「11/7」）。日時の無いパーツ（古いデータなど）は「—」 */
fun walkDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))

private const val FOOTPRINT = "footprint"

/** 折り返さない空白（マークと数字を別の行に分けない） */
private const val NBSP = " "
