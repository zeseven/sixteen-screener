package com.sixteenscreener.domain.model

import com.sixteenscreener.domain.model.IndicatorLevel.BAD
import com.sixteenscreener.domain.model.IndicatorLevel.GOOD
import com.sixteenscreener.domain.model.IndicatorLevel.NEUTRAL
import com.sixteenscreener.domain.model.IndicatorLevel.WARNING
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun setting(
    thresholds: List<Double> = listOf(15.0, 25.0, 40.0),
    levels: List<IndicatorLevel> = listOf(GOOD, NEUTRAL, WARNING, BAD),
    belowZero: IndicatorLevel = BAD,
    code: RatioCode = RatioCode.PE,
) = IndicatorSetting(code, enabled = true, thresholds = thresholds, levels = levels, belowZeroLevel = belowZero)

private val scale = IndicatorScale(scaleMax = 60.0, step = 1.0)

class IndicatorSettingTest : FunSpec({
    context("levelOf") {
        withData(
            nameFn = { (value, level) -> "$value is $level" },
            -0.01 to BAD,
            -1000.0 to BAD,
            0.0 to GOOD,
            14.99 to GOOD,
            15.0 to NEUTRAL,
            24.99 to NEUTRAL,
            25.0 to WARNING,
            40.0 to BAD,
            60.0 to BAD,
            1000.0 to BAD,
        ) { (value, level) ->
            setting().levelOf(value) shouldBe level
        }

        test("below zero level is configurable") {
            setting(belowZero = NEUTRAL).levelOf(-5.0) shouldBe NEUTRAL
        }

        test("negative zero is not below zero") {
            setting().levelOf(-0.0) shouldBe GOOD
        }

        test("equal thresholds make empty intervals") {
            val twoEqual = setting(thresholds = listOf(10.0, 10.0, 20.0), levels = listOf(GOOD, WARNING, NEUTRAL, BAD))

            twoEqual.levelOf(9.99) shouldBe GOOD
            twoEqual.levelOf(10.0) shouldBe NEUTRAL
            twoEqual.levelOf(20.0) shouldBe BAD
        }

        test("thresholds at 0 skip the first intervals") {
            val allZero = setting(thresholds = listOf(0.0, 0.0, 0.0), levels = listOf(GOOD, WARNING, NEUTRAL, BAD))

            allZero.levelOf(0.0) shouldBe BAD
            allZero.levelOf(5.0) shouldBe BAD
        }
    }

    context("validationError") {
        test("valid setting has no error") {
            setting().validationError(scale).shouldBeNull()
            setting(thresholds = listOf(0.0, 0.0, 60.0)).validationError(scale).shouldBeNull()
        }

        withData(
            nameFn = { it.first },
            "per share ratio" to setting(code = RatioCode.EPS),
            "2 thresholds" to setting(thresholds = listOf(1.0, 2.0)),
            "4 thresholds" to setting(thresholds = listOf(1.0, 2.0, 3.0, 4.0)),
            "3 levels" to setting(levels = listOf(GOOD, NEUTRAL, BAD)),
            "5 levels" to setting(levels = listOf(GOOD, NEUTRAL, BAD, BAD, BAD)),
            "unordered thresholds" to setting(thresholds = listOf(10.0, 5.0, 20.0)),
            "negative threshold" to setting(thresholds = listOf(-1.0, 5.0, 20.0)),
            "threshold above the scale maximum" to setting(thresholds = listOf(1.0, 5.0, 60.5)),
            "NaN threshold" to setting(thresholds = listOf(1.0, Double.NaN, 20.0)),
            "infinite threshold" to setting(thresholds = listOf(1.0, 5.0, Double.POSITIVE_INFINITY)),
        ) { (_, invalid) ->
            invalid.validationError(scale).shouldNotBeNull() shouldContain invalid.ratioCode.name
        }
    }

    context("scale validation") {
        test("valid scale") {
            IndicatorScale(60.0, 1.0).validationError().shouldBeNull()
            IndicatorScale(1.0, 1.0).validationError().shouldBeNull()
        }

        withData(
            nameFn = { it.toString() },
            IndicatorScale(0.0, 1.0),
            IndicatorScale(-5.0, 1.0),
            IndicatorScale(Double.NaN, 1.0),
            IndicatorScale(Double.POSITIVE_INFINITY, 1.0),
            IndicatorScale(10.0, 0.0),
            IndicatorScale(10.0, -1.0),
            IndicatorScale(10.0, 11.0),
            IndicatorScale(10.0, Double.NaN),
        ) { invalid ->
            invalid.validationError().shouldNotBeNull()
        }

        test("default with an invalid scale names its ratio") {
            IndicatorDefault(setting(), IndicatorScale(0.0, 1.0)).validationError().shouldNotBeNull() shouldContain "PE"
        }

        test("default with thresholds above its scale is invalid") {
            IndicatorDefault(setting(), IndicatorScale(30.0, 1.0)).validationError().shouldNotBeNull()
        }
    }

    test("indicator name is the short name") {
        Indicator(RatioCode.EV_EBITDA, GOOD).name shouldBe "EV/EB"
    }
})
