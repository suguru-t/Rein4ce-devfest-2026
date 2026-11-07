package com.rein4ce.roadcollector.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.BuildConfig
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * アプリについて（設定の枠の中で切り替えて出す）。版、外に送るもの（プライバシー）、使っている素材とライセンス。
 * 素材の出どころは app/fonts/README.md と README の「天気データ」も参照
 */
@Composable
internal fun ColumnScope.AboutContent() {
    Text(
        "ミチツミ（道の採集家）",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Fieldbook.Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Text(
        "版 ${BuildConfig.VERSION_NAME}",
        fontFamily = Fieldbook.Data,
        fontSize = 13.sp,
        color = Fieldbook.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Heading("外に送るもの")
    Item("歩いた場所（GPS）", "スマホの中だけに残します。サーバには送りません")
    Item("天気", "歩いた場所を 0.1 度（約 10 km）に丸めて、Open-Meteo に送ります")
    Item("AI の名前", "パーツの形・距離・日時をサーバに送ります（設定でオフにできます）")
    Item("みんなのタワー", "送ると、積んだパーツの形と置き方・高さ・ニックネームを、だれでも見られるようになります")

    Heading("使っている素材")
    Item("題字のフォント", "M PLUS Rounded 1c（SIL Open Font License 1.1）")
    Item("天気データ", "Open-Meteo.com（CC BY 4.0）")
    Item("地図", "Google マップ")
    Item("物理演算", "dyn4j（BSD 3-Clause License）")
    Item("そのほか", "Jetpack Compose・Ktor・Reorderable・Maps Compose ほか（Apache License 2.0）")
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Fieldbook.Muted,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp).semantics { heading() },
    )
}

/** 名前と説明の 1 行 */
@Composable
private fun Item(name: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("・", fontSize = 14.sp, color = Fieldbook.Muted)
        Column {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fieldbook.Ink)
            Text(detail, style = noteStyle, fontSize = 13.sp, color = Fieldbook.Ink)
        }
    }
}
