package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Trend

/** Color role of a market data value on a company row. */
enum class MarketValueColor { DEFAULT, UP, DOWN, FAILED }

/** Text of a market data value with its color role. */
data class MarketValue(val text: String, val color: MarketValueColor)

/**
 * Indicators a watchlist row shows: the first [MarketDataDisplay.MAX_SHOWN_INDICATORS] of the received list (already in display
 * order), one per line in the color of their level, or `-` when there are none.
 */
data class IndicatorsDisplay(
    /** Shown indicators, at most [MarketDataDisplay.MAX_SHOWN_INDICATORS]. */
    val shown: List<Indicator>,
    /** Whether more indicators were received than shown; the last shown line then ends with `...`. */
    val hasMore: Boolean,
    /** Whether the last load of the ratios failed; all lines, or the `-`, are then drawn in orange. */
    val isLoadFailed: Boolean,
)

/**
 * What a watchlist row shows in its price and indicator slots.
 */
data class RowMarketDisplay(
    /** Shown quote, `null` when the price is `-`; its fetch time drives the cache indicator. */
    val quote: Quote?,
    val price: MarketValue,
    val indicators: IndicatorsDisplay,
    /** Whether the last load of the quote failed. */
    val isLastLoadFailed: Boolean,
)

/**
 * Rendering rules of the market data of a watchlist row (ADR-058, ADR-075): `-` in the default text color while nothing has been
 * received or when the source has no value, orange after a failed load (keeping the last received values), otherwise the trend
 * color for the price and the level colors for the indicators. The price follows the quote status and the indicators the ratio
 * status.
 */
object MarketDataDisplay {
    const val MAX_SHOWN_INDICATORS = 3

    fun of(data: RowMarketData?): RowMarketDisplay {
        val quote = data?.quote
        val isPriceFailed = data?.isLastLoadFailed == true
        val price = if (quote == null) {
            MarketValue(NumberFormats.MISSING, if (isPriceFailed) MarketValueColor.FAILED else MarketValueColor.DEFAULT)
        } else {
            MarketValue(NumberFormats.price(quote.price, quote.currency), if (isPriceFailed) MarketValueColor.FAILED else trendColor(quote.trend))
        }
        val indicators = data?.indicators.orEmpty()
        val indicatorsDisplay = IndicatorsDisplay(
            shown = indicators.take(MAX_SHOWN_INDICATORS),
            hasMore = indicators.size > MAX_SHOWN_INDICATORS,
            isLoadFailed = data?.isIndicatorsLoadFailed == true,
        )
        return RowMarketDisplay(quote, price, indicatorsDisplay, isPriceFailed)
    }

    private fun trendColor(trend: Trend): MarketValueColor = when (trend) {
        Trend.UP -> MarketValueColor.UP
        Trend.DOWN -> MarketValueColor.DOWN
        Trend.UNCHANGED -> MarketValueColor.DEFAULT
    }
}
