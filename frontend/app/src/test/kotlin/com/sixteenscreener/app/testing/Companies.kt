package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.CompanyIdentity
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.SearchItem
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.domain.model.WatchlistItem
import java.time.Instant

object Companies {
    fun quote(
        price: Double = 189.84,
        currency: String = "USD",
        fetchedAt: Instant = Instant.parse("2026-11-14T10:00:00Z"),
        trend: Trend = Trend.UP,
    ) = Quote(
        price = price,
        currency = currency,
        fetchedAt = fetchedAt,
        previousCachedPrice = 187.5,
        trend = trend,
        source = SourceId.FINNHUB,
    )

    fun item(
        symbol: String = "AAPL",
        name: String = "Apple Inc.",
        exchange: String = "NASDAQ",
        market: Market? = Market.US,
        type: String? = "Common Stock",
        stock: Boolean = true,
        sector: String? = "Technology",
        tags: List<Tag> = emptyList(),
    ) = WatchlistItem(
        symbol = Symbol(symbol),
        ticker = symbol.substringBefore('.'),
        exchange = exchange,
        name = name,
        market = market,
        type = type,
        stock = stock,
        logoUrl = "/logos/$symbol",
        sector = sector,
        tags = tags,
    )

    fun searchItem(
        symbol: String = "AAPL",
        name: String = "Apple Inc.",
        exchange: String = "NASDAQ",
        market: Market? = Market.US,
        type: String? = "Common Stock",
        stock: Boolean = true,
        inWatchlist: Boolean = false,
    ) = SearchItem(
        symbol = Symbol(symbol),
        ticker = symbol.substringBefore('.'),
        exchange = exchange,
        name = name,
        market = market,
        type = type,
        stock = stock,
        logoUrl = "/logos/$symbol",
        inWatchlist = inWatchlist,
    )

    fun identity(
        symbol: String = "AAPL",
        name: String = "Apple Inc.",
        exchange: String = "NASDAQ",
        market: Market? = Market.US,
        type: String? = "Common Stock",
        stock: Boolean = true,
    ) = CompanyIdentity(
        symbol = Symbol(symbol),
        ticker = symbol.substringBefore('.'),
        exchange = exchange,
        name = name,
        market = market,
        type = type,
        stock = stock,
        logoUrl = "/logos/$symbol",
    )

    fun marketData(
        symbol: String = "AAPL",
        status: DataStatus = DataStatus.FRESH,
        quote: Quote? = quote(),
        indicators: List<Indicator> = emptyList(),
        indicatorsStatus: DataStatus = status,
    ) = MarketData(Symbol(symbol), status, quote, indicators, indicatorsStatus)
}
