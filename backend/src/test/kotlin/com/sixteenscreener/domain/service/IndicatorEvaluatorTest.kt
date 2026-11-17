package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Indicator
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorLevel.BAD
import com.sixteenscreener.domain.model.IndicatorLevel.GOOD
import com.sixteenscreener.domain.model.IndicatorLevel.NEUTRAL
import com.sixteenscreener.domain.model.IndicatorLevel.WARNING
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private val LEVELS_BY_LETTER = mapOf('G' to GOOD, 'W' to WARNING, 'N' to NEUTRAL, 'B' to BAD)

private fun inSettingsOrder(levels: String): List<Indicator> =
    levels.split(' ').mapIndexed { index, letter -> Indicator(RatioCode.indicators[index], LEVELS_BY_LETTER.getValue(letter.single())) }

private fun List<Indicator>.compact(): String =
    joinToString(" ") { "${it.level.name.first()}${RatioCode.indicators.indexOf(it.ratioCode)}" }

private fun enabledDefaults(vararg codes: RatioCode): List<IndicatorSetting> =
    IndicatorDefaults.all.map { it.setting.copy(enabled = it.ratioCode in codes) }

class IndicatorEvaluatorTest : FunSpec({

    context("display order") {
        withData(
            nameFn = { (input, expected) -> "'$input' is shown as '$expected'" },
            "G" to "G0",
            "B" to "B0",
            "W B" to "W0 B1",
            "B W" to "W1 B0",
            "B W G" to "G2 W1 B0",
            "G G G G G" to "G0 G1 G2 G3 G4",
            "B B" to "B0 B1",
            "B G W G B" to "G1 W2 B0 G3 B4",
            "W G W W" to "G1 W0 W2 W3",
            "G B G B" to "G0 G2 B1 B3",
            "G B B G" to "G0 B1 B2 G3",
            "W W B B B" to "W0 B2 B3 W1 B4",
            "G W W B B B" to "G0 W1 B3 W2 B4 B5",
            "B B B G" to "G3 B0 B1 B2",
            "W W W B" to "W0 W1 B3 W2",
            "B W W G B" to "G3 W1 B0 W2 B4",
            "G N W" to "G0 W2",
            "N N" to "",
        ) { (input, expected) ->
            IndicatorEvaluator.displayOrder(inSettingsOrder(input)).compact() shouldBe expected
        }

        test("no indicators") {
            IndicatorEvaluator.displayOrder(emptyList()).shouldBeEmpty()
        }

        test("a tie of group sizes goes to the group whose next indicator comes first, whatever its level") {
            IndicatorEvaluator.displayOrder(inSettingsOrder("W B B W")).compact() shouldBe "W0 B1 B2 W3"
            IndicatorEvaluator.displayOrder(inSettingsOrder("B W W B")).compact() shouldBe "W1 W2 B0 B3"
        }

        test("a bigger group wins over a group whose next indicator comes first") {
            IndicatorEvaluator.displayOrder(inSettingsOrder("G G B B B")).compact() shouldBe "G0 B2 B3 G1 B4"
        }

        test("with only one group left after the first picks, it fills all free places") {
            IndicatorEvaluator.displayOrder(inSettingsOrder("W G G G G")).compact() shouldBe "G1 G2 W0 G3 G4"
        }
    }

    context("indicators") {
        test("switched on ratios with a value outside the neutral interval make indicators in display order") {
            val ratios = Ratios.of(mapOf(RatioCode.PE to 45.0, RatioCode.ROE to 25.0, RatioCode.ROA to 3.0))

            val indicators = IndicatorEvaluator.indicators(enabledDefaults(RatioCode.PE, RatioCode.ROE, RatioCode.ROA), ratios, 100.0)

            indicators shouldContainExactly listOf(
                Indicator(RatioCode.ROE, GOOD),
                Indicator(RatioCode.ROA, WARNING),
                Indicator(RatioCode.PE, BAD),
            )
            indicators.map { it.name } shouldContainExactly listOf("ROE", "ROA", "P/E")
        }

        test("switched off, missing and neutral ratios make no indicator") {
            val ratios = Ratios.of(mapOf(RatioCode.PE to 20.0, RatioCode.ROE to 25.0, RatioCode.PB to 0.5))

            IndicatorEvaluator.indicators(enabledDefaults(RatioCode.PE, RatioCode.ROA), ratios, null).shouldBeEmpty()
        }

        test("no ratios make no indicators") {
            IndicatorEvaluator.indicators(enabledDefaults(RatioCode.PE), null, 100.0).shouldBeEmpty()
        }

        test("values below 0 take the below zero level") {
            val ratios = Ratios.of(mapOf(RatioCode.ROE to -60.16, RatioCode.BETA to -0.2, RatioCode.DIVIDEND_GROWTH_5Y to -3.0))

            IndicatorEvaluator.indicators(
                enabledDefaults(RatioCode.ROE, RatioCode.BETA, RatioCode.DIVIDEND_GROWTH_5Y),
                ratios,
                null,
            ) shouldContainExactly listOf(Indicator(RatioCode.DIVIDEND_GROWTH_5Y, WARNING), Indicator(RatioCode.ROE, BAD))
        }

        test("values above the scale maximum belong to the last interval") {
            val ratios = Ratios.of(mapOf(RatioCode.ROE to 127.61))

            IndicatorEvaluator.indicators(enabledDefaults(RatioCode.ROE), ratios, null) shouldContainExactly
                listOf(Indicator(RatioCode.ROE, GOOD))
        }

        test("missing P/E with a negative EPS is calculated from the price and is bad") {
            val ratios = Ratios.of(mapOf(RatioCode.EPS to -2.252))

            IndicatorEvaluator.indicators(enabledDefaults(RatioCode.PE), ratios, 13.9) shouldContainExactly
                listOf(Indicator(RatioCode.PE, BAD))
        }

        test("settings order is the order of the settings list") {
            val ratios = Ratios.of(mapOf(RatioCode.PE to 5.0, RatioCode.ROE to 25.0))
            val settings = enabledDefaults(RatioCode.PE, RatioCode.ROE).sortedByDescending { it.ratioCode == RatioCode.ROE }

            IndicatorEvaluator.indicators(settings, ratios, null) shouldContainExactly
                listOf(Indicator(RatioCode.ROE, GOOD), Indicator(RatioCode.PE, GOOD))
        }
    }

    context("values for indicators") {
        test("P/E is price / EPS when the source has no P/E and the EPS is negative") {
            val values = IndicatorEvaluator.valuesForIndicators(Ratios.of(mapOf(RatioCode.EPS to -2.0)), 13.0)

            values.getValue(RatioCode.PE) shouldBe (-6.5 plusOrMinus 1e-9)
            values.getValue(RatioCode.EPS) shouldBe -2.0
        }

        test("P/E of the source is kept") {
            IndicatorEvaluator.valuesForIndicators(Ratios.of(mapOf(RatioCode.PE to 12.0, RatioCode.EPS to -2.0)), 13.0)
                .getValue(RatioCode.PE) shouldBe 12.0
        }

        withData(
            nameFn = { (eps, price) -> "no P/E for EPS $eps and price $price" },
            2.0 to 13.0,
            0.0 to 13.0,
            null to 13.0,
            -2.0 to null,
        ) { (eps, price) ->
            IndicatorEvaluator.valuesForIndicators(Ratios.of(mapOf(RatioCode.EPS to eps)), price)[RatioCode.PE].shouldBeNull()
        }
    }

    test("level names of the compact form") {
        IndicatorLevel.entries.map { it.name.first() }.toSet() shouldBe LEVELS_BY_LETTER.keys
    }
})
