package com.sixteenscreener.app.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.RatioUnit
import com.sixteenscreener.app.ui.theme.SixteenTheme

/** Full ratio name as displayed: `, %` is appended to the names of percent ratios. */
@Composable
@ReadOnlyComposable
fun ratioDisplayName(name: String, unit: RatioUnit): String =
    if (unit == RatioUnit.PERCENT) stringResource(R.string.ratio_name_percent, name) else name

/** Spoken name of [level]. */
@StringRes
fun indicatorLevelLabel(level: IndicatorLevel): Int = when (level) {
    IndicatorLevel.GOOD -> R.string.indicator_level_good
    IndicatorLevel.WARNING -> R.string.indicator_level_warning
    IndicatorLevel.NEUTRAL, IndicatorLevel.UNKNOWN -> R.string.indicator_level_neutral
    IndicatorLevel.BAD -> R.string.indicator_level_bad
}

/** Text color of a value with an indicator of [level]; the default text color without one. */
@Composable
@ReadOnlyComposable
fun indicatorTextColor(level: IndicatorLevel?): Color =
    level?.let { SixteenTheme.indicatorColors.textColorOf(it) } ?: SixteenTheme.priceColors.unchanged
