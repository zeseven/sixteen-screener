package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.testing.Companies.item
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistFilterTest {

    private val apple = item("AAPL", market = Market.US, sector = "Technology")
    private val jpm = item("JPM", market = Market.US, sector = "Financial Services")
    private val sap = item("SAP.DE", market = Market.EU, sector = "Technology")
    private val shell = item("SHEL.L", market = Market.EU, sector = null)
    private val toyota = item("7203.T", market = null, sector = "Automobiles")
    private val unknown = item("XYZ.HK", market = null, sector = null)
    private val companies = listOf(apple, jpm, sap, shell, toyota, unknown)

    private fun symbols(filter: WatchlistFilter) = filter.apply(companies).map { it.symbol.value }

    @Test
    fun `default filter shows every company including those without market or sector`() {
        assertEquals(companies, WatchlistFilter().apply(companies))
    }

    @Test
    fun `US filter shows only US companies`() {
        assertEquals(listOf("AAPL", "JPM"), symbols(WatchlistFilter(market = MarketFilter.US)))
    }

    @Test
    fun `EU filter shows only EU companies`() {
        assertEquals(listOf("SAP.DE", "SHEL.L"), symbols(WatchlistFilter(market = MarketFilter.EU)))
    }

    @Test
    fun `sector filter shows only that sector across markets`() {
        assertEquals(listOf("AAPL", "SAP.DE"), symbols(WatchlistFilter(sector = "Technology")))
    }

    @Test
    fun `market and sector filters combine`() {
        assertEquals(listOf("SAP.DE"), symbols(WatchlistFilter(market = MarketFilter.EU, sector = "Technology")))
    }

    @Test
    fun `companies without market appear with a sector filter only when the market is all`() {
        assertEquals(listOf("7203.T"), symbols(WatchlistFilter(sector = "Automobiles")))
        assertEquals(emptyList<String>(), symbols(WatchlistFilter(market = MarketFilter.US, sector = "Automobiles")))
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<Any>(), WatchlistFilter(market = MarketFilter.US, sector = "Technology").apply(emptyList()))
    }

    @Test
    fun `sectors are distinct sorted and exclude null and blank`() {
        val withDuplicates = companies + item("MSFT", sector = "Technology") + item("BLANK", sector = " ") +
            item("LOW", sector = "banks")

        assertEquals(listOf("Automobiles", "banks", "Financial Services", "Technology"), WatchlistFilter.sectorsOf(withDuplicates))
    }

    @Test
    fun `sectors of an empty list are empty`() {
        assertEquals(emptyList<String>(), WatchlistFilter.sectorsOf(emptyList()))
    }

    @Test
    fun `filter keeps a sector that still exists`() {
        val filter = WatchlistFilter(market = MarketFilter.EU, sector = "Technology")

        assertEquals(filter, filter.reconciledWith(companies))
    }

    @Test
    fun `filter resets the sector when it disappears and keeps the market`() {
        val filter = WatchlistFilter(market = MarketFilter.EU, sector = "Energy")

        assertEquals(WatchlistFilter(market = MarketFilter.EU), filter.reconciledWith(companies))
    }

    private val king = Tag("chess-king", "Dividend king")
    private val growth = Tag("rocket-takeoff", "Growth")
    private val tagged = listOf(
        item("AAPL", market = Market.US, tags = listOf(growth)),
        item("KO", market = Market.US, tags = listOf(king, Tag("coin", "Income"))),
        item("NESN.SW", market = Market.EU, tags = listOf(Tag("chess-king", "DIVIDEND KING"))),
        item("SAP.DE", market = Market.EU),
    )

    private fun taggedSymbols(filter: WatchlistFilter) = filter.apply(tagged).map { it.symbol.value }

    @Test
    fun `tag filter shows companies having the same tag, ignoring the case of its text`() {
        assertEquals(listOf("KO", "NESN.SW"), taggedSymbols(WatchlistFilter(tag = king)))
    }

    @Test
    fun `tag filter doesn't match the same text with another icon`() {
        assertEquals(emptyList<String>(), taggedSymbols(WatchlistFilter(tag = Tag("star", "Dividend king"))))
    }

    @Test
    fun `tag filter combines with the market filter`() {
        assertEquals(listOf("NESN.SW"), taggedSymbols(WatchlistFilter(market = MarketFilter.EU, tag = king)))
    }

    @Test
    fun `tagsOf lists each tag once, sorted by text ignoring case`() {
        assertEquals(listOf(king, growth, Tag("coin", "Income")), WatchlistFilter.tagsOf(tagged))
    }

    @Test
    fun `tagsOf companies without tags is empty`() {
        assertEquals(emptyList<Tag>(), WatchlistFilter.tagsOf(companies))
    }

    @Test
    fun `reconciling resets a tag no longer used by any company`() {
        assertEquals(WatchlistFilter(), WatchlistFilter(tag = Tag("gem", "Quality")).reconciledWith(tagged))
    }

    @Test
    fun `reconciling keeps a tag still used by a company`() {
        val filter = WatchlistFilter(market = MarketFilter.US, tag = growth)

        assertEquals(filter, filter.reconciledWith(tagged))
    }
}
