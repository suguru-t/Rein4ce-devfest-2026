package com.rein4ce.roadcollector.ui.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.weather.Sky
import com.rein4ce.roadcollector.data.AppSettings
import com.rein4ce.roadcollector.data.PartFilter
import com.rein4ce.roadcollector.data.PartRepository
import com.rein4ce.roadcollector.data.applyPartFilter

/**
 * 手持ちの並び順。
 *
 * 1. 並べ替えたあとに採集したパーツ（[order] に無いもの）を、採集の新しい順に一番上へ
 * 2. 次に、ドラッグなどで並べ替えた順（[order]）
 * 3. 最後に、[order] に無い見本（戻した見本など）を決まった順で
 *
 * まだ並べ替えていなければ（[order] が空）、「採集したパーツを新しい順 → 見本を決まった順」になる。
 * 保存の順番（parts.json の並び）には頼らない。
 */
internal fun inventoryOrder(parts: List<Part>, order: List<String>): List<Part> {
    val byId = parts.associateBy { it.id }
    val arranged = order.distinct().mapNotNull { byId[it] }
    val arrangedIds = arranged.mapTo(HashSet()) { it.id }
    val samples = SampleParts.all.map { it.id }
    val (restSamples, restCollected) = parts.filterNot { it.id in arrangedIds }.partition { it.id in samples }
    return restCollected.sortedByDescending { it.walkedAt } +
        arranged +
        restSamples.sortedBy { samples.indexOf(it.id) }
}

/**
 * コレクションに見えている並び（[inventoryOrder]）。コレクションと積む画面で同じ並びを使うので、
 * 求め方をここ 1 か所にまとめる。手持ちか並び順が変わると、新しい並びになる
 */
@Composable
fun rememberArrangedParts(repository: PartRepository): List<Part> {
    val parts by repository.parts.collectAsStateWithLifecycle()
    val order by repository.order.collectAsStateWithLifecycle()
    return remember(parts, order) { inventoryOrder(parts, order) }
}

/** いまのコレクションの並び（[rememberArrangedParts] と同じもの）を、その時点の値で求める。画面の外（操作の中など）から使う */
fun PartRepository.arrangedNow(): List<Part> = inventoryOrder(parts.value, order.value)

/**
 * 並びと絞り込み（設定に保存。[PartFilter]）・お気に入り・選べる天気をまとめたもの。
 * 並びと絞り込みを 1 か所で決める（片方だけ直して、ずれないように。#75 のレビュー）
 *
 * @property skies 持っているパーツの天気（天気の絞り込みで選べるもの）
 */
internal class PartFiltering(
    val filter: PartFilter,
    val favorites: Set<String>,
    val skies: Set<Sky>,
    private val skyOf: (Part) -> Sky?,
) {
    /** [arranged]（いつもの順）を絞り込んで並べ替える */
    fun apply(arranged: List<Part>): List<Part> = applyPartFilter(arranged, filter, skyOf, favorites)
}

@Composable
internal fun rememberPartFiltering(repository: PartRepository, settings: AppSettings): PartFiltering {
    val filter by settings.partFilter.collectAsStateWithLifecycle()
    val favorites by repository.favorites.collectAsStateWithLifecycle()
    val weathers by repository.weather.collectAsStateWithLifecycle()
    val parts by repository.parts.collectAsStateWithLifecycle()
    return remember(filter, favorites, weathers, parts) {
        val skyOf: (Part) -> Sky? = { repository.weatherOf(it.id, weathers)?.sky }
        PartFiltering(filter, favorites, parts.mapNotNullTo(HashSet(), skyOf), skyOf)
    }
}

/** [from] 番目の要素を [to] 番目へ動かした列 */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (from == to || from !in indices || to !in indices) this
    else toMutableList().apply { add(to, removeAt(from)) }
