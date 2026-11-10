package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

// 「みんな」だけで使うポップな部品（物差しの色・タワーの注記の行）。コレクションと共通の部品は ui/common/Pop.kt、天気の上に直接書く字は ui/common/SkyText.kt

/**
 * 物差しの色の面（白い字を載せる）：高い順はオレンジ、多い順は青緑、新しい順は紺。
 * 連続日数順も紺（毎日 1 つずつ積むの札の色。新しい順と同じ画面には並ばない。決定 77）
 */
internal fun sortFace(sort: TowerSort): Color = when (sort) {
    TowerSort.HEIGHT -> Pop.OrangeDeep
    TowerSort.PARTS -> Pop.TealDeep
    TowerSort.STREAK, TowerSort.NEWEST -> Pop.Navy
}

/**
 * 「みんな」の遊び方ごとの色合い（決定 81）。遊び方のランキングを開いた先（断面図の画面・全部の一覧・あなたのタワー）を、
 * この色でそろえる（下地・断面図の枠・物差しの札・数字・ボタン・一覧の横棒）。前はどの遊び方も同じオレンジのボタンと黄色の札で、
 * 開いたあとにどの遊び方か分かりにくかった。色は、強すぎて目に痛くならないよう、彩度を落とした落ち着いた色にした：
 * 通常はやわらかいテラコッタ、90 秒チャレンジはセージ（緑がかった青緑）、毎日 1 つずつ積むはラベンダー（モードを選ぶ画面の色の並びと同じ）
 *
 * @property face 塗りの色（白い字を載せる。太字で 3:1 以上）
 * @property ink  字と縁の色（[tint] の上で 4.5:1 以上）
 * @property tint 下地のやわらかい色（断面図の枠の面。画面全体には [wash] で薄く敷く）
 */
internal class GalleryTheme(val face: Color, val ink: Color, val tint: Color) {
    /** 画面全体に敷く下地（後ろの方眼と天気がうっすら透ける） */
    val wash: Color get() = tint.copy(alpha = 0.72f)
}

/** 遊び方 [mode] の色合い（ライトとダークで別の色） */
internal fun galleryTheme(mode: TowerMode): GalleryTheme = if (!Fieldbook.dark) {
    when (mode) {
        TowerMode.NORMAL -> GalleryTheme(face = Color(0xFFC47A50), ink = Color(0xFF9A5634), tint = Color(0xFFF6E4D6))
        TowerMode.CHALLENGE -> GalleryTheme(face = Color(0xFF4A8F84), ink = Color(0xFF2F6B61), tint = Color(0xFFDCEDE8))
        TowerMode.DAILY -> GalleryTheme(face = Color(0xFF7A80B8), ink = Color(0xFF545A92), tint = Color(0xFFE5E6F3))
    }
} else {
    when (mode) {
        TowerMode.NORMAL -> GalleryTheme(face = Color(0xFFA8693F), ink = Color(0xFFE9B48F), tint = Color(0xFF3A2D25))
        TowerMode.CHALLENGE -> GalleryTheme(face = Color(0xFF3D7D72), ink = Color(0xFF93CFC3), tint = Color(0xFF1F3431))
        TowerMode.DAILY -> GalleryTheme(face = Color(0xFF5E6499), ink = Color(0xFFB9BDE6), tint = Color(0xFF292C42))
    }
}

/** 遊び方の色の面（白い字を載せる）：通常はオレンジ、3 分チャレンジは青緑、毎日 1 つずつ積むは紺（モードを選ぶ画面のアイコンと同じ） */
internal fun modeFace(mode: TowerMode): Color = when (mode) {
    TowerMode.NORMAL -> Pop.OrangeDeep
    TowerMode.CHALLENGE -> Pop.TealDeep
    TowerMode.DAILY -> Pop.Navy
}

/** 物差しの色の字（紙の上に直接書く）。ダークモードでは濃い色が紙に沈むので、明るい色にする */
internal fun sortInk(sort: TowerSort): Color = if (!Fieldbook.dark) sortFace(sort) else when (sort) {
    TowerSort.HEIGHT -> Pop.Orange
    TowerSort.PARTS -> Pop.Teal
    TowerSort.STREAK, TowerSort.NEWEST -> Color(0xFFB9BCD0)
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
        if (tower.mode == TowerMode.DAILY) {
            // 毎日 1 つずつ積むは、本数の代わりに今も続いている連続日数（決定 77。1 日 1 つなので、本数はほぼ積んだ日数と同じ）
            MetaText("連続 ${tower.currentStreak()} 日", strong = sort == TowerSort.STREAK)
        } else {
            MetaText(if (partsLabel) "パーツ ${tower.partCount} 本" else "${tower.partCount} 本", strong = sort == TowerSort.PARTS)
        }
    }
    val date: @Composable () -> Unit = {
        MetaText(formatDate(tower.createdAt), strong = sort == TowerSort.NEWEST)
    }
    val dot: @Composable () -> Unit = { MetaText(" ・ ", strong = false) }
    val authorText: @Composable RowScope.() -> Unit = {
        Text(
            author,
            fontFamily = Pop.Rounded,
            fontSize = 14.sp,
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
        fontSize = 14.sp,
        color = if (strong) Fieldbook.Ink else Fieldbook.Muted,
        maxLines = 1,
        softWrap = false,
    )
}
