package com.rein4ce.roadcollector.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

class BlockNotesTest {

    /** 再生の速さを、ソ（G5）から何半音かに戻す */
    private fun semitones(rate: Float): Float = (12 * ln(rate.toDouble()) / ln(2.0)).toFloat()

    /** [count] 個並んだ積み木の音を、ソ（G5）から何半音かで */
    private fun word(count: Int): List<Int> = (0 until count).map { semitones(BlockNotes.rate(it, count)).roundToInt() }

    @Test
    fun `each word climbs the scale and ends on the high do`() {
        // ミチツミ：ソ・ラ・シ・ド
        assertEquals(listOf(0, 2, 4, 5), word(4))
        // コレクション：ミ・ファ・ソ・ラ・シ・ド
        assertEquals(listOf(-3, -2, 0, 2, 4, 5), word(6))
        // みんなのタワー：レ・ミ・ファ・ソ・ラ・シ・ド
        assertEquals(listOf(-5, -3, -2, 0, 2, 4, 5), word(7))
    }

    @Test
    fun `the last block is always the high do`() {
        (1..12).forEach { count -> assertEquals("$count 個", 5, word(count).last()) }
    }

    @Test
    fun `notes are exact semitones`() {
        (1..8).forEach { count ->
            (0 until count).forEach { i ->
                val s = semitones(BlockNotes.rate(i, count))
                assertTrue("$count 個の $i 番目: $s", abs(s - s.roundToInt()) < 0.001f)
            }
        }
    }

    @Test
    fun `a word longer than the scale goes around once more to the left`() {
        // 9 個なら、いちばん左は上のド、その右が下のドから始まる
        assertEquals(listOf(5, -7, -5, -3, -2, 0, 2, 4, 5), word(9))
    }

    @Test
    fun `every rate is within what SoundPool can play`() {
        // SoundPool の再生の速さは 0.5〜2
        (1..12).forEach { count ->
            (0 until count).forEach { i -> assertTrue("$count 個の $i 番目", BlockNotes.rate(i, count) in 0.5f..2f) }
        }
    }
}
