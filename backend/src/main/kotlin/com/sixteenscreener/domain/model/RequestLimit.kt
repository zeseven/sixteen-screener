package com.sixteenscreener.domain.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Interval of a request limit. Counter windows are aligned to UTC calendar boundaries.
 */
enum class LimitInterval {
    MINUTE,
    HOUR,
    DAY,
    MONTH,
    ;

    /** Start of the window containing [instant]. */
    fun windowStart(instant: Instant): Instant = when (this) {
        MINUTE -> instant.truncatedTo(ChronoUnit.MINUTES)
        HOUR -> instant.truncatedTo(ChronoUnit.HOURS)
        DAY -> instant.truncatedTo(ChronoUnit.DAYS)
        MONTH -> instant.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    /** End of the window containing [instant] (exclusive), i.e. when its counter resets. */
    fun windowEnd(instant: Instant): Instant {
        val start = windowStart(instant)
        return when (this) {
            MINUTE -> start.plus(1, ChronoUnit.MINUTES)
            HOUR -> start.plus(1, ChronoUnit.HOURS)
            DAY -> start.plus(1, ChronoUnit.DAYS)
            MONTH -> start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()
        }
    }
}

/**
 * What a limit counts.
 */
enum class LimitUnit {
    /** Requests sent to the source. */
    REQUESTS,

    /** Unique symbols requested (Tiingo's monthly symbol limit). */
    UNIQUE_SYMBOLS,
}

/**
 * Request limit of a source, or a counter without a limit (ADR-089).
 *
 * @property interval counter window
 * @property limit maximum count per window; null for a counter that counts and resets with its window but never
 * refuses a request
 * @property unit what is counted
 */
data class RequestLimit(
    val interval: LimitInterval,
    val limit: Int?,
    val unit: LimitUnit = LimitUnit.REQUESTS,
) {
    init {
        require(limit == null || limit >= 0) { "Limit must not be negative" }
    }

    /** Whether this is a counter without a limit. */
    val isUnlimited: Boolean get() = limit == null

    /** Calls still allowed in a window that has counted [used] calls; never negative, [Long.MAX_VALUE] without a limit. */
    fun remaining(used: Long): Long = limit?.let { (it - used).coerceAtLeast(0L) } ?: Long.MAX_VALUE
}

/**
 * Current usage of a request limit.
 *
 * @property limit the limit
 * @property used count in the current window
 * @property resetsAt end of the current window
 */
data class RequestUsage(
    val limit: RequestLimit,
    val used: Int,
    val resetsAt: Instant,
)
