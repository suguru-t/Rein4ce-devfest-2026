package com.rein4ce.roadcollector.core

import com.rein4ce.roadcollector.core.geo.ConversionParams
import com.rein4ce.roadcollector.core.geo.TrackToPart
import com.rein4ce.roadcollector.core.model.AppJson
import com.rein4ce.roadcollector.core.model.Part
import com.rein4ce.roadcollector.core.model.TrackPoint
import com.rein4ce.roadcollector.core.sample.SampleParts
import com.rein4ce.roadcollector.core.sample.SampleTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class TrackToPartTest {

    @Test
    fun `sample tracks convert to the expected kinds`() {
        for (track in SampleTracks.all) {
            val r = TrackToPart.convert(track.points, ConversionParams.DEFAULT, id = track.id)
            assertTrue("${track.id} should convert: $r", r is TrackToPart.Result.Success)
            val part = (r as TrackToPart.Result.Success).part
            assertEquals("${track.id} kind", track.expectedKind, part.kind)
            assertTrue("${track.id} vertices <= 12", part.points.size <= ConversionParams.DEFAULT.maxVertices)
        }
    }

    @Test
    fun `normalized part has long side 1 and is centered`() {
        for (part in SampleParts.all) {
            val xs = part.points.map { it.x }
            val ys = part.points.map { it.y }
            val w = xs.max() - xs.min()
            val h = ys.max() - ys.min()
            assertEquals("${part.id} long side", 1.0, max(w, h).toDouble(), 1e-4)
            assertTrue("${part.id} centered x", abs((xs.max() + xs.min()) / 2) < 1e-4)
            assertTrue("${part.id} centered y", abs((ys.max() + ys.min()) / 2) < 1e-4)
        }
    }

    @Test
    fun `too short walk is rejected`() {
        val pts = SampleTracks.bar.points.take(10)
        val r = TrackToPart.convert(pts, ConversionParams.DEFAULT)
        assertTrue(r is TrackToPart.Result.TooShort)
    }

    @Test
    fun `bad accuracy points are dropped`() {
        val noisy = SampleTracks.l.points.mapIndexed { i, p ->
            if (i % 3 == 0) p.copy(lat = p.lat + 0.01, accuracyM = 500f) else p
        }
        val r = TrackToPart.convert(noisy, ConversionParams.DEFAULT)
        assertTrue(r is TrackToPart.Result.Success)
        assertEquals(com.rein4ce.roadcollector.core.model.Kind.L, (r as TrackToPart.Result.Success).part.kind)
    }

    @Test
    fun `part survives json round trip`() {
        val part = SampleParts.all.first()
        val json = AppJson.encodeToString(Part.serializer(), part)
        val back = AppJson.decodeFromString(Part.serializer(), json)
        assertEquals(part, back)
    }

    @Test
    fun `empty track is handled`() {
        val r = TrackToPart.convert(emptyList<TrackPoint>())
        assertTrue(r is TrackToPart.Result.TooFewPoints)
    }
}
