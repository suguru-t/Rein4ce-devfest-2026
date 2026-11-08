package com.rein4ce.roadcollector.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.physics.PartSize
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
 * - マークと数字、数字と単位は別の行に分けない（[notes]・[noteStyle]）。マークと数字のあいだは空白を入れず
 *   （空白は等幅の字だと広すぎる）、マークの置き場所を字の 1.2 倍の幅にして、マークを左に寄せた残りを小さなすき間にする。
 *   折り返さないように、幅のない印（U+2060）でつなぐ
 *
 * @param date 日付（[walkDate]）。見本は「見本」。出さないときは null（「パーツを選ぶ」の窓）
 * @param size 積むときの大きさ（[partSizeText]）。長さのすぐあとに出す。出さないときは null
 * @param weather 天気の名前（「晴れ」など）。分からなければ null
 * @param separator つなぎの字。カードの狭い注記は空白なしの「・」
 */
@Composable
fun WalkNote(
    distanceM: Int,
    date: String?,
    weather: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    separator: String = " ・ ",
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    size: String? = null,
) {
    val color = Fieldbook.Muted
    val text = buildAnnotatedString {
        appendInlineContent(FOOTPRINT, "歩いた長さ")
        append(WORD_JOINER)
        append(notes(*listOfNotNull("$distanceM m", size, date, weather).toTypedArray(), separator = separator))
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
            FOOTPRINT to InlineTextContent(Placeholder(1.2.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Icon(
                        AppIcons.Footprints,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                }
            },
        ),
        modifier = modifier,
    )
}

/**
 * 積むときの大きさの言い方（「1.2 倍」。長く歩いた道ほど大きい。[PartSize]）。
 * 積む画面の上の行と「パーツを選ぶ」の窓で同じ書き方にするため、ここ 1 か所で作る
 */
fun partSizeText(distanceM: Int): String = "%.1f 倍".format(Locale.US, PartSize.of(distanceM))

/** パーツの日付（「11/7」）。日時の無いパーツ（古いデータなど）は「—」 */
fun walkDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("M/d", Locale.JAPAN).format(Date(epochMs))

private const val FOOTPRINT = "footprint"

/** 幅のない「ここで折り返さない」印（マークと数字を別の行に分けない） */
private const val WORD_JOINER = "\u2060"
