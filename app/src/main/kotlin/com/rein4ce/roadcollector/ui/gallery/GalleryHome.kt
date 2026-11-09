package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rein4ce.roadcollector.core.challenge.ChallengeRules
import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.TowerSummary
import com.rein4ce.roadcollector.ui.common.BlockWord
import com.rein4ce.roadcollector.ui.common.HeaderFillLayout
import com.rein4ce.roadcollector.ui.common.Pop
import com.rein4ce.roadcollector.ui.common.artBars
import com.rein4ce.roadcollector.ui.common.artSlab
import com.rein4ce.roadcollector.ui.common.artStar
import com.rein4ce.roadcollector.ui.common.bar
import com.rein4ce.roadcollector.ui.game.ModeRow
import com.rein4ce.roadcollector.ui.game.PlayMode
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 「みんな」のホーム（決定 77）。みちをつむの、遊び方を選ぶ画面（ui/game/ModeSelect.kt の ModeSelectScreen）と同じ組み方：
 * - 上の部分：積み木で作った塔の絵（[RankingArt]）、大きな画面名「みんなのタワー」（ほかの画面と同じ積み木）、「どのランキングを見に行く？」
 * - 下に、遊び方の行（みちをつむと同じ行。左に色の積み木とアイコン、右に紙のカード：名前・一言・そのランキングのようす）
 * 行を押すと、その遊び方のランキング（断面図と「ランキングを見る」「あなたのタワー」）へ行き、「‹ 戻る」でここに戻る。
 * 行はふつうの大きさのままいちばん下に置き、余った高さは上の部分を大きくして埋める（[HeaderFillLayout]）
 *
 * @param stateOf 遊び方ごとの一覧（TowerGallery.stateOf）
 * @param isMine  自分のタワーか
 * @param onPick  遊び方を選んだ
 */
@Composable
internal fun GalleryHome(
    stateOf: (TowerMode) -> TowerGallery.State,
    isMine: (TowerSummary) -> Boolean,
    onPick: (TowerMode) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = PAGE_TOP, bottom = PAGE_BOTTOM)) {
                HeaderFillLayout(
                    minHeight = viewport - PAGE_TOP - PAGE_BOTTOM,
                    maxScale = HEADER_SCALE_MAX,
                    gap = HEADER_ROWS_GAP,
                    header = { scale -> HomeTitle(scale) },
                    rows = {
                        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                            TowerMode.entries.forEach { mode ->
                                ModeRow(
                                    mode = playModeOf(mode),
                                    note = rankingNote(mode),
                                    record = homeRecord(stateOf(mode), mode, isMine),
                                    onClick = { onPick(mode) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * ホームの上の部分：積み木の塔の絵・画面名「みんなのタワー」・「どのランキングを見に行く？」。まん中にそろえる。
 * 中の大きさを [scale] 倍にする（画面に高さが余るとき、行ではなくここを大きくして埋める。みちをつむと同じ）
 */
@Composable
private fun HomeTitle(scale: Float) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RankingArt(modifier = Modifier.size(width = ART_WIDTH, height = ART_HEIGHT))
            Spacer(Modifier.height(10.dp))
            BlockWord("みんなのタワー", blockDp = TITLE_BLOCK)
            Text(
                "どのランキングを見に行く？",
                fontFamily = Pop.Rounded,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Fieldbook.Ink,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * 積み木で作った塔の絵（飾り。読み上げない）。高さの違う 2 本の塔を 1 本の台に並べる（ランキングで比べる、という絵）。
 * 高い塔のてっぺんには、1 番の星。
 * ホームの塔（アプリのアイコン）と同じ質感で描く：色だけの面で、道のパーツ（橋・L）は角の立った同じ太さの棒を組み合わせ、
 * 板と台だけ角を少し丸め、傾けない（ui/common/TowerArt.kt。決定 82。前は下に濃い側面がのぞく傾いた積み木で、ホームと質感が違った）
 */
@Composable
private fun RankingArt(modifier: Modifier = Modifier) {
    // 台の色は積む画面と同じ（ダークでは、暗い地の上でも見える明るさ）
    val platform = if (Fieldbook.dark) Color(0xFF7A7FA6) else Pop.Navy
    // 紺の積み木は、ダークでは台と同じ明るい紺にして、暗い地に沈ませない
    val navy = if (Fieldbook.dark) Color(0xFF9DA2C8) else Pop.Navy
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        // 絵は 176×112 の目盛りで描き、場所の大きさに合わせる（みちをつむの絵と同じ）
        val u = size.width / 176f
        // 台
        artSlab(platform, x = 20f, y = 98f, w = 136f, h = 12f, r = 3f, u = u)
        // 高い塔（左）：下から、黄の板・青緑の橋・オレンジの L・紺の板、てっぺんに星
        artSlab(Pop.Yellow, x = 36f, y = 84f, w = 52f, h = 14f, r = 4f, u = u)
        artBars(Pop.Teal, u, bar(43f, 60f, 10f, 24f), bar(72f, 60f, 10f, 24f), bar(43f, 60f, 39f, 10f))
        artBars(Pop.Orange, u, bar(52f, 32f, 10f, 28f), bar(52f, 50f, 26f, 10f))
        artSlab(navy, x = 44f, y = 22f, w = 28f, h = 10f, r = 3f, u = u)
        artStar(Pop.Yellow, cx = 58f, cy = 11f, r = 9.5f, u = u)
        // 低い塔（右）：下から、オレンジの板・黄の四角・青緑の短い棒
        artSlab(Pop.Orange, x = 100f, y = 86f, w = 44f, h = 12f, r = 4f, u = u)
        artSlab(Pop.Yellow, x = 110f, y = 64f, w = 24f, h = 22f, r = 3f, u = u)
        artBars(Pop.Teal, u, bar(114f, 54f, 16f, 10f))
    }
}

/** ホームの行の一言（そのランキングで何を比べるか） */
private fun rankingNote(mode: TowerMode): String = when (mode) {
    TowerMode.NORMAL -> "みんなの塔を、高さと本数で比べる"
    TowerMode.CHALLENGE -> "時間内に積んだ高さと本数を比べる"
    TowerMode.DAILY -> "育てた塔の高さと、続けた日数を比べる"
}

/**
 * ランキングに並べるタワー（[state] が取れていれば）。3 分チャレンジは今の決まりの記録だけ（決まりが違う記録は混ぜない。決定 77）
 */
internal fun rankedTowers(state: TowerGallery.State, mode: TowerMode): List<TowerSummary> =
    (state as? TowerGallery.State.Loaded)?.towers.orEmpty()
        .filter { mode != TowerMode.CHALLENGE || it.rule == ChallengeRules.ID }

/**
 * ホームの行に出す、そのランキングのようす（決定 77）。自分のタワーがあれば自分の最高と順位（毎日は自分の塔と連続日数）、
 * 無ければ 1 番の高さと数。読み込み中・つながらない・まだ無い、のときはその一言
 */
internal fun homeRecord(state: TowerGallery.State, mode: TowerMode, isMine: (TowerSummary) -> Boolean): String {
    when (state) {
        TowerGallery.State.Loading -> return "読み込み中…"
        is TowerGallery.State.Error -> return "サーバに接続できません"
        is TowerGallery.State.Loaded -> Unit
    }
    val towers = sortTowers(rankedTowers(state, mode), TowerSort.HEIGHT)
    val top = towers.firstOrNull() ?: return "まだタワーがありません"
    val mineIndex = towers.indexOfFirst(isMine)
    if (mineIndex < 0) return "1 位 %.2f m・全部で %d 個".format(top.height, towers.size)
    val mine = towers[mineIndex]
    return if (mode == TowerMode.DAILY) {
        "あなたの塔 %.2f m・連続 %d 日".format(mine.height, mine.currentStreak())
    } else {
        "あなたの最高 %.2f m・%d 位".format(mine.height, mineIndex + 1)
    }
}

/** ページの上と下の余白（みちをつむと同じ） */
private val PAGE_TOP = 20.dp
private val PAGE_BOTTOM = 20.dp

/** 画面に高さが余るとき、上の部分を大きくする上限と、上の部分と行のあいだ（みちをつむと同じ） */
private const val HEADER_SCALE_MAX = 1.8f
private val HEADER_ROWS_GAP = 22.dp

/** 上の部分の絵の大きさと、画面名の積み木 1 つの大きさ（みちをつむと同じ） */
private val ART_WIDTH = 176.dp
private val ART_HEIGHT = 112.dp
private val TITLE_BLOCK = 28.dp

/** 行と行の間 */
private val ROW_GAP = 14.dp
