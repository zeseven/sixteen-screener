package com.sixteenscreener.domain.model

import java.time.Instant

/**
 * Origin and freshness of company data returned for one source (`SourceDataMeta.status` of the API spec).
 */
enum class DataStatus {
    /** Received from the source within the freshness window. */
    FRESH,

    /** Stale cached data; no refresh was requested. */
    CACHED,

    /** A request was needed but the source limit is reached; cached or empty data returned. */
    LIMIT_REACHED,

    /** The source call failed (response outside 2XX, timeout, application error); cached or empty data returned. */
    SOURCE_ERROR,

    /** The source doesn't provide this data. */
    NOT_SUPPORTED,

    /** The source has no data for the company. */
    NO_DATA,

    /** Nothing is cached and the source wasn't called: its automatic requests are off and the request wasn't manual. */
    NOT_REQUESTED,
}

/**
 * Outcome of a search (`SearchResults.status` of the API spec, ADR-029).
 */
enum class SearchStatus {
    /** The source was searched, or the same query was answered recently. */
    OK,

    /** The source limit is reached; no results. */
    LIMIT_REACHED,

    /** The source call failed; no results. */
    SOURCE_ERROR,
}

/**
 * Company data for one source together with its status and fetch time; [data] is null when nothing is available.
 *
 * @property source source the data belongs to
 * @property status origin and freshness of the data
 * @property data the data, or null when none is returned
 * @property fetchedAt when [data] was received from the source; null when [data] is null
 */
data class SourceData<out T>(
    val source: SourceId,
    val status: DataStatus,
    val data: T?,
    val fetchedAt: Instant?,
)
