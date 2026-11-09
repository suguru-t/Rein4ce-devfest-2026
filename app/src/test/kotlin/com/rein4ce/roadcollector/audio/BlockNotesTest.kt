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

    @Test
    fun `blocks from the left play do re mi fa so la ti do`() {
        val notes = (0 until 8).map { semitones(BlockNotes.rate(it)) }
        assertEquals(listOf(-7, -5, -3, -2, 0, 2, 4, 5), notes.map { it.roundToInt() })
        notes.forEach { assertTrue("半音にそろっている: $it", abs(it - it.roundToInt()) < 0.001f) }
    }

    @Test
    fun `the notes go up from left to right in the longest word`() {
        // 「みんなのタワー」は 7 個。左から押すと、音が上がっていく
        val rates = (0 until 7).map { BlockNotes.rate(it) }
        assertEquals(rates.sorted(), rates)
    }

    @Test
    fun `a ninth block starts over from the first note`() {
        assertEquals(BlockNotes.rate(0), BlockNotes.rate(8), 0f)
        assertEquals(BlockNotes.rate(1), BlockNotes.rate(9), 0f)
    }

    @Test
    fun `every rate is within what SoundPool can play`() {
        // SoundPool の再生の速さは 0.5〜2
        (0 until 14).forEach { assertTrue("index $it: ${BlockNotes.rate(it)}", BlockNotes.rate(it) in 0.5f..2f) }
    }
}
