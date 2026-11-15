package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.CompanyProfileData
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.PriceAndRatios
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.inbound.CompanyDataUseCase
import com.sixteenscreener.port.outbound.BalanceSheetSource
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.CompanyCache
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.SymbolDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

class CompanyDataService(
    private val catalog: SourceCatalog,
    private val marketDataSources: Map<SourceId, MarketDataSource>,
    private val balanceSheetSources: Map<SourceId, BalanceSheetSource>,
    private val profiles: ProfileRepository,
    private val symbolDirectory: SymbolDirectory,
    private val cache: CompanyCache,
    private val rateLimiter: RateLimiter,
    private val guard: SourceCallGuard,
    private val outcomes: SourceCallOutcomes,
    private val freshness: FreshnessPolicy,
    private val marketResolver: MarketResolver,
    private val identities: CompanyIdentityResolver,
    private val watchlistCompanies: WatchlistCompanySync,
    private val clock: Clock,
    private val maxConcurrentCalls: Int = DEFAULT_MAX_CONCURRENT_CALLS,
) : CompanyDataUseCase {

    override suspend fun priceAndRatios(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        refresh: Boolean,
    ): PriceAndRatios {
        val target = target(profileId, symbol, source)
        val quoteKey = CacheKey(target.source, symbol, DataType.QUOTE)
        val ratiosKey = CacheKey(target.source, symbol, DataType.RATIOS)
        val cached = cache.getAll(listOf(quoteKey, ratiosKey))
        val loaded = load(
            target.source,
            listOf(
                detailedPlan(quoteKey, cached[quoteKey], refresh) { quoteFetch(quoteKey, it, target.listing) },
                detailedPlan(ratiosKey, cached[ratiosKey], refresh) { ratiosFetch(ratiosKey, it) },
            ),
        )
        return PriceAndRatios(loaded[quoteKey], loaded[ratiosKey])
    }

    override suspend fun balanceSheet(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        refresh: Boolean,
    ): SourceData<BalanceSheet> {
        val target = target(profileId, symbol, source)
        val key = CacheKey(target.source, symbol, DataType.BALANCE_SHEET)
        return loadSingle(key, refresh) { balanceSheetFetch(key, it) }
    }

    override suspend fun companyProfile(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        refresh: Boolean,
    ): CompanyProfileData {
        val target = target(profileId, symbol, source)
        val key = CacheKey(target.source, symbol, DataType.COMPANY_PROFILE)
        val cached = if (supports(key)) cache.get(key) else null
        val plan = detailedPlan(key, cached, refresh) { profileFetch(key, it) }
        val profile = load(key.source, listOf(plan))[key]
        val fetched = profile.data?.takeIf { plan is Fetch<*, *> && profile.status == DataStatus.FRESH }
        if (fetched != null) watchlistCompanies.profileFetched(target.profile, key.source, symbol, fetched)
        val identity = identities.identify(symbol, target.listing)
        return CompanyProfileData(profile.data?.name?.let { identity.copy(name = it) } ?: identity, profile)
    }

    suspend fun unexpiredCompanyProfile(source: SourceId, symbol: Symbol): SourceData<CompanyProfile> {
        val key = CacheKey(source, symbol, DataType.COMPANY_PROFILE)
        val cached = if (supports(key)) cache.get(key) else null
        return load(source, listOf(unexpiredProfilePlan(key, cached)))[key]
    }

    suspend fun watchlistQuotes(
        source: SourceId,
        symbols: List<Symbol>,
        listings: Map<Symbol, ListedSymbol>,
    ): Map<Symbol, SourceData<QuoteRecord>> {
        if (symbols.isEmpty()) return emptyMap()
        val keys = symbols.associateWith { CacheKey(source, it, DataType.QUOTE) }
        val cached = cache.getAll(keys.values)
        val plans = symbols.map { symbol ->
            val key = keys.getValue(symbol)
            watchlistQuotePlan(key, cached[key], listings[symbol])
        }
        val loaded = load(source, oldestFirst(plans))
        return symbols.associateWith { loaded[keys.getValue(it)] }
    }

    private data class Target(val profile: Profile, val source: SourceId, val listing: ListedSymbol)

    private suspend fun target(profileId: ProfileId, symbol: Symbol, requested: SourceId?): Target {
        val profile = profiles.existing(profileId)
        val source = catalog.selectSource(profile, requested)
        val listing = symbolDirectory.lookup(symbol).listing ?: throw ResourceNotFoundException("Unknown symbol '$symbol'")
        return Target(profile, source, listing)
    }

    private suspend fun <T : Any> loadSingle(
        key: CacheKey<T>,
        refresh: Boolean,
        fetch: (CacheEntry<T>?) -> Fetch<T, *>,
    ): SourceData<T> {
        val cached = if (supports(key)) cache.get(key) else null
        return load(key.source, listOf(detailedPlan(key, cached, refresh, fetch)))[key]
    }

    private fun <T : Any> detailedPlan(
        key: CacheKey<T>,
        cached: CacheEntry<T>?,
        refresh: Boolean,
        fetch: (CacheEntry<T>?) -> Fetch<T, *>,
    ): Plan<T> = when {
        !supports(key) -> notSupported(key)
        refresh || cached == null -> fetch(cached)
        else -> Settled(key, fromCache(key, cached))
    }

    private fun watchlistQuotePlan(
        key: CacheKey<QuoteRecord>,
        cached: CacheEntry<QuoteRecord>?,
        listing: ListedSymbol?,
    ): Plan<QuoteRecord> = when {
        !supports(key) -> notSupported(key)
        cached != null && freshness.isFresh(cached.fetchedAt) -> Settled(key, fromCache(key, cached))
        listing == null -> Settled(key, fallback(key, cached, DataStatus.SOURCE_ERROR))
        else -> quoteFetch(key, cached, listing)
    }

    private fun unexpiredProfilePlan(
        key: CacheKey<CompanyProfile>,
        cached: CacheEntry<CompanyProfile>?,
    ): Plan<CompanyProfile> = when {
        !supports(key) -> notSupported(key)
        cached != null && !freshness.isProfileExpired(cached.fetchedAt) -> Settled(key, fromCache(key, cached))
        else -> profileFetch(key, cached)
    }

    private fun quoteFetch(key: CacheKey<QuoteRecord>, cached: CacheEntry<QuoteRecord>?, listing: ListedSymbol) =
        Fetch(
            key = key,
            cached = cached,
            call = { marketData(key.source).quote(listing) },
            store = { quote: Quote, at ->
                cache.putQuote(key.source, key.symbol, quote, at, marketResolver.timeZoneOf(listing))
            },
            unstored = { quote, at ->
                QuoteRecord.next(cached?.data, quote, LocalDate.ofInstant(at, marketResolver.timeZoneOf(listing)))
            },
        )

    private fun ratiosFetch(key: CacheKey<Ratios>, cached: CacheEntry<Ratios>?) =
        storedAsIs(key, cached) { marketData(key.source).ratios(key.symbol) }

    private fun profileFetch(key: CacheKey<CompanyProfile>, cached: CacheEntry<CompanyProfile>?) =
        storedAsIs(key, cached) { marketData(key.source).companyProfile(key.symbol) }

    private fun balanceSheetFetch(key: CacheKey<BalanceSheet>, cached: CacheEntry<BalanceSheet>?) =
        storedAsIs(key, cached) { balanceSheetSources.getValue(key.source).balanceSheet(key.symbol) }

    private fun <T : Any> storedAsIs(key: CacheKey<T>, cached: CacheEntry<T>?, call: suspend () -> SourceResult<T>) =
        Fetch(
            key = key,
            cached = cached,
            call = call,
            store = { data: T, at -> CacheEntry(data, at).also { cache.put(key, it) } },
            unstored = { data, _ -> data },
        )

    private fun marketData(source: SourceId): MarketDataSource = marketDataSources.getValue(source)

    private fun supports(key: CacheKey<*>): Boolean {
        val hasAdapter = when (key.type) {
            DataType.BALANCE_SHEET -> key.source in balanceSheetSources
            DataType.QUOTE, DataType.RATIOS, DataType.COMPANY_PROFILE -> key.source in marketDataSources
        }
        return hasAdapter && catalog.supports(key.source, key.type.capability)
    }

    private fun oldestFirst(plans: List<Plan<*>>): List<Plan<*>> =
        plans.sortedWith(compareBy(nullsFirst()) { (it as? Fetch<*, *>)?.cached?.fetchedAt })

    private suspend fun load(source: SourceId, plans: List<Plan<*>>): Loaded {
        val settled = plans.filterIsInstance<Settled<*>>().associate { it.key to it.data }
        val fetches = plans.filterIsInstance<Fetch<*, *>>()
        if (fetches.isEmpty()) return Loaded(settled)
        val granted = rateLimiter.tryAcquire(source, fetches.size)
        val fetched = outcomes.recording { recorder -> runGranted(fetches, granted, recorder) }
        return Loaded(settled + fetched)
    }

    private suspend fun runGranted(
        fetches: List<Fetch<*, *>>,
        granted: Int,
        recorder: SourceCallRecorder,
    ): Map<CacheKey<*>, SourceData<*>> = coroutineScope {
        val semaphore = Semaphore(maxConcurrentCalls)
        val rejectedBySource = AtomicBoolean(false)
        fetches.mapIndexed { index, fetch ->
            async(start = CoroutineStart.UNDISPATCHED) {
                val data = if (index >= granted) {
                    fetch.limitReached()
                } else {
                    semaphore.withPermit {
                        if (rejectedBySource.get()) {
                            fetch.limitReached()
                        } else {
                            fetch.execute(recorder, onRejectedBySource = { rejectedBySource.set(true) })
                        }
                    }
                }
                fetch.key to data
            }
        }.awaitAll().toMap()
    }

    private fun <T : Any> fromCache(key: CacheKey<T>, entry: CacheEntry<T>): SourceData<T> {
        val status = if (freshness.isFresh(entry.fetchedAt)) DataStatus.FRESH else DataStatus.CACHED
        return SourceData(key.source, status, entry.data, entry.fetchedAt)
    }

    private fun <T : Any> notSupported(key: CacheKey<T>): Plan<T> =
        Settled(key, SourceData(key.source, DataStatus.NOT_SUPPORTED, data = null, fetchedAt = null))

    private sealed interface Plan<T : Any> {
        val key: CacheKey<T>
    }

    private class Settled<T : Any>(override val key: CacheKey<T>, val data: SourceData<T>) : Plan<T>

    private inner class Fetch<T : Any, R>(
        override val key: CacheKey<T>,
        val cached: CacheEntry<T>?,
        private val call: suspend () -> SourceResult<R>,
        private val store: suspend (R, Instant) -> CacheEntry<T>,
        private val unstored: (R, Instant) -> T,
    ) : Plan<T> {

        fun limitReached(): SourceData<T> = fallback(key, cached, DataStatus.LIMIT_REACHED)

        suspend fun execute(recorder: SourceCallRecorder, onRejectedBySource: () -> Unit): SourceData<T> {
            val result = guard.completed(key.source, recorder, call())
            if (result is SourceResult.LimitReached && result.rejectedBySource) onRejectedBySource()
            return when (result) {
                is SourceResult.Success -> {
                    val entry = storeOrKeep(result.data, clock.now())
                    SourceData(key.source, DataStatus.FRESH, entry.data, entry.fetchedAt)
                }
                else -> fallback(key, cached, result.dataStatus)
            }
        }

        private suspend fun storeOrKeep(data: R, at: Instant): CacheEntry<T> =
            try {
                store(data, at)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Failed to cache {} of '{}' from source '{}'", key.type, key.symbol, key.source, e)
                CacheEntry(unstored(data, at), at)
            }
    }

    private class Loaded(private val byKey: Map<CacheKey<*>, SourceData<*>>) {
        @Suppress("UNCHECKED_CAST")
        operator fun <T : Any> get(key: CacheKey<T>): SourceData<T> = byKey.getValue(key) as SourceData<T>
    }

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_CALLS = 5

        private val logger = LoggerFactory.getLogger(CompanyDataService::class.java)

        fun <T : Any> fallback(key: CacheKey<T>, cached: CacheEntry<T>?, status: DataStatus): SourceData<T> =
            SourceData(key.source, status, cached?.data, cached?.fetchedAt)
    }
}
