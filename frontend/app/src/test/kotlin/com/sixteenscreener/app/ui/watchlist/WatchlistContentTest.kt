package com.sixteenscreener.app.ui.watchlist

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.MarketFilter
import com.sixteenscreener.app.domain.WatchlistFilter
import com.sixteenscreener.app.domain.VisibleRows
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.MarketDataLoad
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.testing.Companies.item
import com.sixteenscreener.app.testing.Companies.quote
import com.sixteenscreener.app.ui.component.CompanyRowTags
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class WatchlistContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apple = item("AAPL", name = "Apple Inc.")

    private val opened = mutableListOf<WatchlistItem>()
    private val removed = mutableListOf<WatchlistItem>()
    private val undone = mutableListOf<WatchlistMessage.Removed>()
    private val shown = mutableListOf<WatchlistMessage>()
    private val markets = mutableListOf<MarketFilter>()
    private val sectors = mutableListOf<String?>()
    private val tags = mutableListOf<Tag?>()
    private val visibleRows = mutableListOf<VisibleRows>()
    private var addClicks = 0

    private fun setContent(uiState: WatchlistUiState) = composeRule.setContent {
        SixteenTheme(dynamicColor = false) {
            WatchlistContent(
                uiState = uiState,
                onRefresh = {},
                onMarketSelected = { markets += it },
                onSectorSelected = { sectors += it },
                onTagSelected = { tags += it },
                onRemove = { removed += it },
                onUndo = { undone += it },
                onMessageShown = { shown += it },
                onOpenDetail = { opened += it },
                onAddCompany = { addClicks++ },
                onVisibleRowsChanged = { visibleRows += it },
            )
        }
    }

    private fun loaded(vararg companies: WatchlistItem) = WatchlistUiState(
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
    fun `row opens the detail and has no remove button`() {
        setContent(loaded(apple))

        composeRule.onNodeWithText("Apple Inc.").performClick()

        assertEquals(listOf(apple), opened)
        composeRule.onNodeWithContentDescription("Remove Apple Inc. from the watchlist").assertDoesNotExist()
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `swiping a row to the start removes the company`() {
        setContent(loaded(apple))

        swipeRow { swipeLeft() }

        assertEquals(listOf(apple), removed)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `swiping a row to the end does not remove the company`() {
        setContent(loaded(apple))

        swipeRow { swipeRight() }

        assertTrue(removed.isEmpty())
        rowNode().assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun `swiped row shows the remove background`() {
        setContent(loaded(apple))
        composeRule.mainClock.autoAdvance = false

        rowNode().performTouchInput {
            down(centerRight)
            moveBy(Offset(-width / 3f, 0f))
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(WatchlistScreenTags.SWIPE_BACKGROUND, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `undo after a swipe removal adds the company back`() {
        val uiState = setStatefulContent(
            loaded(apple),
            onRemove = { state, company -> state.value = loaded().copy(message = WatchlistMessage.Removed(company, 0)) },
            onUndo = { state, removal -> state.value = loaded(removal.company) },
        )

        swipeRow { swipeLeft() }
        composeRule.onNodeWithText("Apple Inc. removed").assertExists()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(apple), removed)
        assertEquals(listOf(WatchlistMessage.Removed(apple, 0)), undone)
        assertEquals(listOf(apple), uiState.value.companies)
        rowNode().assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun `failed removal slides the swiped row back`() {
        val failure = WatchlistMessage.RemoveFailed(apple, ApiError.Network(isTimeout = false))
        val uiState = setStatefulContent(loaded(apple))

        swipeRow { swipeLeft() }
        uiState.value = loaded(apple).copy(message = failure)
        composeRule.waitForIdle()

        assertEquals(listOf(apple), removed)
        rowNode().assertLeftPositionInRootIsEqualTo(0.dp)
        composeRule.onNodeWithText("Cannot remove AAPL: Cannot reach server. Check connection").assertExists()
    }

    @Test
    fun `failed removal of another company keeps the swiped row removed`() {
        val sap = item("SAP.DE", name = "SAP SE")
        val uiState = setStatefulContent(loaded(apple))

        swipeRow { swipeLeft() }
        uiState.value = loaded(apple).copy(message = WatchlistMessage.RemoveFailed(sap, ApiError.Network(isTimeout = false)))
        composeRule.waitForIdle()

        val rowLeft = rowNode().getUnclippedBoundsInRoot().left
        assertTrue("row left $rowLeft must stay swiped away", rowLeft < 0.dp)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `remove accessibility action removes the company`() {
        setContent(loaded(apple))

        rowNode().performCustomAccessibilityActionWithLabel("Remove Apple Inc. from the watchlist")

        assertEquals(listOf(apple), removed)
    }

    @Test
    fun `rows show the market data of their symbol and dashes without one`() {
        val sap = item("SAP.DE", name = "SAP SE")
        val appleData = RowMarketData(quote(price = 200.0), lastLoad = MarketDataLoad.Loading)
        setContent(loaded(apple, sap).copy(marketData = mapOf(apple.symbol to appleData)))

        val prices = composeRule.onAllNodesWithTag(CompanyRowTags.PRICE, useUnmergedTree = true)
        prices[0].assertTextEquals("$200.00")
        prices[1].assertTextEquals("-")
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

        composeRule.onNodeWithText("Apple Inc. removed").assertExists()
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
        val companies = (0 until 40).map { item("S$it", name = "Company $it") }
        setContent(loaded(*companies.toTypedArray()))
        composeRule.waitForIdle()

        val shown = visibleRows.last()
        assertEquals(0, shown.first)
        assertTrue(shown.last in 1 until companies.lastIndex)

        composeRule.onNodeWithTag(WatchlistScreenTags.LIST).performScrollToIndex(20)
        composeRule.waitForIdle()

        assertEquals(20, visibleRows.last().first)
    }

    private fun setStatefulContent(
        initial: WatchlistUiState,
        onRemove: (MutableState<WatchlistUiState>, WatchlistItem) -> Unit = { _, _ -> },
        onUndo: (MutableState<WatchlistUiState>, WatchlistMessage.Removed) -> Unit = { _, _ -> },
    ): MutableState<WatchlistUiState> {
        val uiState = mutableStateOf(initial)
        composeRule.setContent {
            SixteenTheme(dynamicColor = false) {
                WatchlistContent(
                    uiState = uiState.value,
                    onRefresh = {},
                    onMarketSelected = {},
                    onSectorSelected = {},
                    onRemove = {
                        removed += it
                        onRemove(uiState, it)
                    },
                    onUndo = {
                        undone += it
                        onUndo(uiState, it)
                    },
                    onMessageShown = {},
                    onOpenDetail = { opened += it },
                    onAddCompany = {},
                )
            }
        }
        return uiState
    }

    private fun rowNode() = composeRule.onNodeWithTag(CompanyRowTags.ROW)

    private fun swipeRow(gesture: TouchInjectionScope.() -> Unit) {
        rowNode().performTouchInput(gesture)
        composeRule.waitForIdle()
    }

    private val king = Tag("chess-king", "Dividend king")
    private val longTag = Tag("rocket-takeoff", "Growth ".repeat(14).trim())

    @Test
    fun `tag dropdown offers all and each tag with its icon and selects a tag`() {
        setContent(loaded(apple).copy(tags = listOf(king, longTag)))

        composeRule.onNodeWithText("Tag: All").performClick()
        composeRule.onNodeWithText("Dividend king").performClick()

        assertEquals(listOf<Tag?>(king), tags)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `long tag text is cut with an ellipsis in the dropdown`() {
        setContent(loaded(apple).copy(tags = listOf(longTag)))

        composeRule.onNodeWithText("Tag: All").performClick()

        assertTrue(composeRule.onNodeWithText(longTag.text).textLayout().isLineEllipsized(0))
    }

    @Test
    fun `selected tag is shown on the chip`() {
        setContent(loaded(apple).copy(tags = listOf(king), filter = WatchlistFilter(tag = king)))

        composeRule.onNodeWithText("Tag: Dividend king").assertExists()
    }

    @Test
    fun `company rows show no tags`() {
        setContent(loaded(apple.copy(tags = listOf(king))))

        composeRule.onNodeWithText("Dividend king").assertDoesNotExist()
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }
}
