package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.testing.Exchanges.all
import com.sixteenscreener.app.testing.Exchanges.nyse
import com.sixteenscreener.app.testing.Exchanges.paris
import com.sixteenscreener.app.testing.Exchanges.tsxVenture
import com.sixteenscreener.app.testing.Exchanges.xetra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExchangeFilterTest {

    private val onlyMarketMentions = Exchange("CO", "CPH", "Copenhagen", "XCSE", "CO", "CO", "DK", true, "DKK", "CET", Market.US)

    private fun filtered(query: String): List<Exchange> = ExchangeFilter.apply(all, query)

    @Test
    fun `empty query returns every row in order`() {
        assertEquals(all, filtered(""))
    }

    @Test
    fun `whitespace query returns every row`() {
        assertEquals(all, filtered("   \t"))
    }

    @Test
    fun `match ignores case`() {
        assertEquals(listOf(tsxVenture), filtered("tsx venture"))
        assertEquals(listOf(tsxVenture), filtered("TSX VENTURE"))
    }

    @Test
    fun `matches the displayed suffix with or without the dot`() {
        assertEquals(listOf(xetra), filtered(".de"))
        assertEquals(listOf(tsxVenture), filtered(".V"))
    }

    @Test
    fun `dot alone matches every row with a suffix`() {
        assertEquals(listOf(xetra, paris, tsxVenture), filtered("."))
    }

    @Test
    fun `matches the short name`() {
        assertEquals(listOf(xetra), filtered("xetra"))
    }

    @Test
    fun `matches the full name`() {
        assertEquals(listOf(nyse), filtered("new york stock"))
    }

    @Test
    fun `matches the MIC`() {
        assertEquals(listOf(tsxVenture), filtered("xtsx"))
    }

    @Test
    fun `matches the country code`() {
        assertEquals(listOf(paris), filtered("fr"))
        assertEquals(listOf(paris), filtered("FR"))
    }

    @Test
    fun `matches the EODHD code`() {
        val byEodhdCodeOnly = nyse.copy(eodhdCode = "QQX")

        assertEquals(listOf(byEodhdCodeOnly), ExchangeFilter.apply(all + byEodhdCodeOnly, "qqx"))
    }

    @Test
    fun `exchange without an EODHD code matches its other fields only`() {
        val uncovered = tsxVenture.copy(eodhdCode = null)

        assertEquals(listOf(uncovered), ExchangeFilter.apply(listOf(uncovered), "xtsx"))
        assertEquals(emptyList<Exchange>(), ExchangeFilter.apply(listOf(uncovered), "null"))
    }

    @Test
    fun `matches the Yahoo suffix with or without the dot`() {
        val byYahooSuffixOnly = tsxVenture.copy(yahooSuffix = "QQY")

        assertEquals(listOf(byYahooSuffixOnly), ExchangeFilter.apply(all + byYahooSuffixOnly, "qqy"))
        assertEquals(listOf(byYahooSuffixOnly), ExchangeFilter.apply(all + byYahooSuffixOnly, ".QQY"))
    }

    @Test
    fun `US exchange is not matched by the none placeholder of its empty Yahoo suffix`() {
        assertEquals(emptyList<Exchange>(), ExchangeFilter.apply(listOf(nyse), "none"))
        assertEquals(emptyList<Exchange>(), ExchangeFilter.apply(listOf(nyse), "."))
    }

    @Test
    fun `exchange without a Yahoo suffix matches its other fields only`() {
        val uncovered = tsxVenture.copy(yahooSuffix = null)

        assertEquals(listOf(uncovered), ExchangeFilter.apply(listOf(uncovered), "xtsx"))
        assertEquals(emptyList<Exchange>(), ExchangeFilter.apply(listOf(uncovered), "null"))
    }

    @Test
    fun `Yahoo suffix is displayed with a dot, and not at all when empty or missing`() {
        assertEquals(".DE", ExchangeFilter.displayYahooSuffix(xetra))
        assertNull(ExchangeFilter.displayYahooSuffix(nyse))
        assertNull(ExchangeFilter.displayYahooSuffix(xetra.copy(yahooSuffix = null)))
    }

    @Test
    fun `main venue mark is not matched`() {
        assertEquals(emptyList<Exchange>(), filtered("\u2705"))
    }

    @Test
    fun `matches the currency`() {
        assertEquals(listOf(xetra, paris), filtered("eur"))
    }

    @Test
    fun `matches the time zone`() {
        assertEquals(listOf(nyse, tsxVenture), filtered("america/"))
    }

    @Test
    fun `market is not matched`() {
        assertEquals(emptyList<Exchange>(), ExchangeFilter.apply(listOf(onlyMarketMentions), "US"))
        assertEquals(emptyList<Exchange>(), ExchangeFilter.apply(listOf(onlyMarketMentions.copy(market = Market.EU)), "EU"))
    }

    @Test
    fun `EU matches a European time zone but not the EU market alone`() {
        val byMarketOnly = onlyMarketMentions.copy(market = Market.EU)
        val byTimeZone = byMarketOnly.copy(timeZone = "Europe/Copenhagen")

        assertEquals(listOf(byTimeZone), ExchangeFilter.apply(listOf(byTimeZone, byMarketOnly), "EU"))
    }

    @Test
    fun `query is trimmed`() {
        assertEquals(listOf(nyse), filtered("  xnys  "))
    }

    @Test
    fun `no match returns no rows`() {
        assertEquals(emptyList<Exchange>(), filtered("tokyo"))
    }

    @Test
    fun `US exchange displays an empty suffix`() {
        assertEquals("", ExchangeFilter.displaySuffix(nyse))
        assertEquals(".V", ExchangeFilter.displaySuffix(tsxVenture))
    }
}
