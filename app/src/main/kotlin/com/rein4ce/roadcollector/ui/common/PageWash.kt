package com.rein4ce.roadcollector.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 画面の下地の色。4 つの画面の入れ物（AppNav の TabPage）が、端末の時計のバーの下とタブバーの裏まで含めた画面全体に敷く。
 * 画面の中身はバーとタブバーの分をあけて置かれるので、中身の側で敷くと、上下に方眼のままの帯が残って境目が横一直線に見えた
 * （#135 のレビュー）。中身は [PageWash] で色を伝える
 */
class PageWashes {
    private val entries = mutableStateListOf<Pair<Any, Color>>()

    /** いま敷く色（最後に伝えた画面の色。無ければ null で、何も敷かない） */
    val current: Color? get() = entries.lastOrNull()?.second

    internal fun add(entry: Pair<Any, Color>) {
        entries.add(entry)
    }

    internal fun remove(entry: Pair<Any, Color>) {
        entries.remove(entry)
    }
}

/** 画面の入れ物から中身へ、下地の色の受け口を渡す（入れ物の外では null で、[PageWash] は何もしない） */
val LocalPageWashes = staticCompositionLocalOf<PageWashes?> { null }

/**
 * この画面が出ている間、画面全体の下地を [color] にする（「みんな」の遊び方の色など。決定 81）。
 * 画面が切り替わる間に 2 つの画面が同時にいても、あとから出た画面の色を敷き、先の画面が消えても残る
 */
@Composable
fun PageWash(color: Color) {
    val washes = LocalPageWashes.current ?: return
    val token = remember { Any() }
    DisposableEffect(washes, color) {
        val entry = token to color
        washes.add(entry)
        onDispose { washes.remove(entry) }
    }
}
