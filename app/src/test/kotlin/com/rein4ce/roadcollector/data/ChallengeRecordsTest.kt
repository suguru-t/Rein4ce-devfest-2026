package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.challenge.ChallengeScore
import org.junit.Assert.*
import org.junit.Test

class ChallengeRecordsTest {
    private fun score(id: String, height: Double, rule: String = "manual-180-v1") = ChallengeScore(id, 123, height, 42, rule)
    @Test fun `records survive reload and the same run cannot be saved twice`() {
        var disk: String? = null
        val records = ChallengeRecords({ disk }, { disk = it })
        records.add(score("a", 2.0)); records.add(score("a", 9.0))
        val reloaded = ChallengeRecords({ disk }, { disk = it })
        assertEquals(listOf(score("a", 2.0)), reloaded.ranking("manual-180-v1"))
        reloaded.add(score("b", 3.0))
        assertEquals(2, reloaded.rank(score("a", 2.0)))
    }
    @Test fun `different rules never compete and ties share rank`() {
        val records = ChallengeRecords({ null }, {})
        records.add(score("a", 2.0)); records.add(score("b", 2.0)); records.add(score("c", 5.0, "auto-180-v1"))
        assertEquals(1, records.rank(score("a", 2.0)))
        assertEquals(2, records.ranking("manual-180-v1").size)
    }
    @Test fun `failed write can be retried without phantom success`() {
        var writeFails = true
        val records = ChallengeRecords({ null }, { if (writeFails) error("disk full") })
        try { records.add(score("a", 1.0)); fail("expected write failure") } catch (_: IllegalStateException) { }
        assertTrue(records.ranking("manual-180-v1").isEmpty())
        writeFails = false; assertEquals(1, records.add(score("a", 1.0)).size)
    }
    @Test(expected = IllegalArgumentException::class) fun `nonfinite heights are rejected`() {
        ChallengeRecords({ null }, {}).add(score("a", Double.NaN))
    }
}
