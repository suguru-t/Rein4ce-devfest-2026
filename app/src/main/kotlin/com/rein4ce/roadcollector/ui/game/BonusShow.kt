package com.rein4ce.roadcollector.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rein4ce.roadcollector.core.physics.WeatherBonus
import com.rein4ce.roadcollector.ui.common.rememberReduceMotion
import kotlin.math.min

/**
 * 終わったときの天気のボーナスの見せ方の時間割（決定 68）。速く大きくなるだけだと、なぜ大きくなったのか分からないので、順に見せる：
 *
 * 1. 塔が落ち着いたら少し待つ（[TAGS_START_MS]）
 * 2. ボーナスの付いたパーツの上に、低い順に 1 つずつ「雪 +0.20」の札を出す。札が出るたびに、上の「天気ボーナス ×1.20」が数え上がる
 * 3. 少し待って（[PAUSE_MS]）、[GROW_MS] かけて塔と台を大きくする。高さのメーターも一緒に回る
 * 4. 大きくなったら、札を薄くして消す（[TAG_FADE_MS]。上の倍率と終わりのカードは残る）
 *
 * 札の間隔は、全部で [TAGS_TOTAL_MS] に収まるようにし、[MIN_TAG_MS]〜[MAX_TAG_MS] にする（多いと速く、少ないとゆっくり）。
 * 時刻はミリ秒で、見せ始めてからの時間
 *
 * @param marks 札を出すパーツの数
 * @param percents 札 1 つずつの足す分 [百分の一]（出す順）
 */
internal class BonusTimeline(private val percents: List<Int>) {
    val marks: Int get() = percents.size

    /** 札と札の間 [ms] */
    val tagIntervalMs: Long = if (marks == 0) 0L else (TAGS_TOTAL_MS / marks).coerceIn(MIN_TAG_MS, MAX_TAG_MS)

    /** [i] 番目の札が出始める時刻 */
    fun tagStartMs(i: Int): Long = TAGS_START_MS + i * tagIntervalMs

    /** 大きくなり始める時刻 */
    val growStartMs: Long = TAGS_START_MS + marks * tagIntervalMs + PAUSE_MS

    /** 大きくなり終わる時刻 */
    val growEndMs: Long = growStartMs + GROW_MS

    /** 札が消え終わる時刻（見せ方の終わり） */
    val totalMs: Long = growEndMs + TAG_FADE_MS

    /** [t] のときに出ている札の数 */
    fun tagsShown(t: Long): Int = (0 until marks).count { t >= tagStartMs(it) }

    /**
     * [t] のときの [i] 番目の札の大きさ（0〜1。出るときに少し大きくなってから戻る、ポンと出る動き）
     */
    fun tagPop(i: Int, t: Long): Float {
        val p = ((t - tagStartMs(i)).toFloat() / TAG_POP_MS).coerceIn(0f, 1f)
        if (p <= 0f) return 0f
        // 0 → 1.15 → 1
        return if (p < 0.6f) p / 0.6f * 1.15f else 1.15f - (p - 0.6f) / 0.4f * 0.15f
    }

    /** [t] のときの札の濃さ（大きくなり終わったら薄くして消す） */
    fun tagAlpha(t: Long): Float = 1f - ((t - growEndMs).toFloat() / TAG_FADE_MS).coerceIn(0f, 1f)

    /** [t] のときに上に出す倍率（出た札のぶんだけ足す。上限で止める） */
    fun shownMultiplier(t: Long): Double =
        min(1.0 + percents.take(tagsShown(t)).sum() / 100.0, WeatherBonus.MAX_MULTIPLIER)

    /** [t] のときの模型の大きさ（1 → [multiplier]。ゆっくり始まりゆっくり終わる） */
    fun growth(t: Long, multiplier: Double): Float {
        val p = ((t - growStartMs).toFloat() / GROW_MS).coerceIn(0f, 1f)
        return 1f + (multiplier.toFloat() - 1f) * FastOutSlowInEasing.transform(p)
    }

    companion object {
        /** 塔が落ち着いてから、最初の札を出すまで */
        const val TAGS_START_MS = 500L

        /** 札を全部出すのにかける時間の目安 */
        const val TAGS_TOTAL_MS = 1800L
        const val MIN_TAG_MS = 150L
        const val MAX_TAG_MS = 450L

        /** 札がポンと出る動きの時間 */
        const val TAG_POP_MS = 220L

        /** 最後の札から、大きくなり始めるまで */
        const val PAUSE_MS = 400L

        /** 大きくなるのにかける時間 */
        const val GROW_MS = 1600L

        /** 大きくなったあと、札を消すのにかける時間 */
        const val TAG_FADE_MS = 600L
    }
}

/**
 * 終わったときの天気のボーナスの見せ方の、いまの時刻。積む画面（キャンバス・高さのメーター・上の倍率）が同じものを読む。
 *
 * @property result いまの結果（無ければ見せない）
 * @property timeline 時間割
 * @property elapsedMs 見せ始めてからの時間（見せ終わったら [BonusTimeline.totalMs]）
 */
internal class BonusShowState(
    val result: StackViewModel.StackResult?,
    val timeline: BonusTimeline,
    private val elapsed: () -> Long,
) {
    val elapsedMs: Long get() = elapsed()

    /** 見せるボーナスがあるか */
    val active: Boolean get() = result?.bonus?.active == true

    /** いまの模型の大きさ（終わる前と、ボーナスが無いときは 1） */
    val growth: Float get() = result?.takeIf { active }?.let { timeline.growth(elapsedMs, it.bonus.multiplier) } ?: 1f

    /**
     * 終わりのカードに、天気のボーナスの倍率と内訳を出してよいか（大きくなり終わったら）。
     * 塔の上で札を出して大きくしている間に、カードで答えを先に見せないため。ボーナスが無ければ、いつでも
     */
    val revealed: Boolean get() = !active || elapsedMs >= timeline.growEndMs

    /** 高さのメーターに出す高さ（終わったら、大きくなっていくのに合わせて、ボーナスをかけた高さまで） */
    fun meterHeight(playing: Float): Float = result?.let { it.tower.height * growth } ?: playing
}

/**
 * [vm] の結果が出たら、天気のボーナスを時間割（[BonusTimeline]）どおりに見せる。
 * 一度見せた回は、タブを行き来して戻ったときに見せ直さない（すぐ終わりの姿にする）。動きを減らす設定なら、すぐ終わりの姿にする
 */
@Composable
internal fun rememberBonusShow(vm: StackViewModel): BonusShowState {
    val result = vm.result
    val timeline = remember(result) { BonusTimeline(result?.bonusMarks.orEmpty().map { it.grip.bonusPercent }) }
    val clock = remember(result) { Animatable(0f) }
    var shownRun by rememberSaveable { mutableStateOf(-1L) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(result) {
        if (result == null || !result.bonus.active) return@LaunchedEffect
        val end = timeline.totalMs.toFloat()
        if (shownRun == vm.runId || reduceMotion) {
            clock.snapTo(end)
        } else {
            clock.animateTo(end, tween(durationMillis = timeline.totalMs.toInt(), easing = LinearEasing))
        }
        shownRun = vm.runId
    }
    return remember(result, timeline, clock) { BonusShowState(result, timeline) { clock.value.toLong() } }
}
