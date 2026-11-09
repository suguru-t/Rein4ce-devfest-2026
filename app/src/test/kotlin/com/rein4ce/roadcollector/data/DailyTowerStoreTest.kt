package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.daily.DailyBlock
import com.rein4ce.roadcollector.core.daily.DailyTower
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Placement
import com.rein4ce.roadcollector.core.model.Pt
import com.rein4ce.roadcollector.core.physics.Grip
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「毎日 1 つずつ積む」の塔の保存（決定 61）。ファイルの代わりにただの変数で読み書きする */
class DailyTowerStoreTest {

    private var file: String? = null
    private var quarantined = 0
    private val store = DailyTowerStore(read = { file }, write = { file = it }, quarantine = { quarantined++; file = null })

    private val tower = DailyTower(
        blocks = listOf(
            DailyBlock(
                part = Part(id = "p1", points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)), kind = Kind.BAR, distanceM = 400, walkedAt = 0L),
                placement = Placement("p1", x = 0.1f, y = 0.05f, angleRad = 0f),
                grip = Grip.DAMP,
                day = "2026-11-10",
            ),
        ),
        height = 0.12,
    )

    @Test
    fun `ファイルが無ければ空の塔`() = runTest {
        assertEquals(DailyTower(), store.load())
        assertEquals(DailyTower(), store.tower.value)
    }

    @Test
    fun `保存した塔を読み直せる`() = runTest {
        store.save(tower)
        assertEquals(tower, store.tower.value)
        val again = DailyTowerStore(read = { file }, write = { file = it }, quarantine = {})
        assertEquals(tower, again.load())
    }

    @Test
    fun `読めないファイルは退避して、空の塔から始める（上書きして消さない）`() = runTest {
        file = "{broken json"
        assertEquals(DailyTower(), store.load())
        assertEquals(1, quarantined)
        assertNull(file)
        // 空から始めたあとも、積めば保存できる
        store.save(tower)
        assertTrue(file!!.contains("\"p1\""))
    }
}
