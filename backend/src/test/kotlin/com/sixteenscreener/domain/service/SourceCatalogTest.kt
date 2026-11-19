package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.DefaultSources
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private val TIINGO = SourceId.of("tiingo")

private val defaults = DefaultSources(search = FINNHUB, us = FINNHUB, international = EODHD)

private val capabilities = mapOf(
    FINNHUB to setOf(Capability.SEARCH, Capability.QUOTE, Capability.RATIOS, Capability.BALANCE_SHEET),
    SEC_EDGAR to setOf(Capability.BALANCE_SHEET),
    EODHD to setOf(Capability.QUOTE, Capability.DIVIDENDS),
    TIINGO to setOf(Capability.QUOTE, Capability.BALANCE_SHEET),
)

private val roles = mapOf(
    FINNHUB to setOf(SourceRole.SEARCH, SourceRole.US),
    SEC_EDGAR to emptySet(),
    EODHD to setOf(SourceRole.INTERNATIONAL),
    TIINGO to setOf(SourceRole.US, SourceRole.INTERNATIONAL),
)

private val catalog = SourceCatalog(defaults, capabilities, roles, manualOnlySources = setOf(EODHD))

class SourceCatalogTest : FunSpec({

    test("capabilities are checked per source") {
        catalog.supports(FINNHUB, Capability.QUOTE) shouldBe true
        catalog.supports(FINNHUB, Capability.DIVIDENDS) shouldBe false
        catalog.supports(SEC_EDGAR, Capability.BALANCE_SHEET) shouldBe true
        catalog.supports(SourceId.of("fmp"), Capability.QUOTE) shouldBe false
    }

    test("the default source of each role is the configured one") {
        catalog.defaultSourceOf(SourceRole.SEARCH) shouldBe FINNHUB
        catalog.defaultSourceOf(SourceRole.US) shouldBe FINNHUB
        catalog.defaultSourceOf(SourceRole.INTERNATIONAL) shouldBe EODHD
    }

    test("automatic requests are on unless the source is manual only") {
        catalog.automaticRequests(FINNHUB) shouldBe true
        catalog.automaticRequests(EODHD) shouldBe false
        catalog.automaticRequests(SourceId.of("fmp")) shouldBe true
    }

    test("roles of a source; an unknown source has none") {
        catalog.rolesOf(TIINGO) shouldBe setOf(SourceRole.US, SourceRole.INTERNATIONAL)
        catalog.rolesOf(SEC_EDGAR) shouldBe emptySet()
        catalog.rolesOf(SourceId.of("fmp")) shouldBe emptySet()
    }

    context("whether a source serves a role") {
        data class Case(val source: SourceId, val role: SourceRole, val capability: Capability, val serves: Boolean)

        withData(
            nameFn = { "${it.source} for ${it.role} ${it.capability}: ${it.serves}" },
            Case(FINNHUB, SourceRole.US, Capability.QUOTE, serves = true),
            Case(FINNHUB, SourceRole.INTERNATIONAL, Capability.QUOTE, serves = false),
            Case(EODHD, SourceRole.INTERNATIONAL, Capability.DIVIDENDS, serves = true),
            Case(EODHD, SourceRole.US, Capability.QUOTE, serves = false),
            Case(TIINGO, SourceRole.US, Capability.QUOTE, serves = true),
            Case(TIINGO, SourceRole.INTERNATIONAL, Capability.QUOTE, serves = true),
            Case(SEC_EDGAR, SourceRole.US, Capability.BALANCE_SHEET, serves = true),
            Case(SEC_EDGAR, SourceRole.INTERNATIONAL, Capability.BALANCE_SHEET, serves = false),
            Case(SEC_EDGAR, SourceRole.US, Capability.QUOTE, serves = false),
            Case(SourceId.of("fmp"), SourceRole.US, Capability.QUOTE, serves = false),
        ) { case ->
            catalog.serves(case.source, case.role, case.capability) shouldBe case.serves
        }
    }

    test("the default source of the role is selected when no source is requested") {
        catalog.selectSource(null, SourceRole.US) shouldBe FINNHUB
        catalog.selectSource(null, SourceRole.INTERNATIONAL) shouldBe EODHD
    }

    test("a requested source wins over the default, even when it doesn't serve the role") {
        catalog.selectSource(SEC_EDGAR, SourceRole.US) shouldBe SEC_EDGAR
        catalog.selectSource(FINNHUB, SourceRole.INTERNATIONAL) shouldBe FINNHUB
    }

    test("an unknown requested source is not found") {
        shouldThrow<ResourceNotFoundException> { catalog.selectSource(SourceId.of("fmp"), SourceRole.US) }
    }

    test("a default source must be configured") {
        shouldThrow<IllegalArgumentException> {
            SourceCatalog(defaults.copy(international = SourceId.of("fmp")), capabilities, roles)
        }.message shouldContain "INTERNATIONAL"
    }

    test("a default source must serve its role") {
        shouldThrow<IllegalArgumentException> {
            SourceCatalog(defaults.copy(search = EODHD), capabilities, roles)
        }.message shouldContain "SEARCH"
    }
})
