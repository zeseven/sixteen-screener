package com.sixteenscreener.app.ui.exchanges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.testing.Exchanges.all
import com.sixteenscreener.app.testing.Exchanges.nyse
import com.sixteenscreener.app.testing.Exchanges.tsxVenture
import com.sixteenscreener.app.testing.Exchanges.xetra
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExchangesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var uiState by mutableStateOf(ExchangesUiState())
    private var retries = 0

    private fun setContent(state: ExchangesUiState) {
        uiState = state
        composeRule.setContent {
            SixteenTheme(dynamicColor = false) {
                ExchangesContent(
                    uiState = uiState,
                    onQueryChange = { uiState = uiState.copy(query = it) },
                    onRetry = { retries++ },
                )
            }
        }
    }

    private fun rows() = composeRule.onAllNodesWithTag(ExchangesScreenTags.ROW)

    private fun leftOfText(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().left

    private fun leftOfTag(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().left

    @Test
    fun `table shows a header and one row per exchange`() {
        setContent(ExchangesUiState(exchanges = all))

        composeRule.onNodeWithTag(ExchangesScreenTags.HEADER).assert(isHeading())
        rows().assertCountEquals(all.size)
        composeRule.onNodeWithText("New York Stock Exchange", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(".V", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("America/Toronto", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `header shows the market column between suffix and short name`() {
        setContent(ExchangesUiState(exchanges = listOf(xetra)))

        val suffix = leftOfText("Suffix")
        val market = leftOfText("Market")
        val shortName = leftOfText("Short name")
        val fullName = leftOfText("Full name")

        assertTrue(suffix < market)
        assertTrue(market < shortName)
        assertTrue(shortName < fullName)
    }

    @Test
    fun `row shows the market flag between suffix and short name`() {
        setContent(ExchangesUiState(exchanges = listOf(xetra)))

        val suffix = leftOfText(".DE")
        val flag = leftOfTag(ExchangesScreenTags.FLAG)
        val shortName = leftOfText("XETRA")
        val fullName = leftOfText("Deutsche Boerse Xetra")

        assertTrue(suffix < flag)
        assertTrue(flag < shortName)
        assertTrue(shortName < fullName)
    }

    @Test
    fun `row is announced as one item with the market flag description`() {
        setContent(ExchangesUiState(exchanges = listOf(nyse)))

        rows().assertCountEquals(1)[0]
            .assert(hasText("NYSE"))
            .assert(hasText("XNYS"))
            .assertContentDescriptionEquals("US market")
    }

    @Test
    fun `exchange without a market has no flag`() {
        setContent(ExchangesUiState(exchanges = listOf(tsxVenture)))

        composeRule.onNodeWithTag(ExchangesScreenTags.FLAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `typing filters the rows`() {
        setContent(ExchangesUiState(exchanges = all))

        composeRule.onNodeWithTag(ExchangesScreenTags.QUERY_FIELD).performTextInput("toronto")

        rows().assertCountEquals(1)
        composeRule.onNodeWithText("TSX Venture Exchange", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("New York Stock Exchange", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `clear button resets the filter`() {
        setContent(ExchangesUiState(exchanges = all, query = "toronto"))

        composeRule.onNodeWithContentDescription("Clear filter").performClick()

        assertEquals("", uiState.query)
        rows().assertCountEquals(all.size)
    }

    @Test
    fun `no matching rows says so`() {
        setContent(ExchangesUiState(exchanges = all, query = "tokyo"))

        composeRule.onNodeWithText("No exchanges match").assertExists()
        composeRule.onNodeWithTag(ExchangesScreenTags.TABLE).assertDoesNotExist()
    }

    @Test
    fun `loading shows progress`() {
        setContent(ExchangesUiState(isLoading = true))

        composeRule.onNodeWithTag(ExchangesScreenTags.PROGRESS).assertContentDescriptionEquals("Loading exchanges")
    }

    @Test
    fun `load error shows the message and retries`() {
        setContent(ExchangesUiState(loadError = ApiError.Network(isTimeout = true)))

        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }
}
