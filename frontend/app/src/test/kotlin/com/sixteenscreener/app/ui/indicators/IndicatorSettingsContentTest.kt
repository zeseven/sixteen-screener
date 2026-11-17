package com.sixteenscreener.app.ui.indicators

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.RatioCategory
import com.sixteenscreener.app.testing.IndicatorSettings
import com.sixteenscreener.app.ui.component.IntervalBarTags
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class IndicatorSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private var uiState by mutableStateOf(IndicatorSettingsUiState())

    private fun setContent(state: IndicatorSettingsUiState) {
        uiState = state
        composeRule.setContent {
            SixteenTheme(dynamicColor = false) {
                IndicatorSettingsContent(
                    uiState = uiState,
                    onBack = { events += "back" },
                    onRetry = { events += "retry" },
                    onEnabledChange = { code, enabled -> events += "enabled $code $enabled" },
                    onIntervalsChange = { code, intervals -> events += "intervals $code ${intervals.levels}" },
                    onSaveErrorShown = { events += "save error shown" },
                )
            }
        }
    }

    private fun loaded(vararg settings: IndicatorSetting = IndicatorSettings.all.toTypedArray()) =
        IndicatorSettingsUiState(isLoading = false, settings = settings.toList())

    private fun inSetting(code: String, tag: String) =
        composeRule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(IndicatorSettingsTags.setting(code))), useUnmergedTree = true)

    @Test
    fun `loading shows a progress indicator`() {
        setContent(IndicatorSettingsUiState())

        composeRule.onNodeWithTag(IndicatorSettingsTags.PROGRESS).assertContentDescriptionEquals("Loading indicator settings")
    }

    @Test
    fun `a failed load shows the error with a retry`() {
        setContent(IndicatorSettingsUiState(isLoading = false, loadError = ApiError.Network(isTimeout = false)))

        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
        composeRule.onNodeWithTag(IndicatorSettingsTags.RETRY).performClick()

        assertEquals(listOf("retry"), events)
    }

    @Test
    fun `settings are listed under readable category headings in settings order`() {
        setContent(loaded())

        val headers = composeRule.onAllNodesWithTag(IndicatorSettingsTags.CATEGORY, useUnmergedTree = true)
        headers.assertCountEquals(2)
        headers[0].assertTextEquals("Valuation")
        headers[1].assertTextEquals("Profitability")
        composeRule.onAllNodesWithTag(IndicatorSettingsTags.CATEGORY, useUnmergedTree = true).filter(isHeading()).assertCountEquals(2)
        val names = composeRule.onAllNodesWithTag(IndicatorSettingsTags.NAME, useUnmergedTree = true)
        listOf("P/E", "Return on assets, %", "Return on equity, %").forEachIndexed { index, name -> names[index].assertTextEquals(name) }
    }

    @Test
    fun `every category has a readable heading`() {
        val settings = RatioCategory.entries.mapIndexed { index, category ->
            IndicatorSettings.setting(ratioCode = "R$index", category = category)
        }
        setContent(loaded(*settings.toTypedArray()))

        val expected = listOf(
            "Valuation", "Profitability", "Margins", "Growth", "Liquidity", "Leverage", "Efficiency", "Dividends", "Risk", "Per share",
            "Other",
        )
        expected.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun `thresholds are shown with the decimals of the step`() {
        setContent(loaded())

        inSetting("ROE", IndicatorSettingsTags.THRESHOLDS).assertTextEquals("5.0 / 12.5 / 20.0")
        inSetting("PE", IndicatorSettingsTags.THRESHOLDS).assertTextEquals("15 / 25 / 40")
            .assertContentDescriptionEquals("Thresholds 15 / 25 / 40")
    }

    @Test
    fun `the checkbox row shows and toggles the switch`() {
        setContent(loaded())

        inSetting("PE", IndicatorSettingsTags.SETTING).assertIsOn()
        inSetting("ROE", IndicatorSettingsTags.SETTING).assertIsOff().performClick()

        assertEquals(listOf("enabled ROE true"), events)
    }

    @Test
    fun `each setting has an interval bar that reports edits with its ratio code`() {
        setContent(loaded(IndicatorSettings.roe))

        composeRule.onNodeWithTag(IntervalBarTags.interval(1), useUnmergedTree = true)
            .assertContentDescriptionEquals("Return on equity, % from 5.0 to 12.5")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            listOf("intervals ROE ${listOf(IndicatorLevel.BAD, IndicatorLevel.BAD, IndicatorLevel.NEUTRAL, IndicatorLevel.GOOD)}"),
            events,
        )
    }

    @Test
    fun `a failed save is shown in a snackbar once`() {
        setContent(loaded().copy(saveError = ApiError.Http(400, null)))

        composeRule.onNodeWithText("Cannot save indicator settings: Server error (400)").assertExists()
        composeRule.waitUntil(timeoutMillis = 10_000) { "save error shown" in events }
    }

    @Test
    fun `the title is shown and back is forwarded`() {
        setContent(loaded())

        composeRule.onNodeWithText("Indicators").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), events)
    }
}
