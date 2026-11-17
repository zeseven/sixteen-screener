package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.RatioCategory
import com.sixteenscreener.app.domain.model.RatioUnit

object IndicatorSettings {
    fun intervals(
        t1: Double = 15.0,
        t2: Double = 25.0,
        t3: Double = 40.0,
        levels: List<IndicatorLevel> = listOf(IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL, IndicatorLevel.WARNING, IndicatorLevel.BAD),
        belowZeroLevel: IndicatorLevel = IndicatorLevel.BAD,
    ) = IndicatorIntervals(listOf(t1, t2, t3), levels, belowZeroLevel)

    fun setting(
        ratioCode: String = "PE",
        name: String = "P/E",
        shortName: String = name,
        unit: RatioUnit = RatioUnit.RATIO,
        category: RatioCategory = RatioCategory.VALUATION,
        scaleMax: Double = 60.0,
        step: Double = 1.0,
        enabled: Boolean = true,
        intervals: IndicatorIntervals = intervals(),
    ) = IndicatorSetting(ratioCode, name, shortName, unit, category, IndicatorScale(scaleMax, step), enabled, intervals)

    val pe = setting()
    val roe = setting(
        ratioCode = "ROE",
        name = "Return on equity",
        shortName = "ROE",
        unit = RatioUnit.PERCENT,
        category = RatioCategory.PROFITABILITY,
        scaleMax = 50.0,
        step = 0.5,
        enabled = false,
        intervals = intervals(5.0, 12.5, 20.0, listOf(IndicatorLevel.BAD, IndicatorLevel.WARNING, IndicatorLevel.NEUTRAL, IndicatorLevel.GOOD)),
    )
    val roa = setting(
        ratioCode = "ROA",
        name = "Return on assets",
        shortName = "ROA",
        unit = RatioUnit.PERCENT,
        category = RatioCategory.PROFITABILITY,
        scaleMax = 30.0,
        step = 0.5,
        intervals = intervals(2.0, 5.0, 10.0, listOf(IndicatorLevel.BAD, IndicatorLevel.WARNING, IndicatorLevel.NEUTRAL, IndicatorLevel.GOOD)),
    )
    val all = listOf(pe, roa, roe)
}
