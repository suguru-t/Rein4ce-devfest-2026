package com.rein4ce.roadcollector.net

import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Tower
import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** 送るタワーの、パーツを歩いた時刻を日付までに丸める（誰でも読めるので、何時何分に歩いたかを出さない） */
class TowerWalkTimeTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, tokyo).toInstant().toEpochMilli()

    private val part = SampleParts.byId("sample-bar")!!
    private fun towerWith(vararg walkedAt: Long) = Tower(
        parts = walkedAt.mapIndexed { i, t -> part.copy(id = "p$i", walkedAt = t) },
        placements = walkedAt.indices.map { Placement("p$it", 0f, 0.3f * it, 0f) },
        height = 0.3f,
        createdAt = 0L,
    )

    @Test
    fun `the walk time is cut to midnight of that day in Japan`() {
        val sent = withDayOnlyWalkTimes(towerWith(at(2026, 11, 8, 7, 42), at(2026, 11, 9, 23, 59)))
        assertEquals(at(2026, 11, 8, 0, 0), sent.parts[0].walkedAt)
        assertEquals(at(2026, 11, 9, 0, 0), sent.parts[1].walkedAt)
    }

    @Test
    fun `early morning in Japan stays on the same Japanese day`() {
        // 日本の 8:00 は、世界時では前の日の 23:00。日本の日付で丸める
        val sent = withDayOnlyWalkTimes(towerWith(at(2026, 11, 9, 8, 0)))
        assertEquals(at(2026, 11, 9, 0, 0), sent.parts[0].walkedAt)
    }

    @Test
    fun `an unknown walk time stays unknown`() {
        assertEquals(0L, withDayOnlyWalkTimes(towerWith(0L)).parts[0].walkedAt)
    }

    @Test
    fun `nothing else about the parts changes`() {
        val tower = towerWith(at(2026, 11, 8, 7, 42))
        val sent = withDayOnlyWalkTimes(tower)
        assertEquals(tower.parts[0].copy(walkedAt = 0L), sent.parts[0].copy(walkedAt = 0L))
        assertEquals(tower.copy(parts = emptyList()), sent.copy(parts = emptyList()))
    }
}
