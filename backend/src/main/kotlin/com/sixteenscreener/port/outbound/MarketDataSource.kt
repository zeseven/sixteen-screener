package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol

/**
 * Source of prices, ratios, company profiles, search and exchange directories (e.g. Finnhub).
 *
 * Every method makes at most one call to the source; request limits are checked by the caller before calling.
 * Implementations never throw for source failures: they return [SourceResult] variants carrying the raw call outcome.
 * A method for data the source doesn't provide returns [SourceResult.NotSupported] without making a call.
 */
interface MarketDataSource {
    /** Configured ID of the source. */
    val id: SourceId

    /** Searches companies by ticker or name [query]. */
    suspend fun search(query: String): SourceResult<List<SearchHit>>

    /** Latest quote of [listing]; the currency is taken from the listing when the source doesn't return one. */
    suspend fun quote(listing: ListedSymbol): SourceResult<Quote>

    /** Price and financial ratios of [symbol]; ratios missing at the source are returned with a null value. */
    suspend fun ratios(symbol: Symbol): SourceResult<Ratios>

    /** Dividends of [symbol] of the period the source provides (EODHD: the last year, Yahoo Finance: all), newest first. */
    suspend fun dividends(symbol: Symbol): SourceResult<Dividends>

    /** Company profile of [symbol]. */
    suspend fun companyProfile(symbol: Symbol): SourceResult<CompanyProfile>

    /** All symbols listed on the exchange [exchangeCode] (ADR-034). */
    suspend fun exchangeSymbols(exchangeCode: ExchangeCode): SourceResult<List<ListedSymbol>>
}
