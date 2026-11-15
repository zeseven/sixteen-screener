package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.PreviousPrice
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.FakeBalanceSheetSource
import com.sixteenscreener.fakes.FakeMarketDataSource
import com.sixteenscreener.fakes.InMemoryCompanyCache
import com.sixteenscreener.fakes.InMemoryProfileRepository
import com.sixteenscreener.fakes.InMemoryRequestCounterRepository
import com.sixteenscreener.fakes.InMemorySourceCallOutcomeRepository
import com.sixteenscreener.fakes.InMemorySymbolDirectory
import com.sixteenscreener.fakes.MutableClock
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.port.outbound.RequestCounterRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

val FINNHUB: SourceId = SourceId.of("finnhub")
val SEC_EDGAR: SourceId = SourceId.of("sec-edgar")

val ALL_MARKET_DATA = setOf(
    Capability.SEARCH,
    Capability.QUOTE,
    Capability.RATIOS,
    Capability.BALANCE_SHEET,
    Capability.COMPANY_PROFILE,
)

fun listing(symbol: String, mic: String? = "XNAS", currency: String = "USD", name: String = "$symbol INC") =
    ListedSymbol(Symbol.of(symbol), name, mic, currency, "Common Stock")

fun sampleRatios(pe: Double = 30.0) = Ratios(listOf(Ratio(RatioCode.PE, pe), Ratio(RatioCode.BETA, null)))

fun sampleProfile(name: String = "Apple Inc", sector: String? = "Technology") = CompanyProfile(
    name = name,
    country = "US",
    currency = "USD",
    sector = sector,
    industry = "Technology",
    ipoDate = LocalDate.parse("1980-12-12"),
    marketCapitalization = 3.0e12,
    sharesOutstanding = 1.5e10,
    employees = null,
    website = "https://www.apple.com",
    description = null,
)

fun sampleBalanceSheet() = BalanceSheet(
    listOf(
        BalanceSheetPeriod(
            periodEnd = LocalDate.parse("2025-09-27"),
            fiscalYear = 2025,
            fiscalPeriod = FiscalPeriod.FY,
            filedAt = LocalDate.parse("2025-10-31"),
            form = "10-K",
            items = listOf(BalanceSheetItem("Assets", "Total assets", 3.6e11, "USD")),
        ),
    ),
)

class CountingCounters(private val delegate: InMemoryRequestCounterRepository) : RequestCounterRepository by delegate {
    var tryAcquireCalls: Int = 0
        private set

    override suspend fun tryAcquire(source: SourceId, limits: List<RequestLimit>, requested: Int, now: Instant): Int {
        tryAcquireCalls++
        return delegate.tryAcquire(source, limits, requested, now)
    }
}

class CompanyDataFixture(
    limit: Int = 60,
    capabilities: Set<Capability> = ALL_MARKET_DATA,
    defaultSource: SourceId? = null,
    marketDataSource: (FakeMarketDataSource) -> MarketDataSource = { it },
) {
    val clock = MutableClock()
    val cache = InMemoryCompanyCache()
    val counters = InMemoryRequestCounterRepository()
    val countingCounters = CountingCounters(counters)
    val sourceCalls = InMemorySourceCallOutcomeRepository()
    val profiles = InMemoryProfileRepository()
    val directory = InMemorySymbolDirectory()
    val source = FakeMarketDataSource(FINNHUB)
    val finnhubBalanceSheets = FakeBalanceSheetSource(FINNHUB)
    val secBalanceSheets = FakeBalanceSheetSource(SEC_EDGAR)
    val limiter = RateLimiter(
        mapOf(FINNHUB to listOf(RequestLimit(LimitInterval.MINUTE, limit)), SEC_EDGAR to emptyList()),
        countingCounters,
        clock,
    )
    val catalog = SourceCatalog(FINNHUB, mapOf(FINNHUB to capabilities, SEC_EDGAR to setOf(Capability.BALANCE_SHEET)))
    val marketResolver = MarketResolver()
    val identities = CompanyIdentityResolver(marketResolver)
    val sectors = WatchlistSectorSync(profiles, catalog, clock)
    val profile = Profile(
        id = ProfileId.random(),
        name = ProfileName.of("Alice"),
        defaultSource = defaultSource,
        watchlist = emptyList(),
        createdAt = clock.now(),
        updatedAt = clock.now(),
    ).also(profiles::save)

    val service = CompanyDataService(
        catalog = catalog,
        marketDataSources = mapOf(FINNHUB to marketDataSource(source)),
        balanceSheetSources = mapOf(FINNHUB to finnhubBalanceSheets, SEC_EDGAR to secBalanceSheets),
        profiles = profiles,
        symbolDirectory = directory,
        cache = cache,
        rateLimiter = limiter,
        guard = SourceCallGuard(limiter),
        outcomes = SourceCallOutcomes(sourceCalls),
        freshness = FreshnessPolicy(clock),
        marketResolver = marketResolver,
        identities = identities,
        sectors = sectors,
        clock = clock,
    )

    val now: Instant get() = clock.now()

    fun success(): SourceCallOutcome = SourceCallOutcome.success(now)

    fun listed(vararg listings: ListedSymbol) = listings.forEach(directory::add)

    fun quoteFromSource(symbol: String, price: Double, currency: String = "USD") {
        source.quotes[Symbol.of(symbol)] = SourceResult.Success(Quote(price, currency), success())
    }

    fun quoteFails(symbol: String, status: Int = 500) {
        source.quotes[Symbol.of(symbol)] = SourceResult.SourceError(SourceCallOutcome.httpError(status, now))
    }

    fun quoteRateLimited(symbol: String) {
        source.quotes[Symbol.of(symbol)] = SourceResult.LimitReached(SourceCallOutcome.httpError(429, now))
    }

    fun ratiosFromSource(symbol: String, ratios: Ratios = sampleRatios()) {
        source.ratios[Symbol.of(symbol)] = SourceResult.Success(ratios, success())
    }

    fun profileFromSource(symbol: String, profile: CompanyProfile = sampleProfile()) {
        source.profiles[Symbol.of(symbol)] = SourceResult.Success(profile, success())
    }

    fun cachedQuote(
        symbol: String,
        price: Double,
        age: Duration,
        previousPrice: Double? = null,
        source: SourceId = FINNHUB,
    ): CacheEntry<QuoteRecord> {
        val fetchedAt = now - age
        val marketDate = LocalDate.ofInstant(fetchedAt, java.time.ZoneId.of("America/New_York"))
        val record = QuoteRecord(
            quote = Quote(price, "USD"),
            marketDate = marketDate,
            previousPrice = previousPrice?.let { PreviousPrice(it, marketDate.minusDays(1)) },
        )
        return CacheEntry(record, fetchedAt).also { cache.seedQuote(quoteKey(symbol, source), it) }
    }

    suspend fun <T : Any> cached(key: CacheKey<T>, data: T, age: Duration): CacheEntry<T> =
        CacheEntry(data, now - age).also { cache.put(key, it) }

    fun exhaustBudget() = counters.setCount(FINNHUB, LimitInterval.MINUTE, now, Int.MAX_VALUE / 2)

    fun remainingBudget(remaining: Int, limit: Int = 60) =
        counters.setCount(FINNHUB, LimitInterval.MINUTE, now, limit - remaining)

    fun quoteCalls(): List<String> = source.calls.filter { it.startsWith("quote:") }

    fun profileCalls(): List<String> = source.calls.filter { it.startsWith("profile:") }

    companion object {
        fun quoteKey(symbol: String, source: SourceId = FINNHUB) = CacheKey(source, Symbol.of(symbol), DataType.QUOTE)

        fun ratiosKey(symbol: String, source: SourceId = FINNHUB) = CacheKey(source, Symbol.of(symbol), DataType.RATIOS)

        fun profileKey(symbol: String, source: SourceId = FINNHUB) =
            CacheKey(source, Symbol.of(symbol), DataType.COMPANY_PROFILE)

        fun balanceSheetKey(symbol: String, source: SourceId = FINNHUB) =
            CacheKey(source, Symbol.of(symbol), DataType.BALANCE_SHEET)
    }
}
