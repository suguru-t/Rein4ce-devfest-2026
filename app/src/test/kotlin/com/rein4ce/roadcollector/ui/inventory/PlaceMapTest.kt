package com.rein4ce.roadcollector.ui.inventory

import com.rein4ce.roadcollector.core.model.PartPlace
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.pow

/** 拡大表示の背景の地図が、パーツの図と同じ縮尺になるか（PlaceMap.kt の zoomFor） */
class PlaceMapTest {

    /** Google マップの拡大率 [zoom] での 1dp あたりのメートル */
    private fun metersPerDp(lat: Double, zoom: Float): Double =
        40_075_016.686 * cos(Math.toRadians(lat)) / (256.0 * 2.0.pow(zoom.toDouble()))

    @Test
    fun `map scale matches the figure scale`() {
        val place = PartPlace(lat = 35.7496, lng = 139.8049, sizeM = 300.0)
        val figureDp = 300f
        val zoom = zoomFor(place, figureDp)
        // 図は長辺 300 m を 300dp × 98/150 で描く
        val figureMetersPerDp = 300.0 / (figureDp * FIGURE_ROAD_RATIO)
        assertEquals(figureMetersPerDp, metersPerDp(place.lat, zoom), figureMetersPerDp * 1e-4)
    }

    @Test
    fun `a twice longer walk is one zoom level out`() {
        val short = zoomFor(PartPlace(35.75, 139.80, sizeM = 200.0), 300f)
        val long = zoomFor(PartPlace(35.75, 139.80, sizeM = 400.0), 300f)
        assertEquals(1f, short - long, 1e-4f)
    }

    @Test
    fun `the map center moves south by half the logo strip`() {
        val place = PartPlace(lat = 35.7496, lng = 139.8049, sizeM = 300.0)
        val target = cameraTarget(place, figureDp = 300f, belowDp = 28f)
        // 図の 1dp は 300 m ÷ (300dp × 98/150) ≈ 1.53 m。帯の半分 14dp ≈ 21.4 m 南
        val southM = (place.lat - target.latitude) * 110_574.0
        assertEquals(14.0 * 300.0 / (300.0 * 98.0 / 150.0), southM, 1e-3) // 1 mm 以内（図の割合は Float）
        assertEquals(place.lng, target.longitude, 0.0)
    }

    @Test
    fun `the figure leaves 26 of 150 on each side`() {
        assertEquals(98f / 150f, FIGURE_ROAD_RATIO, 1e-6f)
    }
}
