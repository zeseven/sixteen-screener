package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ImageFormat
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

/**
 * Stored logos of one image format with the source that downloads them; the source is null when it is not enabled.
 *
 * @property storage stored logos
 * @property provider logo source, or null when it is not enabled
 */
class LogoTier(
    val storage: LogoStorage,
    val provider: LogoProvider?,
) {
    init {
        require(provider == null || provider.format == storage.format) {
            "Logo source '${provider?.id}' provides ${provider?.format} but its storage keeps ${storage.format}"
        }
    }
}

class LogoService(
    private val tiers: List<LogoTier>,
    private val misses: LogoMissRepository,
    private val guard: SourceCallGuard,
    private val outcomes: SourceCallOutcomes,
    private val clock: Clock,
    private val defaultLogo: ByteArray,
    private val missTtl: Duration = DEFAULT_MISS_TTL,
) : LogoUseCase {

    private sealed interface Step {
        class Found(val logo: Logo) : Step

        data object Missing : Step

        data object Failed : Step
    }

    private val steps: List<suspend (Symbol) -> Step> = buildList {
        tiers.forEach { tier -> add { symbol -> stored(tier.storage, symbol) } }
        tiers.forEach { tier -> tier.provider?.let { provider -> add { symbol -> downloaded(provider, tier.storage, symbol) } } }
    }

    private val hasProvider: Boolean = tiers.any { it.provider != null }

    override suspend fun logo(symbol: Symbol): Logo {
        var failed = false
        for (step in steps) {
            when (val result = step(symbol)) {
                is Step.Found -> return result.logo
                Step.Missing -> Unit
                Step.Failed -> failed = true
            }
        }
        return Logo(defaultLogo, ImageFormat.SVG, defaultOrigin(failed))
    }

    private fun defaultOrigin(failed: Boolean): LogoOrigin =
        if (failed || !hasProvider) LogoOrigin.DEFAULT_UNAVAILABLE else LogoOrigin.DEFAULT_MISSING

    private suspend fun stored(storage: LogoStorage, symbol: Symbol): Step =
        attempt("read the stored ${storage.format} logo of '$symbol'") { storage.find(symbol) }
            .fold(
                onSuccess = { image -> image?.let { Step.Found(Logo(it, storage.format, LogoOrigin.STORED)) } ?: Step.Missing },
                onFailure = { Step.Failed },
            )

    private suspend fun downloaded(provider: LogoProvider, storage: LogoStorage, symbol: Symbol): Step {
        if (isRecentMiss(provider, symbol)) return Step.Missing
        val result = attempt("download the logo of '$symbol' from '${provider.id}'") {
            outcomes.recording { recorder -> guard.single(provider.id, recorder) { provider.logo(symbol) } }
        }.getOrElse { return Step.Failed }
        return when (result) {
            is SourceResult.Success -> {
                attempt("store the logo of '$symbol' from '${provider.id}'") { storage.save(symbol, result.data) }
                Step.Found(Logo(result.data, provider.format, LogoOrigin.DOWNLOADED))
            }
            is SourceResult.NoData -> {
                attempt("record the missing logo of '$symbol' in '${provider.id}'") {
                    misses.recordMiss(provider.id, symbol, clock.now())
                }
                Step.Missing
            }
            is SourceResult.NotSupported, is SourceResult.LimitReached, is SourceResult.SourceError -> Step.Failed
        }
    }

    private suspend fun isRecentMiss(provider: LogoProvider, symbol: Symbol): Boolean {
        val lastMiss = attempt("read the missing logo record of '$symbol' in '${provider.id}'") {
            misses.lastMiss(provider.id, symbol)
        }.getOrNull() ?: return false
        return clock.now() < lastMiss.plus(missTtl)
    }

    private suspend fun <T> attempt(action: String, block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to {}", action, e)
            Result.failure(e)
        }

    companion object {
        val DEFAULT_MISS_TTL: Duration = Duration.ofDays(14)

        private val logger = LoggerFactory.getLogger(LogoService::class.java)
    }
}
