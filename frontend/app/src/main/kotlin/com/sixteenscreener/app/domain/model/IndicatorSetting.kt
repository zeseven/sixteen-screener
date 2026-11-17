package com.sixteenscreener.app.domain.model

/**
 * Intervals of a ratio's indicator: [thresholds] `t1 <= t2 <= t3` split `[0, +inf)` into `[0, t1)`, `[t1, t2)`, `[t2, t3)` and
 * `[t3, +inf)` (lower bounds inclusive), [levels] gives the level of each of them in that order and [belowZeroLevel] the level of
 * values below 0. Equal thresholds make an empty interval.
 */
data class IndicatorIntervals(
    /** Three ordered thresholds. */
    val thresholds: List<Double>,
    /** Four interval levels, left to right. */
    val levels: List<IndicatorLevel>,
    /** Level of values below 0. */
    val belowZeroLevel: IndicatorLevel,
) {
    init {
        require(thresholds.size == THRESHOLD_COUNT) { "$THRESHOLD_COUNT thresholds expected, got ${thresholds.size}" }
        require(levels.size == INTERVAL_COUNT) { "$INTERVAL_COUNT levels expected, got ${levels.size}" }
    }

    companion object {
        const val THRESHOLD_COUNT = 3
        const val INTERVAL_COUNT = 4
    }
}

/**
 * Fixed scale of a ratio's settings bar: the bar runs from 0 to [max]; dragged thresholds snap to multiples of [step].
 */
data class IndicatorScale(
    /** Right end of the bar; values above it belong to the last interval. */
    val max: Double,
    /** Step dragged thresholds snap to. */
    val step: Double,
)

/**
 * Indicator setting of one ratio with the ratio's fixed metadata, as returned by `GET /indicator-settings`.
 */
data class IndicatorSetting(
    /** Ratio code shared across sources, e.g. `PE`. */
    val ratioCode: String,
    /** Full name without the percent suffix, e.g. `Return on equity`. */
    val name: String,
    /** Short name shown on the company row, e.g. `ROE`. */
    val shortName: String,
    /** Unit of the ratio values. */
    val unit: RatioUnit,
    /** Category the setting is grouped under. */
    val category: RatioCategory,
    /** Fixed scale of the settings bar. */
    val scale: IndicatorScale,
    /** Whether the ratio is switched on as an indicator. */
    val enabled: Boolean,
    /** Configured intervals. */
    val intervals: IndicatorIntervals,
)
