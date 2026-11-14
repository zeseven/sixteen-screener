package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.SearchStatus
import com.sixteenscreener.domain.model.SourceId

/**
 * One search result.
 *
 * @property company identity, cached sector and cached quote of the company
 * @property inWatchlist whether the company is in the profile's watchlist
 */
data class SearchResult(
    val company: CompanySummary,
    val inWatchlist: Boolean,
)

/**
 * Results of a search.
 *
 * @property source source that was searched
 * @property status outcome of the search; items are empty unless [SearchStatus.OK]
 * @property items results in the order of the source
 */
data class SearchResults(
    val source: SourceId,
    val status: SearchStatus,
    val items: List<SearchResult>,
)

/**
 * Company search in the profile's default source (ADR-029, ADR-034, ADR-035).
 */
interface SearchUseCase {
    /**
     * Searches the default source of profile [profileId] by ticker or the first letters of a company name.
     *
     * Exchange, market and name of each result are resolved from the exchange directory; results not found there are
     * returned with best-effort data (the exchange code as exchange name, the name from the source, no market).
     * Quotes and sectors are taken from the cache only, no requests are made for them. Successful source answers are
     * kept in memory for 5 minutes, so the same query is answered with `OK` without a source request even when
     * the limit is reached. A reached limit or a failed source call gives empty items with `LIMIT_REACHED` or
     * `SOURCE_ERROR`; a default source without the search capability gives `SOURCE_ERROR` without a call.
     *
     * Throws [com.sixteenscreener.domain.model.InvalidInputException] when [rawQuery] is shorter than 3 or longer than
     * 50 characters after trimming, and [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun search(profileId: ProfileId, rawQuery: String): SearchResults
}
