package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CacheEntries
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.CompanyProfileData
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.DividendsData
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.PriceAndRatios
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.RequestTrigger
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.SourceRole
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.SymbolLookup
import com.sixteenscreener.domain.model.WatchlistSourceData
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
    private val indicatorSettings: IndicatorSettingsService,
    private val clock: Clock,
    private val maxConcurrentCalls: Int = DEFAULT_MAX_CONCURRENT_CALLS,
) : CompanyDataUseCase {

    override suspend fun priceAndRatios(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): PriceAndRatios {
        val target = target(profileId, symbol, source)
        val quoteKey = CacheKey(target.source, symbol, DataType.QUOTE)
        val ratiosKey = CacheKey(target.source, symbol, DataType.RATIOS)
        val cached = cache.getAll(listOf(quoteKey, ratiosKey))
        val loaded = load(
            target.source,
            listOf(
                detailedPlan(quoteKey, cached[quoteKey], trigger) { quoteFetch(quoteKey, it, target.listing) },
                detailedPlan(ratiosKey, cached[ratiosKey], trigger) { ratiosFetch(ratiosKey, it) },
            ),
        )
        val quote = loaded[quoteKey]
        val ratios = loaded[ratiosKey]
        val settings = indicatorSettings.settingsOf(target.profile)
        return PriceAndRatios(quote, ratios, IndicatorEvaluator.indicators(settings, ratios.data, quote.data?.quote?.price))
    }

    override suspend fun dividends(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): DividendsData {
        val target = target(profileId, symbol, source)
        val key = CacheKey(target.source, symbol, DataType.DIVIDENDS)
        val quoteKey = CacheKey(target.source, symbol, DataType.QUOTE)
        val cached = if (supports(key)) cache.getAll(listOf(key, quoteKey)) else CacheEntries(emptyMap())
        val plan = detailedPlan(key, cached[key], trigger) { dividendsFetch(key, it) }
        val dividends = load(key.source, listOf(plan))[key]
        val quote = cached[quoteKey]?.data?.quote
        val asOf = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC)
        return DividendsData(dividends, quote?.let { dividends.data?.trailingYield(it, asOf) })
    }

    override suspend fun balanceSheet(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): SourceData<BalanceSheet> {
        val target = target(profileId, symbol, source)
        val key = CacheKey(target.source, symbol, DataType.BALANCE_SHEET)
        val cached = if (supports(key)) cache.get(key) else null
        return load(key.source, listOf(detailedPlan(key, cached, trigger) { balanceSheetFetch(key, it) }))[key]
    }

    override suspend fun companyProfile(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): CompanyProfileData {
        val target = target(profileId, symbol, source)
        val key = CacheKey(target.source, symbol, DataType.COMPANY_PROFILE)
        val cached = if (supports(key)) cache.get(key) else null
        val plan = detailedPlan(key, cached, trigger) { profileFetch(key, it) }
        val profile = load(key.source, listOf(plan))[key]
        val fetched = profile.data?.takeIf { plan is Fetch<*, *> && profile.status == DataStatus.FRESH }
        if (fetched != null) watchlistCompanies.profileFetched(target.profile, key.source, symbol, fetched)
        val identity = identities.identify(symbol, target.lookup.listing, target.profile.watchlistEntry(symbol)?.name)
        val exchange = identities.exchangeOf(symbol, target.lookup.listing)
        val homeExchange = identity.stock && exchange?.isHomeOf(profile.data?.country) == true
        return CompanyProfileData(profile.data?.name?.let { identity.copy(name = it) } ?: identity, profile, homeExchange)
    }

    suspend fun unexpiredCompanyProfile(source: SourceId, symbol: Symbol): SourceData<CompanyProfile> {
        val key = CacheKey(source, symbol, DataType.COMPANY_PROFILE)
        val cached = if (supports(key)) cache.get(key) else null
        return load(source, listOf(unexpiredProfilePlan(key, cached)))[key]
    }

    suspend fun watchlistData(
        source: SourceId,
        symbols: List<Symbol>,
        lookups: Map<Symbol, SymbolLookup>,
    ): Map<Symbol, WatchlistSourceData> {
        if (symbols.isEmpty()) return emptyMap()
        val quoteKeys = symbols.associateWith { CacheKey(source, it, DataType.QUOTE) }
        val ratiosKeys = symbols.associateWith { CacheKey(source, it, DataType.RATIOS) }
        val cached = cache.getAll(quoteKeys.values + ratiosKeys.values)
        val listingOf = { symbol: Symbol -> listingOf(symbol, lookups[symbol] ?: SymbolLookup.Unavailable) }
        val quotePlans = symbols.map { symbol ->
            quoteKeys.getValue(symbol).let { watchlistQuotePlan(it, listingOf(symbol), cached[it]) }
        }
        val ratiosPlans = symbols.map { symbol ->
            ratiosKeys.getValue(symbol).let { watchlistRatiosPlan(it, listingOf(symbol), cached[it]) }
        }
        val loaded = load(source, oldestFirst(quotePlans) + oldestFirst(ratiosPlans))
        return symbols.associateWith { WatchlistSourceData(loaded[quoteKeys.getValue(it)], loaded[ratiosKeys.getValue(it)]) }
    }

    private data class Target(
        val profile: Profile,
        val source: SourceId,
        val lookup: SymbolLookup,
        val listing: ListedSymbol,
    )

    private suspend fun target(profileId: ProfileId, symbol: Symbol, requested: SourceId?): Target {
        val profile = profiles.existing(profileId)
        val source = catalog.selectSource(requested, SourceRole.of(symbol))
        val lookup = symbolDirectory.lookup(symbol)
        val listing = listingOf(symbol, lookup) ?: throw ResourceNotFoundException("Unknown symbol '$symbol'")
        return Target(profile, source, lookup, listing)
    }

    private fun listingOf(symbol: Symbol, lookup: SymbolLookup): ListedSymbol? =
        lookup.listing ?: lookup.takeIf { it is SymbolLookup.NotCovered }?.let { marketResolver.staticListingOf(symbol) }

    private fun <T : Any> detailedPlan(
        key: CacheKey<T>,
        cached: CacheEntry<T>?,
        trigger: RequestTrigger,
        fetch: (CacheEntry<T>?) -> Fetch<T, *>,
    ): Plan<T> = when {
        !supports(key) -> notSupported(key)
        !shouldCall(key, cached, trigger, autoRefreshAfter = null) -> notCalled(key, cached)
        else -> fetch(cached)
    }

    private fun watchlistQuotePlan(
        key: CacheKey<QuoteRecord>,
        listing: ListedSymbol?,
        cached: CacheEntry<QuoteRecord>?,
    ): Plan<QuoteRecord> = when {
        !supports(key) -> notSupported(key)
        !shouldCall(key, cached, RequestTrigger.AUTO, FreshnessPolicy.FRESHNESS_WINDOW) -> notCalled(key, cached)
        listing == null -> Settled(key, fallback(key, cached, DataStatus.SOURCE_ERROR))
        else -> quoteFetch(key, cached, listing)
    }

    private fun watchlistRatiosPlan(
        key: CacheKey<Ratios>,
        listing: ListedSymbol?,
        cached: CacheEntry<Ratios>?,
    ): Plan<Ratios> = when {
        !supports(key) -> notSupported(key)
        !shouldCall(key, cached, RequestTrigger.AUTO, FreshnessPolicy.WATCHLIST_RATIOS_MAX_AGE) -> notCalled(key, cached)
        listing == null -> Settled(key, fallback(key, cached, DataStatus.SOURCE_ERROR))
        else -> ratiosFetch(key, cached)
    }

    private fun unexpiredProfilePlan(
        key: CacheKey<CompanyProfile>,
        cached: CacheEntry<CompanyProfile>?,
    ): Plan<CompanyProfile> = when {
        !supports(key) -> notSupported(key)
        !shouldCall(key, cached, RequestTrigger.AUTO, FreshnessPolicy.PROFILE_MAX_AGE) -> notCalled(key, cached)
        else -> profileFetch(key, cached)
    }

    private fun shouldCall(
        key: CacheKey<*>,
        cached: CacheEntry<*>?,
        trigger: RequestTrigger,
        autoRefreshAfter: Duration?,
    ): Boolean = freshness.shouldCallSource(catalog.automaticRequests(key.source), trigger, cached?.fetchedAt, autoRefreshAfter)

    private fun <T : Any> notCalled(key: CacheKey<T>, cached: CacheEntry<T>?): Plan<T> =
        Settled(key, cached?.let { fromCache(key, it) } ?: SourceData(key.source, DataStatus.NOT_REQUESTED, data = null, fetchedAt = null))

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

    private fun dividendsFetch(key: CacheKey<Dividends>, cached: CacheEntry<Dividends>?) =
        storedAsIs(key, cached) { marketData(key.source).dividends(key.symbol) }

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
            DataType.QUOTE, DataType.RATIOS, DataType.DIVIDENDS, DataType.COMPANY_PROFILE -> key.source in marketDataSources
        }
        val capability = key.type.capability
        return hasAdapter &&
            catalog.supports(key.source, capability) &&
            catalog.serves(key.source, SourceRole.of(key.symbol), capability)
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
