package com.rein4ce.roadcollector.ui.weather

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.ui.common.noteStyle
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 天気データの出どころ。Open-Meteo のデータは CC BY 4.0 なので、天気を字で出すところに名前とライセンスを添える。
 * 名前とライセンスはタップでブラウザが開く。
 * 見本の天気は作り物（Open-Meteo から取っていない）なので、見本には付けない（呼ぶ側で決める）
 */
@Composable
fun WeatherCredit(modifier: Modifier = Modifier, color: Color = Fieldbook.Muted) {
    val link = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        append("天気データ：")
        withLink(LinkAnnotation.Url(SITE, link)) { append("Open-Meteo.com") }
        append("（")
        // 文字を大きくしても「CC BY 4.0」の途中で折り返さないように、空白は改行しない空白にする
        withLink(LinkAnnotation.Url(LICENSE, link)) { append("CC BY 4.0") }
        append("）")
    }
    Text(
        text,
        modifier = modifier,
        style = noteStyle,
        fontFamily = Fieldbook.Data,
        fontSize = 10.sp,
        color = color,
        textAlign = TextAlign.Center,
    )
}

private const val SITE = "https://open-meteo.com/"
private const val LICENSE = "https://creativecommons.org/licenses/by/4.0/"
