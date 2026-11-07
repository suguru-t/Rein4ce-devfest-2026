package com.rein4ce.roadcollector.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak

/*
 * 注記（「L=488 m ・ 頂点 9 ・ 11/6」「タップで大きく見る ・ 長押しで並べ替え」など）を、
 * 文字を大きくしている人の画面でも崩れずに折り返すための道具。どの画面でも使える（B が作り、コレクションで使っている）。
 * 使い方：Text(notes("L=488 m", "頂点 9"), style = noteStyle)
 */

/**
 * 注記を「・」でつなぐ（「タップで大きく見る ・ 長押しで並べ替え」など）。
 * 文字を大きくして折り返すときに、「6 本」「488 m」の数と単位が別の行に分かれたり、行が「・」で始まったりしないよう、
 * 項目の中の空白と「・」の前の空白は、折り返さない空白（U+00A0）にする。どこで折り返すかは [noteStyle] で決める
 *
 * @param separator つなぎの字。前後の空白も含める（カードの狭い注記は空白なしの「・」）
 */
fun notes(vararg items: String, separator: String = " ・ "): String {
    val dot = separator.trimEnd()
    val glued = dot.replace(' ', NBSP) + separator.substring(dot.length)
    return items.joinToString(glued) { it.replace(' ', NBSP) }
}

/** 折り返さない空白 */
private const val NBSP = '\u00A0'

/**
 * 注記や説明の字の設定。いまの字の設定（`LocalTextStyle`）はそのままで、折り返し方だけを変える。文字を大きくして 1 行に入らないときに、日本語の文節（「タップで」「大きく」「見る」）の切れ目で
 * 折り返し、「見本」「頂点」「削除」のような言葉の途中や、「・」「）」で始まる行を作らない。
 * 文節で折り返すのは Android 13 以降のとき（それより前は字の間で折り返す）。
 * 字のロケールはアプリ全体で日本語にしているので（ui/theme/JapaneseText。#46）、端末の言語が英語などでも文節で折り返す
 */
val noteStyle: TextStyle
    @Composable get() = LocalTextStyle.current.copy(lineBreak = NoteLineBreak)

private val NoteLineBreak = LineBreak.Simple.copy(strictness = LineBreak.Strictness.Strict, wordBreak = LineBreak.WordBreak.Phrase)
