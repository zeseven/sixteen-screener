package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorScale
import com.sixteenscreener.domain.model.RatioCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class IndicatorDefaultsTest : FunSpec({
    test("every indicator ratio has a default, in settings order") {
        IndicatorDefaults.all.map { it.ratioCode } shouldContainExactly RatioCode.indicators
    }

    test("all defaults are valid") {
        IndicatorDefaults.all.mapNotNull { it.validationError() }.shouldBeEmpty()
    }

    test("only P/E, ROE and ROA are switched on") {
        IndicatorDefaults.all.filter { it.setting.enabled }.map { it.ratioCode } shouldContainExactly
            listOf(RatioCode.PE, RatioCode.ROE, RatioCode.ROA)
    }

    test("values below 0 are bad except for dividend growth, dividend yield and beta") {
        IndicatorDefaults.all.filter { it.setting.belowZeroLevel != IndicatorLevel.BAD }
            .associate { it.ratioCode to it.setting.belowZeroLevel } shouldBe mapOf(
            RatioCode.DIVIDEND_GROWTH_5Y to IndicatorLevel.WARNING,
            RatioCode.DIVIDEND_YIELD to IndicatorLevel.NEUTRAL,
            RatioCode.BETA to IndicatorLevel.NEUTRAL,
        )
    }

    test("P/E default") {
        val pe = IndicatorDefaults.of(RatioCode.PE)

        pe.setting.thresholds shouldContainExactly listOf(15.0, 25.0, 40.0)
        pe.setting.levels shouldContainExactly
            listOf(IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL, IndicatorLevel.WARNING, IndicatorLevel.BAD)
        pe.scale shouldBe IndicatorScale(60.0, 1.0)
    }

    test("current ratio default is good in the middle and neutral above") {
        val currentRatio = IndicatorDefaults.of(RatioCode.CURRENT_RATIO)

        currentRatio.setting.thresholds shouldContainExactly listOf(1.0, 1.5, 3.0)
        currentRatio.setting.levels shouldContainExactly
            listOf(IndicatorLevel.BAD, IndicatorLevel.WARNING, IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL)
        currentRatio.scale shouldBe IndicatorScale(5.0, 0.1)
    }

    test("per share ratios have no default") {
        shouldThrow<NoSuchElementException> { IndicatorDefaults.of(RatioCode.EPS) }
    }
})
