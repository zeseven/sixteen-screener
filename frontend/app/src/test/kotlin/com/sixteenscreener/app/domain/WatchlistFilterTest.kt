package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Market
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
}
