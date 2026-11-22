package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.SearchItem
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.SearchStatus
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.domain.model.WatchlistItem
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
data class WatchlistAddDto(
    val name: String,
)

@Serializable
data class WatchlistItemDto(
    val symbol: String,
    val ticker: String,
    val exchange: String,
    val name: String,
    val market: String? = null,
    val type: String? = null,
    val stock: Boolean = true,
    val logoUrl: String,
    val sector: String? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
data class SearchItemDto(
    val symbol: String,
    val ticker: String,
    val exchange: String,
    val name: String,
    val market: String? = null,
    val type: String? = null,
    val stock: Boolean = true,
    val logoUrl: String,
    val inWatchlist: Boolean = false,
)

@Serializable
data class WatchlistDto(
    val items: List<WatchlistItemDto>,
)

@Serializable
data class IndicatorDto(
    val ratioCode: String,
    val name: String,
    val level: String,
)

@Serializable
data class MarketDataDto(
    val symbol: String,
    val status: String,
    val indicatorsStatus: String? = null,
    val quote: QuoteDto? = null,
    val indicators: List<IndicatorDto> = emptyList(),
)

@Serializable
data class WatchlistMarketDataDto(
    val items: List<MarketDataDto>,
)

@Serializable
data class SearchResultsDto(
    val source: String,
    val status: String,
    val items: List<SearchItemDto>,
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

fun WatchlistItemDto.toDomain() = WatchlistItem(
    symbol = Symbol(symbol),
    ticker = ticker,
    exchange = exchange,
    name = name,
    market = market.toMarket(),
    type = type,
    stock = stock,
    logoUrl = logoUrl,
    sector = sector,
    tags = tags.map { it.toDomain() },
)

fun SearchItemDto.toDomain() = SearchItem(
    symbol = Symbol(symbol),
    ticker = ticker,
    exchange = exchange,
    name = name,
    market = market.toMarket(),
    type = type,
    stock = stock,
    logoUrl = logoUrl,
    inWatchlist = inWatchlist,
)

fun WatchlistDto.toDomain(): List<WatchlistItem> = items.map { it.toDomain() }

fun IndicatorDto.toDomain() = Indicator(
    ratioCode = ratioCode,
    name = name,
    level = level.toEnumOr(IndicatorLevel.UNKNOWN),
)

fun MarketDataDto.toDomain() = MarketData(
    symbol = Symbol(symbol),
    status = status.toEnumOr(DataStatus.UNKNOWN),
    quote = quote?.toDomain(),
    indicators = indicators.map { it.toDomain() },
    indicatorsStatus = indicatorsStatus?.toEnumOr(DataStatus.UNKNOWN) ?: DataStatus.UNKNOWN,
)

fun WatchlistMarketDataDto.toDomain(): List<MarketData> = items.map { it.toDomain() }

fun SearchResultsDto.toDomain() = SearchResults(
    source = SourceId(source),
    status = status.toEnumOr(SearchStatus.UNKNOWN),
    items = items.map { it.toDomain() },
)
