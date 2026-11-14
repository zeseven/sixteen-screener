package com.sixteenscreener.domain.model

/**
 * Result of a request to a source. Every variant except [LimitReached] without an outcome stands for a call that was made;
 * [outcome] is the raw call outcome to record for the connection indicator (ADR-028).
 */
sealed interface SourceResult<out T> {
    /** Raw outcome of the call; null when no call was made. */
    val outcome: SourceCallOutcome?

    /** Status of the returned data as seen by the caller. */
    val dataStatus: DataStatus

    /**
     * The source returned data.
     *
     * @property data mapped data
     */
    data class Success<out T>(val data: T, override val outcome: SourceCallOutcome) : SourceResult<T> {
        override val dataStatus: DataStatus get() = DataStatus.FRESH
    }

    /** The source answered but has no data for the request (e.g. unknown symbol, empty quote). */
    data class NoData(override val outcome: SourceCallOutcome) : SourceResult<Nothing> {
        override val dataStatus: DataStatus get() = DataStatus.NO_DATA
    }

    /**
     * The source doesn't provide this data (e.g. `403` for a symbol outside the plan, or a non-US symbol for SEC EDGAR);
     * [outcome] is null when this was decided without a call.
     */
    data class NotSupported(override val outcome: SourceCallOutcome?) : SourceResult<Nothing> {
        override val dataStatus: DataStatus get() = DataStatus.NOT_SUPPORTED
    }

    /**
     * The request limit is reached: [outcome] is the `429` call outcome when the source rejected the call,
     * null when the call was skipped because our own counters were exhausted.
     */
    data class LimitReached(override val outcome: SourceCallOutcome?) : SourceResult<Nothing> {
        override val dataStatus: DataStatus get() = DataStatus.LIMIT_REACHED

        /** Whether the source itself answered `429`, so the counters of the source must be saturated (ADR-031). */
        val rejectedBySource: Boolean get() = outcome != null
    }

    /** The call failed: response outside 2XX, timeout or application error. */
    data class SourceError(override val outcome: SourceCallOutcome) : SourceResult<Nothing> {
        override val dataStatus: DataStatus get() = DataStatus.SOURCE_ERROR
    }
}

/** Transforms the data of a [SourceResult.Success], keeping every other variant unchanged. */
inline fun <T, R> SourceResult<T>.map(transform: (T) -> R): SourceResult<R> = when (this) {
    is SourceResult.Success -> SourceResult.Success(transform(data), outcome)
    is SourceResult.NoData -> this
    is SourceResult.NotSupported -> this
    is SourceResult.LimitReached -> this
    is SourceResult.SourceError -> this
}
