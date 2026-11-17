package com.sixteenscreener.app.domain.model

import com.sixteenscreener.app.testing.Companies.marketData
import com.sixteenscreener.app.testing.Companies.quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowMarketDataTest {

    private val oldQuote = quote(price = 100.0)
    private val newQuote = quote(price = 110.0)
    private val indicator = Indicator("PE", "P/E", IndicatorLevel.WARNING)
    private val previous = RowMarketData(oldQuote, listOf(indicator), MarketDataLoad.Loaded(DataStatus.FRESH))

    @Test
    fun `a row without data starts loading without values`() {
        assertEquals(RowMarketData(null, emptyList(), MarketDataLoad.Loading), null.loading())
    }

    @Test
    fun `loading keeps the previous values`() {
        assertEquals(previous.copy(lastLoad = MarketDataLoad.Loading), previous.loading())
    }

    @Test
    fun `loaded data replaces the values and stores the status`() {
        val data = marketData(status = DataStatus.CACHED, quote = newQuote)

        assertEquals(RowMarketData(newQuote, emptyList(), MarketDataLoad.Loaded(DataStatus.CACHED)), previous.loading().loadedWith(data))
    }

    @Test
    fun `first loaded data of a row is stored`() {
        val data = marketData(status = DataStatus.FRESH, quote = newQuote, indicators = listOf(indicator))

        assertEquals(RowMarketData(newQuote, listOf(indicator), MarketDataLoad.Loaded(DataStatus.FRESH)), null.loading().loadedWith(data))
    }

    @Test
    fun `failed request keeps the previous values`() {
        assertEquals(previous.copy(lastLoad = MarketDataLoad.Failed), previous.loading().failed())
    }

    @Test
    fun `failed request without previous values has no values`() {
        assertEquals(RowMarketData(null, emptyList(), MarketDataLoad.Failed), null.loading().failed())
    }

    @Test
    fun `limit reached without a quote keeps the previous values`() {
        val data = marketData(status = DataStatus.LIMIT_REACHED, quote = null)

        assertEquals(
            RowMarketData(oldQuote, listOf(indicator), MarketDataLoad.Loaded(DataStatus.LIMIT_REACHED)),
            previous.loadedWith(data),
        )
    }

    @Test
    fun `source error stores a returned cached quote`() {
        val data = marketData(status = DataStatus.SOURCE_ERROR, quote = newQuote)

        assertEquals(
            RowMarketData(newQuote, listOf(indicator), MarketDataLoad.Loaded(DataStatus.SOURCE_ERROR)),
            previous.loadedWith(data),
        )
    }

    @Test
    fun `limit reached without previous values and without a quote has no values`() {
        val data = marketData(status = DataStatus.LIMIT_REACHED, quote = null)

        assertEquals(RowMarketData(null, emptyList(), MarketDataLoad.Loaded(DataStatus.LIMIT_REACHED)), null.loadedWith(data))
    }

    @Test
    fun `no data without a quote drops the previous quote`() {
        val data = marketData(status = DataStatus.NO_DATA, quote = null)

        assertEquals(RowMarketData(null, emptyList(), MarketDataLoad.Loaded(DataStatus.NO_DATA)), previous.loadedWith(data))
    }

    @Test
    fun `not supported without a quote drops the previous quote`() {
        val data = marketData(status = DataStatus.NOT_SUPPORTED, quote = null)

        assertEquals(RowMarketData(null, emptyList(), MarketDataLoad.Loaded(DataStatus.NOT_SUPPORTED)), previous.loadedWith(data))
    }

    @Test
    fun `failed ratios keep the previous indicators while a fresh quote replaces the price`() {
        val data = marketData(status = DataStatus.FRESH, quote = newQuote, indicatorsStatus = DataStatus.LIMIT_REACHED)

        val row = previous.loadedWith(data)

        assertEquals(RowMarketData(newQuote, listOf(indicator), MarketDataLoad.Loaded(DataStatus.FRESH, DataStatus.LIMIT_REACHED)), row)
        assertFalse(row.isLastLoadFailed)
        assertTrue(row.isIndicatorsLoadFailed)
    }

    @Test
    fun `fresh ratios replace the indicators while a failed quote keeps the previous price`() {
        val data = marketData(status = DataStatus.SOURCE_ERROR, quote = null, indicatorsStatus = DataStatus.FRESH)

        val row = previous.loadedWith(data)

        assertEquals(RowMarketData(oldQuote, emptyList(), MarketDataLoad.Loaded(DataStatus.SOURCE_ERROR, DataStatus.FRESH)), row)
        assertTrue(row.isLastLoadFailed)
        assertFalse(row.isIndicatorsLoadFailed)
    }

    @Test
    fun `failed ratios store returned cached indicators`() {
        val cached = Indicator("ROE", "ROE", IndicatorLevel.GOOD)
        val data = marketData(indicators = listOf(cached), indicatorsStatus = DataStatus.SOURCE_ERROR)

        assertEquals(listOf(cached), previous.loadedWith(data).indicators)
    }

    @Test
    fun `only failed requests, limit reached and source error of the ratios count as failed indicator loads`() {
        val failedStatuses = setOf(DataStatus.LIMIT_REACHED, DataStatus.SOURCE_ERROR)
        DataStatus.entries.forEach { status ->
            val row = RowMarketData(lastLoad = MarketDataLoad.Loaded(DataStatus.FRESH, indicatorsStatus = status))
            assertEquals(status.name, status in failedStatuses, row.isIndicatorsLoadFailed)
        }
        assertTrue(RowMarketData(lastLoad = MarketDataLoad.Failed).isIndicatorsLoadFailed)
        assertFalse(RowMarketData(lastLoad = MarketDataLoad.Loading).isIndicatorsLoadFailed)
    }

    @Test
    fun `only failed requests, limit reached and source error count as failed loads`() {
        val failedStatuses = setOf(DataStatus.LIMIT_REACHED, DataStatus.SOURCE_ERROR)
        DataStatus.entries.forEach { status ->
            assertEquals(status.name, status in failedStatuses, RowMarketData(lastLoad = MarketDataLoad.Loaded(status)).isLastLoadFailed)
        }
        assertTrue(RowMarketData(lastLoad = MarketDataLoad.Failed).isLastLoadFailed)
        assertFalse(RowMarketData(lastLoad = MarketDataLoad.Loading).isLastLoadFailed)
    }
}
