package com.sixteenscreener.app.ui.sources

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.LimitInterval
import com.sixteenscreener.app.domain.model.LimitUnit
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.testing.MutableClock
import com.sixteenscreener.app.testing.Sources.limit
import com.sixteenscreener.app.testing.Sources.outcome
import com.sixteenscreener.app.testing.Sources.source
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.Instant
import org.junit.Assert.assertEquals
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
            listOf("12 / 60 per minute", "0 / 500 per hour", "1000 / 1000 per day", "7 / 500 symbols per month"),
            texts,
        )
    }

    @Test
    fun `limit shows its reset time when present`() {
        setSources(source(limits = listOf(limit(LimitInterval.MINUTE, limit = 60, used = 59, resetsAt = Instant.parse("2026-11-14T10:01:00Z")))))

        composeRule.onNodeWithTag(SourcesScreenTags.LIMIT, useUnmergedTree = true).assertTextEquals("59 / 60 per minute, resets 10:01")
    }

    @Test
    fun `source without limits says so`() {
        setSources(source(limits = emptyList()))

        composeRule.onNodeWithText("No request limits").assertExists()
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
}
