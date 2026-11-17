package com.sixteenscreener

import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.config.SourceConfig
import com.sixteenscreener.config.SourceSecrets
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.service.LogoTier
import com.sixteenscreener.domain.service.RateLimiter
import com.sixteenscreener.domain.service.SourceCallGuard
import com.sixteenscreener.domain.service.SourceCallOutcomes
import com.sixteenscreener.fakes.FakeBalanceSheetSource
import com.sixteenscreener.fakes.FakeLogoProvider
import com.sixteenscreener.fakes.FakeMarketDataSource
import com.sixteenscreener.fakes.InMemoryCompanyCache
import com.sixteenscreener.fakes.InMemoryIndicatorDefaultsRepository
import com.sixteenscreener.fakes.InMemoryLogoMissRepository
import com.sixteenscreener.fakes.InMemoryLogoStorage
import com.sixteenscreener.fakes.InMemoryProfileRepository
import com.sixteenscreener.fakes.InMemoryRequestCounterRepository
import com.sixteenscreener.fakes.InMemorySourceCallOutcomeRepository
import com.sixteenscreener.fakes.InMemorySymbolDirectory
import com.sixteenscreener.fakes.MutableClock
import kotlin.time.Duration.Companion.seconds

fun testConfig(): AppConfig = AppConfig(
    port = 8080,
    gcpProjectId = "test-project",
    firestoreDatabase = "sixteen-screener",
    logoBucket = "test-bucket",
    defaultSource = SourceId.of("finnhub"),
    sources = listOf(
        SourceConfig(
            id = SourceId.of("finnhub"),
            name = "Finnhub",
            enabled = true,
            baseUrl = "https://finnhub.test",
            capabilities = setOf(Capability.SEARCH, Capability.QUOTE, Capability.RATIOS),
            selectableAsDefault = true,
            limits = listOf(RequestLimit(LimitInterval.MINUTE, 60)),
            timeout = 5.seconds,
        ),
    ),
    secrets = SourceSecrets(finnhubApiKey = "test-key", secEdgarUserAgent = "test agent"),
)

class TestPorts(
    val clock: MutableClock = MutableClock(),
    val profiles: InMemoryProfileRepository = InMemoryProfileRepository(),
    val indicatorDefaults: InMemoryIndicatorDefaultsRepository = InMemoryIndicatorDefaultsRepository(),
    val companyCache: InMemoryCompanyCache = InMemoryCompanyCache(),
    val logoMisses: InMemoryLogoMissRepository = InMemoryLogoMissRepository(),
    val requestCounters: InMemoryRequestCounterRepository = InMemoryRequestCounterRepository(),
    val sourceCalls: InMemorySourceCallOutcomeRepository = InMemorySourceCallOutcomeRepository(),
    val marketData: FakeMarketDataSource = FakeMarketDataSource(),
    val balanceSheets: FakeBalanceSheetSource = FakeBalanceSheetSource(SourceId.of("finnhub")),
    val additionalBalanceSheets: List<FakeBalanceSheetSource> = emptyList(),
    val symbolDirectory: InMemorySymbolDirectory = InMemorySymbolDirectory(),
    val logoStorage: InMemoryLogoStorage = InMemoryLogoStorage(),
    val logoProvider: FakeLogoProvider = FakeLogoProvider(),
    val pngLogoStorage: InMemoryLogoStorage = InMemoryLogoStorage(ImageFormat.PNG),
    val pngLogoProvider: FakeLogoProvider = FakeLogoProvider(SourceId.of("finnhub-logos"), ImageFormat.PNG),
)

fun testServices(ports: TestPorts = TestPorts(), config: AppConfig = testConfig()): AppServices {
    val rateLimiter = RateLimiter(
        limitsBySource = config.sources.associate { it.id to it.limits },
        counters = ports.requestCounters,
        clock = ports.clock,
        totalCountedSources = config.totalCountedSources,
    )
    return AppServices(
        config = config,
        clock = ports.clock,
        profiles = ports.profiles,
        indicatorDefaults = ports.indicatorDefaults,
        companyCache = ports.companyCache,
        logoMisses = ports.logoMisses,
        rateLimiter = rateLimiter,
        sourceCallOutcomes = SourceCallOutcomes(ports.sourceCalls),
        sourceCallGuard = SourceCallGuard(rateLimiter),
        marketDataSources = mapOf(ports.marketData.id to ports.marketData),
        balanceSheetSources = (listOf(ports.balanceSheets) + ports.additionalBalanceSheets).associateBy { it.id },
        symbolDirectory = ports.symbolDirectory,
        logoTiers = listOf(
            LogoTier(ports.logoStorage, ports.logoProvider.enabledIn(config)),
            LogoTier(ports.pngLogoStorage, ports.pngLogoProvider.enabledIn(config)),
        ),
    )
}

private fun FakeLogoProvider.enabledIn(config: AppConfig): FakeLogoProvider? =
    takeIf { provider -> config.enabledSources.any { it.id == provider.id } }
