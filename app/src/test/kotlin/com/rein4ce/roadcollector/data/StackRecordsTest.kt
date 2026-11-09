package com.rein4ce.roadcollector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 積む画面の、モードごとの自分の記録（モードを選ぶ画面に出す） */
class StackRecordsTest {

    /** SharedPreferences の代わり */
    private class MemoryStore : StackRecords.Store {
        var best: Float? = null
        var saves = 0
        override fun loadNormalBest(): Float? = best
        override fun saveNormalBest(heightM: Float) {
            best = heightM
            saves++
        }
    }

    @Test
    fun `はじめは記録が無い`() {
        assertNull(StackRecords(MemoryStore()).normalBest.value)
    }

    @Test
    fun `高いときだけ最高を更新し、立ち上げ直しても残る`() {
        val store = MemoryStore()
        val records = StackRecords(store)
        assertTrue(records.submitNormal(1.2f))
        assertFalse(records.submitNormal(0.9f))
        assertTrue(records.submitNormal(2.4f))
        assertEquals(2.4f, records.normalBest.value!!, 0f)
        assertEquals(2.4f, StackRecords(store).normalBest.value!!, 0f)
        assertEquals(2, store.saves)
    }

    @Test
    fun `同じ高さを何度渡しても、保存は 1 回`() {
        val store = MemoryStore()
        val records = StackRecords(store)
        records.submitNormal(1.5f)
        records.submitNormal(1.5f)
        assertEquals(1, store.saves)
    }

    @Test
    fun `0 以下と数でない高さは数えない`() {
        val store = MemoryStore()
        val records = StackRecords(store)
        assertFalse(records.submitNormal(0f))
        assertFalse(records.submitNormal(-1f))
        assertFalse(records.submitNormal(Float.NaN))
        assertNull(records.normalBest.value)
        assertEquals(0, store.saves)
    }
}
