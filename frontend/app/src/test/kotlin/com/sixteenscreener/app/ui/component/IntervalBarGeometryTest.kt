package com.sixteenscreener.app.ui.component

import com.sixteenscreener.app.domain.IntervalZone
import com.sixteenscreener.app.testing.IndicatorSettings.intervals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalBarGeometryTest {

    private val geometry = IntervalBarGeometry(width = 320f, belowZeroWidth = 16f, gap = 4f, scaleMax = 60.0)

    @Test
    fun `values map linearly onto the scale after the below-zero zone`() {
        assertEquals(20f, geometry.xOf(0.0), 0.001f)
        assertEquals(170f, geometry.xOf(30.0), 0.001f)
        assertEquals(320f, geometry.xOf(60.0), 0.001f)
    }

    @Test
    fun `values outside the scale are drawn at its ends`() {
        assertEquals(20f, geometry.xOf(-5.0), 0.001f)
        assertEquals(320f, geometry.xOf(90.0), 0.001f)
    }

    @Test
    fun `positions map back to values clamped to the scale`() {
        assertEquals(30.0, geometry.valueAt(170f), 1e-6)
        assertEquals(0.0, geometry.valueAt(5f), 0.0)
        assertEquals(60.0, geometry.valueAt(400f), 0.0)
        listOf(0.0, 12.5, 41.0).forEach { assertEquals(it, geometry.valueAt(geometry.xOf(it)), 1e-6) }
    }

    @Test
    fun `a zero width bar maps everything to 0`() {
        val empty = IntervalBarGeometry(width = 10f, belowZeroWidth = 16f, gap = 4f, scaleMax = 60.0)

        assertEquals(0.0, empty.valueAt(15f), 0.0)
    }

    @Test
    fun `zones are hit by position`() {
        val intervals = intervals(15.0, 25.0, 40.0)

        assertEquals(IntervalZone.BelowZero, geometry.zoneAt(5f, intervals))
        assertEquals(IntervalZone.BelowZero, geometry.zoneAt(17.9f, intervals))
        assertEquals(IntervalZone.Interval(0), geometry.zoneAt(18.1f, intervals))
        assertEquals(IntervalZone.Interval(1), geometry.zoneAt(geometry.xOf(20.0), intervals))
        assertEquals(IntervalZone.Interval(2), geometry.zoneAt(geometry.xOf(30.0), intervals))
        assertEquals(IntervalZone.Interval(3), geometry.zoneAt(319f, intervals))
    }

    @Test
    fun `the nearest marker within the touch radius is picked`() {
        val intervals = intervals(15.0, 25.0, 40.0)

        assertEquals(0, geometry.markerAt(geometry.xOf(16.0), intervals, touchRadius = 24f))
        assertEquals(1, geometry.markerAt(geometry.xOf(24.0), intervals, touchRadius = 24f))
        assertEquals(2, geometry.markerAt(geometry.xOf(40.0) + 20f, intervals, touchRadius = 24f))
    }

    @Test
    fun `no marker is picked outside the touch radius`() {
        assertNull(geometry.markerAt(geometry.xOf(32.0), intervals(15.0, 25.0, 40.0), touchRadius = 10f))
    }

    @Test
    fun `coinciding markers are picked by drag direction`() {
        val intervals = intervals(25.0, 25.0, 25.0)
        val x = geometry.xOf(25.0)

        assertEquals(2, geometry.markerAt(x, intervals, touchRadius = 24f, dragDirection = 3f))
        assertEquals(0, geometry.markerAt(x, intervals, touchRadius = 24f, dragDirection = -3f))
        assertEquals(0, geometry.markerAt(x, intervals, touchRadius = 24f))
    }

    @Test
    fun `two coinciding markers next to a separate one are picked by direction`() {
        val intervals = intervals(10.0, 30.0, 30.0)
        val x = geometry.xOf(30.0)

        assertEquals(2, geometry.markerAt(x, intervals, touchRadius = 24f, dragDirection = 1f))
        assertEquals(1, geometry.markerAt(x, intervals, touchRadius = 24f, dragDirection = -1f))
    }
}
