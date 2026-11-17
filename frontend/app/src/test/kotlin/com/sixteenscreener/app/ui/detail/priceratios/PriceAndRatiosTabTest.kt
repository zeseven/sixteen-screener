package com.sixteenscreener.app.ui.detail.priceratios

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.Ratio
import com.sixteenscreener.app.domain.model.RatioUnit
import com.sixteenscreener.app.testing.CompanyData
import com.sixteenscreener.app.testing.MutableClock
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.theme.IndicatorColors
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceAndRatiosTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var indicatorColors: IndicatorColors

    private val ratios = listOf(
        Ratio("PE", "P/E", RatioUnit.RATIO, null),
        Ratio("ROE", "Return on equity", RatioUnit.PERCENT, 18.4),
        Ratio("PB", "P/B", RatioUnit.RATIO, 52.3),
        Ratio("EPS", "EPS", RatioUnit.CURRENCY, 6.42),
        Ratio("DIVIDEND_YIELD", "Dividend yield", RatioUnit.PERCENT, 0.5),
    )

    private fun setContent(indicators: List<Indicator>, darkTheme: Boolean = false) = composeRule.setContent {
        SixteenTheme(darkTheme = darkTheme, dynamicColor = false) {
            indicatorColors = SixteenTheme.indicatorColors
            CompositionLocalProvider(LocalClock provides MutableClock(CompanyData.FETCHED_AT)) {
                PriceAndRatiosTab(CompanyData.priceAndRatios(ratios = ratios, indicators = indicators))
            }
        }
    }

    private fun nodes(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.textColor(): Color {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.color
    }

    private fun colorsOf(tag: String) = ratios.indices.map { nodes(tag)[it].textColor() }

    @Test
    fun `percent ratios get the percent suffix`() {
        setContent(emptyList())

        listOf("P/E", "Return on equity, %", "P/B", "EPS", "Dividend yield, %").forEachIndexed { index, name ->
            nodes(PriceAndRatiosTags.RATIO_NAME)[index].assertTextEquals(name)
        }
    }

    @Test
    fun `ratios with an indicator are drawn in its level color, the others in the default color`() {
        setContent(
            listOf(
                Indicator("ROE", "ROE", IndicatorLevel.GOOD),
                Indicator("PB", "P/B", IndicatorLevel.WARNING),
                Indicator("DIVIDEND_YIELD", "DivY", IndicatorLevel.BAD),
            ),
        )

        val expected = listOf(Color.Black, indicatorColors.good, indicatorColors.warning, Color.Black, indicatorColors.bad)
        assertEquals(expected, colorsOf(PriceAndRatiosTags.RATIO_NAME))
        assertEquals(expected, colorsOf(PriceAndRatiosTags.RATIO_VALUE))
    }

    @Test
    fun `a ratio without a value is colored by its indicator`() {
        setContent(listOf(Indicator("PE", "P/E", IndicatorLevel.BAD)))

        nodes(PriceAndRatiosTags.RATIO_VALUE)[0].assertTextEquals("-")
        assertEquals(indicatorColors.bad, nodes(PriceAndRatiosTags.RATIO_NAME)[0].textColor())
        assertEquals(indicatorColors.bad, nodes(PriceAndRatiosTags.RATIO_VALUE)[0].textColor())
    }

    @Test
    fun `indicator colors follow the dark theme`() {
        setContent(listOf(Indicator("PB", "P/B", IndicatorLevel.WARNING)), darkTheme = true)

        assertEquals(indicatorColors.warning, nodes(PriceAndRatiosTags.RATIO_NAME)[2].textColor())
        assertEquals(Color.White, nodes(PriceAndRatiosTags.RATIO_NAME)[0].textColor())
    }

    @Test
    fun `colored ratios announce the indicator level`() {
        setContent(listOf(Indicator("ROE", "ROE", IndicatorLevel.GOOD)))

        composeRule.onAllNodesWithTag(PriceAndRatiosTags.RATIO)[1]
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "good"))
        composeRule.onAllNodesWithTag(PriceAndRatiosTags.RATIO)[0]
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
