package com.rein4ce.roadcollector.ui.inventory

import com.rein4ce.roadcollector.data.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 積むパーツを「おまかせ」で選ぶ（Issue #44） */
class RandomPickTest {

    private val ids = (1..11).map { "p$it" } // 見本 5 本＋採集 6 本くらい

    @Test
    fun `picks the asked number without duplicates, only from the collection`() {
        for (seed in 0 until 200) {
            for (count in 1..GameSession.MAX_PARTS) {
                val picked = pickRandom(ids, count, Random(seed))
                assertEquals(count, picked.size)
                assertEquals(picked.size, picked.toSet().size)
                assertTrue(ids.containsAll(picked))
            }
        }
    }

    @Test
    fun `when the collection is smaller than asked, every part is picked`() {
        val few = listOf("a", "b", "c")
        val picked = pickRandom(few, 5, Random(1))
        assertEquals(few.toSet(), picked.toSet())
        assertEquals("コレクションが 3 本なので、全部選びました", randomPickNote(picked.size, 5))
        assertEquals("ランダムに 5 本選びました", randomPickNote(5, 5))
    }

    @Test
    fun `the count stays within 1 to the most that can be stacked`() {
        assertEquals(GameSession.MAX_PARTS, pickRandom(ids, 99, Random(2)).size)
        assertEquals(1, pickRandom(ids, 0, Random(3)).size)
        assertTrue(pickRandom(emptyList(), 5, Random(4)).isEmpty())
        // 同じ id が 2 回あっても、1 本としか数えない
        assertEquals(listOf("a"), pickRandom(listOf("a", "a"), 5, Random(5)))
    }

    @Test
    fun `every part can be picked and the order changes`() {
        val seen = HashSet<String>()
        val firsts = HashSet<String>()
        repeat(500) { seed ->
            val picked = pickRandom(ids, 5, Random(seed))
            seen += picked
            firsts += picked.first()
        }
        assertEquals(ids.toSet(), seen)
        assertEquals(ids.toSet(), firsts) // 1 番目（最初に積む）も、どれにもなる
        assertNotEquals(pickRandom(ids, 5, Random(10)), pickRandom(ids, 5, Random(11)))
    }
}
