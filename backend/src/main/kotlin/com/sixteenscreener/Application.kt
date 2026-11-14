package com.sixteenscreener

import com.sixteenscreener.adapter.inbound.http.configureCallLogging
import com.sixteenscreener.adapter.inbound.http.configureRouting
import com.sixteenscreener.adapter.inbound.http.configureSerialization
import com.sixteenscreener.adapter.inbound.http.configureStatusPages
import com.sixteenscreener.adapter.outbound.finnhub.FinnhubClient
import com.sixteenscreener.adapter.outbound.firestore.FirestoreClientFactory
import com.sixteenscreener.adapter.outbound.firestore.FirestoreCompanyCache
import com.sixteenscreener.adapter.outbound.firestore.FirestoreLogoMissRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreProfileRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreRequestCounterRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreSourceCallOutcomeRepository
import com.sixteenscreener.adapter.outbound.gcs.GcsClientFactory
import com.sixteenscreener.adapter.outbound.gcs.GcsExchangeDirectoryStore
import com.sixteenscreener.adapter.outbound.gcs.GcsLogoStorage
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.adapter.outbound.time.SystemClock
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.config.AppConfigLoader
import com.sixteenscreener.config.InvalidConfigurationException
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.service.ExchangeDirectoryService
import com.sixteenscreener.domain.service.NoSymbolDirectory
import com.sixteenscreener.domain.service.RateLimiter
import com.sixteenscreener.domain.service.SourceCallGuard
import com.sixteenscreener.domain.service.SourceCallOutcomes
import com.sixteenscreener.port.outbound.BalanceSheetSource
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.CompanyCache
import com.sixteenscreener.port.outbound.LogoMissRepository
import com.sixteenscreener.port.outbound.LogoStorage
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.port.outbound.ProfileRepository
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
    val companyCache: CompanyCache,
    val logoMisses: LogoMissRepository,
    val rateLimiter: RateLimiter,
    val sourceCallOutcomes: SourceCallOutcomes,
    val sourceCallGuard: SourceCallGuard,
    val marketDataSources: Map<SourceId, MarketDataSource>,
    val balanceSheetSources: Map<SourceId, BalanceSheetSource>,
    val symbolDirectory: SymbolDirectory,
    val logoStorage: LogoStorage,
    private val resources: List<AutoCloseable> = emptyList(),
) : AutoCloseable {
    override fun close() = resources.forEach(AutoCloseable::close)
}

fun main() {
    val config = loadConfigOrExit()
    val services = wireServices(config)
    embeddedServer(CIO, port = config.port, host = "0.0.0.0") {
        module(services)
        monitor.subscribe(ApplicationStopped) { services.close() }
    }.start(wait = true)
}

fun wireServices(config: AppConfig): AppServices {
    val clock = SystemClock
    val firestore = FirestoreClientFactory.create(config.gcpProjectId)
    val storage = GcsClientFactory.create(config.gcpProjectId)
    val httpClient = SourceHttpClient.create()
    val rateLimiter = RateLimiter(
        limitsBySource = config.sources.associate { it.id to it.limits },
        counters = FirestoreRequestCounterRepository(firestore),
        clock = clock,
    )
    val sourceCallOutcomes = SourceCallOutcomes(FirestoreSourceCallOutcomeRepository(firestore))
    val sourceCallGuard = SourceCallGuard(rateLimiter)
    val finnhub = finnhubClient(config, httpClient, clock)
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
        companyCache = FirestoreCompanyCache(firestore),
        logoMisses = FirestoreLogoMissRepository(firestore),
        rateLimiter = rateLimiter,
        sourceCallOutcomes = sourceCallOutcomes,
        sourceCallGuard = sourceCallGuard,
        marketDataSources = listOfNotNull(finnhub).associateBy { it.id },
        balanceSheetSources = listOfNotNull(finnhub).associateBy { it.id },
        symbolDirectory = symbolDirectory,
        logoStorage = GcsLogoStorage(storage, config.logoBucket),
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

private val FINNHUB = SourceId.of("finnhub")

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
