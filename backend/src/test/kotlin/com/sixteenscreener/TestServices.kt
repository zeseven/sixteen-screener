package com.sixteenscreener

import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.config.SourceConfig
import com.sixteenscreener.config.SourceSecrets
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.fakes.MutableClock
import kotlin.time.Duration.Companion.seconds

fun testConfig(): AppConfig = AppConfig(
    port = 8080,
    gcpProjectId = "test-project",
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

fun testServices(clock: MutableClock = MutableClock()): AppServices = AppServices(config = testConfig(), clock = clock)
