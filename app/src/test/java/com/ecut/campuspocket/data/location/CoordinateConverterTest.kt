package com.ecut.campuspocket.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot

class CoordinateConverterTest {

    @Test
    fun `converts Nanchang WGS-84 coordinates into GCJ-02 with expected ~600m shift`() {
        val wgsLat = 28.721300
        val wgsLng = 115.820300
        val (gcjLat, gcjLng) = CoordinateConverter.wgs84ToGcj02(wgsLat, wgsLng)

        // 东偏约 +0.0050 度、南偏约 -0.0032 度，总偏移约 600 米（广兰不再偏移到青岚）。
        assertTrue("GCJ-02 纬度应略小于 WGS-84", gcjLat < wgsLat - 0.002)
        assertTrue("GCJ-02 经度应略大于 WGS-84", gcjLng > wgsLng + 0.003)

        val dLatMeters = (gcjLat - wgsLat) * 111_320.0
        val dLngMeters = (gcjLng - wgsLng) * 111_320.0 * cos(Math.toRadians(wgsLat))
        val offsetMeters = hypot(dLatMeters, dLngMeters)
        assertTrue("南昌偏移应在 450~750m 范围内，实测=$offsetMeters", offsetMeters in 450.0..750.0)
    }

    @Test
    fun `leaves out-of-China coordinates unchanged`() {
        assertFalse(CoordinateConverter.isInChina(37.7749, -122.4194))
        val (lat, lng) = CoordinateConverter.wgs84ToGcj02(37.7749, -122.4194)
        assertEquals(37.7749, lat, 1e-9)
        assertEquals(-122.4194, lng, 1e-9)
    }
}
