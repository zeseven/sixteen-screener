package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId

/**
 * Last call outcome per source (`sourceCalls` collection, ADR-028).
 */
interface SourceCallOutcomeRepository {
    /** Stores [outcome] as the last call outcome of [source], replacing the previous one. */
    suspend fun record(source: SourceId, outcome: SourceCallOutcome)

    /** Last call outcomes of [sources]; sources never called are absent from the result. */
    suspend fun findAll(sources: Collection<SourceId>): Map<SourceId, SourceCallOutcome>
}
