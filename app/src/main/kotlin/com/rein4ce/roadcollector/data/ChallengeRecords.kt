package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** IO is supplied by the screen's private AtomicFile. Failed writes never update the in-memory list. */
class ChallengeRecords(private val read: () -> String?, private val write: (String) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true }
    private var scores = read()?.let { json.decodeFromString<List<ChallengeScore>>(it) }.orEmpty()
    @Synchronized fun add(score: ChallengeScore): List<ChallengeScore> {
        require(score.height.isFinite() && score.height >= 0)
        if (scores.none { it.runId == score.runId }) {
            val updated = scores + score
            write(json.encodeToString(updated))
            scores = updated
        }
        return ranking(score.rule)
    }
    @Synchronized fun ranking(rule: String): List<ChallengeScore> = scores.filter { it.rule == rule }
        .sortedWith(compareByDescending<ChallengeScore> { it.height }.thenBy { it.createdAt }.thenBy { it.runId })
    fun rank(score: ChallengeScore): Int = 1 + ranking(score.rule).count { it.height > score.height }
}
