package com.rein4ce.roadcollector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 持ち主の印（端末の id に塩を付けた SHA-256） */
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
}
