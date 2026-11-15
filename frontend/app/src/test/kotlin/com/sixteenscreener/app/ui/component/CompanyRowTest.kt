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
import com.sixteenscreener.app.domain.model.Company
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.MarketDataLoad
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.testing.Companies.item
import com.sixteenscreener.app.testing.Companies.quote
import com.sixteenscreener.app.testing.Companies.searchItem
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

    private fun loaded(quote: Quote?, status: DataStatus = DataStatus.CACHED) =
        RowMarketData(quote, lastLoad = MarketDataLoad.Loaded(status))

    private fun setRow(
        company: Company = item(),
        marketData: (@Composable RowScope.() -> Unit)? = { WatchlistMarketData(loaded(quote())) },
        darkTheme: Boolean = false,
        onClick: () -> Unit = {},
        action: @Composable RowScope.() -> Unit = {},
    ) = composeRule.setContent {
        SixteenTheme(darkTheme = darkTheme, dynamicColor = false) {
            priceColors = SixteenTheme.priceColors
            CompositionLocalProvider(LocalClock provides MutableClock(now)) {
                CompanyRow(company = company, onClick = onClick, modifier = Modifier.width(400.dp), marketData = marketData, action = action)
            }
        }
    }

    private fun setWatchlistRow(data: RowMarketData?, darkTheme: Boolean = false) =
        setRow(marketData = { WatchlistMarketData(data) }, darkTheme = darkTheme)

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun SemanticsNodeInteraction.textColor(): Color = textLayout().layoutInput.style.color

    @Test
    fun `long name is truncated with ellipsis at half of the row width`() {
        setRow(item(name = "International Business Machines Corporation Holdings Worldwide Limited"))

        val rowWidth = node(CompanyRowTags.ROW).getUnclippedBoundsInRoot().let { it.right - it.left }
        val contentWidth = rowWidth - 32.dp
        val nameBounds = node(CompanyRowTags.NAME).getUnclippedBoundsInRoot()
        val nameWidth = nameBounds.right - nameBounds.left

        assertTrue("name width $nameWidth must not exceed ${contentWidth / 2}", nameWidth <= contentWidth / 2 + 0.5.dp)
        assertTrue(node(CompanyRowTags.NAME).textLayout().isLineEllipsized(0))
    }

    @Test
    fun `short name is not truncated`() {
        setRow(item(name = "IBM"))
        assertFalse(node(CompanyRowTags.NAME).textLayout().isLineEllipsized(0))
    }

    @Test
    fun `flag follows a short name directly`() {
        setRow(item(name = "IBM", market = Market.US))

        val nameRight = node(CompanyRowTags.NAME).getUnclippedBoundsInRoot().right
        val flagLeft = node(CompanyRowTags.FLAG).getUnclippedBoundsInRoot().left
        assertTrue("flag at $flagLeft, name ends at $nameRight", abs((flagLeft - nameRight - 4.dp).value) < 0.5f)
        node(CompanyRowTags.FLAG).assertTextEquals("\uD83C\uDDFA\uD83C\uDDF8").assertContentDescriptionEquals("US market")
    }

    @Test
    fun `flag follows a truncated name directly`() {
        setRow(item(name = "Very Long Company Name That Does Not Fit Into Half Of The Row", market = Market.EU))

        val nameRight = node(CompanyRowTags.NAME).getUnclippedBoundsInRoot().right
        val flagLeft = node(CompanyRowTags.FLAG).getUnclippedBoundsInRoot().left
        assertTrue(abs((flagLeft - nameRight - 4.dp).value) < 0.5f)
        node(CompanyRowTags.FLAG).assertTextEquals("\uD83C\uDDEA\uD83C\uDDFA").assertContentDescriptionEquals("EU market")
    }

    @Test
    fun `no flag without market`() {
        setRow(item(market = null))

        node(CompanyRowTags.FLAG).assertDoesNotExist()
    }

    @Test
    fun `rising price is green`() {
        setWatchlistRow(loaded(quote(trend = Trend.UP)))

        assertEquals(priceColors.up, node(CompanyRowTags.PRICE).textColor())
        node(CompanyRowTags.PRICE).assertTextEquals("189.84 USD")
            .assertContentDescriptionEquals("Price 189.84 USD, higher than the previous price")
    }

    @Test
    fun `falling price is red`() {
        setWatchlistRow(loaded(quote(trend = Trend.DOWN)))

        assertEquals(priceColors.down, node(CompanyRowTags.PRICE).textColor())
        node(CompanyRowTags.PRICE).assertContentDescriptionEquals("Price 189.84 USD, lower than the previous price")
    }

    @Test
    fun `unchanged price uses the default text color`() {
        setWatchlistRow(loaded(quote(trend = Trend.UNCHANGED)))

        assertEquals(Color.Black, node(CompanyRowTags.PRICE).textColor())
        node(CompanyRowTags.PRICE).assertContentDescriptionEquals("Price 189.84 USD, unchanged")
    }

    @Test
    fun `price and indicators are dashes in the default color before any data arrives`() {
        setWatchlistRow(null)

        node(CompanyRowTags.PRICE).assertTextEquals("-").assertContentDescriptionEquals("No price available")
        node(CompanyRowTags.INDICATORS).assertTextEquals("-").assertContentDescriptionEquals("No indicators")
        node(CompanyRowTags.CACHE_INDICATOR).assertDoesNotExist()
        assertEquals(Color.Black, node(CompanyRowTags.PRICE).textColor())
        assertEquals(Color.Black, node(CompanyRowTags.INDICATORS).textColor())
    }

    @Test
    fun `loading row keeps the last price in its trend color`() {
        setWatchlistRow(RowMarketData(quote(trend = Trend.UP), lastLoad = MarketDataLoad.Loading))

        node(CompanyRowTags.PRICE).assertTextEquals("189.84 USD")
        assertEquals(priceColors.up, node(CompanyRowTags.PRICE).textColor())
    }

    @Test
    fun `no data without a quote shows dashes in the default color`() {
        setWatchlistRow(loaded(null, DataStatus.NO_DATA))

        node(CompanyRowTags.PRICE).assertTextEquals("-")
        assertEquals(Color.Black, node(CompanyRowTags.PRICE).textColor())
        assertEquals(Color.Black, node(CompanyRowTags.INDICATORS).textColor())
    }

    @Test
    fun `failed load keeps the last price in orange`() {
        setWatchlistRow(RowMarketData(quote(trend = Trend.DOWN, fetchedAt = now - Duration.ofHours(2)), lastLoad = MarketDataLoad.Failed))

        node(CompanyRowTags.PRICE).assertTextEquals("189.84 USD")
            .assertContentDescriptionEquals("Price 189.84 USD, last update failed")
        node(CompanyRowTags.INDICATORS).assertContentDescriptionEquals("No indicators, last update failed")
        node(CompanyRowTags.CACHE_INDICATOR).assertTextEquals("\uD83D\uDD52" + "1")
        assertEquals(priceColors.loadFailed, node(CompanyRowTags.PRICE).textColor())
        assertEquals(priceColors.loadFailed, node(CompanyRowTags.INDICATORS).textColor())
    }

    @Test
    fun `failed load without a price shows orange dashes`() {
        setWatchlistRow(RowMarketData(lastLoad = MarketDataLoad.Failed))

        node(CompanyRowTags.PRICE).assertTextEquals("-").assertContentDescriptionEquals("No price available, last update failed")
        assertEquals(priceColors.loadFailed, node(CompanyRowTags.PRICE).textColor())
        assertEquals(priceColors.loadFailed, node(CompanyRowTags.INDICATORS).textColor())
    }

    @Test
    fun `limit reached is drawn in orange`() {
        setWatchlistRow(loaded(quote(trend = Trend.UP), DataStatus.LIMIT_REACHED))

        assertEquals(priceColors.loadFailed, node(CompanyRowTags.PRICE).textColor())
    }

    @Test
    fun `failed load uses the dark orange in the dark theme`() {
        setWatchlistRow(RowMarketData(lastLoad = MarketDataLoad.Failed), darkTheme = true)

        assertEquals(priceColors.loadFailed, node(CompanyRowTags.PRICE).textColor())
    }

    @Test
    fun `received indicators are shown by name`() {
        setWatchlistRow(RowMarketData(quote(), listOf(Indicator("PE", "P/E", IndicatorLevel.BAD)), MarketDataLoad.Loaded(DataStatus.FRESH)))

        node(CompanyRowTags.INDICATORS).assertTextEquals("P/E").assertContentDescriptionEquals("Indicators P/E")
    }

    @Test
    fun `row without market data has no price, indicators or cache indicator`() {
        setRow(company = searchItem(), marketData = null)

        node(CompanyRowTags.PRICE).assertDoesNotExist()
        node(CompanyRowTags.INDICATORS).assertDoesNotExist()
        node(CompanyRowTags.CACHE_INDICATOR).assertDoesNotExist()
        node(CompanyRowTags.NAME).assertTextEquals("Apple Inc.")
    }

    @Test
    fun `quote price shows a dash without a quote`() {
        setRow(marketData = { QuotePriceAndCacheIndicator(null) })

        node(CompanyRowTags.PRICE).assertTextEquals("-").assertContentDescriptionEquals("No price available")
        node(CompanyRowTags.CACHE_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun `quote price uses the trend color`() {
        setRow(marketData = { QuotePriceAndCacheIndicator(quote(trend = Trend.DOWN)) })

        assertEquals(priceColors.down, node(CompanyRowTags.PRICE).textColor())
    }

    @Test
    fun `cache indicator is hidden for fresh data`() {
        setWatchlistRow(loaded(quote(fetchedAt = now - Duration.ofMinutes(59))))

        node(CompanyRowTags.CACHE_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun `cache indicator shows today's data older than one hour`() {
        setWatchlistRow(loaded(quote(fetchedAt = now - Duration.ofHours(2))))

        node(CompanyRowTags.CACHE_INDICATOR).assertTextEquals("\uD83D\uDD52" + "1")
            .assertContentDescriptionEquals("Cached earlier today")
    }

    @Test
    fun `cache indicator shows days for older data`() {
        setWatchlistRow(loaded(quote(fetchedAt = now - Duration.ofDays(2))))

        node(CompanyRowTags.CACHE_INDICATOR).assertTextEquals("\uD83D\uDD52" + "3")
            .assertContentDescriptionEquals("Cached 2 days ago")
    }

    @Test
    fun `ticker and exchange are shown`() {
        setRow(item(symbol = "SAP.DE", exchange = "XETRA"))

        composeRule.onNodeWithText("SAP", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("XETRA", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `row click and action are forwarded`() {
        var rowClicks = 0
        var removed = 0
        setRow(item(name = "Apple Inc."), onClick = { rowClicks++ }) {
            RemoveFromWatchlistButton(companyName = "Apple Inc.", onClick = { removed++ })
        }

        composeRule.onNodeWithTag(CompanyRowTags.ROW).performClick()
        composeRule.onNodeWithContentDescription("Remove Apple Inc. from the watchlist").performClick()

        assertEquals(1, rowClicks)
        assertEquals(1, removed)
    }

    @Test
    fun `logo has a content description`() {
        setRow(item(name = "Apple Inc."))

        composeRule.onNodeWithContentDescription("Logo of Apple Inc.", useUnmergedTree = true).assertExists()
    }
}
