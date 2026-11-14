package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.testing.Companies.quote
import com.sixteenscreener.app.testing.Companies.summary
import com.sixteenscreener.app.testing.MutableClock
import com.sixteenscreener.app.ui.theme.PriceColors
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanyRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-14T12:00:00Z")
    private lateinit var priceColors: PriceColors

    private fun setRow(
        company: CompanySummary,
        onClick: () -> Unit = {},
        action: @Composable RowScope.() -> Unit = {},
    ) = composeRule.setContent {
        SixteenTheme(darkTheme = false, dynamicColor = false) {
            priceColors = SixteenTheme.priceColors
            CompositionLocalProvider(LocalClock provides MutableClock(now)) {
                CompanyRow(company = company, onClick = onClick, modifier = Modifier.width(400.dp), action = action)
            }
        }
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun SemanticsNodeInteraction.textColor(): Color = textLayout().layoutInput.style.color

    @Test
    fun `long name is truncated with ellipsis at half of the row width`() {
        setRow(summary(name = "International Business Machines Corporation Holdings Worldwide Limited"))

        val rowWidth = node(CompanyRowTags.ROW).getUnclippedBoundsInRoot().let { it.right - it.left }
        val contentWidth = rowWidth - 32.dp
        val nameBounds = node(CompanyRowTags.NAME).getUnclippedBoundsInRoot()
        val nameWidth = nameBounds.right - nameBounds.left

        assertTrue("name width $nameWidth must not exceed ${contentWidth / 2}", nameWidth <= contentWidth / 2 + 0.5.dp)
        assertTrue(node(CompanyRowTags.NAME).textLayout().isLineEllipsized(0))
    }

    @Test
    fun `short name is not truncated`() {
        setRow(summary(name = "IBM"))
        assertFalse(node(CompanyRowTags.NAME).textLayout().isLineEllipsized(0))
    }

    @Test
    fun `flag follows a short name directly`() {
        setRow(summary(name = "IBM", market = Market.US))

        val nameRight = node(CompanyRowTags.NAME).getUnclippedBoundsInRoot().right
        val flagLeft = node(CompanyRowTags.FLAG).getUnclippedBoundsInRoot().left
        assertTrue("flag at $flagLeft, name ends at $nameRight", abs((flagLeft - nameRight - 4.dp).value) < 0.5f)
        node(CompanyRowTags.FLAG).assertTextEquals("\uD83C\uDDFA\uD83C\uDDF8").assertContentDescriptionEquals("US market")
    }

    @Test
    fun `flag follows a truncated name directly`() {
        setRow(summary(name = "Very Long Company Name That Does Not Fit Into Half Of The Row", market = Market.EU))

        val nameRight = node(CompanyRowTags.NAME).getUnclippedBoundsInRoot().right
        val flagLeft = node(CompanyRowTags.FLAG).getUnclippedBoundsInRoot().left
        assertTrue(abs((flagLeft - nameRight - 4.dp).value) < 0.5f)
        node(CompanyRowTags.FLAG).assertTextEquals("\uD83C\uDDEA\uD83C\uDDFA").assertContentDescriptionEquals("EU market")
    }

    @Test
    fun `no flag without market`() {
        setRow(summary(market = null))

        node(CompanyRowTags.FLAG).assertDoesNotExist()
    }

    @Test
    fun `rising price is green`() {
        setRow(summary(quote = quote(trend = Trend.UP)))

        assertEquals(priceColors.up, node(CompanyRowTags.PRICE).textColor())
        node(CompanyRowTags.PRICE).assertTextEquals("189.84 USD")
            .assertContentDescriptionEquals("Price 189.84 USD, higher than the previous price")
    }

    @Test
    fun `falling price is red`() {
        setRow(summary(quote = quote(trend = Trend.DOWN)))

        assertEquals(priceColors.down, node(CompanyRowTags.PRICE).textColor())
        node(CompanyRowTags.PRICE).assertContentDescriptionEquals("Price 189.84 USD, lower than the previous price")
    }

    @Test
    fun `unchanged price uses the default text color`() {
        setRow(summary(quote = quote(trend = Trend.UNCHANGED)))

        assertEquals(Color.Black, node(CompanyRowTags.PRICE).textColor())
        node(CompanyRowTags.PRICE).assertContentDescriptionEquals("Price 189.84 USD, unchanged")
    }

    @Test
    fun `price and indicator are dashes without quote`() {
        setRow(summary(quote = null))

        node(CompanyRowTags.PRICE).assertTextEquals("-").assertContentDescriptionEquals("No price available")
        node(CompanyRowTags.CACHE_INDICATOR).assertTextEquals("-")
    }

    @Test
    fun `cache indicator is hidden for fresh data`() {
        setRow(summary(quote = quote(fetchedAt = now - Duration.ofMinutes(59))))

        node(CompanyRowTags.CACHE_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun `cache indicator shows today's data older than one hour`() {
        setRow(summary(quote = quote(fetchedAt = now - Duration.ofHours(2))))

        node(CompanyRowTags.CACHE_INDICATOR).assertTextEquals("\uD83D\uDD52" + "1")
            .assertContentDescriptionEquals("Cached earlier today")
    }

    @Test
    fun `cache indicator shows days for older data`() {
        setRow(summary(quote = quote(fetchedAt = now - Duration.ofDays(2))))

        node(CompanyRowTags.CACHE_INDICATOR).assertTextEquals("\uD83D\uDD52" + "3")
            .assertContentDescriptionEquals("Cached 2 days ago")
    }

    @Test
    fun `ticker and exchange are shown`() {
        setRow(summary(symbol = "SAP.DE", exchange = "XETRA"))

        composeRule.onNodeWithText("SAP", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("XETRA", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `row click and action are forwarded`() {
        var rowClicks = 0
        var removed = 0
        setRow(summary(name = "Apple Inc."), onClick = { rowClicks++ }) {
            RemoveFromWatchlistButton(companyName = "Apple Inc.", onClick = { removed++ })
        }

        composeRule.onNodeWithTag(CompanyRowTags.ROW).performClick()
        composeRule.onNodeWithContentDescription("Remove Apple Inc. from the watchlist").performClick()

        assertEquals(1, rowClicks)
        assertEquals(1, removed)
    }

    @Test
    fun `logo has a content description`() {
        setRow(summary(name = "Apple Inc."))

        composeRule.onNodeWithContentDescription("Logo of Apple Inc.", useUnmergedTree = true).assertExists()
    }
}
