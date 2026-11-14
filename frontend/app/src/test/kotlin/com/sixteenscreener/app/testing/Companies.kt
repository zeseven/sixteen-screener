package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Trend
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

    fun summary(
        symbol: String = "AAPL",
        name: String = "Apple Inc.",
        exchange: String = "NASDAQ",
        market: Market? = Market.US,
        sector: String? = "Technology",
        quote: Quote? = quote(),
        inWatchlist: Boolean = true,
    ) = CompanySummary(
        symbol = Symbol(symbol),
        ticker = symbol.substringBefore('.'),
        exchange = exchange,
        name = name,
        market = market,
        sector = sector,
        logoUrl = "/logos/$symbol",
        quote = quote,
        inWatchlist = inWatchlist,
    )
}
