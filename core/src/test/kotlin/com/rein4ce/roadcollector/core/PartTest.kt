package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Kind
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartTest {

    private val base = Part(id = "p1", points = listOf(Pt(-0.5f, 0f), Pt(0.5f, 0f)), kind = Kind.L)

    @Test
    fun `blank name is treated as no name`() {
        for (name in listOf(null, "", "  ")) {
            val p = base.copy(name = name)
            assertFalse("name=[$name]", p.hasName)
            assertEquals("name=[$name]", Kind.L.label, p.displayName)
        }
        val named = base.copy(name = "駅前の寄り道")
        assertTrue(named.hasName)
        assertEquals("駅前の寄り道", named.displayName)
    }

    @Test
    fun `computed properties are not written to json`() {
        // hasName と displayName はデータ形式に入らない（サーバや保存ファイルの形は変わらない）
        val json = AppJson.encodeToString(Part.serializer(), base.copy(name = "x"))
        assertFalse(json, "hasName" in json)
        assertFalse(json, "displayName" in json)
    }
}
