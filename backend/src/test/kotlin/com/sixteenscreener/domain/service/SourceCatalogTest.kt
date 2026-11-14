package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

private val catalog = SourceCatalog(
    FINNHUB,
    mapOf(FINNHUB to setOf(Capability.QUOTE, Capability.RATIOS), SEC_EDGAR to setOf(Capability.BALANCE_SHEET)),
)

private fun profile(defaultSource: SourceId?) =
    Profile(ProfileId.random(), ProfileName.of("Bob"), defaultSource, emptyList(), Instant.EPOCH, Instant.EPOCH)

class SourceCatalogTest : FunSpec({

    test("capabilities are checked per source") {
        catalog.supports(FINNHUB, Capability.QUOTE) shouldBe true
        catalog.supports(FINNHUB, Capability.BALANCE_SHEET) shouldBe false
        catalog.supports(SEC_EDGAR, Capability.BALANCE_SHEET) shouldBe true
        catalog.supports(SourceId.of("tiingo"), Capability.QUOTE) shouldBe false
    }

    test("profile without a default source uses the configured default") {
        catalog.defaultSourceOf(profile(null)) shouldBe FINNHUB
    }

    test("profile default source is used when configured") {
        catalog.defaultSourceOf(profile(SEC_EDGAR)) shouldBe SEC_EDGAR
    }

    test("profile default source that is not configured falls back to the configured default") {
        catalog.defaultSourceOf(profile(SourceId.of("tiingo"))) shouldBe FINNHUB
    }

    test("requested source wins over the profile default") {
        catalog.selectSource(profile(null), SEC_EDGAR) shouldBe SEC_EDGAR
    }

    test("unknown requested source is not found") {
        shouldThrow<ResourceNotFoundException> { catalog.selectSource(profile(null), SourceId.of("tiingo")) }
    }

    test("default source must be configured") {
        shouldThrow<IllegalArgumentException> { SourceCatalog(SourceId.of("tiingo"), emptyMap()) }
    }
})
