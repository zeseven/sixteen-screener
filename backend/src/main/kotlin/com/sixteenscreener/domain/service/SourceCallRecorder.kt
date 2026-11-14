package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.port.outbound.SourceCallOutcomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class SourceCallRecorder internal constructor(private val repository: SourceCallOutcomeRepository) {
    private val latest = ConcurrentHashMap<SourceId, SourceCallOutcome>()

    fun record(source: SourceId, outcome: SourceCallOutcome) {
        latest.merge(source, outcome) { current, new -> if (new.at >= current.at) new else current }
    }

    fun record(source: SourceId, result: SourceResult<*>) {
        result.outcome?.let { record(source, it) }
    }

    internal suspend fun flush() {
        val outcomes = latest.toMap()
        latest.clear()
        coroutineScope {
            outcomes.forEach { (source, outcome) -> launch { write(source, outcome) } }
        }
    }

    private suspend fun write(source: SourceId, outcome: SourceCallOutcome) {
        try {
            repository.record(source, outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to record the call outcome of source '{}'", source, e)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(SourceCallRecorder::class.java)
    }
}

class SourceCallOutcomes(private val repository: SourceCallOutcomeRepository) {

    suspend fun <R> recording(block: suspend (SourceCallRecorder) -> R): R {
        val recorder = SourceCallRecorder(repository)
        try {
            return block(recorder)
        } finally {
            withContext(NonCancellable) { recorder.flush() }
        }
    }

    suspend fun lastCalls(sources: Collection<SourceId>): Map<SourceId, SourceCallOutcome> = repository.findAll(sources)
}
