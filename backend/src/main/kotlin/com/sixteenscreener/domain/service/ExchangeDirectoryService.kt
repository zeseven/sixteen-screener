package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.ExchangeDirectory
import com.sixteenscreener.port.outbound.ExchangeDirectoryStore
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.port.outbound.SymbolDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ExchangeDirectoryPolicy(
    val maxAge: Duration = Duration.ofDays(1),
    val retryAfterFailure: Duration = Duration.ofMinutes(5),
    val retryAfterUnavailable: Duration = Duration.ofDays(1),
)

class ExchangeDirectoryService(
    private val source: MarketDataSource,
    private val store: ExchangeDirectoryStore,
    private val guard: SourceCallGuard,
    private val outcomes: SourceCallOutcomes,
    private val clock: Clock,
    private val policy: ExchangeDirectoryPolicy = ExchangeDirectoryPolicy(),
) : SymbolDirectory {

    private class LoadedDirectory(
        val listings: Map<Symbol, ListedSymbol>,
        val downloadedAt: Instant?,
        val nextRefreshAt: Instant,
    )

    private val directories = ConcurrentHashMap<ExchangeCode, LoadedDirectory>()
    private val locks = ConcurrentHashMap<ExchangeCode, Mutex>()

    override suspend fun find(symbol: Symbol): ListedSymbol? = listingsOf(symbol.exchangeCode)[symbol]

    override suspend fun findAll(symbols: Collection<Symbol>): Map<Symbol, ListedSymbol> = coroutineScope {
        symbols.distinct()
            .groupBy { it.exchangeCode }
            .map { (exchangeCode, group) ->
                async {
                    val listings = listingsOf(exchangeCode)
                    group.mapNotNull { symbol -> listings[symbol]?.let { symbol to it } }
                }
            }
            .awaitAll()
            .flatten()
            .toMap()
    }

    private suspend fun listingsOf(exchangeCode: ExchangeCode): Map<Symbol, ListedSymbol> =
        current(exchangeCode)
            ?: locks.computeIfAbsent(exchangeCode) { Mutex() }.withLock {
                current(exchangeCode) ?: refresh(exchangeCode).also { directories[exchangeCode] = it }.listings
            }

    private fun current(exchangeCode: ExchangeCode): Map<Symbol, ListedSymbol>? =
        directories[exchangeCode]?.takeIf { clock.now() < it.nextRefreshAt }?.listings

    private suspend fun refresh(exchangeCode: ExchangeCode): LoadedDirectory {
        val known = newest(directories[exchangeCode], loadStored(exchangeCode))
        return known?.takeIf { clock.now() < it.nextRefreshAt } ?: download(exchangeCode, known)
    }

    private suspend fun download(exchangeCode: ExchangeCode, stale: LoadedDirectory?): LoadedDirectory {
        val result = outcomes.recording { recorder ->
            guard.single(source.id, recorder) { source.exchangeSymbols(exchangeCode) }
        }
        val now = clock.now()
        return when (result) {
            is SourceResult.Success -> {
                val directory = ExchangeDirectory(source.id, exchangeCode, result.data, now)
                saveStored(directory)
                loaded(directory)
            }
            is SourceResult.NoData, is SourceResult.NotSupported -> {
                logger.warn("Source '{}' has no symbol list for exchange '{}'", source.id, exchangeCode)
                retained(stale, now + policy.retryAfterUnavailable)
            }
            is SourceResult.LimitReached, is SourceResult.SourceError -> {
                logger.warn("Symbol list of exchange '{}' not refreshed: {}", exchangeCode, result.dataStatus)
                retained(stale, now + policy.retryAfterFailure)
            }
        }
    }

    private fun newest(first: LoadedDirectory?, second: LoadedDirectory?): LoadedDirectory? =
        listOfNotNull(first, second).maxByOrNull { it.downloadedAt ?: Instant.MIN }
            ?.takeIf { it.downloadedAt != null }

    private fun loaded(directory: ExchangeDirectory) = LoadedDirectory(
        listings = directory.symbols.associateBy { it.symbol },
        downloadedAt = directory.downloadedAt,
        nextRefreshAt = directory.downloadedAt + policy.maxAge,
    )

    private fun retained(stale: LoadedDirectory?, nextRefreshAt: Instant) =
        LoadedDirectory(stale?.listings.orEmpty(), stale?.downloadedAt, nextRefreshAt)

    private suspend fun loadStored(exchangeCode: ExchangeCode): LoadedDirectory? =
        guarded("load the stored symbol list of exchange '$exchangeCode'") { store.load(source.id, exchangeCode) }
            ?.let(::loaded)

    private suspend fun saveStored(directory: ExchangeDirectory) {
        guarded("store the symbol list of exchange '${directory.exchangeCode}'") { store.save(directory) }
    }

    private suspend fun <T> guarded(action: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to {}", action, e)
            null
        }

    private companion object {
        private val logger = LoggerFactory.getLogger(ExchangeDirectoryService::class.java)
    }
}
