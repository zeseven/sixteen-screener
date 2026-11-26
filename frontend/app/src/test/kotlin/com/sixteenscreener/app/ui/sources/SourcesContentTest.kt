package com.sixteenscreener.app.ui.sources

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.LimitInterval
import com.sixteenscreener.app.domain.model.LimitUnit
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceRole
import com.sixteenscreener.app.testing.MutableClock
import com.sixteenscreener.app.testing.Sources.limit
import com.sixteenscreener.app.testing.Sources.outcome
import com.sixteenscreener.app.testing.Sources.source
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))

    private fun setContent(uiState: SourcesUiState) = composeRule.setContent {
        SixteenTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalClock provides clock) {
                SourcesContent(uiState = uiState, onRefresh = {}, onMessageShown = {})
            }
        }
    }

    private fun setSources(vararg sources: Source) = setContent(SourcesUiState(sources = sources.toList()))

    private fun indicator() = composeRule.onNodeWithTag(SourcesScreenTags.CONNECTION_INDICATOR, useUnmergedTree = true)

    private fun boundsOfAll(tag: String): List<DpRect> {
        val nodes = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
        return nodes.fetchSemanticsNodes().indices.map { nodes[it].getUnclippedBoundsInRoot() }
    }

    private fun assertDpEquals(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.5f)

    private fun assertIndicatorsEndAtCardContentEdge() {
        val cards = boundsOfAll(SourcesScreenTags.CARD)
        val indicators = boundsOfAll(SourcesScreenTags.CONNECTION_INDICATOR)
        assertEquals(cards.size, indicators.size)
        cards.zip(indicators).forEach { (card, indicator) -> assertDpEquals(card.right - 16.dp, indicator.right) }
    }

    @Test
    fun `card shows name, capabilities and the default marker`() {
        setSources(source(isDefault = true, capabilities = listOf(Capability.SEARCH, Capability.BALANCE_SHEET, Capability.UNKNOWN)))

        composeRule.onNodeWithText("Finnhub").assertExists()
        composeRule.onNodeWithText("Provides: Search, Balance sheet").assertExists()
        composeRule.onNodeWithTag(SourcesScreenTags.DEFAULT_MARKER, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `non default source has no marker`() {
        setSources(source(isDefault = false))

        composeRule.onNodeWithTag(SourcesScreenTags.DEFAULT_MARKER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `default search source lists the roles it is the default for`() {
        setSources(source(isDefault = true, defaultFor = listOf(SourceRole.SEARCH, SourceRole.US)))

        composeRule.onNodeWithTag(SourcesScreenTags.DEFAULT_FOR, useUnmergedTree = true).assertTextEquals("Default for: Search, US")
        composeRule.onNodeWithTag(SourcesScreenTags.DEFAULT_MARKER, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `international default source without the search role has roles but no default marker`() {
        setSources(source(id = "eodhd", name = "EODHD", isDefault = false, defaultFor = listOf(SourceRole.INTERNATIONAL)))

        composeRule.onNodeWithTag(SourcesScreenTags.DEFAULT_FOR, useUnmergedTree = true).assertTextEquals("Default for: International")
        composeRule.onNodeWithTag(SourcesScreenTags.DEFAULT_MARKER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `source that is the default for no role or only unknown roles shows no roles`() {
        setSources(source(id = "sec-edgar", name = "SEC EDGAR"), source(id = "fmp", name = "FMP", defaultFor = listOf(SourceRole.UNKNOWN)))

        composeRule.onNodeWithTag(SourcesScreenTags.DEFAULT_FOR, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `manual-only source is marked`() {
        setSources(source(id = "eodhd", name = "EODHD", automaticRequests = false))

        composeRule.onNodeWithTag(SourcesScreenTags.MANUAL_ONLY_MARKER, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Manual only").assertExists()
    }

    @Test
    fun `source with automatic requests has no manual-only mark`() {
        setSources(source(automaticRequests = true))

        composeRule.onNodeWithTag(SourcesScreenTags.MANUAL_ONLY_MARKER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `limits are shown for each interval`() {
        setSources(
            source(
                limits = listOf(
                    limit(LimitInterval.MINUTE, limit = 60, used = 12),
                    limit(LimitInterval.HOUR, limit = 500, used = 0),
                    limit(LimitInterval.DAY, limit = 1000, used = 1000),
                    limit(LimitInterval.MONTH, limit = 500, used = 7, unit = LimitUnit.UNIQUE_SYMBOLS),
                ),
            ),
        )

        val texts = composeRule.onAllNodesWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(
            listOf("12 / 60 per minute", "0 / 500 per hour", "1000 / 1000 per day, limit reached", "7 / 500 symbols per month"),
            texts,
        )
    }

    @Test
    fun `symbol limit of one uses the singular unit`() {
        setSources(source(limits = listOf(limit(LimitInterval.MONTH, limit = 1, used = 0, unit = LimitUnit.UNIQUE_SYMBOLS))))

        composeRule.onNodeWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).assertTextEquals("0 / 1 symbol per month")
    }

    @Test
    fun `limit shows its reset time when present`() {
        setSources(source(limits = listOf(limit(LimitInterval.MINUTE, limit = 60, used = 59, resetsAt = Instant.parse("2026-11-14T10:01:00Z")))))

        composeRule.onNodeWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).assertTextEquals("59 / 60 per minute, resets 10:01")
    }

    @Test
    fun `counter without a limit shows only the used count with its interval`() {
        setSources(
            source(
                limits = listOf(
                    limit(LimitInterval.DAY, limit = null, used = 12),
                    limit(LimitInterval.MINUTE, limit = null, used = 0),
                    limit(LimitInterval.MONTH, limit = null, used = 7, unit = LimitUnit.UNIQUE_SYMBOLS),
                    limit(LimitInterval.MONTH, limit = null, used = 1, unit = LimitUnit.UNIQUE_SYMBOLS),
                ),
            ),
        )

        val texts = composeRule.onAllNodesWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(listOf("12 per day", "0 per minute", "7 symbols per month", "1 symbol per month"), texts)
    }

    @Test
    fun `counter without a limit is never reached and shows its reset time`() {
        setSources(source(limits = listOf(limit(LimitInterval.DAY, limit = null, used = 5000, resetsAt = Instant.parse("2026-11-14T10:01:00Z")))))

        composeRule.onNodeWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).assertTextEquals("5000 per day, resets 10:01")
    }

    @Test
    fun `sources are listed in backend order with the last one after the others`() {
        setSources(
            source(id = "finnhub", name = "Finnhub"),
            source(id = "eodhd", name = "EODHD"),
            source(id = "yahoo", name = "Yahoo Finance", limits = listOf(limit(LimitInterval.DAY, limit = null, used = 3))),
        )

        val names = composeRule.onAllNodesWithTag(SourcesScreenTags.NAME, useUnmergedTree = true).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(listOf("Finnhub", "EODHD", "Yahoo Finance"), names)
    }

    @Test
    fun `source without limits says so`() {
        setSources(source(limits = emptyList()))

        composeRule.onNodeWithText("No request limits").assertExists()
    }

    @Test
    fun `total calls are shown after the limits`() {
        setSources(source(limits = listOf(limit(LimitInterval.MINUTE, limit = 60, used = 12)), totalCalls = 1234))

        composeRule.onNodeWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).assertTextEquals("12 / 60 per minute")
        composeRule.onNodeWithTag(SourcesScreenTags.TOTAL_CALLS, useUnmergedTree = true).assertTextEquals("Total: 1234")
    }

    @Test
    fun `source with only a total counter shows the total instead of no limits`() {
        setSources(source(id = "finnhub-logos", name = "Finnhub logos", limits = emptyList(), totalCalls = 1234))

        composeRule.onNodeWithText("Finnhub logos").assertExists()
        composeRule.onNodeWithTag(SourcesScreenTags.TOTAL_CALLS, useUnmergedTree = true).assertTextEquals("Total: 1234")
        composeRule.onNodeWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("No request limits").assertDoesNotExist()
    }

    @Test
    fun `source without a total counter shows no total`() {
        setSources(source(totalCalls = null))

        composeRule.onNodeWithTag(SourcesScreenTags.TOTAL_CALLS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `source with only a total counter keeps its connection indicator`() {
        setSources(source(limits = emptyList(), totalCalls = 0, lastCall = outcome(CallResult.HTTP_ERROR, 404)))

        indicator().assertTextEquals("404").assertContentDescriptionEquals("Last call failed with HTTP status 404")
        composeRule.onNodeWithTag(SourcesScreenTags.TOTAL_CALLS, useUnmergedTree = true).assertTextEquals("Total: 0")
    }

    @Test
    fun `never called source has no connection indicator`() {
        setSources(source(lastCall = null))

        indicator().assertDoesNotExist()
    }

    @Test
    fun `successful last call has no connection indicator`() {
        setSources(source(lastCall = outcome(CallResult.SUCCESS)))

        indicator().assertDoesNotExist()
    }

    @Test
    fun `HTTP error shows the status code`() {
        setSources(source(lastCall = outcome(CallResult.HTTP_ERROR, 429)))

        indicator().assertTextEquals("429").assertContentDescriptionEquals("Last call failed with HTTP status 429")
    }

    @Test
    fun `timeout shows T`() {
        setSources(source(lastCall = outcome(CallResult.TIMEOUT)))

        indicator().assertTextEquals("T").assertContentDescriptionEquals("Last call timed out")
    }

    @Test
    fun `app error shows NPE`() {
        setSources(source(lastCall = outcome(CallResult.APP_ERROR)))

        indicator().assertTextEquals("NPE").assertContentDescriptionEquals("Last call failed with an internal error")
    }

    @Test
    fun `first load error is shown in place of the list`() {
        setContent(SourcesUiState(loadError = ApiError.Network(isTimeout = true)))

        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
    }

    @Test
    fun `source name is a heading`() {
        setSources(source())

        composeRule.onNodeWithTag(SourcesScreenTags.NAME, useUnmergedTree = true).assert(isHeading())
    }

    @Test
    fun `reached limit is stated in the text before the reset time`() {
        setSources(source(limits = listOf(limit(LimitInterval.MINUTE, limit = 60, used = 61, resetsAt = Instant.parse("2026-11-14T10:01:00Z")))))

        composeRule.onNodeWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true)
            .assertTextEquals("61 / 60 per minute, limit reached, resets 10:01")
    }

    @Test
    fun `connection indicators end at the card content edge regardless of name length`() {
        setSources(
            source(id = "fmp", name = "FMP", lastCall = outcome(CallResult.HTTP_ERROR, 404)),
            source(id = "finnhub-logos", name = "Finnhub logos", isDefault = true, lastCall = outcome(CallResult.APP_ERROR)),
            source(id = "eodhd", name = "EODHD", automaticRequests = false, lastCall = outcome(CallResult.TIMEOUT)),
        )

        assertIndicatorsEndAtCardContentEdge()
        val rights = boundsOfAll(SourcesScreenTags.CONNECTION_INDICATOR).map { it.right }
        rights.forEach { assertDpEquals(rights.first(), it) }
    }

    @Test
    fun `markers stay next to the name`() {
        setSources(source(isDefault = true, automaticRequests = false, lastCall = outcome(CallResult.HTTP_ERROR, 404)))

        val name = boundsOfAll(SourcesScreenTags.NAME).single()
        val defaultMarker = boundsOfAll(SourcesScreenTags.DEFAULT_MARKER).single()
        val manualOnlyMarker = boundsOfAll(SourcesScreenTags.MANUAL_ONLY_MARKER).single()
        assertDpEquals(name.right + 8.dp, defaultMarker.left)
        assertDpEquals(defaultMarker.right + 8.dp, manualOnlyMarker.left)
        assertTrue(manualOnlyMarker.right < boundsOfAll(SourcesScreenTags.CONNECTION_INDICATOR).single().left)
    }

    @Test
    fun `very long name with markers keeps the indicator visible at the card content edge`() {
        setSources(
            source(
                name = "A very long source name that does not fit on a single line of the card at all",
                isDefault = true,
                automaticRequests = false,
                lastCall = outcome(CallResult.HTTP_ERROR, 404),
            ),
        )

        indicator().assertIsDisplayed()
        assertIndicatorsEndAtCardContentEdge()
        val indicatorLeft = boundsOfAll(SourcesScreenTags.CONNECTION_INDICATOR).single().left
        assertTrue(boundsOfAll(SourcesScreenTags.MANUAL_ONLY_MARKER).single().right <= indicatorLeft)
        assertTrue(boundsOfAll(SourcesScreenTags.NAME).single().right <= indicatorLeft)
    }
}
