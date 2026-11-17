package com.sixteenscreener.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.sixteenscreener.app.domain.model.IndicatorLevel

/**
 * Colors of indicator levels, used for indicator names and the intervals of the settings bar. The yellow is a dark mustard in the
 * light theme so that it stays readable on white and differs from the orange of failed loads. They don't follow the dynamic
 * color scheme.
 */
@Immutable
data class IndicatorColors(
    /** Color of a good value (green). */
    val good: Color,
    /** Color of a value worth a warning (yellow). */
    val warning: Color,
    /** Color of a bad value (red). */
    val bad: Color,
    /** Fill of a neutral interval on the settings bar (grey); neutral values make no indicator. */
    val neutral: Color,
) {
    /** Color of [level]; `null` for levels without an indicator color (neutral and unknown). */
    fun textColorOf(level: IndicatorLevel): Color? = when (level) {
        IndicatorLevel.GOOD -> good
        IndicatorLevel.WARNING -> warning
        IndicatorLevel.BAD -> bad
        IndicatorLevel.NEUTRAL, IndicatorLevel.UNKNOWN -> null
    }

    /** Fill of an interval with [level] on the settings bar. */
    fun fillOf(level: IndicatorLevel): Color = textColorOf(level) ?: neutral
}

internal fun indicatorColors(darkTheme: Boolean) = if (darkTheme) {
    IndicatorColors(good = PriceUpDark, warning = IndicatorWarningDark, bad = PriceDownDark, neutral = IndicatorNeutralDark)
} else {
    IndicatorColors(good = PriceUpLight, warning = IndicatorWarningLight, bad = PriceDownLight, neutral = IndicatorNeutralLight)
}

internal val LocalIndicatorColors = staticCompositionLocalOf {
    IndicatorColors(good = Color.Unspecified, warning = Color.Unspecified, bad = Color.Unspecified, neutral = Color.Unspecified)
}
