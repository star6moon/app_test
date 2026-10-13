package com.plantdex.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

class MapZoomTest {

    /** 계산한 확대 수준에서 화면 폭이 실제로 몇 m 인지 되돌려 확인합니다. */
    private fun visibleWidthMeters(latitude: Double, zoom: Float, widthDp: Int) =
        156_543.03392 * cos(latitude * PI / 180) / 2.0.pow(zoom.toDouble()) * widthDp

    @Test
    fun oneKilometerRadiusFillsScreenWidthInSeoul() {
        val zoom = MapZoom.forRadius(37.5665, 1_000.0, 400)
        assertEquals(2_000.0, visibleWidthMeters(37.5665, zoom, 400), 1.0)
        assertTrue(zoom in 14f..16f)
    }

    @Test
    fun widerScreenZoomsInSoTheSameRadiusFits() {
        assertTrue(MapZoom.forRadius(37.5, 1_000.0, 800) > MapZoom.forRadius(37.5, 1_000.0, 400))
    }

    @Test
    fun zoomStaysInMapLimits() {
        assertEquals(21f, MapZoom.forRadius(0.0, 0.01, 400))
        assertEquals(2f, MapZoom.forRadius(0.0, 50_000_000.0, 400))
    }
}
