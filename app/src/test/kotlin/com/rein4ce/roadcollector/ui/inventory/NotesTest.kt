package com.rein4ce.roadcollector.ui.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

/** 注記を「・」でつなぐ（文字を大きくして折り返すとき、数と単位を分けず、行を「・」で始めない） */
class NotesTest {

    private val nbsp = '\u00A0' // 折り返さない空白（見えない字なので、エスケープで書く）

    @Test
    fun `spaces inside an item do not break`() {
        assertEquals("これまでに集めた道${nbsp}6${nbsp}本", notes("これまでに集めた道 6 本"))
    }

    @Test
    fun `the dot sticks to the item before it`() {
        // 「・」の前は折り返さない空白、後ろはふつうの空白（折り返すならここ）
        assertEquals("これまでに集めた道${nbsp}6${nbsp}本${nbsp}・ 見本${nbsp}5", notes("これまでに集めた道 6 本", "見本 5"))
    }

    @Test
    fun `the narrow card note uses a bare dot`() {
        assertEquals("L=488${nbsp}m・頂点${nbsp}9・11/6", notes("L=488 m", "頂点 9", "11/6", separator = "・"))
    }
}
