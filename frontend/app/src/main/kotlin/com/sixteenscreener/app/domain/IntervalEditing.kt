package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import java.math.BigDecimal
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Part of an indicator settings bar that has its own level. */
sealed interface IntervalZone {
    /** Values below 0. */
    data object BelowZero : IntervalZone

    /** Interval [index] (0 to 3) of `[0, t1)`, `[t1, t2)`, `[t2, t3)`, `[t3, +inf)`. */
    data class Interval(val index: Int) : IntervalZone
}

/**
 * Edits of indicator intervals on the settings bar. Thresholds stay within `[0, scale max]` and between their neighbours (equal
 * neighbours are allowed); only dragging snaps to the step, so exact values typed or received keep their precision.
 */
object IntervalEditing {
    private const val STEP_TOLERANCE = 1e-9

    /** [value] rounded to the nearest multiple of [step]; unchanged for a non-positive [step]. */
    fun snap(value: Double, step: Double): Double =
        if (step <= 0.0) value else multipleOf(step, (value / step).roundToLong())

    /** The next multiple of [step] above [value]. */
    fun stepUp(value: Double, step: Double): Double =
        if (step <= 0.0) value else multipleOf(step, floor(value / step + STEP_TOLERANCE).toLong() + 1)

    /** The next multiple of [step] below [value]. */
    fun stepDown(value: Double, step: Double): Double =
        if (step <= 0.0) value else multipleOf(step, ceil(value / step - STEP_TOLERANCE).toLong() - 1)

    /** Values threshold [index] may take: from its left neighbour (or 0) to its right neighbour (or the scale maximum). */
    fun thresholdBounds(intervals: IndicatorIntervals, index: Int, scale: IndicatorScale): ClosedFloatingPointRange<Double> {
        val thresholds = intervals.thresholds
        val lower = thresholds.getOrNull(index - 1)?.coerceAtLeast(0.0) ?: 0.0
        val upper = (thresholds.getOrNull(index + 1) ?: scale.max).coerceAtMost(scale.max).coerceAtLeast(lower)
        return lower..upper
    }

    /** [intervals] with threshold [index] moved to [value], clamped to [thresholdBounds]. */
    fun withThreshold(intervals: IndicatorIntervals, index: Int, value: Double, scale: IndicatorScale): IndicatorIntervals {
        val clamped = value.coerceIn(thresholdBounds(intervals, index, scale))
        return intervals.copy(thresholds = intervals.thresholds.mapIndexed { i, threshold -> if (i == index) clamped else threshold })
    }

    /** [intervals] with threshold [index] dragged to [value]: snapped to the step, then clamped. */
    fun withDraggedThreshold(intervals: IndicatorIntervals, index: Int, value: Double, scale: IndicatorScale): IndicatorIntervals =
        withThreshold(intervals, index, snap(value, scale.step), scale)

    /** Zone a value belongs to; lower bounds are inclusive, so a value equal to a threshold belongs to the interval right of it. */
    fun zoneOf(intervals: IndicatorIntervals, value: Double): IntervalZone =
        if (value < 0.0) IntervalZone.BelowZero else IntervalZone.Interval(intervals.thresholds.count { it <= value })

    /** Level of [zone]. */
    fun levelOf(intervals: IndicatorIntervals, zone: IntervalZone): IndicatorLevel = when (zone) {
        IntervalZone.BelowZero -> intervals.belowZeroLevel
        is IntervalZone.Interval -> intervals.levels[zone.index]
    }

    /** Level after [level] in the cycle `GOOD`, `WARNING`, `BAD`, `NEUTRAL`. */
    fun nextLevel(level: IndicatorLevel): IndicatorLevel = when (level) {
        IndicatorLevel.GOOD -> IndicatorLevel.WARNING
        IndicatorLevel.WARNING -> IndicatorLevel.BAD
        IndicatorLevel.BAD -> IndicatorLevel.NEUTRAL
        IndicatorLevel.NEUTRAL, IndicatorLevel.UNKNOWN -> IndicatorLevel.GOOD
    }

    /** [intervals] with the level of [zone] moved to the next level of the cycle. */
    fun withNextLevel(intervals: IndicatorIntervals, zone: IntervalZone): IndicatorIntervals {
        val next = nextLevel(levelOf(intervals, zone))
        return when (zone) {
            IntervalZone.BelowZero -> intervals.copy(belowZeroLevel = next)
            is IntervalZone.Interval -> intervals.copy(levels = intervals.levels.mapIndexed { i, level -> if (i == zone.index) next else level })
        }
    }

    private fun multipleOf(step: Double, count: Long): Double = BigDecimal.valueOf(step).multiply(BigDecimal.valueOf(count)).toDouble()
}

/** Outcome of parsing a typed threshold. */
sealed interface ThresholdInput {
    data class Valid(val value: Double) : ThresholdInput

    data object NotANumber : ThresholdInput

    /** The number is outside [bounds]. */
    data class OutOfRange(val bounds: ClosedFloatingPointRange<Double>) : ThresholdInput

    companion object {
        /** Parses [text] (a `.` or `,` decimal separator) as a threshold within [bounds]. */
        fun parse(text: String, bounds: ClosedFloatingPointRange<Double>): ThresholdInput {
            val value = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return NotANumber
            return if (value in bounds) Valid(value) else OutOfRange(bounds)
        }
    }
}
