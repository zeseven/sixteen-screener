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
import androidx.compose.ui.unit.Dp
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
import com.sixteenscreener.app.ui.theme.IndicatorColors
import com.sixteenscreener.app.ui.theme.PriceColors
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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
    private lateinit var indicatorColors: IndicatorColors

    private val roe = Indicator("ROE", "ROE", IndicatorLevel.GOOD)
    private val pe = Indicator("PE", "P/E", IndicatorLevel.WARNING)
    private val roa = Indicator("ROA", "ROA", IndicatorLevel.BAD)
    private val pb = Indicator("PB", "P/B", IndicatorLevel.BAD)

    private fun loaded(quote: Quote?, status: DataStatus = DataStatus.CACHED) =
        RowMarketData(quote, lastLoad = MarketDataLoad.Loaded(status))

    private fun setRow(
        company: Company = item(),
        marketData: (@Composable RowScope.() -> Unit)? = { WatchlistMarketData(loaded(quote())) },
        darkTheme: Boolean = false,
        onClick: () -> Unit = {},
        rowWidth: Dp = 400.dp,
        action: @Composable RowScope.() -> Unit = {},
    ) = composeRule.setContent {
        SixteenTheme(darkTheme = darkTheme, dynamicColor = false) {
            priceColors = SixteenTheme.priceColors
            indicatorColors = SixteenTheme.indicatorColors
            CompositionLocalProvider(LocalClock provides MutableClock(now)) {
                CompanyRow(company = company, onClick = onClick, modifier = Modifier.width(rowWidth), marketData = marketData, action = action)
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

    private fun SemanticsNodeInteraction.lineColors(): List<Color> {
        val input = textLayout().layoutInput
        var lineStart = 0
        return input.text.text.split('\n').map { line ->
            val span = input.text.spanStyles.firstOrNull { it.start <= lineStart && lineStart < it.end }
            lineStart += line.length + 1
            span?.item?.color ?: input.style.color
        }
    }

    private fun indicatorRow(indicators: List<Indicator>, status: DataStatus = DataStatus.FRESH, indicatorsStatus: DataStatus = status) =
        RowMarketData(quote(trend = Trend.UP), indicators, MarketDataLoad.Loaded(status, indicatorsStatus))

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
    fun `one indicator is shown by its short name in its level color`() {
        setWatchlistRow(indicatorRow(listOf(Indicator("PE", "P/E", IndicatorLevel.BAD))))

        node(CompanyRowTags.INDICATORS).assertTextEquals("P/E").assertContentDescriptionEquals("Indicators P/E bad")
        assertEquals(listOf(indicatorColors.bad), node(CompanyRowTags.INDICATORS).lineColors())
    }

    @Test
    fun `three indicators are shown one under another in their level colors`() {
        setWatchlistRow(indicatorRow(listOf(roe, pe, roa)))

        node(CompanyRowTags.INDICATORS).assertTextEquals("ROE\nP/E\nROA")
            .assertContentDescriptionEquals("Indicators ROE good, P/E warning, ROA bad")
        assertEquals(listOf(indicatorColors.good, indicatorColors.warning, indicatorColors.bad), node(CompanyRowTags.INDICATORS).lineColors())
    }

    @Test
    fun `more than three indicators show the first three and dots after the third`() {
        setWatchlistRow(indicatorRow(listOf(roe, pe, roa, pb)))

        node(CompanyRowTags.INDICATORS).assertTextEquals("ROE\nP/E\nROA ...")
            .assertContentDescriptionEquals("Indicators ROE good, P/E warning, ROA bad, and more")
        assertEquals(listOf(indicatorColors.good, indicatorColors.warning, indicatorColors.bad), node(CompanyRowTags.INDICATORS).lineColors())
    }

    @Test
    fun `no indicators show a dash in the default color`() {
        setWatchlistRow(indicatorRow(emptyList()))

        node(CompanyRowTags.INDICATORS).assertTextEquals("-").assertContentDescriptionEquals("No indicators")
        assertEquals(Color.Black, node(CompanyRowTags.INDICATORS).textColor())
    }

    @Test
    fun `failed ratios draw the indicators in orange while the price keeps its trend color`() {
        setWatchlistRow(indicatorRow(listOf(roe, pe, roa, pb), indicatorsStatus = DataStatus.LIMIT_REACHED))

        node(CompanyRowTags.INDICATORS).assertTextEquals("ROE\nP/E\nROA ...")
            .assertContentDescriptionEquals("Indicators ROE good, P/E warning, ROA bad, and more, last update failed")
        assertEquals(List(3) { priceColors.loadFailed }, node(CompanyRowTags.INDICATORS).lineColors())
        assertEquals(priceColors.up, node(CompanyRowTags.PRICE).textColor())
    }

    @Test
    fun `source error of the ratios without indicators shows an orange dash`() {
        setWatchlistRow(indicatorRow(emptyList(), indicatorsStatus = DataStatus.SOURCE_ERROR))

        node(CompanyRowTags.INDICATORS).assertTextEquals("-").assertContentDescriptionEquals("No indicators, last update failed")
        assertEquals(priceColors.loadFailed, node(CompanyRowTags.INDICATORS).textColor())
    }

    @Test
    fun `failed quote keeps the indicator level colors`() {
        setWatchlistRow(indicatorRow(listOf(roe), status = DataStatus.LIMIT_REACHED, indicatorsStatus = DataStatus.CACHED))

        assertEquals(priceColors.loadFailed, node(CompanyRowTags.PRICE).textColor())
        assertEquals(listOf(indicatorColors.good), node(CompanyRowTags.INDICATORS).lineColors())
    }

    @Test
    fun `indicators are to the right of the price`() {
        setWatchlistRow(indicatorRow(listOf(roe, pe)))

        val priceRight = node(CompanyRowTags.PRICE).getUnclippedBoundsInRoot().right
        val indicatorsLeft = node(CompanyRowTags.INDICATORS).getUnclippedBoundsInRoot().left
        assertTrue("indicators at $indicatorsLeft, price ends at $priceRight", indicatorsLeft >= priceRight)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `row with a long name, price and indicators fits a phone width`() {
        setRow(
            company = item(name = "International Business Machines Corporation"),
            marketData = { WatchlistMarketData(indicatorRow(listOf(roe, pe, roa, pb))) },
            rowWidth = 360.dp,
        ) {
            RemoveFromWatchlistButton(companyName = "IBM", onClick = {})
        }

        val rowRight = node(CompanyRowTags.ROW).getUnclippedBoundsInRoot().right
        val indicators = node(CompanyRowTags.INDICATORS)
        assertTrue(indicators.getUnclippedBoundsInRoot().right <= rowRight)
        assertFalse(indicators.textLayout().hasVisualOverflow)
        assertTrue(node(CompanyRowTags.PRICE).getUnclippedBoundsInRoot().right <= indicators.getUnclippedBoundsInRoot().left)
        node(CompanyRowTags.NAME).getUnclippedBoundsInRoot().let { assertTrue(it.right > it.left) }
        assertTrue(node(CompanyRowTags.NAME).textLayout().isLineEllipsized(0))
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
