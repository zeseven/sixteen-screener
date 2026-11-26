package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.Dividend
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.Indicator
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.PriceTrend
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.RequestTrigger
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
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import java.time.LocalDate

private val AAPL = Symbol.of("AAPL")
private val AD = Symbol.of("AD.AS")
private val apple = listing("AAPL", name = "APPLE INC")

private data class HomeExchangeCase(
    val symbol: String,
    val mic: String,
    val type: String?,
    val country: String?,
    val home: Boolean,
)

private fun fixture(
    limit: Int = 60,
    capabilities: Set<Capability> = ALL_MARKET_DATA,
) = CompanyDataFixture(limit, capabilities).apply { listed(apple) }

class CompanyDataServiceTest : FunSpec({

    context("price and ratios") {
        test("cache miss requests quote and ratios and stores both") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.indicators shouldContainExactly listOf(Indicator(RatioCode.PE, IndicatorLevel.BAD))
            f.profiles.findById(f.profile.id)?.indicatorSettings shouldBe IndicatorDefaults.all.map { it.setting }
        }

        test("P/E calculated from the quote price makes an indicator while the returned ratio stays null") {
            val f = fixture()
            f.quoteFromSource("AAPL", 13.9)
            f.ratiosFromSource("AAPL", Ratios.of(mapOf(RatioCode.EPS to -2.252)))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.indicators shouldContainExactly listOf(Indicator(RatioCode.PE, IndicatorLevel.BAD))
            result.ratios.data?.valueOf(RatioCode.PE) shouldBe null
        }

        test("no ratios make no indicators") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)

            f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).indicators.shouldBeEmpty()
        }

        test("budget for both calls is reserved in one transaction") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            f.countingCounters.tryAcquireCalls shouldBe 1
        }

        test("fresh cache is returned as FRESH without a call") {
            val f = fixture()
            val quote = f.cachedQuote("AAPL", 189.0, age = Duration.ofMinutes(10))
            val ratios = f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofMinutes(20))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.CACHED
            result.quote.status shouldBe DataStatus.CACHED
            result.fetchedAt shouldBe f.now - Duration.ofDays(3)
            f.source.calls.shouldBeEmpty()
        }

        test("only the missing part is requested") {
            val f = fixture()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofHours(5))
            f.ratiosFromSource("AAPL")

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.FRESH
            result.fetchedAt shouldBe f.now
            result.quote.data?.quote?.price shouldBe 191.0
            result.ratios.data shouldBe sampleRatios(31.0)
        }

        test("cache miss without budget returns empty data with LIMIT_REACHED") {
            val f = fixture()
            f.exhaustBudget()
            f.quoteFromSource("AAPL", 190.0)

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.SOURCE_ERROR
            result.fetchedAt.shouldBeNull()
            result.quote.data.shouldBeNull()
            result.ratios.data.shouldBeNull()
        }

        test("source without data for the company returns NO_DATA") {
            val f = fixture()
            f.source.quotes[AAPL] = SourceResult.NoData(f.success())
            f.source.ratios[AAPL] = SourceResult.NoData(f.success())

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.NO_DATA
            result.fetchedAt.shouldBeNull()
        }

        test("source answering 429 gives LIMIT_REACHED and saturates its counters") {
            val f = fixture()
            f.quoteRateLimited("AAPL")
            f.source.ratios[AAPL] = SourceResult.LimitReached(SourceCallOutcome.httpError(429, f.now))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.LIMIT_REACHED
            f.limiter.tryAcquire(FINNHUB, 1) shouldBe 0
        }

        test("source lacking the capabilities gives NOT_SUPPORTED without a call") {
            val f = fixture(capabilities = setOf(Capability.SEARCH))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            result.fetchedAt.shouldBeNull()
            f.source.calls.shouldBeEmpty()
        }

        test("source without a market data adapter gives NOT_SUPPORTED") {
            val f = fixture()

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = SEC_EDGAR, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            result.source shouldBe SEC_EDGAR
            f.source.calls.shouldBeEmpty()
        }

        test("quote without ratios capability combines to the quote status") {
            val f = fixture(capabilities = setOf(Capability.QUOTE))
            f.quoteFromSource("AAPL", 190.0)

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.ratios.status shouldBe DataStatus.NOT_SUPPORTED
            result.status shouldBe DataStatus.FRESH
        }

        test("refreshed quote of a later market day carries the previous price and trend") {
            val f = fixture()
            f.cachedQuote("AAPL", 180.0, age = Duration.ofDays(1))
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            val quote = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL).quote.data

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

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

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

            val quote = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).quote

            quote.status shouldBe DataStatus.FRESH
            quote.data?.previousPrice.shouldBeNull()
            quote.data?.marketDate shouldBe java.time.LocalDate.parse("2026-11-14")
        }

        test("one call outcome is recorded per request") {
            val f = fixture()
            f.quoteFromSource("AAPL", 190.0)
            f.source.ratios[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(502, f.now))

            f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            f.sourceCalls.writes.map { it.first } shouldContainExactly listOf(FINNHUB)
        }
    }

    context("dividends") {
        fun dividends(vararg amounts: Double) = Dividends(
            amounts.mapIndexed { index, amount ->
                Dividend(LocalDate.parse("2026-10-01").minusMonths(index * 3L), null, null, null, amount, "USD")
            },
        )

        test("cache miss requests and stores the dividends; the yield comes from the cached quote") {
            val f = fixture(capabilities = ALL_MARKET_DATA + Capability.DIVIDENDS)
            f.cachedQuote("AAPL", 100.0, age = Duration.ofDays(3))
            f.source.dividends[AAPL] = SourceResult.Success(dividends(0.5, 0.5), f.success())

            val result = f.service.dividends(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.dividends.status shouldBe DataStatus.FRESH
            result.dividends.data shouldBe dividends(0.5, 0.5)
            result.dividendYield.shouldNotBeNull() shouldBe (1.0 plusOrMinus 1e-9)
            f.source.calls shouldContainExactly listOf("dividends:AAPL")
            f.cache.get(CacheKey(FINNHUB, AAPL, DataType.DIVIDENDS))?.data shouldBe dividends(0.5, 0.5)
        }

        test("cached dividends are returned without a call; without a cached quote there is no yield") {
            val f = fixture(capabilities = ALL_MARKET_DATA + Capability.DIVIDENDS)
            f.cached(CacheKey(FINNHUB, AAPL, DataType.DIVIDENDS), dividends(1.0), age = Duration.ofDays(2))

            val result = f.service.dividends(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.dividends.status shouldBe DataStatus.CACHED
            result.dividendYield.shouldBeNull()
            f.source.calls.shouldBeEmpty()
        }

        test("an empty dividend list is data with a yield of 0") {
            val f = fixture(capabilities = ALL_MARKET_DATA + Capability.DIVIDENDS)
            f.cachedQuote("AAPL", 100.0, age = Duration.ofMinutes(3))
            f.source.dividends[AAPL] = SourceResult.Success(Dividends(emptyList()), f.success())

            val result = f.service.dividends(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.dividends.status shouldBe DataStatus.FRESH
            result.dividendYield shouldBe 0.0
        }

        test("source without the capability gives NOT_SUPPORTED without a call or cache read") {
            val f = fixture()

            val result = f.service.dividends(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.dividends shouldBe SourceData(FINNHUB, DataStatus.NOT_SUPPORTED, data = null, fetchedAt = null)
            result.dividendYield.shouldBeNull()
            f.source.calls.shouldBeEmpty()
            f.cache.getAllCalls shouldBe 0
        }

        test("failed call without cache gives SOURCE_ERROR and no yield") {
            val f = fixture(capabilities = ALL_MARKET_DATA + Capability.DIVIDENDS)
            f.cachedQuote("AAPL", 100.0, age = Duration.ofMinutes(3))
            f.source.dividends[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(500, f.now))

            val result = f.service.dividends(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.dividends.status shouldBe DataStatus.SOURCE_ERROR
            result.dividendYield.shouldBeNull()
        }
    }

    context("balance sheet") {
        test("cache miss requests and stores the balance sheet") {
            val f = fixture()
            f.finnhubBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.FRESH
            result.data shouldBe sampleBalanceSheet()
            result.fetchedAt shouldBe f.now
            f.cache.get(balanceSheetKey("AAPL"))?.data shouldBe sampleBalanceSheet()
        }

        test("failed cache write still returns the fresh balance sheet") {
            val f = fixture()
            f.finnhubBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())
            f.cache.failWrites = true

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.FRESH
            result.data shouldBe sampleBalanceSheet()
            result.fetchedAt shouldBe f.now
        }

        test("explicit SEC EDGAR source is used instead of the default source") {
            val f = fixture()
            f.secBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = SEC_EDGAR, trigger = RequestTrigger.AUTO)

            result.source shouldBe SEC_EDGAR
            result.status shouldBe DataStatus.FRESH
            f.secBalanceSheets.calls shouldContainExactly listOf(AAPL)
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
        }

        test("not supported by the source for the symbol keeps empty data") {
            val f = fixture()
            f.secBalanceSheets.balanceSheets[AAPL] = SourceResult.NotSupported(outcome = null)

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = SEC_EDGAR, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            result.data.shouldBeNull()
        }

        test("source without the capability gives NOT_SUPPORTED without a call or cache read") {
            val f = fixture(capabilities = setOf(Capability.QUOTE, Capability.RATIOS))

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
        }

        test("stale cache is returned as CACHED") {
            val f = fixture()
            val cached = f.cached(balanceSheetKey("AAPL"), sampleBalanceSheet(), age = Duration.ofDays(10))

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.CACHED
            result.fetchedAt shouldBe cached.fetchedAt
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
        }
    }

    context("company profile") {
        test("identity comes from the directory and the name from the source profile") {
            val f = fixture()
            f.profileFromSource("AAPL")

            val result = f.service.companyProfile(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

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

            val result = f.service.companyProfile(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.profile.data?.sector shouldBe "Technology"
            f.profileCalls() shouldContainExactly listOf("profile:AAPL")
        }

        test("without profile data the directory name is used") {
            val f = fixture()
            f.exhaustBudget()

            val result = f.service.companyProfile(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.profile.status shouldBe DataStatus.LIMIT_REACHED
            result.profile.data.shouldBeNull()
            result.identity.name shouldBe "APPLE INC"
            result.homeExchange shouldBe false
        }

        test("identity keeps the security type of the listing when the name is taken from the source profile") {
            val f = CompanyDataFixture().apply { listed(listing("AAPL", name = "APPLE INC").copy(type = "ADR")) }
            f.profileFromSource("AAPL")

            val identity = f.service.companyProfile(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).identity

            identity.name shouldBe "Apple Inc"
            identity.type shouldBe "ADR"
            identity.stock shouldBe false
        }

        test("without a listing type the security type of the source profile is used") {
            val f = CompanyDataFixture(internationalCapabilities = ALL_MARKET_DATA).apply {
                directory.notCovered(ExchangeCode("AS"), FINNHUB)
            }
            f.international.profiles[AD] = SourceResult.Success(
                sampleProfile(name = "VanEck AEX ETF").copy(country = "NL", securityType = "ETF"),
                f.success(),
            )

            val result = f.service.companyProfile(f.profile.id, AD, source = null, trigger = RequestTrigger.MANUAL)

            result.identity.type shouldBe "ETF"
            result.identity.stock shouldBe false
            result.homeExchange shouldBe false
        }

        test("the security type of the listing wins over the one of the source profile") {
            val f = fixture()
            f.profileFromSource("AAPL", sampleProfile().copy(securityType = "Fund"))

            val identity = f.service.companyProfile(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).identity

            identity.type shouldBe "Common Stock"
        }

        context("home exchange") {
            withData(
                nameFn = { "${it.symbol} on ${it.mic} (${it.type}) for ${it.country} -> ${it.home}" },
                HomeExchangeCase("AAPL", "XNAS", "Common Stock", "US", home = true),
                HomeExchangeCase("SAP.DE", "XETR", "Common Stock", "de", home = true),
                HomeExchangeCase("O", "XNYS", "REIT", "US", home = true),
                HomeExchangeCase("SAP.DE", "XETR", null, "DE", home = true),
                HomeExchangeCase("APC.DE", "XETR", "Common Stock", "US", home = false),
                HomeExchangeCase("SAP.F", "XFRA", "Common Stock", "DE", home = false),
                HomeExchangeCase("NSRGY", "OTCQ", "Common Stock", "US", home = false),
                HomeExchangeCase("TM", "XNYS", "ADR", "US", home = false),
                HomeExchangeCase("AAPL", "XNAS", "Common Stock", null, home = false),
                HomeExchangeCase("AAPL", "ZZZZ", "Common Stock", "US", home = false),
            ) { case ->
                val symbol = Symbol.of(case.symbol)
                val f = CompanyDataFixture(internationalCapabilities = ALL_MARKET_DATA, internationalAutomaticRequests = true)
                f.listed(ListedSymbol(symbol, case.symbol, case.mic, "USD", case.type))
                f.profileFromSource(case.symbol, sampleProfile().copy(country = case.country))
                f.international.profiles[symbol] = SourceResult.Success(sampleProfile().copy(country = case.country), f.success())

                f.service.companyProfile(f.profile.id, symbol, source = null, trigger = RequestTrigger.AUTO).homeExchange shouldBe case.home
            }
        }
    }

    context("source and target resolution") {
        test("the default source of the US role is used for a US company when no source is requested") {
            val f = fixture()
            f.finnhubBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            f.service.balanceSheet(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).source shouldBe FINNHUB
        }

        test("the default source of the international role is used for a non-US company when no source is requested") {
            val f = fixture().apply { directory.notCovered(ExchangeCode("AS"), FINNHUB) }

            f.service.balanceSheet(f.profile.id, AD, source = null, trigger = RequestTrigger.AUTO).source shouldBe EODHD
        }

        test("unknown source is not found") {
            val f = fixture()

            shouldThrow<ResourceNotFoundException> {
                f.service.priceAndRatios(f.profile.id, AAPL, source = SourceId.of("tiingo"), trigger = RequestTrigger.AUTO)
            }.message shouldContain "tiingo"
        }

        test("unknown profile is not found") {
            val f = fixture()

            shouldThrow<ResourceNotFoundException> {
                f.service.companyProfile(ProfileId.random(), AAPL, source = null, trigger = RequestTrigger.AUTO)
            }
        }

        test("symbol missing from the directory is not found") {
            val f = fixture()

            shouldThrow<ResourceNotFoundException> {
                f.service.balanceSheet(f.profile.id, Symbol.of("NOPE"), source = null, trigger = RequestTrigger.AUTO)
            }.message shouldContain "NOPE"
            f.source.calls.shouldBeEmpty()
        }

        test("symbol of an exchange whose directory is temporarily unavailable is not found") {
            val f = fixture()
            f.directory.unavailable(ExchangeCode("AS"))

            shouldThrow<ResourceNotFoundException> {
                f.service.priceAndRatios(f.profile.id, AD, source = null, trigger = RequestTrigger.AUTO)
            }
            f.source.calls.shouldBeEmpty()
        }
    }

    context("company of a non-US exchange") {
        fun international() = fixture().apply { directory.notCovered(ExchangeCode("AS"), FINNHUB) }

        test("price and ratios come from the international source: AUTO without cache is not requested") {
            val f = international()
            f.quoteFromSource("AD.AS", 30.0, "EUR")
            f.international.quotes[AD] = SourceResult.Success(Quote(31.0, "EUR"), f.success())

            val result = f.service.priceAndRatios(f.profile.id, AD, source = null, trigger = RequestTrigger.AUTO)

            result.source shouldBe EODHD
            result.quote.status shouldBe DataStatus.NOT_REQUESTED
            result.ratios.status shouldBe DataStatus.NOT_SUPPORTED
            result.status shouldBe DataStatus.NOT_REQUESTED
            f.source.calls.shouldBeEmpty()
            f.international.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("a manual refresh requests the quote from the international source with the static exchange table listing") {
            val f = international()
            f.international.quotes[AD] = SourceResult.Success(Quote(31.0, "EUR"), f.success())

            val result = f.service.priceAndRatios(f.profile.id, AD, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.FRESH
            result.quote.data?.quote shouldBe Quote(31.0, "EUR")
            result.ratios.status shouldBe DataStatus.NOT_SUPPORTED
            f.international.calls shouldContainExactly listOf("quote:AD.AS")
            f.source.calls.shouldBeEmpty()
            f.cache.history(EODHD, AD).map { it.quote.price } shouldContainExactly listOf(31.0)
        }

        test("a requested source not configured for the international role is NOT_SUPPORTED without a call") {
            val f = international()
            f.quoteFromSource("AD.AS", 30.0, "EUR")
            f.ratiosFromSource("AD.AS")

            val result = f.service.priceAndRatios(f.profile.id, AD, source = FINNHUB, trigger = RequestTrigger.MANUAL)

            result.source shouldBe FINNHUB
            result.status shouldBe DataStatus.NOT_SUPPORTED
            f.source.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("a requested source not configured for the US role is NOT_SUPPORTED for a US company") {
            val f = fixture()
            f.international.quotes[AAPL] = SourceResult.Success(Quote(31.0, "USD"), f.success())

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = EODHD, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.NOT_SUPPORTED
            f.international.calls.shouldBeEmpty()
        }

        test("a symbol listed in the directory of its exchange is still routed by its exchange") {
            val f = fixture().apply { listed(listing("SAP.DE", mic = "XETR", currency = "EUR", name = "SAP SE")) }
            f.international.quotes[Symbol.of("SAP.DE")] = SourceResult.Success(Quote(200.0, "EUR"), f.success())

            val result = f.service.priceAndRatios(f.profile.id, Symbol.of("SAP.DE"), source = null, trigger = RequestTrigger.MANUAL)

            result.source shouldBe EODHD
            f.international.calls shouldContainExactly listOf("quote:SAP.DE")
        }

        test("dividends come from the international source on a manual refresh") {
            val f = international()
            val dividends = Dividends(listOf(Dividend(LocalDate.parse("2026-05-01"), null, null, null, 1.0, "EUR")))
            f.international.dividends[AD] = SourceResult.Success(dividends, f.success())

            f.service.dividends(f.profile.id, AD, source = null, trigger = RequestTrigger.AUTO).dividends.status shouldBe
                DataStatus.NOT_REQUESTED
            val result = f.service.dividends(f.profile.id, AD, source = null, trigger = RequestTrigger.MANUAL)

            result.dividends.data shouldBe dividends
            f.international.calls shouldContainExactly listOf("dividends:AD.AS")
        }

        test("company profile of the international source is NOT_SUPPORTED and has the static exchange table identity") {
            val f = international()
            f.profileFromSource("AD.AS")

            val result = f.service.companyProfile(f.profile.id, AD, source = null, trigger = RequestTrigger.MANUAL)

            result.profile.source shouldBe EODHD
            result.profile.status shouldBe DataStatus.NOT_SUPPORTED
            result.identity shouldBe CompanyIdentity(AD, "AMS", "AD", Market.EU, null)
            f.source.calls.shouldBeEmpty()
            f.international.calls.shouldBeEmpty()
        }

        test("company profile identity takes the name stored in the watchlist and doesn't change the entry") {
            val f = international()
            val entry = watchlistEntry("AD.AS", name = "Koninklijke Ahold Delhaize NV")
            f.profiles.save(f.profile.copy(watchlist = listOf(entry)))

            f.service.companyProfile(f.profile.id, AD, source = null, trigger = RequestTrigger.AUTO).identity.name shouldBe
                "Koninklijke Ahold Delhaize NV"
            f.profiles.findById(f.profile.id)!!.watchlist shouldContainExactly listOf(entry)
            f.profiles.companyUpdates shouldBe 0
        }

        test("balance sheet of the international source is NOT_SUPPORTED without a call, a cache read or a budget reservation") {
            val f = international()
            f.cached(balanceSheetKey("AD.AS", EODHD), sampleBalanceSheet(), age = Duration.ofDays(1))

            val result = f.service.balanceSheet(f.profile.id, AD, source = null, trigger = RequestTrigger.MANUAL)

            result shouldBe SourceData(EODHD, DataStatus.NOT_SUPPORTED, data = null, fetchedAt = null)
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("balance sheet of the US source requested for an international company is NOT_SUPPORTED without a call") {
            val f = international()
            f.finnhubBalanceSheets.balanceSheets[AD] = SourceResult.Success(sampleBalanceSheet(), f.success())

            f.service.balanceSheet(f.profile.id, AD, source = FINNHUB, trigger = RequestTrigger.MANUAL).status shouldBe
                DataStatus.NOT_SUPPORTED
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("SEC EDGAR is accepted for the balance sheet of a US company") {
            val f = fixture()
            f.secBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.success())

            val result = f.service.balanceSheet(f.profile.id, AAPL, source = SEC_EDGAR, trigger = RequestTrigger.AUTO)

            result.source shouldBe SEC_EDGAR
            result.status shouldBe DataStatus.FRESH
            f.secBalanceSheets.calls shouldContainExactly listOf(AAPL)
        }

        test("SEC EDGAR is NOT_SUPPORTED without a call for the balance sheet of an international company") {
            val f = international()

            f.service.balanceSheet(f.profile.id, AD, source = SEC_EDGAR, trigger = RequestTrigger.AUTO).status shouldBe
                DataStatus.NOT_SUPPORTED
            f.secBalanceSheets.calls.shouldBeEmpty()
        }

        test("SEC EDGAR is NOT_SUPPORTED for anything but the balance sheet") {
            val f = fixture()

            f.service.priceAndRatios(f.profile.id, AAPL, source = SEC_EDGAR, trigger = RequestTrigger.MANUAL).status shouldBe
                DataStatus.NOT_SUPPORTED
            f.service.companyProfile(f.profile.id, AAPL, source = SEC_EDGAR, trigger = RequestTrigger.MANUAL).profile.status shouldBe
                DataStatus.NOT_SUPPORTED
        }
    }

    context("source with automatic requests off") {
        fun manualOnly(limit: Int = 60) = CompanyDataFixture(limit = limit, automaticRequests = false).apply { listed(apple) }

        test("AUTO without cache is NOT_REQUESTED with empty data, no call and no budget reservation") {
            val f = manualOnly()
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.NOT_REQUESTED
            result.quote.status shouldBe DataStatus.NOT_REQUESTED
            result.ratios.status shouldBe DataStatus.NOT_REQUESTED
            result.fetchedAt.shouldBeNull()
            result.quote.data.shouldBeNull()
            result.ratios.data.shouldBeNull()
            f.source.calls.shouldBeEmpty()
            f.countingCounters.tryAcquireCalls shouldBe 0
        }

        test("AUTO with a stale cache returns it as CACHED without a call") {
            val f = manualOnly()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofDays(5))
            f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofDays(5))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.status shouldBe DataStatus.CACHED
            result.quote.data?.quote?.price shouldBe 189.0
            f.source.calls.shouldBeEmpty()
        }

        test("AUTO with a fresh cache returns it as FRESH without a call") {
            val f = manualOnly()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofMinutes(5))
            f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofMinutes(5))

            f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).status shouldBe
                DataStatus.FRESH
            f.source.calls.shouldBeEmpty()
        }

        test("AUTO with only the quote cached combines to NOT_REQUESTED") {
            val f = manualOnly()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofDays(1))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

            result.quote.status shouldBe DataStatus.CACHED
            result.ratios.status shouldBe DataStatus.NOT_REQUESTED
            result.status shouldBe DataStatus.NOT_REQUESTED
            f.source.calls.shouldBeEmpty()
        }

        test("MANUAL without cache calls the source") {
            val f = manualOnly()
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL")

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.FRESH
            f.source.calls shouldContainExactlyInAnyOrder listOf("quote:AAPL", "ratios:AAPL")
        }

        test("MANUAL with a cache younger than 1 hour returns it without a call") {
            val f = manualOnly()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofMinutes(59))
            f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofMinutes(59))
            f.quoteFromSource("AAPL", 190.0)

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.FRESH
            result.quote.data?.quote?.price shouldBe 189.0
            f.source.calls.shouldBeEmpty()
        }

        test("MANUAL with a cache of 1 hour or older calls the source") {
            val f = manualOnly()
            f.cachedQuote("AAPL", 189.0, age = Duration.ofHours(1))
            f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofHours(3))
            f.quoteFromSource("AAPL", 190.0)
            f.ratiosFromSource("AAPL", sampleRatios(12.0))

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.FRESH
            result.quote.data?.quote?.price shouldBe 190.0
            result.ratios.data shouldBe sampleRatios(12.0)
        }

        test("MANUAL with a stale cache and a reached limit returns the cache with LIMIT_REACHED") {
            val f = manualOnly()
            val quote = f.cachedQuote("AAPL", 189.0, age = Duration.ofHours(2))
            f.cached(ratiosKey("AAPL"), sampleRatios(), age = Duration.ofHours(2))
            f.exhaustBudget()

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.LIMIT_REACHED
            result.quote.data shouldBe quote.data
            f.source.calls.shouldBeEmpty()
        }

        test("MANUAL without cache and a reached limit returns empty data with LIMIT_REACHED") {
            val f = manualOnly()
            f.exhaustBudget()

            val result = f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.MANUAL)

            result.status shouldBe DataStatus.LIMIT_REACHED
            result.quote.data.shouldBeNull()
        }

        test("balance sheet and company profile are NOT_REQUESTED for AUTO without cache") {
            val f = manualOnly()
            f.profileFromSource("AAPL")

            f.service.balanceSheet(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).status shouldBe
                DataStatus.NOT_REQUESTED
            f.service.companyProfile(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO).profile.status shouldBe
                DataStatus.NOT_REQUESTED
            f.source.calls.shouldBeEmpty()
            f.finnhubBalanceSheets.calls.shouldBeEmpty()
        }

        test("an unexpired company profile for adding to the watchlist is not requested") {
            val f = manualOnly()
            f.profileFromSource("AAPL")

            f.service.unexpiredCompanyProfile(FINNHUB, AAPL).status shouldBe DataStatus.NOT_REQUESTED
            f.source.calls.shouldBeEmpty()
        }

        test("an expired company profile for adding to the watchlist is returned as CACHED without a call") {
            val f = manualOnly()
            f.cached(profileKey("AAPL"), sampleProfile(), age = Duration.ofDays(120))
            f.profileFromSource("AAPL")

            val profile = f.service.unexpiredCompanyProfile(FINNHUB, AAPL)

            profile.status shouldBe DataStatus.CACHED
            profile.data shouldBe sampleProfile()
            f.source.calls.shouldBeEmpty()
        }
    }

    test("outcome of a skipped call is not recorded") {
        val f = fixture()
        f.exhaustBudget()

        f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

        f.sourceCalls.writes.shouldBeEmpty()
    }

    test("call outcome of a failed call is recorded as is") {
        val f = fixture()
        f.quoteFails("AAPL", 503)
        f.source.ratios[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(503, f.now))

        f.service.priceAndRatios(f.profile.id, AAPL, source = null, trigger = RequestTrigger.AUTO)

        f.sourceCalls.writes.single().second.result shouldBe SourceCallResult.HTTP_ERROR
    }
})
