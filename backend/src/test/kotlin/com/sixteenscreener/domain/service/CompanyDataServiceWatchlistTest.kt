package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.quoteKey
import com.sixteenscreener.fakes.FakeMarketDataSource
import com.sixteenscreener.port.outbound.MarketDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

private val quotesOnly = setOf(Capability.QUOTE)

private fun symbols(count: Int): List<String> = (1..count).map { "C%03d".format(it) }

private class ConcurrencyProbe(private val delegate: FakeMarketDataSource) : MarketDataSource by delegate {
    private val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()

    override suspend fun quote(listing: ListedSymbol): SourceResult<Quote> {
        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
        delay(20)
        inFlight.decrementAndGet()
        return delegate.quote(listing)
    }
}

private suspend fun CompanyDataFixture.watchlist(vararg tickers: String) = watchlist(tickers.toList())

private suspend fun CompanyDataFixture.watchlist(tickers: List<String>) =
    tickers.map(Symbol::of).let { symbols -> service.watchlistQuotes(FINNHUB, symbols, directory.findAll(symbols)) }

private suspend fun CompanyDataFixture.unexpiredProfile(ticker: String) =
    service.unexpiredCompanyProfile(FINNHUB, Symbol.of(ticker))

private fun CompanyDataFixture.listedWithQuotes(tickers: List<String>) = tickers.forEachIndexed { index, ticker ->
    listed(listing(ticker))
    quoteFromSource(ticker, 100.0 + index)
}

class CompanyDataServiceWatchlistTest : FunSpec({

    test("empty watchlist reads nothing and calls nothing") {
        val f = CompanyDataFixture()

        f.watchlist().shouldBeEmpty()

        f.cache.getAllCalls shouldBe 0
        f.countingCounters.tryAcquireCalls shouldBe 0
    }

    test("fresh quotes are returned without calls, stale and missing quotes are refreshed") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        f.listedWithQuotes(listOf("FRESH", "STALE", "MISSING"))
        val fresh = f.cachedQuote("FRESH", 50.0, age = Duration.ofMinutes(59))
        f.cachedQuote("STALE", 60.0, age = Duration.ofHours(1))

        val result = f.watchlist("FRESH", "STALE", "MISSING")

        result.getValue(Symbol.of("FRESH")).status shouldBe DataStatus.FRESH
        result.getValue(Symbol.of("FRESH")).fetchedAt shouldBe fresh.fetchedAt
        result.getValue(Symbol.of("STALE")).status shouldBe DataStatus.FRESH
        result.getValue(Symbol.of("STALE")).fetchedAt shouldBe f.now
        result.getValue(Symbol.of("MISSING")).data?.quote?.price shouldBe 102.0
        f.quoteCalls() shouldContainExactlyInAnyOrder listOf("quote:STALE", "quote:MISSING")
    }

    test("all quotes are read with one cache read and budget is reserved once") {
        val f = CompanyDataFixture()
        val tickers = symbols(10)
        f.listedWithQuotes(tickers)

        f.watchlist(tickers)

        f.cache.getAllCalls shouldBe 1
        f.countingCounters.tryAcquireCalls shouldBe 1
    }

    test("company profiles are neither read nor requested, even when absent") {
        val f = CompanyDataFixture()
        f.listedWithQuotes(listOf("AAPL", "MSFT"))
        f.profileFromSource("AAPL")
        f.profileFromSource("MSFT")
        f.cached(profileKey("MSFT"), sampleProfile(), age = Duration.ofDays(400))

        f.watchlist("AAPL", "MSFT")

        f.profileCalls().shouldBeEmpty()
        f.quoteCalls() shouldContainExactlyInAnyOrder listOf("quote:AAPL", "quote:MSFT")
    }

    test("100 companies with budget for 30 refresh the 30 oldest quotes") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        val tickers = symbols(100)
        f.listedWithQuotes(tickers)
        tickers.forEachIndexed { index, ticker -> f.cachedQuote(ticker, 1.0, age = Duration.ofHours(2L + index)) }
        f.remainingBudget(30)

        val result = f.watchlist(tickers)

        val oldest = tickers.takeLast(30).toSet()
        f.quoteCalls() shouldContainExactlyInAnyOrder oldest.map { "quote:$it" }
        result.forEach { (symbol, quote) ->
            if (symbol.value in oldest) {
                quote.status shouldBe DataStatus.FRESH
                quote.fetchedAt shouldBe f.now
            } else {
                quote.status shouldBe DataStatus.LIMIT_REACHED
                quote.data?.quote?.price shouldBe 1.0
                quote.fetchedAt shouldBe f.cache.get(quoteKey(symbol.value))?.fetchedAt
            }
        }
    }

    test("companies without any cached quote are refreshed first") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        f.listedWithQuotes(listOf("OLD", "NEW"))
        f.cachedQuote("OLD", 1.0, age = Duration.ofDays(5))
        f.remainingBudget(1)

        val result = f.watchlist("OLD", "NEW")

        f.quoteCalls() shouldContainExactly listOf("quote:NEW")
        result.getValue(Symbol.of("OLD")).status shouldBe DataStatus.LIMIT_REACHED
    }

    test("company beyond the budget without a cached quote gets empty data with LIMIT_REACHED") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        f.listedWithQuotes(listOf("AAPL"))
        f.exhaustBudget()

        val quote = f.watchlist("AAPL").getValue(Symbol.of("AAPL"))

        quote.status shouldBe DataStatus.LIMIT_REACHED
        quote.data.shouldBeNull()
        quote.fetchedAt.shouldBeNull()
        f.sourceCalls.writes.shouldBeEmpty()
    }

    test("429 in the middle of the batch skips the remaining granted calls") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        val tickers = symbols(40)
        f.listedWithQuotes(tickers)
        tickers.forEachIndexed { index, ticker -> f.cachedQuote(ticker, 1.0, age = Duration.ofHours(100L - index)) }
        f.quoteRateLimited("C010")

        val result = f.watchlist(tickers)

        val called = f.quoteCalls().map { it.removePrefix("quote:") }
        called.size shouldBeLessThanOrEqual 10 + 4
        tickers.drop(14).forEach { called shouldNotContain it }
        result.getValue(Symbol.of("C010")).status shouldBe DataStatus.LIMIT_REACHED
        tickers.filterNot { it in called }.forEach { ticker ->
            val quote = result.getValue(Symbol.of(ticker))
            quote.status shouldBe DataStatus.LIMIT_REACHED
            quote.data?.quote?.price shouldBe 1.0
        }
        f.limiter.tryAcquire(FINNHUB, 1) shouldBe 0
    }

    test("source failures for some companies return their cached or empty data") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        f.listedWithQuotes(listOf("OK", "FAILCACHED", "FAILEMPTY"))
        val cached = f.cachedQuote("FAILCACHED", 7.0, age = Duration.ofHours(4))
        f.quoteFails("FAILCACHED", 500)
        f.quoteFails("FAILEMPTY", 502)

        val result = f.watchlist("OK", "FAILCACHED", "FAILEMPTY")

        result.getValue(Symbol.of("OK")).status shouldBe DataStatus.FRESH
        result.getValue(Symbol.of("FAILCACHED")).status shouldBe DataStatus.SOURCE_ERROR
        result.getValue(Symbol.of("FAILCACHED")).data shouldBe cached.data
        result.getValue(Symbol.of("FAILCACHED")).fetchedAt shouldBe cached.fetchedAt
        f.cache.get(quoteKey("FAILCACHED")) shouldBe cached
        result.getValue(Symbol.of("FAILEMPTY")).status shouldBe DataStatus.SOURCE_ERROR
        result.getValue(Symbol.of("FAILEMPTY")).data.shouldBeNull()
        result.getValue(Symbol.of("FAILEMPTY")).fetchedAt.shouldBeNull()
    }

    test("quotes are requested with at most 5 concurrent calls") {
        var probe: ConcurrencyProbe? = null
        val f = CompanyDataFixture(capabilities = quotesOnly) { fake -> ConcurrencyProbe(fake).also { probe = it } }
        val tickers = symbols(20)
        f.listedWithQuotes(tickers)

        f.watchlist(tickers)

        f.quoteCalls() shouldHaveSize 20
        probe!!.maxInFlight.get() shouldBe 5
    }

    test("stale quote of a symbol missing from the directory is not requested") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        val cached = f.cachedQuote("GONE", 3.0, age = Duration.ofHours(2))

        val quote = f.watchlist("GONE").getValue(Symbol.of("GONE"))

        quote.status shouldBe DataStatus.SOURCE_ERROR
        quote.data shouldBe cached.data
        f.quoteCalls().shouldBeEmpty()
    }

    test("fresh quote of a symbol missing from the directory is still returned as FRESH") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        f.cachedQuote("GONE", 3.0, age = Duration.ofMinutes(5))

        f.watchlist("GONE").getValue(Symbol.of("GONE")).status shouldBe DataStatus.FRESH
    }

    test("refreshed quote is stored in the exchange time zone of its listing") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        f.clock.instant = java.time.Instant.parse("2026-11-14T23:30:00Z")
        f.listed(listing("SAP.DE", mic = "XETR", currency = "EUR"))
        f.quoteFromSource("SAP.DE", 200.0, "EUR")

        val quote = f.watchlist("SAP.DE").getValue(Symbol.of("SAP.DE"))

        quote.data?.marketDate shouldBe java.time.LocalDate.parse("2026-11-15")
        quote.data?.quote shouldBe Quote(200.0, "EUR")
    }

    test("failed cache writes still return the refreshed quotes as FRESH") {
        val f = CompanyDataFixture()
        f.listedWithQuotes(listOf("AAPL"))
        f.cachedQuote("AAPL", 90.0, age = Duration.ofDays(1))
        f.cache.failWrites = true

        val quote = f.watchlist("AAPL").getValue(Symbol.of("AAPL"))

        quote.status shouldBe DataStatus.FRESH
        quote.fetchedAt shouldBe f.now
        quote.data?.quote?.price shouldBe 100.0
        quote.data?.previousPrice?.price shouldBe 90.0
    }

    test("source without data for the company gives NO_DATA with its cached quote") {
        val f = CompanyDataFixture(capabilities = quotesOnly)
        f.listed(listing("AAPL"))
        val cached = f.cachedQuote("AAPL", 3.0, age = Duration.ofHours(2))
        f.source.quotes[Symbol.of("AAPL")] = SourceResult.NoData(f.success())

        val quote = f.watchlist("AAPL").getValue(Symbol.of("AAPL"))

        quote.status shouldBe DataStatus.NO_DATA
        quote.data shouldBe cached.data
    }

    test("watchlist without supported data types makes no calls") {
        val f = CompanyDataFixture(capabilities = emptySet())
        f.listedWithQuotes(listOf("AAPL"))

        f.watchlist("AAPL").getValue(Symbol.of("AAPL")).status shouldBe DataStatus.NOT_SUPPORTED
        f.countingCounters.tryAcquireCalls shouldBe 0
        f.source.calls.shouldBeEmpty()
    }

    context("unexpired company profile") {

        test("cached profile younger than 90 days is returned without a request, even when older than 14 days") {
            val f = CompanyDataFixture()
            f.cached(profileKey("AAPL"), sampleProfile(), age = Duration.ofDays(90).minusSeconds(1))
            f.profileFromSource("AAPL", sampleProfile(sector = "New"))

            val profile = f.unexpiredProfile("AAPL")

            f.profileCalls().shouldBeEmpty()
            profile.status shouldBe DataStatus.CACHED
            profile.data?.sector shouldBe "Technology"
        }

        test("cached profile of exactly 90 days or older is requested again and cached") {
            val f = CompanyDataFixture()
            f.cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(90))
            f.profileFromSource("AAPL", sampleProfile(sector = "New"))

            val profile = f.unexpiredProfile("AAPL")

            f.profileCalls() shouldContainExactly listOf("profile:AAPL")
            profile.status shouldBe DataStatus.FRESH
            profile.data?.sector shouldBe "New"
            f.cache.get(profileKey("AAPL"))?.fetchedAt shouldBe f.now
        }

        test("absent profile is requested") {
            val f = CompanyDataFixture()
            f.profileFromSource("AAPL")

            f.unexpiredProfile("AAPL").status shouldBe DataStatus.FRESH
            f.profileCalls() shouldContainExactly listOf("profile:AAPL")
        }

        test("expired profile beyond the budget keeps its cached data with LIMIT_REACHED") {
            val f = CompanyDataFixture()
            f.cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(100))
            f.exhaustBudget()

            val profile = f.unexpiredProfile("AAPL")

            profile.status shouldBe DataStatus.LIMIT_REACHED
            profile.data?.sector shouldBe "Old"
            f.source.calls.shouldBeEmpty()
        }

        test("failed cache write still returns the fetched profile as FRESH") {
            val f = CompanyDataFixture()
            f.profileFromSource("AAPL")
            f.cache.failWrites = true

            val profile = f.unexpiredProfile("AAPL")

            profile.status shouldBe DataStatus.FRESH
            profile.data shouldBe sampleProfile()
        }

        test("source without the profile capability gives NOT_SUPPORTED without a request") {
            val f = CompanyDataFixture(capabilities = quotesOnly)

            f.unexpiredProfile("AAPL").status shouldBe DataStatus.NOT_SUPPORTED
            f.source.calls.shouldBeEmpty()
        }
    }
})
