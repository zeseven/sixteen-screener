package com.sixteenscreener.app.ui.component

import com.sixteenscreener.app.domain.IntervalEditing
import com.sixteenscreener.app.domain.IntervalZone
import com.sixteenscreener.app.domain.model.IndicatorIntervals
import kotlin.math.abs

/**
 * Horizontal layout of an indicator settings bar in pixels: the below-zero zone of [belowZeroWidth] at the left edge, a [gap], and
 * the scale from 0 to [scaleMax] over the rest of [width]. Values above [scaleMax] are drawn at the right end.
 */
data class IntervalBarGeometry(
    val width: Float,
    val belowZeroWidth: Float,
    val gap: Float,
    val scaleMax: Double,
) {
    /** X of value 0. */
    val scaleStart: Float get() = belowZeroWidth + gap

    private val scaleWidth: Float get() = (width - scaleStart).coerceAtLeast(0f)

    /** X of [value], clamped to the scale. */
    fun xOf(value: Double): Float {
        val fraction = if (scaleMax > 0.0) (value / scaleMax).coerceIn(0.0, 1.0) else 0.0
        return scaleStart + (fraction * scaleWidth).toFloat()
    }

    /** Value at [x], clamped to `[0, scaleMax]`. */
    fun valueAt(x: Float): Double {
        if (scaleWidth <= 0f) return 0.0
        return ((x - scaleStart) / scaleWidth * scaleMax).coerceIn(0.0, scaleMax)
    }

    /** Zone under [x]: the below-zero zone left of the middle of the gap, otherwise the interval containing [valueAt]. */
    fun zoneAt(x: Float, intervals: IndicatorIntervals): IntervalZone =
        if (x < belowZeroWidth + gap / 2) IntervalZone.BelowZero else IntervalEditing.zoneOf(intervals, valueAt(x))

    /**
     * Index of the threshold marker nearest to [x] within [touchRadius], `null` when none is that close. When several markers
     * coincide, a positive [dragDirection] picks the rightmost of them and a negative one the leftmost, so that they can be pulled
     * apart; without a direction the leftmost is picked.
     */
    fun markerAt(x: Float, intervals: IndicatorIntervals, touchRadius: Float, dragDirection: Float = 0f): Int? {
        val distances = intervals.thresholds.map { abs(xOf(it) - x) }
        val nearest = distances.minOrNull()?.takeIf { it <= touchRadius } ?: return null
        val nearestIndex = distances.indexOf(nearest)
        val nearestX = xOf(intervals.thresholds[nearestIndex])
        val coinciding = intervals.thresholds.indices.filter { abs(xOf(intervals.thresholds[it]) - nearestX) < COINCIDENCE_PX }
        return if (dragDirection > 0f) coinciding.last() else coinciding.first()
    }

    private companion object {
        const val COINCIDENCE_PX = 0.5f
    }
}
