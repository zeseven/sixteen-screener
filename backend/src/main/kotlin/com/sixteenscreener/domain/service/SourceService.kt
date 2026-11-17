package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.RequestUsage
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceDescription
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceOverview
import com.sixteenscreener.port.inbound.SourcesUseCase
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class SourceService(
    private val sources: List<SourceDescription>,
    private val profiles: ProfileRepository,
    private val catalog: SourceCatalog,
    private val rateLimiter: RateLimiter,
    private val outcomes: SourceCallOutcomes,
    private val clock: Clock,
) : SourcesUseCase {

    override suspend fun sources(profileId: ProfileId): List<SourceOverview> = coroutineScope {
        val defaultSource = catalog.defaultSourceOf(profiles.existing(profileId))
        val lastCalls = async { lastCalls() }
        val totalCalls = async { totalCalls() }
        val usages = sources.map { source -> async { usage(source.id) } }
        sources.zip(usages.awaitAll()) { source, usage ->
            SourceOverview(source, source.id == defaultSource, usage, lastCalls.await()[source.id], totalCalls.await()[source.id])
        }
    }

    private suspend fun lastCalls(): Map<SourceId, SourceCallOutcome> =
        try {
            outcomes.lastCalls(sources.map { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to read the last source calls", e)
            emptyMap()
        }

    private suspend fun totalCalls(): Map<SourceId, Long> =
        try {
            rateLimiter.totalCalls(sources.map { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to read the total source calls", e)
            emptyMap()
        }

    private suspend fun usage(source: SourceId): List<RequestUsage> =
        try {
            rateLimiter.usage(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to read the request counters of source '{}'", source, e)
            val now = clock.now()
            rateLimiter.limitsOf(source).map { RequestUsage(it, used = 0, resetsAt = it.interval.windowEnd(now)) }
        }

    private companion object {
        private val logger = LoggerFactory.getLogger(SourceService::class.java)
    }
}
