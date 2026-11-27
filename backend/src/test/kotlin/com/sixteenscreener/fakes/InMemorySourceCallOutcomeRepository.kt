package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.SourceCallOutcomeRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InMemorySourceCallOutcomeRepository : SourceCallOutcomeRepository {
    private val outcomes = ConcurrentHashMap<SourceId, SourceCallOutcome>()
    private val recorded = mutableListOf<Pair<SourceId, SourceCallOutcome>>()

    var failWrites: Boolean = false
    var failReads: Boolean = false

    private val readCount = AtomicInteger()

    val reads: Int get() = readCount.get()

    val writes: List<Pair<SourceId, SourceCallOutcome>> get() = synchronized(recorded) { recorded.toList() }

    override suspend fun record(source: SourceId, outcome: SourceCallOutcome) {
        check(!failWrites) { "Simulated outcome write failure" }
        outcomes[source] = outcome
        synchronized(recorded) { recorded += source to outcome }
    }

    override suspend fun findAll(sources: Collection<SourceId>): Map<SourceId, SourceCallOutcome> {
        readCount.incrementAndGet()
        check(!failReads) { "Simulated outcome read failure" }
        return sources.mapNotNull { source -> outcomes[source]?.let { source to it } }.toMap()
    }
}
