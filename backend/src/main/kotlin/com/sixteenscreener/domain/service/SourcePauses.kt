package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.Clock
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Pauses of sources configured with a pause after a `429` (ADR-090). A source is paused while its last call outcome is an
 * HTTP `429` younger than the pause; the outcome is shared by all instances, so the pause is too.
 */
class SourcePauses(
    private val pauseAfterRateLimit: Map<SourceId, Duration>,
    private val outcomes: SourceCallOutcomes,
    private val clock: Clock,
) {

    fun hasPause(source: SourceId): Boolean = source in pauseAfterRateLimit

    fun pausedUntil(source: SourceId, lastCall: SourceCallOutcome?): Instant? {
        val pause = pauseAfterRateLimit[source] ?: return null
        val rateLimitedAt = lastCall?.takeIf { it.isRateLimited }?.at ?: return null
        return (rateLimitedAt + pause).takeIf { it.isAfter(clock.now()) }
    }

    suspend fun isPaused(source: SourceId): Boolean =
        hasPause(source) && pausedUntil(source, lastCallOf(source)) != null

    private suspend fun lastCallOf(source: SourceId): SourceCallOutcome? =
        try {
            outcomes.lastCalls(listOf(source))[source]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to read the last call of source '{}'; treating it as not paused", source, e)
            null
        }

    private companion object {
        private val logger = LoggerFactory.getLogger(SourcePauses::class.java)
    }
}
