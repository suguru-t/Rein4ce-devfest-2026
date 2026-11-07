package com.rein4ce.roadcollector.net

import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.sample.SampleParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 命名 API（POST /parts/name）に送るもの。名前の手がかりは形・距離・日時だけ（decisions.md の 8） */
class NamingRequestTest {

    private val part = SampleParts.byId("sample-l")!!.copy(name = "自分で付けた名前")

    @Test
    fun `the name the user gave is not sent`() {
        val sent = namingRequest(part)
        assertNull(sent.name)
        // 形・距離・日時はそのまま
        assertEquals(part.copy(name = null), sent)
    }

    @Test
    fun `the request has no place in it`() {
        val json = AppJson.encodeToString(Part.serializer(), namingRequest(part))
        assertFalse(json, json.contains("自分で付けた名前"))
        listOf("lat", "lng", "place").forEach { assertFalse("$it in $json", json.contains("\"$it")) }
    }
}
