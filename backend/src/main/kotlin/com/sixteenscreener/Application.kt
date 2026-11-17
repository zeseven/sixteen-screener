package com.sixteenscreener

import com.sixteenscreener.adapter.inbound.http.configureCallLogging
import com.sixteenscreener.adapter.inbound.http.configureRouting
import com.sixteenscreener.adapter.inbound.http.configureSerialization
import com.sixteenscreener.adapter.inbound.http.configureStatusPages
import com.sixteenscreener.adapter.outbound.finnhub.FinnhubClient
import com.sixteenscreener.adapter.outbound.finnhublogos.FinnhubLogoClient
import com.sixteenscreener.adapter.outbound.firestore.FirestoreClientFactory
import com.sixteenscreener.adapter.outbound.firestore.FirestoreCompanyCache
import com.sixteenscreener.adapter.outbound.firestore.FirestoreIndicatorDefaultsRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreLogoMissRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreProfileRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreRequestCounterRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreSecCikRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreSourceCallOutcomeRepository
import com.sixteenscreener.adapter.outbound.gcs.GcsClientFactory
import com.sixteenscreener.adapter.outbound.gcs.GcsExchangeDirectoryStore
import com.sixteenscreener.adapter.outbound.gcs.GcsLogoStorage
import com.sixteenscreener.adapter.outbound.gcs.GcsPaths
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.logoapi.LogoApiClient
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.adapter.outbound.secedgar.SecCikResolver
import com.sixteenscreener.adapter.outbound.secedgar.SecEdgarClient
import com.sixteenscreener.adapter.outbound.time.SystemClock
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.config.AppConfigLoader
import com.sixteenscreener.config.AppConfigLoader.SEC_EDGAR_FILES_BASE_URL
import com.sixteenscreener.config.InvalidConfigurationException
import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.SourceDescription
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.service.CompanyDataService
import com.sixteenscreener.domain.service.CompanyIdentityResolver
import com.sixteenscreener.domain.service.ExchangeDirectoryService
import com.sixteenscreener.domain.service.ExchangeService
import com.sixteenscreener.domain.service.FreshnessPolicy
import com.sixteenscreener.domain.service.IndicatorSettingsService
import com.sixteenscreener.domain.service.LogoService
import com.sixteenscreener.domain.service.LogoTier
import com.sixteenscreener.domain.service.MarketResolver
import com.sixteenscreener.domain.service.NoSymbolDirectory
import com.sixteenscreener.domain.service.ProfileService
import com.sixteenscreener.domain.service.RateLimiter
import com.sixteenscreener.domain.service.SearchResponseCache
import com.sixteenscreener.domain.service.SearchService
import com.sixteenscreener.domain.service.SourceCallGuard
import com.sixteenscreener.domain.service.SourceCallOutcomes
import com.sixteenscreener.domain.service.SourceCatalog
import com.sixteenscreener.domain.service.SourceService
import com.sixteenscreener.domain.service.WatchlistCompanySync
import com.sixteenscreener.domain.service.WatchlistService
import com.sixteenscreener.port.inbound.CompanyDataUseCase
import com.sixteenscreener.port.inbound.ExchangesUseCase
import com.sixteenscreener.port.inbound.IndicatorSettingsUseCase
import com.sixteenscreener.port.inbound.LogoUseCase
import com.sixteenscreener.port.inbound.ProfileUseCase
import com.sixteenscreener.port.inbound.SearchUseCase
import com.sixteenscreener.port.inbound.SourcesUseCase
import com.sixteenscreener.port.inbound.WatchlistUseCase
import com.sixteenscreener.port.outbound.BalanceSheetSource
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.CompanyCache
import com.sixteenscreener.port.outbound.IndicatorDefaultsRepository
import com.sixteenscreener.port.outbound.LogoMissRepository
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.SecCikRepository
import com.sixteenscreener.port.outbound.SymbolDirectory
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class AppServices(
    val config: AppConfig,
    val clock: Clock,
    val profiles: ProfileRepository,
    val indicatorDefaults: IndicatorDefaultsRepository,
    val companyCache: CompanyCache,
    val logoMisses: LogoMissRepository,
    val rateLimiter: RateLimiter,
    val sourceCallOutcomes: SourceCallOutcomes,
    val sourceCallGuard: SourceCallGuard,
    val marketDataSources: Map<SourceId, MarketDataSource>,
    val balanceSheetSources: Map<SourceId, BalanceSheetSource>,
    val symbolDirectory: SymbolDirectory,
    val logoTiers: List<LogoTier>,
    val defaultLogo: ByteArray = bundledDefaultLogo(),
    private val resources: List<AutoCloseable> = emptyList(),
) : AutoCloseable {
    val sourceCatalog = SourceCatalog(config.defaultSource, config.enabledSources.associate { it.id to it.capabilities })
    val marketResolver = MarketResolver()

    private val identities = CompanyIdentityResolver(marketResolver)

    private val watchlistCompanies = WatchlistCompanySync(profiles, sourceCatalog, clock)

    val indicatorSettings = IndicatorSettingsService(profiles, indicatorDefaults, clock)

    val companyData = CompanyDataService(
        catalog = sourceCatalog,
        marketDataSources = marketDataSources,
        balanceSheetSources = balanceSheetSources,
        profiles = profiles,
        symbolDirectory = symbolDirectory,
        cache = companyCache,
        rateLimiter = rateLimiter,
        guard = sourceCallGuard,
        outcomes = sourceCallOutcomes,
        freshness = FreshnessPolicy(clock),
        marketResolver = marketResolver,
        identities = identities,
        watchlistCompanies = watchlistCompanies,
        indicatorSettings = indicatorSettings,
        clock = clock,
    )

    val profileUseCase: ProfileUseCase =
        ProfileService(profiles, symbolDirectory, sourceCatalog, companyData, identities, marketResolver, clock)

    val watchlistUseCase: WatchlistUseCase =
        WatchlistService(profiles, sourceCatalog, symbolDirectory, companyData, identities, indicatorSettings)

    val companyDataUseCase: CompanyDataUseCase get() = companyData

    val searchUseCase: SearchUseCase = SearchService(
        profiles = profiles,
        catalog = sourceCatalog,
        marketDataSources = marketDataSources,
        symbolDirectory = symbolDirectory,
        identities = identities,
        guard = sourceCallGuard,
        outcomes = sourceCallOutcomes,
        responses = SearchResponseCache(clock),
    )

    val sourcesUseCase: SourcesUseCase = SourceService(
        sources = config.enabledSources.map { SourceDescription(it.id, it.name, it.capabilities, it.selectableAsDefault) },
        profiles = profiles,
        catalog = sourceCatalog,
        rateLimiter = rateLimiter,
        outcomes = sourceCallOutcomes,
        clock = clock,
    )

    val exchangesUseCase: ExchangesUseCase = ExchangeService(marketResolver)

    val indicatorSettingsUseCase: IndicatorSettingsUseCase get() = indicatorSettings

    val logoUseCase: LogoUseCase = LogoService(
        tiers = logoTiers,
        misses = logoMisses,
        guard = sourceCallGuard,
        outcomes = sourceCallOutcomes,
        clock = clock,
        defaultLogo = defaultLogo,
    )

    override fun close() = resources.forEach(AutoCloseable::close)
}

private const val NETTY_ALLOCATOR_PROPERTY = "io.grpc.netty.shaded.io.netty.allocator.type"

fun main() {
    usePooledNettyAllocator()
    val config = loadConfigOrExit()
    val services = wireServices(config)
    embeddedServer(CIO, port = config.port, host = "0.0.0.0") {
        module(services)
        monitor.subscribe(ApplicationStopped) { services.close() }
    }.start(wait = true)
}

internal fun usePooledNettyAllocator() {
    if (System.getProperty(NETTY_ALLOCATOR_PROPERTY) == null) {
        System.setProperty(NETTY_ALLOCATOR_PROPERTY, "pooled")
    }
}

fun wireServices(config: AppConfig): AppServices {
    val clock = SystemClock
    val firestore = FirestoreClientFactory.create(config.gcpProjectId, config.firestoreDatabase)
    val storage = GcsClientFactory.create(config.gcpProjectId, config.storageEmulatorHost)
    val httpClient = SourceHttpClient.create()
    val rateLimiter = RateLimiter(
        limitsBySource = config.sources.associate { it.id to it.limits },
        counters = FirestoreRequestCounterRepository(firestore),
        clock = clock,
        totalCountedSources = config.totalCountedSources,
    )
    val sourceCallOutcomes = SourceCallOutcomes(FirestoreSourceCallOutcomeRepository(firestore))
    val sourceCallGuard = SourceCallGuard(rateLimiter)
    val companyCache = FirestoreCompanyCache(firestore)
    val finnhub = finnhubClient(config, httpClient, clock)
    val secEdgar = secEdgarClient(config, httpClient, FirestoreSecCikRepository(firestore), rateLimiter, clock)
    val symbolDirectory = finnhub?.let { source ->
        ExchangeDirectoryService(
            source = source,
            store = GcsExchangeDirectoryStore(storage, config.logoBucket),
            guard = sourceCallGuard,
            outcomes = sourceCallOutcomes,
            clock = clock,
        )
    } ?: NoSymbolDirectory
    return AppServices(
        config = config,
        clock = clock,
        profiles = FirestoreProfileRepository(firestore),
        indicatorDefaults = FirestoreIndicatorDefaultsRepository(firestore),
        companyCache = companyCache,
        logoMisses = FirestoreLogoMissRepository(firestore),
        rateLimiter = rateLimiter,
        sourceCallOutcomes = sourceCallOutcomes,
        sourceCallGuard = sourceCallGuard,
        marketDataSources = listOfNotNull<MarketDataSource>(finnhub).associateBy { it.id },
        balanceSheetSources = listOfNotNull<BalanceSheetSource>(finnhub, secEdgar).associateBy { it.id },
        symbolDirectory = symbolDirectory,
        logoTiers = listOf(
            LogoTier(
                storage = GcsLogoStorage(storage, config.logoBucket, GcsPaths.LOGOS, ImageFormat.SVG),
                provider = logoApiClient(config, httpClient, clock),
            ),
            LogoTier(
                storage = GcsLogoStorage(storage, config.logoBucket, GcsPaths.FINNHUB_LOGOS, ImageFormat.PNG),
                provider = finnhubLogoClient(config, httpClient, companyCache, clock),
            ),
        ),
        resources = listOf(httpClient, storage, firestore),
    )
}

private fun finnhubClient(config: AppConfig, httpClient: HttpClient, clock: Clock): FinnhubClient? =
    config.enabledSources.firstOrNull { it.id == FINNHUB }?.let { source ->
        FinnhubClient(
            id = source.id,
            baseUrl = source.baseUrl,
            apiKey = checkNotNull(config.secrets.finnhubApiKey) { "Finnhub API key missing" },
            call = SourceHttpCall(httpClient, source.timeout, clock),
        )
    }

private fun secEdgarClient(
    config: AppConfig,
    httpClient: HttpClient,
    ciks: SecCikRepository,
    rateLimiter: RateLimiter,
    clock: Clock,
): SecEdgarClient? =
    config.enabledSources.firstOrNull { it.id == SEC_EDGAR }?.let { source ->
        val userAgent = checkNotNull(config.secrets.secEdgarUserAgent) { "SEC EDGAR user agent missing" }
        val call = SourceHttpCall(httpClient, source.timeout, clock)
        SecEdgarClient(
            id = source.id,
            baseUrl = source.baseUrl,
            userAgent = userAgent,
            call = call,
            ciks = SecCikResolver(
                filesBaseUrl = source.additionalBaseUrls.getValue(SEC_EDGAR_FILES_BASE_URL),
                userAgent = userAgent,
                call = call,
                repository = ciks,
                budget = { rateLimiter.tryAcquire(source.id, 1) == 1 },
                clock = clock,
            ),
        )
    }

private fun logoApiClient(config: AppConfig, httpClient: HttpClient, clock: Clock): LogoApiClient? =
    config.enabledSources.firstOrNull { it.id == LOGOAPI }?.let { source ->
        LogoApiClient(id = source.id, baseUrl = source.baseUrl, call = SourceHttpCall(httpClient, source.timeout, clock))
    }

private fun finnhubLogoClient(
    config: AppConfig,
    httpClient: HttpClient,
    companyCache: CompanyCache,
    clock: Clock,
): FinnhubLogoClient? =
    config.enabledSources.firstOrNull { it.id == FINNHUB_LOGOS }?.let { source ->
        FinnhubLogoClient(
            id = source.id,
            baseUrl = source.baseUrl,
            call = SourceHttpCall(httpClient, source.timeout, clock),
            profiles = companyCache,
            profileSource = FINNHUB,
        )
    }

fun bundledDefaultLogo(): ByteArray =
    checkNotNull(AppServices::class.java.getResource(DEFAULT_LOGO_RESOURCE)) { "Default logo resource missing" }.readBytes()

private const val DEFAULT_LOGO_RESOURCE = "/default-logo.svg"

private val FINNHUB = SourceId.of("finnhub")
private val SEC_EDGAR = SourceId.of("sec-edgar")
private val LOGOAPI = SourceId.of("logoapi")
private val FINNHUB_LOGOS = SourceId.of("finnhub-logos")

fun Application.module(services: AppServices) {
    configureCallLogging()
    configureSerialization()
    configureStatusPages()
    configureRouting(services)
}

private fun loadConfigOrExit(): AppConfig =
    try {
        AppConfigLoader.load(ApplicationConfig("application.conf"), System.getenv())
    } catch (e: InvalidConfigurationException) {
        LoggerFactory.getLogger("com.sixteenscreener.Application").error(e.message)
        exitProcess(1)
    }
