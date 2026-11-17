package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Logo
import com.sixteenscreener.domain.model.LogoOrigin
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.inbound.LogoUseCase
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.LogoMissRepository
import com.sixteenscreener.port.outbound.LogoProvider
import com.sixteenscreener.port.outbound.LogoStorage
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Duration

class LogoService(
    private val storage: LogoStorage,
    private val provider: LogoProvider?,
    private val misses: LogoMissRepository,
    private val guard: SourceCallGuard,
    private val outcomes: SourceCallOutcomes,
    private val clock: Clock,
    private val defaultLogo: ByteArray,
    private val missTtl: Duration = DEFAULT_MISS_TTL,
) : LogoUseCase {

    override suspend fun logo(symbol: Symbol): Logo {
        guarded("read the stored logo of '$symbol'") { storage.find(symbol) }
            ?.let { return Logo(it, LogoOrigin.STORED) }
        val source = provider ?: return defaultLogo(LogoOrigin.DEFAULT_UNAVAILABLE)
        if (isRecentMiss(source, symbol)) return defaultLogo(LogoOrigin.DEFAULT_MISSING)
        val result = outcomes.recording { recorder -> guard.single(source.id, recorder) { source.logo(symbol) } }
        return when (result) {
            is SourceResult.Success -> {
                guarded("store the logo of '$symbol'") { storage.save(symbol, result.data) }
                Logo(result.data, LogoOrigin.DOWNLOADED)
            }
            is SourceResult.NoData -> {
                guarded("record the missing logo of '$symbol'") { misses.recordMiss(source.id, symbol, clock.now()) }
                defaultLogo(LogoOrigin.DEFAULT_MISSING)
            }
            is SourceResult.NotSupported, is SourceResult.LimitReached, is SourceResult.SourceError ->
                defaultLogo(LogoOrigin.DEFAULT_UNAVAILABLE)
        }
    }

    private suspend fun isRecentMiss(source: LogoProvider, symbol: Symbol): Boolean {
        val lastMiss = guarded("read the missing logo record of '$symbol'") { misses.lastMiss(source.id, symbol) } ?: return false
        return clock.now() < lastMiss.plus(missTtl)
    }

    private fun defaultLogo(origin: LogoOrigin) = Logo(defaultLogo, origin)

    private suspend fun <T> guarded(action: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to {}", action, e)
            null
        }

    companion object {
        val DEFAULT_MISS_TTL: Duration = Duration.ofDays(14)

        private val logger = LoggerFactory.getLogger(LogoService::class.java)
    }
}
