package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.Indicator
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.PriceTrend
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.balanceSheetKey
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.quoteKey
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.ratiosKey
import com.sixteenscreener.watchlistEntry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration

private val AAPL = Symbol.of("AAPL")
private val AD = Symbol.of("AD.AS")
private val apple = listing("AAPL", name = "APPLE INC")

private fun fixture(
    limit: Int = 60,
    capabilities: Set<Capability> = ALL_MARKET_DATA,
    defaultSource: SourceId? = null,
) = CompanyDataFixture(limit, capabilities, defaultSource).apply { listed(apple) }

class CompanyDataServiceTest : FunSpec({

    context("price and ratios") {
        test("cache miss requests quote and ratios and stores both") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.FRESH
            result.fetchedAt shouldBe f.now
            result.source shouldBe FINNHUB
            result.quote.data?.quote shouldBe Quote(190.0, "USD")
            result.ratios.data shouldBe sampleRatios()
            f.source.calls shouldContainExactlyInAnyOrder listOf("quote:AAPL", "ratios:AAPL")
            f.cache.get(quoteKey("AAPL"))?.fetchedAt shouldBe f.now
            f.cache.get(ratiosKey("AAPL"))?.data shouldBe sampleRatios()
            f.cache.history(FINNHUB, AAPL).map { it.quote.price } shouldContainExactly listOf(190.0)
        }

        test("indicators are calculated with the profile's settings, copied from the defaults first") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL", sampleRatios(pe = 45.0))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.indicators shouldContainExactly listOf(Indicator(RatioCode.PE, IndicatorLevel.BAD))
            f.profiles.findById(f.profile.id)?.indicatorSettings shouldBe IndicatorDefaults.all.map { it.setting }
        }

        test("P/E calculated from the quote price makes an indicator while the returned ratio stays null") {
            val f = fixture()
            f.quoteFromSource("AAPL", 13.9)
            f.ratiosFromSource("AAPL", Ratios.of(mapOf(RatioCode.EPS to -2.252)))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.indicators shouldContainExactly listOf(Indicator(RatioCode.PE, IndicatorLevel.BAD))
            result.ratios.data?.valueOf(RatioCode.PE) shouldBe null
        }

        test("no ratios make no indicators") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)

            f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false).indicators.shouldBeEmpty()
        }

        test("budget for both calls is reserved in one transaction") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            f.countingCounters.tryAcquireCalls shouldBe 1
        }

        test("fresh cache is returned as FRESH without a call") {
            val f = fixture()
            val quote = f.cachedQuote("AAPL", 189.0, age = Duration.ofMinutes(10))
            val ratios = f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofMinutes(20))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.FRESH
            result.fetchedAt shouldBe ratios.fetchedAt
            result.quote.fetchedAt shouldBe quote.fetchedAt
            f.source.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("stale cache is returned as CACHED without a call when no refresh is requested") {
            val f = fixture()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofHours(5))
            f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofDays(3))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.CACHED
            result.quote.status shouldBe DataStatus.CACHED
            result.fetchedAt shouldBe f.now - Duration.ofDays(3)
            f.source.calls.shouldBeEmpty()
        }

        test("only the missing part is requested") {
            val f = fixture()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofHours(5))
            f.ratiosFromSource("AAPL")

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            f.source.calls shouldContainExactly listOf("ratios:AAPL")
            result.quote.status shouldBe DataStatus.CACHED
            result.ratios.status shouldBe DataStatus.FRESH
            result.status shouldBe DataStatus.CACHED
        }

        test("refresh requests the source even when the cache is fresh") {
            val f = fixture()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofMinutes(10))
            f.cached(ratiosKey("AAPL"), sampleRatios(10.0), age = Duration.ofMinutes(10))
            f.quoteFromSource("AAPL", 191.0)
            f.ratiosFromSource("AAPL", sampleRatios(31.0))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = true)

            result.status shouldBe DataStatus.FRESH
            result.fetchedAt shouldBe f.now
            result.quote.data?.quote?.price shouldBe 191.0
            result.ratios.data shouldBe sampleRatios(31.0)
        }

        test("cache miss without budget returns empty data with LIMIT_REACHED") {
            val f = fixture()
            f.exhaustBudget()
            f.quoteFromSource("AAPL", 190.0)

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.LIMIT_REACHED
            result.fetchedAt.shouldBeNull()
            result.quote.data.shouldBeNull()
            result.ratios.data.shouldBeNull()
            f.source.calls.shouldBeEmpty()
        }

        test("refresh without budget returns the cache with LIMIT_REACHED and its original fetch time") {
            val f = fixture()
            val quote = f.cachedQuote("AAPL", 189.0, age = Duration.ofMinutes(10))
            f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofMinutes(10))
            f.exhaustBudget()

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = true)

            result.status shouldBe DataStatus.LIMIT_REACHED
            result.fetchedAt shouldBe quote.fetchedAt
            result.quote.data shouldBe quote.data
            result.ratios.data shouldBe sampleRatios()
            f.source.calls.shouldBeEmpty()
        }

        test("budget for only one of the calls refreshes the quote and reports LIMIT_REACHED") {
            val f = fixture()
            f.remainingBudget(1)
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            f.source.calls shouldContainExactly listOf("quote:AAPL")
            result.quote.status shouldBe DataStatus.FRESH
            result.ratios.status shouldBe DataStatus.LIMIT_REACHED
            result.status shouldBe DataStatus.LIMIT_REACHED
        }

        test("failed refresh returns the cache with SOURCE_ERROR and keeps the cache unchanged") {
            val f = fixture()
            val quote = f.cachedQuote("AAPL", 189.0, age = Duration.ofHours(3))
            val ratios = f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofHours(3))
            f.quoteFails("AAPL")
            f.source.ratios[AAPL] = SourceResult.SourceError(SourceCallOutcome.timeout(f.now))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = true)

            result.status shouldBe DataStatus.SOURCE_ERROR
            result.fetchedAt shouldBe quote.fetchedAt
            result.quote.data shouldBe quote.data
            f.cache.get(quoteKey("AAPL")) shouldBe quote
            f.cache.get(ratiosKey("AAPL")) shouldBe ratios
            f.cache.history(FINNHUB, AAPL).shouldBeEmpty()
        }

        test("failed call without cache returns empty data with SOURCE_ERROR") {
            val f = fixture()
            f.quoteFails("AAPL", 503)
            f.source.ratios[AAPL] = SourceResult.SourceError(SourceCallOutcome.appError(f.now))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.SOURCE_ERROR
            result.fetchedAt.shouldBeNull()
            result.quote.data.shouldBeNull()
            result.ratios.data.shouldBeNull()
        }

        test("source without data for the company returns NO_DATA") {
            val f = fixture()
            f.source.quotes[AAPL] = SourceResult.NoData(f.success())
            f.source.ratios[AAPL] = SourceResult.NoData(f.success())

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.NO_DATA
            result.fetchedAt.shouldBeNull()
        }

        test("source answering 429 gives LIMIT_REACHED and saturates its counters") {
            val f = fixture()
            f.quoteRateLimited("AAPL")
            f.source.ratios[AAPL] = SourceResult.LimitReached(SourceCallOutcome.httpError(429, f.now))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.LIMIT_REACHED
            f.limiter.tryAcquire(FINNHUB, 1) shouldBe 0
        }

        test("source lacking the capabilities gives NOT_SUPPORTED without a call") {
            val f = fixture(capabilities = setOf(Capability.SEARCH))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = true)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            result.fetchedAt.shouldBeNull()
            f.source.calls.shouldBeEmpty()
        }

        test("source without a market data adapter gives NOT_SUPPORTED") {
            val f = fixture()

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = SEC_EDGAR, refresh = false)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            result.source shouldBe SEC_EDGAR
            f.source.calls.shouldBeEmpty()
        }

        test("quote without ratios capability combines to the quote status") {
            val f = fixture(capabilities = setOf(Capability.QUOTE))
            f.quoteFromSource("AAPL", 190.0)

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            result.ratios.status shouldBe DataStatus.NOT_SUPPORTED
            result.status shouldBe DataStatus.FRESH
        }

        test("refreshed quote of a later market day carries the previous price and trend") {
            val f = fixture()
            f.cachedQuote("AAPL", 180.0, age = Duration.ofDays(1))
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            val quote = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = true).quote.data

            quote.shouldNotBeNull()
            quote.previousPrice?.price shouldBe 180.0
            quote.trend shouldBe PriceTrend.UP
        }

        test("failed cache write still returns the fresh data with FRESH") {
            val f = fixture()
            f.cachedQuote("AAPL", 180.0, age = Duration.ofDays(1))
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")
            f.cache.failWrites = true

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = true)

            result.status shouldBe DataStatus.FRESH
            result.fetchedAt shouldBe f.now
            result.quote.data?.quote shouldBe Quote(190.0, "USD")
            result.quote.data?.previousPrice?.price shouldBe 180.0
            result.ratios.data shouldBe sampleRatios()
            f.cache.get(quoteKey("AAPL"))?.data?.quote?.price shouldBe 180.0
            f.cache.get(ratiosKey("AAPL")).shouldBeNull()
        }

        test("failed cache write of a quote without cache has no previous price") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.cache.failWrites = true

            val quote = f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false).quote

            quote.status shouldBe DataStatus.FRESH
            quote.data?.previousPrice.shouldBeNull()
            quote.data?.marketDate shouldBe java.time.LocalDate.parse("2026-11-14")
        }

        test("one call outcome is recorded per request") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.source.ratios[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(502, f.now))

            f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

            f.sourceCalls.writes.map { it.first } shouldContainExactly listOf(FINNHUB)
        }
    }

    context("balance sheet") {
        test("cache miss requests and stores the balance sheet") {
            val f = fixture()
            f.finnhubBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.FRESH
            result.data shouldBe sampleBalanceSheet()
            result.fetchedAt shouldBe f.now
            f.cache.get(balanceSheetKey("AAPL"))?.data shouldBe sampleBalanceSheet()
        }

        test("failed cache write still returns the fresh balance sheet") {
            val f = fixture()
            f.finnhubBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())
            f.cache.failWrites = true

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.FRESH
            result.data shouldBe sampleBalanceSheet()
            result.fetchedAt shouldBe f.now
        }

        test("explicit SEC EDGAR source is used instead of the default source") {
            val f = fixture()
            f.secBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = SEC_EDGAR, refresh = false)

            result.source shouldBe SEC_EDGAR
            result.status shouldBe DataStatus.FRESH
            f.secBalanceSheets.calls shouldContainExactly listOf(AAPL)
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
        }

        test("not supported by the source for the symbol keeps empty data") {
            val f = fixture()
            f.secBalanceSheets.balanceSheets[AAPL] = SourceResult.NotSupported(outcome = null)

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = SEC_EDGAR, refresh = false)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            result.data.shouldBeNull()
        }

        test("source without the capability gives NOT_SUPPORTED without a call or cache read") {
            val f = fixture(capabilities = setOf(Capability.QUOTE, Capability.RATIOS))

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, refresh = true)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
        }

        test("stale cache is returned as CACHED") {
            val f = fixture()
            val cached = f.cached(balanceSheetKey("AAPL"), sampleBalanceSheet(), age = Duration.ofDays(10))

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, refresh = false)

            result.status shouldBe DataStatus.CACHED
            result.fetchedAt shouldBe cached.fetchedAt
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
        }
    }

    context("company profile") {
        test("identity comes from the directory and the name from the source profile") {
            val f = fixture()
            f.profileFromSource("AAPL")

            val result = f.service.companyProfile(f.profile.id, AAPL, source = null, refresh = false)

            result.profile.status shouldBe DataStatus.FRESH
            result.identity.symbol shouldBe AAPL
            result.identity.exchange shouldBe "NASDAQ"
            result.identity.market shouldBe Market.US
            result.identity.name shouldBe "Apple Inc"
            f.cache.get(profileKey("AAPL"))?.data shouldBe sampleProfile()
        }

        test("refresh requests the profile even when it is younger than 90 days") {
            val f = fixture()
            f.cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(1))
            f.profileFromSource("AAPL")

            val result = f.service.companyProfile(f.profile.id, AAPL, source = null, refresh = true)

            result.profile.data?.sector shouldBe "Technology"
            f.profileCalls() shouldContainExactly listOf("profile:AAPL")
        }

        test("without profile data the directory name is used") {
            val f = fixture()
            f.exhaustBudget()

            val result = f.service.companyProfile(f.profile.id, AAPL, source = null, refresh = false)

            result.profile.status shouldBe DataStatus.LIMIT_REACHED
            result.profile.data.shouldBeNull()
            result.identity.name shouldBe "APPLE INC"
        }
    }

    context("source and target resolution") {
        test("profile default source is used when no source is requested") {
            val f = fixture(defaultSource = SEC_EDGAR)
            f.secBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            f.service.balanceSheet(f.profile.id, AAPL, source = null, refresh = false).source shouldBe SEC_EDGAR
        }

        test("profile default source that is no longer configured falls back to the configured default") {
            val f = fixture(defaultSource = SourceId.of("tiingo"))
            f.finnhubBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            f.service.balanceSheet(f.profile.id, AAPL, source = null, refresh = false).source shouldBe FINNHUB
        }

        test("unknown source is not found") {
            val f = fixture()

            shouldThrow<ResourceNotFoundException> {
                f.service.priceAndRatios(f.profile.id, AAPL, source = SourceId.of("tiingo"), refresh = false)
            }.message shouldContain "tiingo"
        }

        test("unknown profile is not found") {
            val f = fixture()

            shouldThrow<ResourceNotFoundException> {
                f.service.companyProfile(ProfileId.random(), AAPL, source = null, refresh = false)
            }
        }

        test("symbol missing from the directory is not found") {
            val f = fixture()

            shouldThrow<ResourceNotFoundException> {
                f.service.balanceSheet(f.profile.id, Symbol.of("NOPE"), source = null, refresh = false)
            }.message shouldContain "NOPE"
            f.source.calls.shouldBeEmpty()
        }

        test("symbol of an exchange whose directory is temporarily unavailable is not found") {
            val f = fixture()
            f.directory.unavailable(ExchangeCode("AS"))

            shouldThrow<ResourceNotFoundException> {
                f.service.priceAndRatios(f.profile.id, AD, source = null, refresh = false)
            }
            f.source.calls.shouldBeEmpty()
        }
    }

    context("symbol of an exchange not covered by the directory source") {
        fun notCovered(capabilities: Set<Capability> = ALL_MARKET_DATA) =
            fixture(capabilities = capabilities).apply { directory.notCovered(ExchangeCode("AS"), FINNHUB) }

        test("price and ratios are NOT_SUPPORTED without a call, a budget reservation or a not found") {
            val f = notCovered()
            f.quoteFromSource("AD.AS", 30.0, "EUR")
            f.ratiosFromSource("AD.AS")

            val result = f.service.priceAndRatios(f.profile.id, AD, source = null, refresh = true)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            result.quote.status shouldBe DataStatus.NOT_SUPPORTED
            result.ratios.status shouldBe DataStatus.NOT_SUPPORTED
            result.quote.data.shouldBeNull()
            f.source.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("company profile is NOT_SUPPORTED without a call and has the static exchange table identity") {
            val f = notCovered()
            f.profileFromSource("AD.AS")

            val result = f.service.companyProfile(f.profile.id, AD, source = null, refresh = true)

            result.profile.status shouldBe DataStatus.NOT_SUPPORTED
            result.identity shouldBe CompanyIdentity(AD, "AMS", "AD", Market.EU)
            f.source.calls.shouldBeEmpty()
        }

        test("company profile identity takes the name stored in the watchlist and doesn't change the entry") {
            val f = notCovered()
            val entry = watchlistEntry("AD.AS", name = "Koninklijke Ahold Delhaize NV")
            f.profiles.save(f.profile.copy(watchlist = listOf(entry)))

            f.service.companyProfile(f.profile.id, AD, source = null, refresh = false).identity.name shouldBe
                "Koninklijke Ahold Delhaize NV"
            f.profiles.findById(f.profile.id)!!.watchlist shouldContainExactly listOf(entry)
            f.profiles.companyUpdates shouldBe 0
        }

        test("balance sheet of the directory source is NOT_SUPPORTED without a call, a cache read or a budget reservation") {
            val f = notCovered()
            f.cached(balanceSheetKey("AD.AS"), sampleBalanceSheet(), age = Duration.ofDays(1))
            f.finnhubBalanceSheets.balanceSheets[AD] = SourceResult.Success(sampleBalanceSheet(), f.success())

            val result = f.service.balanceSheet(f.profile.id, AD, source = null, refresh = false)

            result shouldBe SourceData(FINNHUB, DataStatus.NOT_SUPPORTED, data = null, fetchedAt = null)
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("balance sheet of the directory source is NOT_SUPPORTED without a call on refresh") {
            val f = notCovered()

            f.service.balanceSheet(f.profile.id, AD, source = FINNHUB, refresh = true).status shouldBe DataStatus.NOT_SUPPORTED
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("balance sheet of a US symbol is still requested from the directory source") {
            val f = notCovered()
            f.finnhubBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            f.service.balanceSheet(f.profile.id, AAPL, source = null, refresh = false).status shouldBe DataStatus.FRESH
            f.finnhubBalanceSheets.calls shouldContainExactly listOf(AAPL)
        }

        test("balance sheet of SEC EDGAR is requested from its adapter instead of not found") {
            val f = notCovered()

            f.service.balanceSheet(f.profile.id, AD, source = SEC_EDGAR, refresh = false).source shouldBe SEC_EDGAR
            f.secBalanceSheets.calls shouldContainExactly listOf(AD)
        }

        test("exchange not covered by another source doesn't stop requests to the selected source") {
            val f = fixture().apply { directory.notCovered(ExchangeCode("AS"), SourceId.of("tiingo")) }
            f.quoteFromSource("AD.AS", 30.0, "EUR")
            f.ratiosFromSource("AD.AS")

            val result = f.service.priceAndRatios(f.profile.id, AD, source = null, refresh = false)

            result.status shouldBe DataStatus.FRESH
            result.quote.data?.quote shouldBe Quote(30.0, "EUR")
            f.source.calls shouldContainExactlyInAnyOrder listOf("quote:AD.AS", "ratios:AD.AS")
        }
    }

    test("outcome of a skipped call is not recorded") {
        val f = fixture()
        f.exhaustBudget()

        f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

        f.sourceCalls.writes.shouldBeEmpty()
    }

    test("call outcome of a failed call is recorded as is") {
        val f = fixture()
        f.quoteFails("AAPL", 503)
        f.source.ratios[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(503, f.now))

        f.service.priceAndRatios(f.profile.id, AAPL, source = null, refresh = false)

        f.sourceCalls.writes.single().second.result shouldBe SourceCallResult.HTTP_ERROR
    }
})
