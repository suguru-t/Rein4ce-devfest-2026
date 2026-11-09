package com.rein4ce.roadcollector.ui.gallery

import com.rein4ce.roadcollector.core.model.TowerMode
import com.rein4ce.roadcollector.core.model.TowerSummary
import java.time.LocalDate

/**
 * 「みんなのタワー」の並び順（＝比べる物差し）。画面の上の切り替えで選ぶ。
 * どの並びでも、同じ値なら新しいほうを上にする（並びが取り直すたびに入れ替わらないように）
 *
 * @property label    切り替えに出す字
 * @property spoken   読み上げ（TalkBack）で言う言葉
 * @property topLabel その物差しで 1 番のタワーの呼び名（断面図の下の字、ランキングの 1 番の札、見出し）
 * @property unit     値の単位（高さは m、パーツは本、連続日数は日）
 * @property mineLabel 見出しで自分のいちばん良い値に付ける呼び名（「あなたの最高」など）
 */
internal enum class TowerSort(val label: String, val spoken: String, val topLabel: String, val unit: String, val mineLabel: String) {
    HEIGHT("高い順", "高い順に並べる", "最長", "m", "あなたの最高"),
    PARTS("多い順", "パーツの多い順に並べる", "最多", "本", "あなたの最多"),

    /**
     * 連続日数順（毎日 1 つずつ積むだけ。決定 73）。今も続いている日数（[currentStreak]）で並べる。
     * 最後に積んだ日が今日か昨日なら、その日までの連続日数。それより前なら 0（積むのをやめると、日がたつと 0 になる）
     */
    STREAK("連続日数順", "今も続いている連続日数の多い順に並べる", "最長連続", "日", "あなたの連続"),

    /** 新しい順。「あなたのタワー」だけで使う（断面図とランキングは、値で比べられる物差しだけ） */
    NEWEST("新しい順", "新しい順に並べる", "最新", "m", "あなたの最新"),
    ;

    val comparator: Comparator<TowerSummary>
        get() = when (this) {
            HEIGHT -> compareByDescending<TowerSummary> { it.height }.thenByDescending { it.createdAt }
            PARTS -> compareByDescending<TowerSummary> { it.partCount }.thenByDescending { it.height }.thenByDescending { it.createdAt }
            STREAK -> {
                val today = LocalDate.now()
                compareByDescending<TowerSummary> { it.currentStreak(today) }.thenByDescending { it.height }.thenByDescending { it.createdAt }
            }
            NEWEST -> compareByDescending<TowerSummary> { it.createdAt }.thenByDescending { it.height }
        }

    /** この物差しでのタワーの値（高さ [m]・パーツの本数・今の連続日数）。新しい順には値の物差しが無いので、横棒は高さで出す */
    fun valueOf(t: TowerSummary): Float = when (this) {
        HEIGHT, NEWEST -> t.height
        PARTS -> t.partCount.toFloat()
        STREAK -> t.currentStreak().toFloat()
    }

    /** 値を字にする（高さは小数 2 桁、本数・日数は整数） */
    fun format(value: Float): String = when (this) {
        HEIGHT, NEWEST -> "%.2f".format(java.util.Locale.US, value)
        PARTS, STREAK -> "${value.toInt()}"
    }

    /** 読み上げの単位 */
    val spokenUnit: String
        get() = when (this) {
            HEIGHT, NEWEST -> "メートル"
            PARTS -> "本"
            STREAK -> "日"
        }

    companion object {
        /** 断面図とランキングで選べる並び（通常の、値で比べられるもの） */
        val COMPARABLE: List<TowerSort> = listOf(HEIGHT, PARTS)

        /**
         * 遊び方ごとに、断面図とランキングで選べる並び（決定 73）。
         * 通常と 3 分チャレンジは高い順・多い順、毎日 1 つずつ積むは高い順・連続日数順
         */
        fun comparableFor(mode: TowerMode): List<TowerSort> = when (mode) {
            TowerMode.NORMAL, TowerMode.CHALLENGE -> COMPARABLE
            TowerMode.DAILY -> listOf(HEIGHT, STREAK)
        }
    }
}

/** 「みんな」の遊び方の切り替えに出す短い名前（決定 73） */
internal val TowerMode.shortLabel: String
    get() = when (this) {
        TowerMode.NORMAL -> "通常"
        TowerMode.CHALLENGE -> "3分チャレンジ"
        TowerMode.DAILY -> "毎日積む"
    }

/** 遊び方の名前（読み上げ。みちをつむのモードの名前と同じ） */
internal val TowerMode.fullLabel: String
    get() = when (this) {
        TowerMode.NORMAL -> "通常"
        TowerMode.CHALLENGE -> "3 分チャレンジ"
        TowerMode.DAILY -> "毎日 1 つずつ積む"
    }

/**
 * まだ自分のタワーが無いときの、ランキングに並ぶまでの一言（「〜すると、ここに立ちます」の前半。決定 73）。
 * 通常は終わりのカードの「保存」で送る。3 分チャレンジと毎日 1 つずつ積むは、結果が出たら・積んで止まったら自動で送る
 */
internal fun howToSend(mode: TowerMode): String = when (mode) {
    TowerMode.NORMAL -> "積んで「保存」すると"
    TowerMode.CHALLENGE -> "3 分チャレンジで記録を出すと"
    TowerMode.DAILY -> "毎日 1 つずつ積むで積むと"
}

/**
 * 毎日 1 つずつ積むの、今も続いている連続日数（決定 73）。最後に積んだ日（[TowerSummary.lastDay]）が [today] か前の日なら、
 * その日までの連続日数（[TowerSummary.streakDays]）。それより前・日付が読めない・ほかの遊び方なら 0
 */
internal fun TowerSummary.currentStreak(today: LocalDate = LocalDate.now()): Int {
    val last = lastDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 0
    return if (!last.isBefore(today.minusDays(1))) streakDays else 0
}

/** 一覧を [sort] の順に並べ直す */
internal fun sortTowers(towers: List<TowerSummary>, sort: TowerSort): List<TowerSummary> = towers.sortedWith(sort.comparator)

/**
 * 断面図で比べる 2 基：その物差しで 1 番のタワー（[top]）と、自分のいちばん良いタワー（[mine]）。
 * 自分のが 1 番（値が 1 番と同じなら 1 番と見なす。同じ 5 本などはよくあるため）なら、[top] に自分のタワーを置き、
 * [mine] には他人でいちばん良いタワーを入れて、比べる相手がいるようにする（その順位は [otherRank]）。
 * 自分のタワーが無ければ [mine] は null（断面図には空の台を出す）
 *
 * @property mineIsTop 自分のタワーが 1 番か（そのとき [mine] は他人で 1 番のタワー）
 * @property otherRank [mineIsTop] のときの、[mine]（他人で 1 番）の全部の中での順位（1 から）。自分のタワーが 1・2 番なら 3。
 *   値が同じで他人が先に並んでいても、自分を 1 番と見なすので相手は 2（「1 番」が 2 つにならない）
 */
internal data class Comparison(val top: TowerSummary?, val mine: TowerSummary?, val mineIsTop: Boolean, val otherRank: Int? = null)

internal fun comparison(towers: List<TowerSummary>, sort: TowerSort, isMine: (TowerSummary) -> Boolean): Comparison {
    val sorted = sortTowers(towers, sort)
    val top = sorted.firstOrNull() ?: return Comparison(null, null, mineIsTop = false)
    val myIndex = sorted.indexOfFirst(isMine)
    val myBest = sorted.getOrNull(myIndex) ?: return Comparison(top, null, mineIsTop = false)
    // 値が同じなら自分を 1 番と見なす（他人が先に並んでいても「あと 0 本」にはしない）
    if (sort.valueOf(myBest) < sort.valueOf(top)) return Comparison(top, myBest, mineIsTop = false)
    val otherIndex = sorted.indexOfFirst { !isMine(it) }
    val other = sorted.getOrNull(otherIndex)
    // 相手の順位。自分を 1 番と見なしているので、並びで自分より前にいる相手（値が同じ）は 1 つ下げる
    val otherRank = other?.let { if (otherIndex < myIndex) otherIndex + 2 else otherIndex + 1 }
    return Comparison(myBest, other, mineIsTop = true, otherRank = otherRank)
}

/** 一度に出すタワーの数（ランキング）。それより多いときは「次へ」でめくる */
internal const val PAGE_SIZE = 5

/** [count] 個のタワーを [PAGE_SIZE] ずつに分けたときのページの数（0 個なら 0） */
internal fun pageCount(count: Int): Int = (count + PAGE_SIZE - 1) / PAGE_SIZE

/** [page]（0 から）に出すタワー。範囲の外なら空 */
internal fun <T> pageOf(items: List<T>, page: Int): List<T> =
    if (page < 0) emptyList() else items.drop(page * PAGE_SIZE).take(PAGE_SIZE)
