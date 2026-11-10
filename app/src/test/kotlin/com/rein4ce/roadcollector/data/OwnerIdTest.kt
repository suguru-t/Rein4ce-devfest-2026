package com.rein4ce.roadcollector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 持ち主の印（はじめて開いたときに決めた値に塩を付けた SHA-256。決定 90） */
class OwnerIdTest {

    @Test
    fun `同じ端末なら同じ印、違う端末なら違う印`() {
        assertEquals(OwnerId.hash("9774d56d682e549c"), OwnerId.hash("9774d56d682e549c"))
        assertNotEquals(OwnerId.hash("9774d56d682e549c"), OwnerId.hash("9774d56d682e549d"))
    }

    @Test
    fun `印は 64 桁の 16 進で、元の id は入っていない`() {
        val id = "9774d56d682e549c"
        val mark = OwnerId.hash(id)
        assertTrue(mark, Regex("^[0-9a-f]{64}$").matches(mark))
        assertFalse(id in mark)
    }

    @Test
    fun `残してある印があれば、それをそのまま使う`() {
        val saved = OwnerId.hash("before")
        val mark = OwnerId.decide(saved, sentBefore = false, legacy = { error("読まない") }, fresh = { error("作らない") })
        assertEquals(saved, mark)
    }

    @Test
    fun `新しく入れたアプリは乱数から作り、入れ直すと別の印になる（決定 90）`() {
        // 前は端末の id から作っていて、同じ端末に入れ直すと、前に送ったタワーが「あなた」として出ていた
        val first = OwnerId.decide(null, sentBefore = false, legacy = { "android-id" }, fresh = { "random-1" })
        val reinstalled = OwnerId.decide(null, sentBefore = false, legacy = { "android-id" }, fresh = { "random-2" })
        assertEquals(OwnerId.hash("random-1"), first)
        assertNotEquals(first, reinstalled)
        assertNotEquals(OwnerId.hash("android-id"), first)
    }

    @Test
    fun `前の版でタワーを送ったことがあれば、前と同じ端末の id からの印のまま（「あなた」が外れない）`() {
        val mark = OwnerId.decide(null, sentBefore = true, legacy = { "android-id" }, fresh = { error("作らない") })
        assertEquals(OwnerId.hash("android-id"), mark)
    }

    @Test
    fun `形の違う値が残っていたら、残していないものとして決め直す`() {
        val mark = OwnerId.decide("broken", sentBefore = false, legacy = { "android-id" }, fresh = { "random-1" })
        assertEquals(OwnerId.hash("random-1"), mark)
    }
}
