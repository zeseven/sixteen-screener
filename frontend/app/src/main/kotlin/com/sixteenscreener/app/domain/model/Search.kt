package com.sixteenscreener.app.domain.model

/** Outcome of a company search at the source. */
enum class SearchStatus { OK, LIMIT_REACHED, SOURCE_ERROR, UNKNOWN }

/**
 * Search result with directory data only.
 */
data class SearchItem(
    override val symbol: Symbol,
    override val ticker: String,
    override val exchange: String,
    override val name: String,
    override val market: Market?,
    override val logoUrl: String,
    /** Whether the company is in the profile's watchlist. */
    val inWatchlist: Boolean,
) : Company

/**
 * Companies found by a search of the profile's default source.
 */
data class SearchResults(
    /** Source that was searched. */
    val source: SourceId,
    /** Outcome of the search; items are empty unless [SearchStatus.OK]. */
    val status: SearchStatus,
    /** Found companies. */
    val items: List<SearchItem>,
)
