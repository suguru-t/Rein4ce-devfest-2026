package com.rein4ce.roadcollector.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 遊び方ごとの色合い（決定 81・84）。「みんな」で遊び方のランキングを開いた先（断面図の画面・全部の一覧・あなたのタワー）と、
 * みちをつむでその遊び方を遊んでいる画面（[LocalModeTheme]。決定 84）を、この色でそろえる。「みんな」では、下地・断面図の枠・物差しの札・数字・ボタン・一覧の横棒とめくる札・全体を出す画面をこの色にする。前はどの遊び方も同じオレンジのボタンと黄色の札で、
 * 開いたあとにどの遊び方か分かりにくかった。色は、強すぎて目に痛くならないよう、彩度を落とした落ち着いた色にした：
 * 通常はやわらかいテラコッタ、90 秒チャレンジはセージ（緑がかった青緑）、毎日 1 つずつ積むはラベンダー（モードを選ぶ画面の色の並びと同じ）。
 * 物差し（高い順・多い順など）ごとの色はやめた（遊び方の色と混ざって、かえって分かりにくいため）
 *
 * @property face 塗りの色（白い字（[Pop.Letter]）を載せる。札の字は小さいので 4.5:1 以上。#135 のレビューで、前の 3.4〜3.8 から少し濃くした）
 * @property ink  字と縁の色（[tint] の上で 4.5:1 以上）
 * @property tint 下地のやわらかい色（断面図の枠の面・全体を出す画面。画面全体には [wash] で薄く敷く）
 */
internal class ModeTheme(val face: Color, val ink: Color, val tint: Color) {
    /** 画面全体に敷く下地（後ろの方眼と天気がうっすら透ける。端末のバーとタブバーの裏まで敷く：ui/common/PageWash.kt） */
    val wash: Color get() = tint.copy(alpha = 0.72f)

    /**
     * みちをつむで遊ぶ画面に敷く下地（決定 84）。[wash] より薄くして、後ろの雨や雪が見えるようにする
     * （遊ぶ画面では天気も画面の一部。[wash] の濃さでは雪がほとんど見えなくなった。#138 の C のレビュー・B の判断）
     */
    val playWash: Color get() = tint.copy(alpha = 0.45f)
}

/** 遊び方 [mode] の色合い（ライトとダークで別の色） */
internal fun modeTheme(mode: TowerMode): ModeTheme = if (!Fieldbook.dark) {
    when (mode) {
        TowerMode.NORMAL -> ModeTheme(face = Color(0xFFA15D36), ink = Color(0xFF9A5634), tint = Color(0xFFF6E4D6))
        TowerMode.CHALLENGE -> ModeTheme(face = Color(0xFF3F7970), ink = Color(0xFF2F6B61), tint = Color(0xFFDCEDE8))
        TowerMode.DAILY -> ModeTheme(face = Color(0xFF6269AB), ink = Color(0xFF545A92), tint = Color(0xFFE5E6F3))
    }
} else {
    when (mode) {
        TowerMode.NORMAL -> ModeTheme(face = Color(0xFF9B613A), ink = Color(0xFFE9B48F), tint = Color(0xFF3A2D25))
        TowerMode.CHALLENGE -> ModeTheme(face = Color(0xFF3B796E), ink = Color(0xFF93CFC3), tint = Color(0xFF1F3431))
        TowerMode.DAILY -> ModeTheme(face = Color(0xFF5E6499), ink = Color(0xFFB9BDE6), tint = Color(0xFF292C42))
    }
}

/**
 * みちをつむで遊んでいる遊び方の色合い（決定 84）。遊ぶ画面の枠（ui/game/ModeSelect.kt の ModePlayPage）が渡し、
 * 積む画面・90 秒チャレンジ・毎日 1 つずつ積むの数字・ボタン・札がこの色を使う。渡されていなければ null（今までの色）
 */
internal val LocalModeTheme = staticCompositionLocalOf<ModeTheme?> { null }

/** 遊んでいる遊び方の塗り（白い字を載せるボタン・札。決定 84）。遊び方の画面の外では [fallback]（今までの色） */
@Composable
@ReadOnlyComposable
internal fun modeFaceOr(fallback: Color): Color = LocalModeTheme.current?.face ?: fallback

/** 遊んでいる遊び方の字と縁の色（大きな数字・白い面に色の縁の札。決定 84）。遊び方の画面の外では [fallback]（今までの色） */
@Composable
@ReadOnlyComposable
internal fun modeInkOr(fallback: Color): Color = LocalModeTheme.current?.ink ?: fallback
