package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 3分チャレンジの端末内の記録。読み書きは [ChallengeStorage] が渡す（テストでは Android なしで動かす）。
 *
 * - 書き込みに失敗したら、手元の一覧も変えない（失敗したのに保存できたように見せない）
 * - 読めないファイル（壊れている・前の形式で決まりの ID が無い）は、上書きせずに [quarantine] で別の名前に退避して、空から始める。
 *   前は読めないまま毎回失敗して、結果の画面から先に進めなかった（#78 のレビュー）。退避したファイルは消さずに残る
 *
 * @param read       保存してある中身。ファイルが無ければ null
 * @param write      中身を保存する（失敗したら例外）
 * @param quarantine 読めなかったファイルを別の名前に退避する
 */
class ChallengeRecords(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val quarantine: () -> Unit = {},
) {
    private val serializer = ListSerializer(ChallengeScore.serializer())
    private var scores: List<ChallengeScore> = load()

    private fun load(): List<ChallengeScore> {
        val text = read() ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (_: IllegalArgumentException) {
            // 壊れた JSON・決まりの ID が無い前の形式（kotlinx.serialization の SerializationException も IllegalArgumentException）
            quarantine()
            emptyList()
        }
    }

    @Synchronized
    fun add(score: ChallengeScore): List<ChallengeScore> {
        require(score.height.isFinite() && score.height >= 0)
        // 台に何も残らなかった回（0 m）は書かない（決定 89）
        if (score.counts && scores.none { it.runId == score.runId }) {
            val updated = scores + score
            write(json.encodeToString(serializer, updated))
            scores = updated
        }
        return ranking(score.rule)
    }

    /** 同じ決まり（[rule]）の記録だけを、高い順（同じ高さなら先に出した順）に。前に残した 0 m の回は出さない（決定 89） */
    @Synchronized
    fun ranking(rule: String): List<ChallengeScore> = scores.filter { it.rule == rule && it.counts }
        .sortedWith(compareByDescending<ChallengeScore> { it.height }.thenBy { it.createdAt }.thenBy { it.runId })

    fun rank(score: ChallengeScore): Int = rankOf(score.height, ranking(score.rule))

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** [ranking] の中での [height] の順位。同じ高さは同じ順位（1 + 自分より高い記録の数。「みんな」の順位と同じ決め方） */
fun rankOf(height: Double, ranking: List<ChallengeScore>): Int = 1 + ranking.count { it.height > height }
