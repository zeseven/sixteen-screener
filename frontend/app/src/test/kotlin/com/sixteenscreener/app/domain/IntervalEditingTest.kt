package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import com.sixteenscreener.app.testing.IndicatorSettings.intervals
import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalEditingTest {

    private val scale = IndicatorScale(max = 60.0, step = 1.0)

    @Test
    fun `snap rounds to the nearest multiple of the step without floating point noise`() {
        assertEquals(25.0, IntervalEditing.snap(24.6, 1.0), 0.0)
        assertEquals(24.0, IntervalEditing.snap(24.4, 1.0), 0.0)
        assertEquals(0.3, IntervalEditing.snap(0.29, 0.1), 0.0)
        assertEquals(12.5, IntervalEditing.snap(12.4, 0.5), 0.0)
        assertEquals(0.0, IntervalEditing.snap(0.2, 1.0), 0.0)
    }

    @Test
    fun `snap keeps the value for a non-positive step`() {
        assertEquals(2.37, IntervalEditing.snap(2.37, 0.0), 0.0)
        assertEquals(2.37, IntervalEditing.snap(2.37, -1.0), 0.0)
    }

    @Test
    fun `step up and down move to the next multiple of the step`() {
        assertEquals(26.0, IntervalEditing.stepUp(25.0, 1.0), 0.0)
        assertEquals(24.0, IntervalEditing.stepDown(25.0, 1.0), 0.0)
        assertEquals(3.0, IntervalEditing.stepUp(2.25, 1.0), 0.0)
        assertEquals(2.0, IntervalEditing.stepDown(2.25, 1.0), 0.0)
        assertEquals(0.4, IntervalEditing.stepUp(0.3, 0.1), 0.0)
        assertEquals(0.2, IntervalEditing.stepDown(0.3, 0.1), 0.0)
    }

    @Test
    fun `threshold bounds are the neighbours, 0 and the scale maximum`() {
        val intervals = intervals(15.0, 25.0, 40.0)

        assertEquals(0.0..25.0, IntervalEditing.thresholdBounds(intervals, 0, scale))
        assertEquals(15.0..40.0, IntervalEditing.thresholdBounds(intervals, 1, scale))
        assertEquals(25.0..60.0, IntervalEditing.thresholdBounds(intervals, 2, scale))
    }

    @Test
    fun `threshold bounds keep the order for a neighbour above the scale maximum`() {
        assertEquals(15.0..60.0, IntervalEditing.thresholdBounds(intervals(15.0, 25.0, 80.0), 1, scale))
        assertEquals(70.0..70.0, IntervalEditing.thresholdBounds(intervals(15.0, 70.0, 80.0), 2, scale))
    }

    @Test
    fun `a moved threshold is clamped to its neighbours and the scale`() {
        val intervals = intervals(15.0, 25.0, 40.0)

        assertEquals(listOf(15.0, 40.0, 40.0), IntervalEditing.withThreshold(intervals, 1, 55.0, scale).thresholds)
        assertEquals(listOf(15.0, 15.0, 40.0), IntervalEditing.withThreshold(intervals, 1, 3.0, scale).thresholds)
        assertEquals(listOf(0.0, 25.0, 40.0), IntervalEditing.withThreshold(intervals, 0, -5.0, scale).thresholds)
        assertEquals(listOf(15.0, 25.0, 60.0), IntervalEditing.withThreshold(intervals, 2, 99.0, scale).thresholds)
    }

    @Test
    fun `a typed threshold keeps its precision while a dragged one snaps`() {
        val intervals = intervals(15.0, 25.0, 40.0)

        assertEquals(22.37, IntervalEditing.withThreshold(intervals, 1, 22.37, scale).thresholds[1], 0.0)
        assertEquals(22.0, IntervalEditing.withDraggedThreshold(intervals, 1, 22.37, scale).thresholds[1], 0.0)
    }

    @Test
    fun `a dragged threshold meets an unsnapped neighbour exactly`() {
        val intervals = intervals(15.0, 25.0, 40.3)

        assertEquals(40.3, IntervalEditing.withDraggedThreshold(intervals, 1, 41.0, scale).thresholds[1], 0.0)
    }

    @Test
    fun `zones use inclusive lower bounds`() {
        val intervals = intervals(15.0, 25.0, 40.0)

        assertEquals(IntervalZone.BelowZero, IntervalEditing.zoneOf(intervals, -0.1))
        assertEquals(IntervalZone.Interval(0), IntervalEditing.zoneOf(intervals, 0.0))
        assertEquals(IntervalZone.Interval(1), IntervalEditing.zoneOf(intervals, 15.0))
        assertEquals(IntervalZone.Interval(2), IntervalEditing.zoneOf(intervals, 39.9))
        assertEquals(IntervalZone.Interval(3), IntervalEditing.zoneOf(intervals, 400.0))
    }

    @Test
    fun `equal thresholds skip the empty interval`() {
        assertEquals(IntervalZone.Interval(2), IntervalEditing.zoneOf(intervals(15.0, 15.0, 40.0), 15.0))
    }

    @Test
    fun `levels cycle good, warning, bad, neutral`() {
        assertEquals(IndicatorLevel.WARNING, IntervalEditing.nextLevel(IndicatorLevel.GOOD))
        assertEquals(IndicatorLevel.BAD, IntervalEditing.nextLevel(IndicatorLevel.WARNING))
        assertEquals(IndicatorLevel.NEUTRAL, IntervalEditing.nextLevel(IndicatorLevel.BAD))
        assertEquals(IndicatorLevel.GOOD, IntervalEditing.nextLevel(IndicatorLevel.NEUTRAL))
        assertEquals(IndicatorLevel.GOOD, IntervalEditing.nextLevel(IndicatorLevel.UNKNOWN))
    }

    @Test
    fun `next level changes only the given zone`() {
        val intervals = intervals()

        val interval = IntervalEditing.withNextLevel(intervals, IntervalZone.Interval(1))
        val belowZero = IntervalEditing.withNextLevel(intervals, IntervalZone.BelowZero)

        assertEquals(listOf(IndicatorLevel.GOOD, IndicatorLevel.GOOD, IndicatorLevel.WARNING, IndicatorLevel.BAD), interval.levels)
        assertEquals(IndicatorLevel.BAD, interval.belowZeroLevel)
        assertEquals(intervals.levels, belowZero.levels)
        assertEquals(IndicatorLevel.NEUTRAL, belowZero.belowZeroLevel)
    }

    @Test
    fun `typed thresholds are parsed with either decimal separator`() {
        assertEquals(ThresholdInput.Valid(22.5), ThresholdInput.parse(" 22.5 ", 15.0..40.0))
        assertEquals(ThresholdInput.Valid(22.5), ThresholdInput.parse("22,5", 15.0..40.0))
        assertEquals(ThresholdInput.Valid(15.0), ThresholdInput.parse("15", 15.0..40.0))
        assertEquals(ThresholdInput.Valid(40.0), ThresholdInput.parse("40", 15.0..40.0))
    }

    @Test
    fun `typed thresholds outside the bounds or not numbers are rejected`() {
        assertEquals(ThresholdInput.OutOfRange(15.0..40.0), ThresholdInput.parse("40.01", 15.0..40.0))
        assertEquals(ThresholdInput.OutOfRange(15.0..40.0), ThresholdInput.parse("-1", 15.0..40.0))
        listOf("", "abc", "1.2.3", "NaN", "Infinity").forEach {
            assertEquals(it, ThresholdInput.NotANumber, ThresholdInput.parse(it, 15.0..40.0))
        }
    }
}
