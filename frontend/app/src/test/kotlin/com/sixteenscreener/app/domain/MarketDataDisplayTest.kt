package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.MarketDataLoad
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.testing.Companies.quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataDisplayTest {

    private val missingDefault = MarketValue("-", MarketValueColor.DEFAULT)
    private val missingFailed = MarketValue("-", MarketValueColor.FAILED)

    private fun loaded(status: DataStatus) = MarketDataLoad.Loaded(status)

    @Test
    fun `row never requested shows dashes in the default color`() {
        val display = MarketDataDisplay.of(null)

        assertEquals(RowMarketDisplay(null, missingDefault, missingDefault, isLastLoadFailed = false), display)
    }

    @Test
    fun `loading row without a value shows dashes in the default color`() {
        val display = MarketDataDisplay.of(RowMarketData(lastLoad = MarketDataLoad.Loading))

        assertEquals(missingDefault, display.price)
        assertEquals(missingDefault, display.indicators)
        assertFalse(display.isLastLoadFailed)
    }

    @Test
    fun `loading row with a previous value shows it in the trend color`() {
        val quote = quote(trend = Trend.DOWN)

        val display = MarketDataDisplay.of(RowMarketData(quote, lastLoad = MarketDataLoad.Loading))

        assertEquals(MarketValue("189.84 USD", MarketValueColor.DOWN), display.price)
        assertEquals(quote, display.quote)
    }

    @Test
    fun `loaded quote uses the trend color`() {
        mapOf(Trend.UP to MarketValueColor.UP, Trend.DOWN to MarketValueColor.DOWN, Trend.UNCHANGED to MarketValueColor.DEFAULT)
            .forEach { (trend, color) ->
                val display = MarketDataDisplay.of(RowMarketData(quote(trend = trend), lastLoad = loaded(DataStatus.FRESH)))

                assertEquals(trend.name, MarketValue("189.84 USD", color), display.price)
                assertEquals(trend.name, missingDefault, display.indicators)
            }
    }

    @Test
    fun `cached quote uses the trend color`() {
        val display = MarketDataDisplay.of(RowMarketData(quote(trend = Trend.UP), lastLoad = loaded(DataStatus.CACHED)))

        assertEquals(MarketValueColor.UP, display.price.color)
    }

    @Test
    fun `no data and not supported without a quote show dashes in the default color`() {
        listOf(DataStatus.NO_DATA, DataStatus.NOT_SUPPORTED).forEach { status ->
            val display = MarketDataDisplay.of(RowMarketData(lastLoad = loaded(status)))

            assertEquals(status.name, RowMarketDisplay(null, missingDefault, missingDefault, isLastLoadFailed = false), display)
        }
    }

    @Test
    fun `failed request without a value shows orange dashes`() {
        val display = MarketDataDisplay.of(RowMarketData(lastLoad = MarketDataLoad.Failed))

        assertEquals(RowMarketDisplay(null, missingFailed, missingFailed, isLastLoadFailed = true), display)
    }

    @Test
    fun `failed request keeps the last value in orange`() {
        val quote = quote(trend = Trend.UP)

        val display = MarketDataDisplay.of(RowMarketData(quote, lastLoad = MarketDataLoad.Failed))

        assertEquals(MarketValue("189.84 USD", MarketValueColor.FAILED), display.price)
        assertEquals(missingFailed, display.indicators)
        assertEquals(quote, display.quote)
        assertTrue(display.isLastLoadFailed)
    }

    @Test
    fun `limit reached and source error are drawn in orange with and without a quote`() {
        listOf(DataStatus.LIMIT_REACHED, DataStatus.SOURCE_ERROR).forEach { status ->
            val withQuote = MarketDataDisplay.of(RowMarketData(quote(), lastLoad = loaded(status)))
            val withoutQuote = MarketDataDisplay.of(RowMarketData(lastLoad = loaded(status)))

            assertEquals(status.name, MarketValue("189.84 USD", MarketValueColor.FAILED), withQuote.price)
            assertEquals(status.name, missingFailed, withQuote.indicators)
            assertEquals(status.name, missingFailed, withoutQuote.price)
        }
    }

    @Test
    fun `unknown status is not a failure`() {
        val display = MarketDataDisplay.of(RowMarketData(lastLoad = loaded(DataStatus.UNKNOWN)))

        assertEquals(missingDefault, display.price)
        assertNull(display.quote)
    }

    @Test
    fun `received indicators are listed by name`() {
        val indicators = listOf(Indicator("PE", "P/E", IndicatorLevel.BAD), Indicator("ROE", "ROE", IndicatorLevel.GOOD))

        val display = MarketDataDisplay.of(RowMarketData(quote(), indicators, loaded(DataStatus.FRESH)))

        assertEquals(MarketValue("P/E ROE", MarketValueColor.DEFAULT), display.indicators)
    }
}
