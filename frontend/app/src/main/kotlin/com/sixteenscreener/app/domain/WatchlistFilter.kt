package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem

/** Market filter of the watchlist; companies without a market are shown only with [ALL]. */
enum class MarketFilter(val market: Market?) {
    ALL(null),
    US(Market.US),
    EU(Market.EU),
}

/**
 * In-memory watchlist filter by market, sector and user tag; [sector] `null` means all sectors, [tag] `null` all companies with or
 * without tags.
 */
data class WatchlistFilter(
    val market: MarketFilter = MarketFilter.ALL,
    val sector: String? = null,
    val tag: Tag? = null,
) {
    fun matches(company: WatchlistItem): Boolean =
        (market == MarketFilter.ALL || company.market == market.market) &&
            (sector == null || company.sector == sector) &&
            (tag == null || TagRules.contains(company.tags, tag))

    fun apply(companies: List<WatchlistItem>): List<WatchlistItem> = companies.filter(::matches)

    /** This filter with the sector and the tag reset to all when [companies] no longer contain the selected one. */
    fun reconciledWith(companies: List<WatchlistItem>): WatchlistFilter = copy(
        sector = sector?.takeIf { it in sectorsOf(companies) },
        tag = tag?.takeIf { TagRules.contains(tagsOf(companies), it) },
    )

    companion object {
        /** Distinct non-blank sectors of [companies], sorted alphabetically. */
        fun sectorsOf(companies: List<WatchlistItem>): List<String> = companies
            .mapNotNull { it.sector?.takeIf(String::isNotBlank) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER.thenBy { it })

        /** Distinct tags of [companies] (same tags count once), sorted by text ignoring case, then by icon. */
        fun tagsOf(companies: List<WatchlistItem>): List<Tag> = TagRules.distinctSorted(companies.flatMap { it.tags })
    }
}
