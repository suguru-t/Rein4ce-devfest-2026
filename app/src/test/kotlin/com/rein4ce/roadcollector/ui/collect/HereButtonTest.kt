package com.rein4ce.roadcollector.ui.collect

import com.rein4ce.roadcollector.core.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 記録画面の地図の「今いる場所へ戻る」ボタン（#112・決定 70）：どこへ戻すか、どれだけ寄せるか */
class HereButtonTest {
    private val walk = listOf(TrackPoint(35.0, 139.0), TrackPoint(35.001, 139.002))
    private val idle = MapSpot(36.0, 140.0)

    @Test
    fun `while recording it returns to the walker`() {
        assertEquals(MapSpot(35.001, 139.002), hereTarget(walk, recording = true, playingDemo = false, idle = idle))
    }

    @Test
    fun `while a demo walk plays it returns to the end of the played road`() {
        assertEquals(MapSpot(35.001, 139.002), hereTarget(walk, recording = false, playingDemo = true, idle = null))
    }

    @Test
    fun `when not recording it returns to where you are now, even with a finished walk on the map`() {
        assertEquals(idle, hereTarget(walk, recording = false, playingDemo = false, idle = idle))
        assertEquals(idle, hereTarget(emptyList(), recording = false, playingDemo = false, idle = idle))
    }

    @Test
    fun `recording before the first point uses where you are now`() {
        assertEquals(idle, hereTarget(emptyList(), recording = true, playingDemo = false, idle = idle))
    }

    @Test
    fun `nothing to return to without a known place`() {
        assertNull(hereTarget(emptyList(), recording = false, playingDemo = false, idle = null))
        assertNull(hereTarget(walk, recording = false, playingDemo = false, idle = null))
    }

    @Test
    fun `zooms in only when the map is far out`() {
        assertEquals(HERE_ZOOM, hereZoom(10f), 0f)
        assertEquals(HERE_ZOOM, hereZoom(HERE_MIN_ZOOM - 0.1f), 0f)
        assertEquals(HERE_MIN_ZOOM, hereZoom(HERE_MIN_ZOOM), 0f)
        assertEquals(18.5f, hereZoom(18.5f), 0f)
    }
}
