package com.sixteenscreener.app.domain.model

import java.time.Instant

/** Kind of data a source provides. */
enum class Capability { SEARCH, QUOTE, RATIOS, DIVIDENDS, BALANCE_SHEET, COMPANY_PROFILE, LOGO, UNKNOWN }

/** Interval a request limit applies to. */
enum class LimitInterval { MINUTE, HOUR, DAY, MONTH, UNKNOWN }

/** What a request limit counts. */
enum class LimitUnit { REQUESTS, UNIQUE_SYMBOLS, UNKNOWN }

/** Result of the last call to a source. */
enum class CallResult { SUCCESS, HTTP_ERROR, TIMEOUT, APP_ERROR, UNKNOWN }

/**
 * Request limit of a source with its current usage.
 */
data class RequestLimit(
    /** Interval the limit applies to. */
    val interval: LimitInterval,
    /** What is counted. */
    val unit: LimitUnit,
    /** Maximum count per interval. */
    val limit: Int,
    /** Count used in the current interval. */
    val used: Int,
    /** When the current interval ends, if known. */
    val resetsAt: Instant?,
)

/**
 * Outcome of the last call to a source, shown as the connection indicator.
 */
data class SourceCallOutcome(
    /** Result of the call. */
    val result: CallResult,
    /** Response status code; set for [CallResult.HTTP_ERROR]. */
    val httpStatus: Int?,
    /** When the call was made. */
    val at: Instant,
)

/**
 * Configured external data source.
 */
data class Source(
    /** Source identifier. */
    val id: SourceId,
    /** Display name. */
    val name: String,
    /** Kinds of data the source provides. */
    val capabilities: List<Capability>,
    /** Whether the source can be chosen as the profile's default source. */
    val selectableAsDefault: Boolean,
    /** Whether this is the profile's default source. */
    val isDefault: Boolean,
    /** Request limits with current usage. */
    val limits: List<RequestLimit>,
    /** Outcome of the last call, `null` if the source has not been called yet. */
    val lastCall: SourceCallOutcome?,
    /** Calls to the source since the counter was introduced, `null` if the source has no total counter. */
    val totalCalls: Long? = null,
)
