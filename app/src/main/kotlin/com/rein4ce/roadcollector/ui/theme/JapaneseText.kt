package com.rein4ce.roadcollector.ui.theme

import android.os.LocaleList
import java.util.Locale

/**
 * アプリの字を、端末の言語によらず日本語として描く（#46）。
 *
 * 漢字には、同じ文字でも日本語用と中国語用で形が違うものがある（「置」の中の「直」、「骨」「角」など）。
 * Android はどちらの形で描くかを字のロケールで決め、アプリは字ごとにロケールを指定していないので、端末の言語が使われる。
 * 端末が英語などだと日本語用の形が選ばれず、中国語の形で描かれていた（エミュレータの en-US で確認）。
 *
 * 字ごとに指定すると、自分でスタイルを作っている所（`TextStyle(...)`・Canvas に描く字）で漏れが出るので、
 * 字のロケールの既定（`LocaleList.getDefault()`。Compose の字もこれを使う）の先頭に日本語を置く。
 * 端末の言語の一覧はそのあとに残すので、日本語のフォントに無い字は、今までどおり端末の言語で描かれる。
 * あわせて、注記を日本語の文節で折り返す設定（`noteStyle`）も、端末の言語によらず効くようになる。
 *
 * 画面の設定が変わると（ダークへの切り替え・文字の大きさなど）、Android が既定を端末の言語に戻すので、
 * アプリの起動時と、画面を作るたび（`MainActivity.onCreate`）に呼ぶ。
 */
object JapaneseText {
    private val JAPANESE: Locale = Locale.JAPAN

    fun apply() {
        val current = LocaleList.getDefault()
        if (current.size() > 0 && current[0] == JAPANESE) return
        val locales = buildList {
            add(JAPANESE)
            for (i in 0 until current.size()) if (current[i] != JAPANESE) add(current[i])
        }
        LocaleList.setDefault(LocaleList(*locales.toTypedArray()))
    }
}
