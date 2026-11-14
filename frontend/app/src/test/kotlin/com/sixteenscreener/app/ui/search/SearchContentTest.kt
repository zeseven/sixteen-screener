package com.sixteenscreener.app.ui.search

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.testing.AccessibilityMatchers
import com.sixteenscreener.app.testing.Companies.summary
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apple = summary("AAPL", name = "Apple Inc.", inWatchlist = false)
    private val microsoft = summary("MSFT", name = "Microsoft", inWatchlist = true)

    private val typed = mutableListOf<String>()
    private val added = mutableListOf<CompanySummary>()
    private val opened = mutableListOf<CompanySummary>()
    private val shown = mutableListOf<AddFailedMessage>()

    private fun setContent(uiState: SearchUiState) = composeRule.setContent {
        SixteenTheme(dynamicColor = false) {
            SearchContent(
                uiState = uiState,
                onQueryChange = { typed += it },
                onAdd = { added += it },
                onMessageShown = { shown += it },
                onOpenDetail = { opened += it },
            )
        }
    }

    private fun assertNotice(notice: SearchNotice, text: String) {
        setContent(SearchUiState(query = "app", notice = notice))
        composeRule.onNodeWithTag(SearchScreenTags.NOTICE).assertExists()
        composeRule.onNodeWithText(text).assertExists()
    }

    @Test
    fun `typing is forwarded`() {
        setContent(SearchUiState())

        composeRule.onNodeWithTag(SearchScreenTags.QUERY_FIELD).performTextInput("app")

        assertEquals(listOf("app"), typed)
    }

    @Test
    fun `limit reached notice`() = assertNotice(SearchNotice.LIMIT_REACHED, "Source request limit reached")

    @Test
    fun `source error notice`() = assertNotice(SearchNotice.SOURCE_ERROR, "Source is not available")

    @Test
    fun `nothing found notice`() = assertNotice(SearchNotice.NOTHING_FOUND, "Nothing found")

    @Test
    fun `request failure notice`() = assertNotice(SearchNotice.REQUEST_FAILED, "Cannot reach server. Check connection")

    @Test
    fun `no notice without a status`() {
        setContent(SearchUiState(query = "app", results = listOf(apple)))

        composeRule.onNodeWithTag(SearchScreenTags.NOTICE).assertDoesNotExist()
    }

    @Test
    fun `progress is shown while searching`() {
        setContent(SearchUiState(query = "app", isSearching = true))

        composeRule.onNodeWithTag(SearchScreenTags.PROGRESS).assertExists()
    }

    @Test
    fun `add and open are forwarded and companies in the watchlist cannot be added`() {
        setContent(SearchUiState(query = "app", results = listOf(apple, microsoft)))

        composeRule.onNodeWithContentDescription("Add Apple Inc. to the watchlist").performClick()
        composeRule.onNodeWithText("Apple Inc.").performClick()
        composeRule.onNodeWithContentDescription("Microsoft is in the watchlist").assertIsNotEnabled()

        assertEquals(listOf(apple), added)
        assertEquals(listOf(apple), opened)
    }

    @Test
    fun `add failure snackbar shows the problem detail`() {
        val problem = ProblemDetails(null, "Conflict", 409, "Watchlist is full")
        setContent(SearchUiState(query = "app", results = listOf(apple), message = AddFailedMessage(apple, ApiError.Http(409, problem))))

        composeRule.onNodeWithText("Cannot add AAPL: Watchlist is full").assertExists()
    }

    @Test
    fun `clear button empties the query`() {
        setContent(SearchUiState(query = "app"))

        composeRule.onNodeWithContentDescription("Clear search").performClick()

        assertEquals(listOf(""), typed)
    }

    @Test
    fun `notice is announced as a live region`() {
        setContent(SearchUiState(query = "app", notice = SearchNotice.LIMIT_REACHED))

        composeRule.onNodeWithTag(SearchScreenTags.NOTICE).assert(AccessibilityMatchers.isPoliteLiveRegion)
    }
}
