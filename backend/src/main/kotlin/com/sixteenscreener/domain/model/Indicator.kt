package com.sixteenscreener.domain.model

/**
 * Level of a ratio value (ADR-071). [NEUTRAL] values make no indicator.
 */
enum class IndicatorLevel {
    GOOD,
    WARNING,
    NEUTRAL,
    BAD,
}

/**
 * A switched on ratio whose value falls into a [IndicatorLevel.GOOD], [IndicatorLevel.WARNING] or
 * [IndicatorLevel.BAD] interval.
 *
 * @property ratioCode ratio of the indicator
 * @property level level of the ratio value
 */
data class Indicator(
    val ratioCode: RatioCode,
    val level: IndicatorLevel,
) {
    /** Short name shown on the company row. */
    val name: String get() = ratioCode.shortName
}

/**
 * Fixed scale of a ratio on the settings page: the bar is drawn from 0 to [scaleMax] and thresholds snap to [step].
 *
 * @property scaleMax right end of the settings bar; values above it belong to the last interval
 * @property step step the thresholds snap to when dragged
 */
data class IndicatorScale(
    val scaleMax: Double,
    val step: Double,
) {
    /** Description of the first problem of this scale, or null when it is valid. */
    fun validationError(): String? = when {
        !scaleMax.isFinite() || scaleMax <= 0 -> "scale maximum must be a positive number, got $scaleMax"
        !step.isFinite() || step <= 0 || step > scaleMax -> "step must be a positive number up to the scale maximum, got $step"
        else -> null
    }
}

/**
 * Indicator setting of one ratio (ADR-071): thresholds `t1 <= t2 <= t3` split `[0, +inf)` into the intervals
 * `[0, t1)`, `[t1, t2)`, `[t2, t3)` and `[t3, +inf)`.
 *
 * @property ratioCode ratio the setting belongs to
 * @property enabled whether the ratio is switched on as an indicator
 * @property thresholds the 3 thresholds in ascending order
 * @property levels level of each of the 4 intervals, in interval order
 * @property belowZeroLevel level of values below 0
 */
data class IndicatorSetting(
    val ratioCode: RatioCode,
    val enabled: Boolean,
    val thresholds: List<Double>,
    val levels: List<IndicatorLevel>,
    val belowZeroLevel: IndicatorLevel,
) {
    /** Level of [value]; lower interval bounds are inclusive, so equal thresholds make an empty interval. */
    fun levelOf(value: Double): IndicatorLevel =
        if (value < 0) belowZeroLevel else levels[thresholds.count { value >= it }]

    /** Description of the first problem of this setting against [scale], or null when it is valid. */
    fun validationError(scale: IndicatorScale): String? = when {
        !ratioCode.isIndicator -> "$ratioCode can't be an indicator"
        thresholds.size != THRESHOLD_COUNT -> "$ratioCode must have $THRESHOLD_COUNT thresholds, got ${thresholds.size}"
        levels.size != LEVEL_COUNT -> "$ratioCode must have $LEVEL_COUNT levels, got ${levels.size}"
        thresholds.any { !it.isFinite() } -> "$ratioCode thresholds must be finite numbers"
        thresholds.zipWithNext().any { (lower, upper) -> lower > upper } -> "$ratioCode thresholds must be in ascending order"
        thresholds.any { it < 0 || it > scale.scaleMax } -> "$ratioCode thresholds must be between 0 and ${scale.scaleMax}"
        else -> null
    }

    companion object {
        /** Number of thresholds of a setting. */
        const val THRESHOLD_COUNT = 3

        /** Number of intervals, and so of levels, of a setting. */
        const val LEVEL_COUNT = THRESHOLD_COUNT + 1
    }
}

/**
 * Default indicator setting of a ratio with its fixed scale (ADR-072).
 *
 * @property setting default setting copied into profiles
 * @property scale fixed scale of the ratio
 */
data class IndicatorDefault(
    val setting: IndicatorSetting,
    val scale: IndicatorScale,
) {
    /** Ratio of the default. */
    val ratioCode: RatioCode get() = setting.ratioCode

    /** Description of the first problem of this default, or null when it is valid. */
    fun validationError(): String? = scale.validationError()?.let { "${setting.ratioCode} $it" } ?: setting.validationError(scale)
}
