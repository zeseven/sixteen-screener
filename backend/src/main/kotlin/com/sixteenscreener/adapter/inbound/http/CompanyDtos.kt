package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistItem
import com.sixteenscreener.port.inbound.SearchResults
import kotlinx.serialization.Serializable

@Serializable
data class QuoteDto(
    val price: Double,
    val currency: String,
    val fetchedAt: String,
    val previousCachedPrice: Double?,
    val trend: String,
    val source: String,
)

@Serializable
data class CompanySummaryDto(
    val symbol: String,
    val ticker: String,
    val exchange: String,
    val name: String,
    val market: String?,
    val sector: String?,
    val logoUrl: String,
    val quote: QuoteDto?,
    val inWatchlist: Boolean,
)

@Serializable
data class WatchlistItemDto(
    val symbol: String,
    val ticker: String,
    val exchange: String,
    val name: String,
    val market: String?,
    val logoUrl: String,
    val sector: String?,
    val tags: List<String>,
)

@Serializable
data class WatchlistDto(
    val items: List<WatchlistItemDto>,
)

@Serializable
data class WatchlistRowsDto(
    val items: List<CompanySummaryDto>,
)

@Serializable
data class SearchResultsDto(
    val source: String,
    val status: String,
    val items: List<CompanySummaryDto>,
)

fun SearchResults.toDto() = SearchResultsDto(
    source = source.value,
    status = status.name,
    items = items.map { it.company.toDto(it.inWatchlist) },
)

fun WatchlistItem.toDto() = WatchlistItemDto(
    symbol = identity.symbol.value,
    ticker = identity.ticker,
    exchange = identity.exchange,
    name = identity.name,
    market = identity.market?.name,
    logoUrl = logoUrlOf(identity.symbol),
    sector = sector,
    tags = tags,
)

fun logoUrlOf(symbol: Symbol): String = "/logos/${symbol.value}"

fun SourceData<QuoteRecord>.toQuoteDto(): QuoteDto? {
    val record = data ?: return null
    val receivedAt = fetchedAt ?: return null
    return QuoteDto(
        price = record.quote.price,
        currency = record.quote.currency,
        fetchedAt = receivedAt.toString(),
        previousCachedPrice = record.previousPrice?.price,
        trend = record.trend.name,
        source = source.value,
    )
}

fun CompanySummary.toDto(inWatchlist: Boolean) = CompanySummaryDto(
    symbol = identity.symbol.value,
    ticker = identity.ticker,
    exchange = identity.exchange,
    name = identity.name,
    market = identity.market?.name,
    sector = sector,
    logoUrl = logoUrlOf(identity.symbol),
    quote = quote?.toQuoteDto(),
    inWatchlist = inWatchlist,
)
