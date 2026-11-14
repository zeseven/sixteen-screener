package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.SearchStatus
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Trend
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class QuoteDto(
    val price: Double,
    val currency: String,
    val fetchedAt: String,
    val previousCachedPrice: Double? = null,
    val trend: String,
    val source: String? = null,
)

@Serializable
data class CompanySummaryDto(
    val symbol: String,
    val ticker: String,
    val exchange: String,
    val name: String,
    val market: String? = null,
    val sector: String? = null,
    val logoUrl: String,
    val quote: QuoteDto? = null,
    val inWatchlist: Boolean = false,
)

@Serializable
data class WatchlistDto(
    val items: List<CompanySummaryDto>,
)

@Serializable
data class SearchResultsDto(
    val source: String,
    val status: String,
    val items: List<CompanySummaryDto>,
)

fun String?.toMarket(): Market? = this?.let { value -> Market.entries.firstOrNull { it.name == value } }

fun String.toTrend(): Trend = Trend.entries.firstOrNull { it.name == this } ?: Trend.UNCHANGED

fun QuoteDto.toDomain() = Quote(
    price = price,
    currency = currency,
    fetchedAt = Instant.parse(fetchedAt),
    previousCachedPrice = previousCachedPrice,
    trend = trend.toTrend(),
    source = source?.let(::SourceId),
)

fun CompanySummaryDto.toDomain() = CompanySummary(
    symbol = Symbol(symbol),
    ticker = ticker,
    exchange = exchange,
    name = name,
    market = market.toMarket(),
    sector = sector,
    logoUrl = logoUrl,
    quote = quote?.toDomain(),
    inWatchlist = inWatchlist,
)

fun WatchlistDto.toDomain(): List<CompanySummary> = items.map { it.toDomain() }

fun SearchResultsDto.toDomain() = SearchResults(
    source = SourceId(source),
    status = status.toEnumOr(SearchStatus.UNKNOWN),
    items = items.map { it.toDomain() },
)
