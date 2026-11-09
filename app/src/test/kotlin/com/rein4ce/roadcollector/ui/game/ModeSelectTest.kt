package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.data.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 積むタブで、モードを選ぶ画面と遊ぶ画面のどちらを出すか */
class ModeSelectTest {

    private fun request(id: Long, started: Boolean = false) = GameSession.StartRequest(id, listOf("p1"), started)

    @Test
    fun `はじめはモードを選ぶ画面`() {
        assertNull(shownMode(chosen = null, request = null, jumpedFor = null))
    }

    @Test
    fun `選んだモードを出す`() {
        assertEquals(PlayMode.NORMAL, shownMode(chosen = PlayMode.NORMAL, request = null, jumpedFor = null))
    }

    @Test
    fun `コレクションで選んだパーツの開始要求が来たら、選ぶ画面を飛ばして通常`() {
        assertEquals(PlayMode.NORMAL, shownMode(chosen = null, request = request(1), jumpedFor = null))
    }

    @Test
    fun `一度飛んだ要求では、戻ったあと選ぶ画面を出す（パーツが消えて始められない要求でも）`() {
        assertNull(shownMode(chosen = null, request = request(1), jumpedFor = 1))
    }

    @Test
    fun `前に始めた要求では飛ばない`() {
        assertNull(shownMode(chosen = null, request = request(1, started = true), jumpedFor = null))
    }

    @Test
    fun `新しい要求が来たら、また通常へ飛ぶ`() {
        assertEquals(PlayMode.NORMAL, shownMode(chosen = null, request = request(2), jumpedFor = 1))
    }
}
