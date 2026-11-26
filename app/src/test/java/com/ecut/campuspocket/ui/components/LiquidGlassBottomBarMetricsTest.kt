package com.ecut.campuspocket.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassBottomBarMetricsTest {

    @Test
    fun `maps slide positions across center fab gap to nearest tab`() {
        val metrics = computeTabMetrics(
            totalWidth = 1000f,
            tabCount = 4,
            horizontalPadding = 20f,
            centerGap = 200f
        )
        assertNotNull(metrics)
        metrics!!
        // 可用宽度 = 1000 - 40 - 200 = 760，每 Tab 宽 190
        // 中心：0 -> 115, 1 -> 305, gap [400..600], 2 -> 695, 3 -> 885
        assertEquals(115f, metrics.centerOf(0), 0.1f)
        assertEquals(305f, metrics.centerOf(1), 0.1f)
        assertEquals(695f, metrics.centerOf(2), 0.1f)
        assertEquals(885f, metrics.centerOf(3), 0.1f)

        assertTrue(metrics.isInCenterGap(500f))
        assertFalse(metrics.isInCenterGap(305f))

        assertEquals(0, metrics.nearestTabIndex(100f))
        assertEquals(1, metrics.nearestTabIndex(440f))
        assertEquals(2, metrics.nearestTabIndex(560f))
        assertEquals(3, metrics.nearestTabIndex(920f))
    }
}
