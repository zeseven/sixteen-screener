package com.sixteenscreener.app.ui.watchlist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.MarketFilter
import com.sixteenscreener.app.domain.VisibleRows
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.testing.Companies.summary
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchlistContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apple = summary("AAPL", name = "Apple Inc.")

    private val opened = mutableListOf<CompanySummary>()
    private val removed = mutableListOf<CompanySummary>()
    private val undone = mutableListOf<WatchlistMessage.Removed>()
    private val shown = mutableListOf<WatchlistMessage>()
    private val markets = mutableListOf<MarketFilter>()
    private val sectors = mutableListOf<String?>()
    private val visibleRows = mutableListOf<VisibleRows>()
    private var addClicks = 0

    private fun setContent(uiState: WatchlistUiState) = composeRule.setContent {
        SixteenTheme(dynamicColor = false) {
            WatchlistContent(
                uiState = uiState,
                onRefresh = {},
                onMarketSelected = { markets += it },
                onSectorSelected = { sectors += it },
                onRemove = { removed += it },
                onUndo = { undone += it },
                onMessageShown = { shown += it },
                onOpenDetail = { opened += it },
                onAddCompany = { addClicks++ },
                onVisibleRowsChanged = { visibleRows += it },
            )
        }
    }

    private fun loaded(vararg companies: CompanySummary) = WatchlistUiState(
        companies = companies.toList(),
        isLoaded = true,
        hasCompanies = companies.isNotEmpty(),
        sectors = listOf("Energy", "Technology"),
    )

    @Test
    fun `empty watchlist shows the hint to add companies via search`() {
        setContent(loaded())

        composeRule.onNodeWithText("Your watchlist is empty. Add companies via search.").assertExists()
    }

    @Test
    fun `filtered out companies show the no match hint`() {
        setContent(loaded().copy(hasCompanies = true))

        composeRule.onNodeWithText("No companies match the filters").assertExists()
    }

    @Test
    fun `first load error is shown in place of the list`() {
        setContent(WatchlistUiState(loadError = ApiError.Network(isTimeout = false)))

        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
    }

    @Test
    fun `row opens the detail and its button removes the company`() {
        setContent(loaded(apple))

        composeRule.onNodeWithText("Apple Inc.").performClick()
        composeRule.onNodeWithContentDescription("Remove Apple Inc. from the watchlist").performClick()

        assertEquals(listOf(apple), opened)
        assertEquals(listOf(apple), removed)
    }

    @Test
    fun `add button switches to search`() {
        setContent(loaded())

        composeRule.onNodeWithTag(WatchlistScreenTags.ADD_BUTTON).performClick()

        assertEquals(1, addClicks)
    }

    @Test
    fun `removal snackbar offers undo`() {
        val message = WatchlistMessage.Removed(apple, 0)
        setContent(loaded().copy(message = message))

        composeRule.onNodeWithText("AAPL removed").assertExists()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(message), undone)
    }

    @Test
    fun `remove failure snackbar shows the problem`() {
        setContent(loaded(apple).copy(message = WatchlistMessage.RemoveFailed(apple, ApiError.Network(isTimeout = false))))

        composeRule.onNodeWithText("Cannot remove AAPL: Cannot reach server. Check connection").assertExists()
    }

    @Test
    fun `market and sector dropdowns select filters`() {
        setContent(loaded(apple))

        composeRule.onNodeWithText("Market: All").performClick()
        composeRule.onNodeWithText("EU").performClick()
        composeRule.onNodeWithText("Sector: All").performClick()
        composeRule.onNodeWithText("Energy").performClick()

        assertEquals(listOf(MarketFilter.EU), markets)
        assertEquals(listOf<String?>("Energy"), sectors)
    }

    @Test
    fun `visible rows are reported when shown and after scrolling`() {
        val companies = (0 until 40).map { summary("S$it", name = "Company $it") }
        setContent(loaded(*companies.toTypedArray()))
        composeRule.waitForIdle()

        val shown = visibleRows.last()
        assertEquals(0, shown.first)
        assertTrue(shown.last in 1 until companies.lastIndex)

        composeRule.onNodeWithTag(WatchlistScreenTags.LIST).performScrollToIndex(20)
        composeRule.waitForIdle()

        assertEquals(20, visibleRows.last().first)
    }
}
