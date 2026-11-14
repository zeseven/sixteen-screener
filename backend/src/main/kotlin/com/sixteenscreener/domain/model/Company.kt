package com.sixteenscreener.domain.model

import java.time.Instant

/**
 * How a company is identified and displayed: resolved by the backend from the symbol and the exchange directory.
 *
 * @property symbol canonical symbol
 * @property exchange short exchange name shown under the ticker
 * @property name company name
 * @property market market of the exchange; null outside the US and EU markets (ADR-035)
 */
data class CompanyIdentity(
    val symbol: Symbol,
    val exchange: String,
    val name: String,
    val market: Market?,
) {
    /** Ticker without the exchange suffix. */
    val ticker: String get() = symbol.ticker
}

/**
 * Data of a company row on the watchlist and search screens.
 *
 * @property identity symbol, exchange, name and market
 * @property sector sector as provided by the source; null when no company profile is cached
 * @property quote latest quote with its status; null when no quote was looked up (e.g. a search result without cached data)
 */
data class CompanySummary(
    val identity: CompanyIdentity,
    val sector: String?,
    val quote: SourceData<QuoteRecord>?,
)

/**
 * Price and ratios of a company from one source (detailed screen, "Price and ratio" tab).
 *
 * The combined [status] and [fetchedAt] are derived from both parts:
 * - parts with `NOT_SUPPORTED` or `NO_DATA` are ignored, unless no other part is left: then `NO_DATA` when any part has
 *   no data, otherwise `NOT_SUPPORTED`;
 * - otherwise the worst status of the remaining parts wins, in the order `SOURCE_ERROR`, `LIMIT_REACHED`, `CACHED`, `FRESH`;
 * - [fetchedAt] is the oldest fetch time of the returned data, null when no data is returned.
 *
 * @property quote latest quote
 * @property ratios price and financial ratios
 */
data class PriceAndRatios(
    val quote: SourceData<QuoteRecord>,
    val ratios: SourceData<Ratios>,
) {
    /** Source of both parts. */
    val source: SourceId get() = quote.source

    /** Combined status of both parts. */
    val status: DataStatus get() = combinedStatus(listOf(quote.status, ratios.status))

    /** Oldest fetch time of the returned data. */
    val fetchedAt: Instant? get() = listOfNotNull(quote.fetchedAt, ratios.fetchedAt).minOrNull()

    private companion object {
        private val IGNORED = setOf(DataStatus.NOT_SUPPORTED, DataStatus.NO_DATA)
        private val SEVERITY = listOf(DataStatus.SOURCE_ERROR, DataStatus.LIMIT_REACHED, DataStatus.CACHED, DataStatus.FRESH)

        fun combinedStatus(statuses: List<DataStatus>): DataStatus {
            val relevant = statuses.filterNot { it in IGNORED }
            return when {
                relevant.isNotEmpty() -> relevant.minBy(SEVERITY::indexOf)
                DataStatus.NO_DATA in statuses -> DataStatus.NO_DATA
                else -> DataStatus.NOT_SUPPORTED
            }
        }
    }
}

/**
 * Company profile of a company from one source (detailed screen, "Company profile" tab).
 *
 * @property identity symbol, exchange, name and market resolved by the backend
 * @property profile profile data from the source with its status
 */
data class CompanyProfileData(
    val identity: CompanyIdentity,
    val profile: SourceData<CompanyProfile>,
)
