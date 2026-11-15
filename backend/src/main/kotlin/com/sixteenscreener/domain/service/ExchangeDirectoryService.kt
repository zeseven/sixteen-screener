package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.SymbolLookup
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
    val maxAge: Duration = Duration.ofDays(7),
    val refreshOnMissAfter: Duration = Duration.ofDays(1),
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

    private enum class Coverage { COVERED, NOT_COVERED, UNAVAILABLE }

    private class LoadedDirectory(
        val listings: Map<Symbol, ListedSymbol>,
        val downloadedAt: Instant?,
        val nextRefreshAt: Instant,
        val coverage: Coverage,
    )

    private val directories = ConcurrentHashMap<ExchangeCode, LoadedDirectory>()
    private val locks = ConcurrentHashMap<ExchangeCode, Mutex>()
    private val lastDownloadAttempts = ConcurrentHashMap<ExchangeCode, Instant>()

    override suspend fun lookup(symbol: Symbol): SymbolLookup = lookupIn(symbol.exchangeCode, listOf(symbol)).getValue(symbol)

    override suspend fun lookupAll(symbols: Collection<Symbol>): Map<Symbol, SymbolLookup> = coroutineScope {
        symbols.distinct()
            .groupBy { it.exchangeCode }
            .map { (exchangeCode, group) -> async { lookupIn(exchangeCode, group) } }
            .awaitAll()
            .fold(emptyMap()) { all, lookups -> all + lookups }
    }

    private suspend fun lookupIn(exchangeCode: ExchangeCode, symbols: List<Symbol>): Map<Symbol, SymbolLookup> {
        val directory = directoryOf(exchangeCode)
        val missed = symbols.any { it !in directory.listings }
        val searched = if (missed && isMissRefreshDue(exchangeCode, directory)) refreshAfterMiss(exchangeCode, directory) else directory
        return symbols.associateWith { searched.lookup(it) }
    }

    private fun isMissRefreshDue(exchangeCode: ExchangeCode, directory: LoadedDirectory): Boolean {
        val lastDownloadAttempt = lastDownloadAttempts[exchangeCode]
        return directory.coverage == Coverage.COVERED &&
            directory.downloadedAt != null &&
            !directory.isYoungerThan(policy.refreshOnMissAfter) &&
            (lastDownloadAttempt == null || clock.now() >= lastDownloadAttempt + policy.refreshOnMissAfter)
    }

    private fun LoadedDirectory.isYoungerThan(age: Duration): Boolean =
        downloadedAt?.let { clock.now() < it + age } ?: false

    private suspend fun refreshAfterMiss(exchangeCode: ExchangeCode, missedIn: LoadedDirectory): LoadedDirectory =
        lockOf(exchangeCode).withLock {
            val directory = directories[exchangeCode] ?: missedIn
            if (!isMissRefreshDue(exchangeCode, directory)) return@withLock directory
            val known = newest(directory, loadStored(exchangeCode))
            val recent = known?.takeIf { it.isYoungerThan(policy.refreshOnMissAfter) }
            (recent ?: download(exchangeCode, known)).also { directories[exchangeCode] = it }
        }

    private fun LoadedDirectory.lookup(symbol: Symbol): SymbolLookup =
        listings[symbol]?.let(SymbolLookup::Listed) ?: when (coverage) {
            Coverage.COVERED -> SymbolLookup.NotListed
            Coverage.NOT_COVERED -> SymbolLookup.NotCovered(source.id)
            Coverage.UNAVAILABLE -> SymbolLookup.Unavailable
        }

    private suspend fun directoryOf(exchangeCode: ExchangeCode): LoadedDirectory =
        current(exchangeCode)
            ?: lockOf(exchangeCode).withLock {
                current(exchangeCode) ?: refresh(exchangeCode).also { directories[exchangeCode] = it }
            }

    private fun lockOf(exchangeCode: ExchangeCode): Mutex = locks.computeIfAbsent(exchangeCode) { Mutex() }

    private fun current(exchangeCode: ExchangeCode): LoadedDirectory? =
        directories[exchangeCode]?.takeIf { clock.now() < it.nextRefreshAt }

    private suspend fun refresh(exchangeCode: ExchangeCode): LoadedDirectory {
        val known = newest(directories[exchangeCode], loadStored(exchangeCode))
        return known?.takeIf { clock.now() < it.nextRefreshAt } ?: download(exchangeCode, known)
    }

    private suspend fun download(exchangeCode: ExchangeCode, stale: LoadedDirectory?): LoadedDirectory {
        lastDownloadAttempts[exchangeCode] = clock.now()
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
                retained(stale, now + policy.retryAfterUnavailable, Coverage.NOT_COVERED)
            }
            is SourceResult.LimitReached, is SourceResult.SourceError -> {
                logger.warn("Symbol list of exchange '{}' not refreshed: {}", exchangeCode, result.dataStatus)
                retained(stale, now + policy.retryAfterFailure, if (stale == null) Coverage.UNAVAILABLE else Coverage.COVERED)
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
        coverage = Coverage.COVERED,
    )

    private fun retained(stale: LoadedDirectory?, nextRefreshAt: Instant, coverage: Coverage) =
        LoadedDirectory(stale?.listings.orEmpty(), stale?.downloadedAt, nextRefreshAt, coverage)

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
