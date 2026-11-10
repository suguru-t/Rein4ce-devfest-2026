package com.rein4ce.roadcollector.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
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
import com.rein4ce.roadcollector.ui.common.artBar
import com.rein4ce.roadcollector.ui.common.popBlock
import com.rein4ce.roadcollector.ui.game.MODE_LEADING_DEPTH
import com.rein4ce.roadcollector.ui.game.MODE_LEADING_SIZE
import com.rein4ce.roadcollector.ui.game.ModeRow
import com.rein4ce.roadcollector.ui.game.modeBlockFace
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/**
 * 「みんな」のホーム（決定 77）。みちをつむの、遊び方を選ぶ画面（ui/game/ModeSelect.kt の ModeSelectScreen）と同じ組み方：
 * - 上の部分：表彰台の絵（[RankingArt]。決定 82）、大きな画面名「みんなのタワー」（ほかの画面と同じ積み木）、「どのランキングを見に行く？」
 * - 下に、遊び方の行（みちをつむと同じ行。左に遊び方の色のメダルと自分の順位（[RankMedal]）、右に紙のカード：名前・一言・そのランキングのようす）
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
                                val play = playModeOf(mode)
                                val state = stateOf(mode)
                                // 高い順の並びは 1 回だけ作り、カードの文とメダルの順位で同じものを使う
                                val order = heightOrder(state, mode)
                                val rank = if (state is TowerGallery.State.Loaded) myRank(order, isMine) else null
                                ModeRow(
                                    mode = play,
                                    note = rankingNote(mode),
                                    record = homeRecord(state, mode, order, isMine),
                                    onClick = { onPick(mode) },
                                    // みちをつむの四角い積み木（アイコン）の代わりに、遊び方の色のメダルに自分の順位（決定 82）。
                                    // 通常と 90 秒チャレンジはカードの文も順位を言うので、読み上げはカードだけ。毎日 1 つずつ積むのカードは連続日数なので、メダルが順位を言う
                                    leading = {
                                        RankMedal(
                                            face = modeBlockFace(play),
                                            rank = rank,
                                            spoken = if (mode == TowerMode.DAILY && rank != null) "高さの順位は $rank 位" else null,
                                        )
                                    },
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
 * 表彰台の絵（飾り。読み上げない）。1・2・3 の段（まん中がいちばん高い 1 位、左が 2 位、右が 3 位）に、
 * 高さの違う積み木の塔を立て、1 位の塔のてっぺんに星を置く。みちをつむの絵（積んでいる途中）と見分けられるよう、
 * 順位を比べる画面だと一目で分かる絵にした（B の希望。決定 82。前は 1 本の台に 2 本の塔を並べた絵）。
 * ホームの塔（アプリのアイコン）と同じ質感で描く：色だけの面で、道のパーツは角の立った同じ太さの棒を組み合わせ、
 * 板と段だけ角を少し丸め、傾けない（ui/common/TowerArt.kt）
 */
@Composable
private fun RankingArt(modifier: Modifier = Modifier) {
    // 段の色は積む画面の台と同じ（ダークでは、暗い地の上でも見える明るさ）
    val step = if (Fieldbook.dark) Color(0xFF7A7FA6) else Pop.Navy
    // 段の数字：ライトは白。ダークの明るい段の上では白が読みにくい（3.9:1）ので、暗い紙の色（4.5:1）
    val numberColor = if (Fieldbook.dark) Fieldbook.Paper else Pop.Letter
    val measurer = rememberTextMeasurer()
    // 段の数字の大きさを測るのは、大きさが変わったときだけ（描くたびに測らない）
    Spacer(
        modifier = modifier.clearAndSetSemantics {}.drawWithCache {
            // 絵は 176×112 の目盛りで描き、場所の大きさに合わせる（みちをつむの絵と同じ）
            val u = size.width / 176f
            // 段：(順位, 左, 上, 幅, 高さ)。左が 2 位・まん中が 1 位・右が 3 位
            val steps = listOf(
                floatArrayOf(1f, 65f, 70f, 46f, 40f),
                floatArrayOf(2f, 17f, 82f, 46f, 28f),
                floatArrayOf(3f, 113f, 92f, 46f, 18f),
            )
            val numbers = steps.map { st ->
                val rank = st[0].toInt()
                val style = TextStyle(fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, color = numberColor, fontSize = ((if (rank == 1) 15f else 12f) * u).toSp())
                measurer.measure("$rank", style)
            }
            onDrawBehind {
                steps.forEachIndexed { k, st ->
                    artSlab(step, x = st[1], y = st[2], w = st[3], h = st[4], r = 3f, u = u)
                    // 数字は段の上のほう（高い段でも上から 12 の所まで）
                    val text = numbers[k]
                    val cx = (st[1] + st[3] / 2) * u
                    val cy = (st[2] + minOf(st[4] / 2, 12f)) * u
                    drawText(text, topLeft = Offset(cx - text.size.width / 2f, cy - text.size.height / 2f))
                }
                // 1 位の塔（まん中の段の上）：黄の板・青緑の橋・オレンジの L、てっぺんに星
                artSlab(Pop.Yellow, x = 70f, y = 60f, w = 36f, h = 10f, r = 3f, u = u)
                artBars(Pop.Teal, u, artBar(74f, 43f, 8f, 17f), artBar(94f, 43f, 8f, 17f), artBar(74f, 43f, 28f, 8f))
                artBars(Pop.Orange, u, artBar(80f, 22f, 8f, 21f), artBar(80f, 35f, 18f, 8f))
                artStar(Pop.Yellow, cx = 84f, cy = 12f, r = 8.5f, u = u)
                // 2 位の塔（左の段の上）：オレンジの板・黄の四角・青緑の短い棒
                artSlab(Pop.Orange, x = 24f, y = 72f, w = 32f, h = 10f, r = 3f, u = u)
                artSlab(Pop.Yellow, x = 30f, y = 56f, w = 20f, h = 16f, r = 3f, u = u)
                artBars(Pop.Teal, u, artBar(34f, 48f, 12f, 8f))
                // 3 位の塔（右の段の上）：青緑の板・オレンジの四角
                artSlab(Pop.Teal, x = 120f, y = 84f, w = 32f, h = 8f, r = 3f, u = u)
                artSlab(Pop.Orange, x = 127f, y = 70f, w = 18f, h = 14f, r = 3f, u = u)
            }
        },
    )
}

/**
 * 行の左に置くメダル（決定 82）：遊び方の色の丸い積み木（下に側面がのぞく）に、高い順での自分の順位を大きな白い字で。
 * まだ自分のタワーが無ければ「—」。上に細い V のリボンがのぞく。
 * みちをつむの行は四角い積み木にアイコンなので、丸いメダルに順位で、どちらの画面か見分けられるようにした（B の希望）。
 * 字は丸に収めるため、端末の文字の大きさの設定では大きくしない（「‹ 戻る」と同じく dp で決める。#136 の A のレビュー）
 *
 * @param spoken 読み上げで伝えること。右のカードがもう順位を言うときは null（2 回言わない）
 */
@Composable
private fun RankMedal(face: Color, rank: Int?, spoken: String?) {
    val ribbon = Pop.side(face)
    val density = LocalDensity.current
    // 数字はけた数で小さくする（4 けたまで丸に入る）。「位」と「—」も dp で決める
    val digits = rank?.toString()?.length ?: 0
    val numberSize = with(density) { (if (digits <= 2) 26.dp else if (digits == 3) 18.dp else 14.dp).toSp() }
    val unitSize = with(density) { 12.dp.toSp() }
    val dashSize = with(density) { 22.dp.toSp() }
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .size(width = MODE_LEADING_SIZE, height = MODE_LEADING_SIZE + MODE_LEADING_DEPTH)
            .clearAndSetSemantics { if (spoken != null) contentDescription = spoken },
    ) {
        // リボン：上の端から、メダルの裏のまん中へ下りる細い V の 2 本の帯（メダルの上に、首にかける帯がのぞく）
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val bandW = w * 0.15f
            fun band(topX: Float, bottomX: Float) {
                val path = Path().apply {
                    moveTo(topX - bandW / 2, 0f)
                    lineTo(topX + bandW / 2, 0f)
                    lineTo(bottomX + bandW / 2, size.height * 0.5f)
                    lineTo(bottomX - bandW / 2, size.height * 0.5f)
                    close()
                }
                drawPath(path, ribbon)
            }
            band(topX = w * 0.36f, bottomX = w * 0.46f)
            band(topX = w * 0.64f, bottomX = w * 0.54f)
        }
        // メダル（丸い積み木）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = MEDAL_SIZE, height = MEDAL_SIZE + MODE_LEADING_DEPTH)
                .popBlock(face = face, corner = MEDAL_SIZE / 2, depth = MODE_LEADING_DEPTH),
        ) {
            if (rank == null) {
                Text("—", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = dashSize, color = Pop.Letter)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$rank",
                        fontFamily = Pop.Rounded,
                        fontWeight = FontWeight.Black,
                        fontSize = numberSize,
                        color = Pop.Letter,
                        maxLines = 1,
                    )
                    Text("位", fontFamily = Pop.Rounded, fontWeight = FontWeight.Black, fontSize = unitSize, color = Pop.Letter, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

/** メダルの丸の大きさ（行の左の積み木より一回り小さく、上にリボンがのぞく場所を残す） */
private val MEDAL_SIZE = 52.dp

/** 今の決まりの記録を高い順に並べたもの（読み込めていなければ空）。カードの文（[homeRecord]）とメダル（[myRank]）で同じ並びを使う */
internal fun heightOrder(state: TowerGallery.State, mode: TowerMode): List<TowerSummary> =
    sortTowers(rankedTowers(state, mode), TowerSort.HEIGHT)

/** [order]（[heightOrder]）での自分のいちばん良い順位（1 から）。自分のタワーが無いときは null */
internal fun myRank(order: List<TowerSummary>, isMine: (TowerSummary) -> Boolean): Int? =
    order.indexOfFirst(isMine).takeIf { it >= 0 }?.plus(1)

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
internal fun homeRecord(
    state: TowerGallery.State,
    mode: TowerMode,
    order: List<TowerSummary> = heightOrder(state, mode),
    isMine: (TowerSummary) -> Boolean,
): String {
    when (state) {
        TowerGallery.State.Loading -> return "読み込み中…"
        is TowerGallery.State.Error -> return "サーバに接続できません"
        is TowerGallery.State.Loaded -> Unit
    }
    val towers = order
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
