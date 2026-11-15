package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.domain.model.Market

/** Market filter of the watchlist; companies without a market are shown only with [ALL]. */
enum class MarketFilter(val market: Market?) {
    ALL(null),
    US(Market.US),
    EU(Market.EU),
}

/**
 * In-memory watchlist filter by market and sector; [sector] `null` means all sectors.
 */
data class WatchlistFilter(
    val market: MarketFilter = MarketFilter.ALL,
    val sector: String? = null,
) {
    fun matches(company: WatchlistItem): Boolean =
        (market == MarketFilter.ALL || company.market == market.market) &&
            (sector == null || company.sector == sector)

    fun apply(companies: List<WatchlistItem>): List<WatchlistItem> = companies.filter(::matches)

    /** This filter with the sector reset to all when [companies] no longer contain the selected sector. */
    fun reconciledWith(companies: List<WatchlistItem>): WatchlistFilter =
        if (sector == null || sector in sectorsOf(companies)) this else copy(sector = null)

    companion object {
        /** Distinct non-blank sectors of [companies], sorted alphabetically. */
        fun sectorsOf(companies: List<WatchlistItem>): List<String> = companies
            .mapNotNull { it.sector?.takeIf(String::isNotBlank) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER.thenBy { it })
    }
}
