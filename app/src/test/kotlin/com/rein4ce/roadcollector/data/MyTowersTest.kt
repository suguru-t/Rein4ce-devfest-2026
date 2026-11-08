package com.rein4ce.roadcollector.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** このスマホから送信したタワーの id（「みんな」の「あなた」の札） */
class MyTowersTest {

    /** SharedPreferences の代わり */
    private class SetStore : MyTowers.Store {
        var saved: Set<String> = emptySet()
        var saves = 0
        override fun load(): Set<String> = saved
        override fun save(ids: Set<String>) {
            saved = ids
            saves++
        }
    }

    @Test
    fun `送信した id を覚えて、立ち上げ直しても残る`() {
        val store = SetStore()
        MyTowers(store).add("t1")
        MyTowers(store).add("t2")
        assertEquals(setOf("t1", "t2"), MyTowers(store).ids.value)
    }

    @Test
    fun `空の id と同じ id は保存しない`() {
        val store = SetStore()
        val mine = MyTowers(store)
        mine.add("")
        mine.add("   ")
        mine.add("t1")
        mine.add("t1")
        assertEquals(setOf("t1"), mine.ids.value)
        assertEquals(1, store.saves)
    }
}
