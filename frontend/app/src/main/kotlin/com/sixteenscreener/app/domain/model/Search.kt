package com.sixteenscreener.app.domain.model

/** Outcome of a company search at the source. */
enum class SearchStatus { OK, LIMIT_REACHED, SOURCE_ERROR, UNKNOWN }

/**
 * Companies found by a search of the profile's default source.
 */
data class SearchResults(
    /** Source that was searched. */
    val source: SourceId,
    /** Outcome of the search; items are empty unless [SearchStatus.OK]. */
    val status: SearchStatus,
    /** Found companies, with a quote only when it is cached. */
    val items: List<CompanySummary>,
)
