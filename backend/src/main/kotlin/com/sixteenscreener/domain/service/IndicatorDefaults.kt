package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.IndicatorDefault
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorLevel.BAD
import com.sixteenscreener.domain.model.IndicatorLevel.GOOD
import com.sixteenscreener.domain.model.IndicatorLevel.NEUTRAL
import com.sixteenscreener.domain.model.IndicatorLevel.WARNING
import com.sixteenscreener.domain.model.IndicatorScale
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.RatioCode

/**
 * Default indicator settings in code (ADR-072): used to create the defaults document when it is missing and for ratios
 * whose stored default is missing or invalid. Only P/E, ROE and ROA are switched on.
 */
object IndicatorDefaults {
    private val LOWER_IS_BETTER = listOf(GOOD, NEUTRAL, WARNING, BAD)
    private val HIGHER_IS_BETTER = listOf(BAD, WARNING, NEUTRAL, GOOD)
    private val GROWTH = listOf(WARNING, NEUTRAL, GOOD, GOOD)
    private val DIVIDEND_GROWTH = listOf(NEUTRAL, NEUTRAL, GOOD, GOOD)
    private val LIQUIDITY = listOf(BAD, WARNING, GOOD, NEUTRAL)
    private val TURNOVER = listOf(WARNING, NEUTRAL, NEUTRAL, GOOD)
    private val DIVIDEND_YIELD = listOf(NEUTRAL, GOOD, WARNING, BAD)

    /** Defaults of all indicator ratios in settings order. */
    val all: List<IndicatorDefault> = listOf(
        default(RatioCode.PE, 15.0, 25.0, 40.0, LOWER_IS_BETTER, scaleMax = 60.0, step = 1.0, enabled = true),
        default(RatioCode.PB, 1.0, 3.0, 5.0, LOWER_IS_BETTER, scaleMax = 10.0, step = 0.1),
        default(RatioCode.PS, 1.0, 4.0, 8.0, LOWER_IS_BETTER, scaleMax = 15.0, step = 0.1),
        default(RatioCode.EV_EBITDA, 8.0, 15.0, 25.0, LOWER_IS_BETTER, scaleMax = 40.0, step = 1.0),
        default(RatioCode.EV_REVENUE, 1.0, 5.0, 10.0, LOWER_IS_BETTER, scaleMax = 20.0, step = 0.1),
        default(RatioCode.PCF, 8.0, 20.0, 30.0, LOWER_IS_BETTER, scaleMax = 50.0, step = 1.0),
        default(RatioCode.PFCF, 10.0, 25.0, 40.0, LOWER_IS_BETTER, scaleMax = 60.0, step = 1.0),
        default(RatioCode.PEG, 1.0, 2.0, 3.0, LOWER_IS_BETTER, scaleMax = 5.0, step = 0.1),
        default(RatioCode.ROE, 5.0, 10.0, 20.0, HIGHER_IS_BETTER, scaleMax = 40.0, step = 1.0, enabled = true),
        default(RatioCode.ROA, 2.0, 5.0, 10.0, HIGHER_IS_BETTER, scaleMax = 20.0, step = 0.5, enabled = true),
        default(RatioCode.ROI, 5.0, 10.0, 20.0, HIGHER_IS_BETTER, scaleMax = 40.0, step = 1.0),
        default(RatioCode.GROSS_MARGIN, 20.0, 30.0, 50.0, HIGHER_IS_BETTER, scaleMax = 100.0, step = 1.0),
        default(RatioCode.OPERATING_MARGIN, 5.0, 10.0, 20.0, HIGHER_IS_BETTER, scaleMax = 50.0, step = 1.0),
        default(RatioCode.PRETAX_MARGIN, 5.0, 10.0, 20.0, HIGHER_IS_BETTER, scaleMax = 50.0, step = 1.0),
        default(RatioCode.NET_MARGIN, 3.0, 8.0, 15.0, HIGHER_IS_BETTER, scaleMax = 40.0, step = 1.0),
        default(RatioCode.REVENUE_GROWTH, 5.0, 15.0, 30.0, GROWTH, scaleMax = 50.0, step = 1.0),
        default(RatioCode.REVENUE_GROWTH_5Y, 3.0, 10.0, 20.0, GROWTH, scaleMax = 40.0, step = 1.0),
        default(RatioCode.EPS_GROWTH, 5.0, 15.0, 30.0, GROWTH, scaleMax = 60.0, step = 1.0),
        default(RatioCode.EPS_GROWTH_5Y, 3.0, 10.0, 20.0, GROWTH, scaleMax = 40.0, step = 1.0),
        default(RatioCode.DIVIDEND_GROWTH_5Y, 2.0, 5.0, 10.0, DIVIDEND_GROWTH, scaleMax = 20.0, step = 0.5, belowZero = WARNING),
        default(RatioCode.CURRENT_RATIO, 1.0, 1.5, 3.0, LIQUIDITY, scaleMax = 5.0, step = 0.1),
        default(RatioCode.QUICK_RATIO, 0.5, 1.0, 2.0, LIQUIDITY, scaleMax = 4.0, step = 0.1),
        default(RatioCode.DEBT_TO_EQUITY, 0.5, 1.0, 2.0, LOWER_IS_BETTER, scaleMax = 4.0, step = 0.1),
        default(RatioCode.LT_DEBT_TO_EQUITY, 0.3, 0.8, 1.5, LOWER_IS_BETTER, scaleMax = 3.0, step = 0.1),
        default(RatioCode.INTEREST_COVERAGE, 1.5, 3.0, 8.0, HIGHER_IS_BETTER, scaleMax = 20.0, step = 0.5),
        default(RatioCode.ASSET_TURNOVER, 0.3, 0.5, 1.0, TURNOVER, scaleMax = 3.0, step = 0.1),
        default(RatioCode.INVENTORY_TURNOVER, 2.0, 4.0, 8.0, TURNOVER, scaleMax = 20.0, step = 0.5),
        default(RatioCode.RECEIVABLES_TURNOVER, 4.0, 6.0, 10.0, TURNOVER, scaleMax = 20.0, step = 0.5),
        default(RatioCode.DIVIDEND_YIELD, 2.0, 6.0, 10.0, DIVIDEND_YIELD, scaleMax = 15.0, step = 0.1, belowZero = NEUTRAL),
        default(RatioCode.PAYOUT_RATIO, 30.0, 60.0, 90.0, LOWER_IS_BETTER, scaleMax = 150.0, step = 1.0),
        default(RatioCode.BETA, 0.8, 1.3, 2.0, LOWER_IS_BETTER, scaleMax = 3.0, step = 0.1, belowZero = NEUTRAL),
    )

    /** Default of [code]; throws [NoSuchElementException] for a ratio that can't be an indicator. */
    fun of(code: RatioCode): IndicatorDefault = byCode.getValue(code)

    private val byCode: Map<RatioCode, IndicatorDefault> = all.associateBy { it.ratioCode }

    private fun default(
        code: RatioCode,
        t1: Double,
        t2: Double,
        t3: Double,
        levels: List<IndicatorLevel>,
        scaleMax: Double,
        step: Double,
        belowZero: IndicatorLevel = BAD,
        enabled: Boolean = false,
    ) = IndicatorDefault(
        setting = IndicatorSetting(code, enabled, listOf(t1, t2, t3), levels, belowZero),
        scale = IndicatorScale(scaleMax, step),
    )
}
