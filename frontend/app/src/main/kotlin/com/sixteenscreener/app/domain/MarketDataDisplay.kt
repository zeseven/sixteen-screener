package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Trend

/** Color role of a market data value on a company row. */
enum class MarketValueColor { DEFAULT, UP, DOWN, FAILED }

/** Text of a market data value with its color role. */
data class MarketValue(val text: String, val color: MarketValueColor)

/**
 * What a watchlist row shows in its price and indicator slots.
 */
data class RowMarketDisplay(
    /** Shown quote, `null` when the price is `-`; its fetch time drives the cache indicator. */
    val quote: Quote?,
    val price: MarketValue,
    val indicators: MarketValue,
    /** Whether the last load of the row failed. */
    val isLastLoadFailed: Boolean,
)

/**
 * Rendering rules of the market data of a watchlist row (ADR-058): `-` in the default text color while nothing has been received
 * or when the source has no quote, orange after a failed load (keeping the last received values), otherwise the trend color.
 */
object MarketDataDisplay {
    private const val INDICATOR_SEPARATOR = " "

    fun of(data: RowMarketData?): RowMarketDisplay {
        val quote = data?.quote
        val isFailed = data?.isLastLoadFailed == true
        val price = if (quote == null) {
            MarketValue(NumberFormats.MISSING, if (isFailed) MarketValueColor.FAILED else MarketValueColor.DEFAULT)
        } else {
            MarketValue(NumberFormats.price(quote.price, quote.currency), if (isFailed) MarketValueColor.FAILED else trendColor(quote.trend))
        }
        val indicatorNames = data?.indicators.orEmpty().map { it.name }
        val indicators = MarketValue(
            text = indicatorNames.ifEmpty { listOf(NumberFormats.MISSING) }.joinToString(INDICATOR_SEPARATOR),
            color = if (isFailed) MarketValueColor.FAILED else MarketValueColor.DEFAULT,
        )
        return RowMarketDisplay(quote, price, indicators, isFailed)
    }

    private fun trendColor(trend: Trend): MarketValueColor = when (trend) {
        Trend.UP -> MarketValueColor.UP
        Trend.DOWN -> MarketValueColor.DOWN
        Trend.UNCHANGED -> MarketValueColor.DEFAULT
    }
}
