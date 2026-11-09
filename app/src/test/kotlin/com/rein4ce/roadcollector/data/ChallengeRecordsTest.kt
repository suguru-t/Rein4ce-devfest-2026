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

    @Test fun `壊れたファイルは退避して空から始め、新しい記録を保存できる`() {
        // 前は読めないまま毎回失敗して、結果の画面から先に進めなかった（#78 のレビュー）
        var disk: String? = "{ broken"
        var quarantined = 0
        val records = ChallengeRecords({ disk }, { disk = it }, quarantine = { quarantined++; disk = null })
        assertEquals(1, quarantined)
        assertTrue(records.ranking("manual-180-v1").isEmpty())
        assertEquals(1, records.add(score("a", 1.5)).size)
        assertEquals(listOf(score("a", 1.5)), ChallengeRecords({ disk }, { disk = it }).ranking("manual-180-v1"))
    }

    @Test fun `決まりの ID を書いていない前の形式は、今の決まりに混ぜずに退避する`() {
        // 前は rule が既定値だったので書き出されず、読むと今の決まりに化けていた
        var quarantined = 0
        val old = """[{"runId":"old","seed":1,"height":9.0,"createdAt":1}]"""
        val records = ChallengeRecords({ old }, {}, quarantine = { quarantined++ })
        assertEquals(1, quarantined)
        assertTrue(records.ranking(com.rein4ce.roadcollector.core.challenge.ChallengeRules.ID).isEmpty())
    }

    @Test fun `保存するときは決まりの ID も書き出す`() {
        var disk: String? = null
        ChallengeRecords({ disk }, { disk = it }).add(score("a", 1.0, "manual-180-v3"))
        assertTrue(disk!!.contains("\"rule\":\"manual-180-v3\""))
    }

    @Test fun `同じ高さは同じ順位`() {
        val ranking = listOf(score("a", 3.0), score("b", 2.0), score("c", 2.0))
        assertEquals(listOf(1, 2, 2), ranking.map { rankOf(it.height, ranking) })
    }
}
