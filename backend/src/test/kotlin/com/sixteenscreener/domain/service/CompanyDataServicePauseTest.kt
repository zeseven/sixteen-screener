package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.RequestTrigger
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.ratiosKey
import com.sixteenscreener.fakes.FakeMarketDataSource
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

private val AAPL = Symbol.of("AAPL")
private val YAHOO_DAY = listOf(RequestLimit(LimitInterval.DAY, null))

private class SlowQuotes(private val delegate: FakeMarketDataSource) : MarketDataSource by delegate {
    override suspend fun quote(listing: ListedSymbol): SourceResult<Quote> {
        delay(50)
        return delegate.quote(listing)
    }
}

private class PauseFixture(yahooMarketDataSource: (FakeMarketDataSource) -> MarketDataSource = { it }) {
    val data = CompanyDataFixture(yahooMarketDataSource = yahooMarketDataSource).apply { listed(listing("AAPL")) }

    suspend fun lastCall(outcome: SourceCallOutcome, source: SourceId = YAHOO) = data.sourceCalls.record(source, outcome)

    suspend fun rateLimitedAgo(age: Duration, source: SourceId = YAHOO) =
        lastCall(SourceCallOutcome.httpError(429, data.now - age), source)

    fun yahooPriceAndRatios() {
        data.yahoo.quotes[AAPL] = SourceResult.Success(Quote(190.0, "USD"), data.success())
        data.yahoo.ratios[AAPL] = SourceResult.Success(sampleRatios(), data.success())
    }

    suspend fun priceAndRatios(source: SourceId = YAHOO, trigger: RequestTrigger = RequestTrigger.MANUAL) =
        data.service.priceAndRatios(data.profile.id, AAPL, source, trigger)

    suspend fun yahooUsed(): Int = data.counters.usage(YAHOO, YAHOO_DAY, data.now).single().used

    suspend fun storedLastCall(source: SourceId = YAHOO) = data.sourceCalls.findAll(listOf(source))[source]
}

class CompanyDataServicePauseTest : FunSpec({

    context("while Yahoo Finance is paused after a 429") {
        test("price and ratios return cached data with LIMIT_REACHED without a call") {
            val f = PauseFixture()
            f.rateLimitedAgo(Duration.ofMinutes(5))
            f.yahooPriceAndRatios()
            val quote = f.data.cachedQuote("AAPL", 180.0, age = Duration.ofDays(2), source = YAHOO)
            val ratios = f.data.cached(ratiosKey("AAPL", YAHOO), sampleRatios(pe = 25.0), age = Duration.ofDays(2))

            val result = f.priceAndRatios()

            result.quote shouldBe SourceData(YAHOO, DataStatus.LIMIT_REACHED, quote.data, quote.fetchedAt)
            result.ratios shouldBe SourceData(YAHOO, DataStatus.LIMIT_REACHED, ratios.data, ratios.fetchedAt)
            f.data.yahoo.calls.shouldBeEmpty()
        }

        test("without cached data the result is empty with LIMIT_REACHED") {
            val f = PauseFixture()
            f.rateLimitedAgo(Duration.ofMinutes(5))
            f.yahooPriceAndRatios()

            val result = f.priceAndRatios()

            result.quote shouldBe SourceData(YAHOO, DataStatus.LIMIT_REACHED, data = null, fetchedAt = null)
            result.ratios.status shouldBe DataStatus.LIMIT_REACHED
            result.ratios.data.shouldBeNull()
            f.data.yahoo.calls.shouldBeEmpty()
        }

        test("the skipped request is not counted and doesn't change the last call outcome") {
            val f = PauseFixture()
            f.rateLimitedAgo(Duration.ofMinutes(5))
            val stored = f.storedLastCall()
            f.yahooPriceAndRatios()

            f.priceAndRatios()

            f.yahooUsed() shouldBe 0
            f.storedLastCall() shouldBe stored
            f.data.sourceCalls.writes.size shouldBe 1
        }

        test("dividends, balance sheet and company profile are not requested either") {
            val f = PauseFixture()
            f.rateLimitedAgo(Duration.ofMinutes(14))
            f.data.yahoo.dividends[AAPL] = SourceResult.Success(Dividends(emptyList()), f.data.success())
            f.data.yahoo.profiles[AAPL] = SourceResult.Success(sampleProfile(), f.data.success())
            f.data.yahooBalanceSheets.balanceSheets[AAPL] = SourceResult.Success(sampleBalanceSheet(), f.data.success())
            val id = f.data.profile.id

            f.data.service.dividends(id, AAPL, YAHOO, RequestTrigger.MANUAL).dividends.status shouldBe DataStatus.LIMIT_REACHED
            f.data.service.balanceSheet(id, AAPL, YAHOO, RequestTrigger.MANUAL).status shouldBe DataStatus.LIMIT_REACHED
            f.data.service.companyProfile(id, AAPL, YAHOO, RequestTrigger.MANUAL).profile.status shouldBe
                DataStatus.LIMIT_REACHED

            f.data.yahoo.calls.shouldBeEmpty()
            f.data.yahooBalanceSheets.calls.shouldBeEmpty()
            f.yahooUsed() shouldBe 0
        }

        test("a cached company profile returned during the pause doesn't set the watchlist sector") {
            val f = PauseFixture()
            f.data.profiles.save(f.data.profile.copy(watchlist = listOf(watchlistEntry("AAPL"))))
            f.rateLimitedAgo(Duration.ofMinutes(5))
            f.data.cached(profileKey("AAPL", YAHOO), sampleProfile(sector = "Technology"), age = Duration.ofDays(2))

            val result = f.data.service.companyProfile(f.data.profile.id, AAPL, YAHOO, RequestTrigger.MANUAL)

            result.profile.status shouldBe DataStatus.LIMIT_REACHED
            result.profile.data?.sector shouldBe "Technology"
            f.data.profiles.findById(f.data.profile.id)!!.watchlist shouldContainExactly listOf(watchlistEntry("AAPL"))
        }
    }

    test("Yahoo Finance is called again just after the end of the pause") {
        val f = PauseFixture()
        f.rateLimitedAgo(YAHOO_PAUSE)
        f.yahooPriceAndRatios()

        f.priceAndRatios().quote.status shouldBe DataStatus.FRESH

        f.data.yahoo.calls shouldContainExactly listOf("quote:AAPL", "ratios:AAPL")
        f.yahooUsed() shouldBe 2
    }

    test("Yahoo Finance is still paused just before the end of the pause") {
        val f = PauseFixture()
        f.rateLimitedAgo(YAHOO_PAUSE.minusMillis(1))
        f.yahooPriceAndRatios()

        f.priceAndRatios().quote.status shouldBe DataStatus.LIMIT_REACHED

        f.data.yahoo.calls.shouldBeEmpty()
    }

    context("other last call outcomes don't pause Yahoo Finance") {
        withData(
            mapOf<String, (Instant) -> SourceCallOutcome>(
                "HTTP 500" to { SourceCallOutcome.httpError(500, it) },
                "HTTP 404" to { SourceCallOutcome.httpError(404, it) },
                "timeout" to SourceCallOutcome::timeout,
                "success" to SourceCallOutcome::success,
            ),
        ) { outcomeAt ->
            val f = PauseFixture()
            f.lastCall(outcomeAt(f.data.now.minusSeconds(60)))
            f.yahooPriceAndRatios()

            f.priceAndRatios().quote.status shouldBe DataStatus.FRESH
            f.data.yahoo.calls shouldContainExactly listOf("quote:AAPL", "ratios:AAPL")
        }
    }

    test("Yahoo Finance never called before is called, with one read of its last call") {
        val f = PauseFixture()
        f.yahooPriceAndRatios()

        f.priceAndRatios().quote.status shouldBe DataStatus.FRESH

        f.data.sourceCalls.reads shouldBe 1
    }

    test("a request that calls nothing doesn't read the last call") {
        val f = PauseFixture()
        f.rateLimitedAgo(Duration.ofMinutes(5))
        f.data.cachedQuote("AAPL", 180.0, age = Duration.ofDays(2), source = YAHOO)

        f.priceAndRatios(trigger = RequestTrigger.AUTO).quote.status shouldBe DataStatus.CACHED

        f.data.sourceCalls.reads shouldBe 0
    }

    test("a source without a pause is called after a recent 429 without reading its last call") {
        val f = PauseFixture()
        f.rateLimitedAgo(Duration.ofMinutes(1), source = FINNHUB)
        f.data.quoteFromSource("AAPL", 190.0)
        f.data.ratiosFromSource("AAPL")

        f.priceAndRatios(source = FINNHUB).quote.status shouldBe DataStatus.FRESH

        f.data.quoteCalls() shouldContainExactly listOf("quote:AAPL")
        f.data.sourceCalls.reads shouldBe 0
    }

    context("a 429 of the first of the two price and ratios calls") {
        test("stops the second call and records the 429") {
            val f = PauseFixture()
            f.yahooPriceAndRatios()
            f.data.yahoo.quotes[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(429, f.data.now))

            val result = f.priceAndRatios()

            f.data.yahoo.calls shouldContainExactly listOf("quote:AAPL")
            result.quote.status shouldBe DataStatus.SOURCE_ERROR
            result.ratios.status shouldBe DataStatus.LIMIT_REACHED
            f.storedLastCall() shouldBe SourceCallOutcome.httpError(429, f.data.now)
        }

        test("stops the second call even when the first takes a while") {
            val f = PauseFixture(yahooMarketDataSource = ::SlowQuotes)
            f.yahooPriceAndRatios()
            f.data.yahoo.quotes[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(429, f.data.now))

            f.priceAndRatios().ratios.status shouldBe DataStatus.LIMIT_REACHED

            f.data.yahoo.calls shouldContainExactly listOf("quote:AAPL")
        }

        test("pauses Yahoo Finance for the next requests") {
            val f = PauseFixture()
            f.yahooPriceAndRatios()
            f.data.yahoo.quotes[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(429, f.data.now))
            f.priceAndRatios()
            f.data.yahoo.calls.clear()
            f.data.clock.advanceBy(Duration.ofMinutes(10))

            f.priceAndRatios().quote.status shouldBe DataStatus.LIMIT_REACHED

            f.data.yahoo.calls.shouldBeEmpty()
        }

        test("of a source without a pause still sends the second call") {
            val f = PauseFixture()
            f.data.quoteFails("AAPL", status = 429)
            f.data.ratiosFromSource("AAPL")

            val result = f.priceAndRatios(source = FINNHUB)

            f.data.source.calls shouldContainExactlyInAnyOrder listOf("quote:AAPL", "ratios:AAPL")
            result.ratios.status shouldBe DataStatus.FRESH
        }
    }

    test("a 429 of the second call keeps the data of the first") {
        val f = PauseFixture()
        f.yahooPriceAndRatios()
        f.data.yahoo.ratios[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(429, f.data.now))

        val result = f.priceAndRatios()

        f.data.yahoo.calls shouldContainExactly listOf("quote:AAPL", "ratios:AAPL")
        result.quote.status shouldBe DataStatus.FRESH
        result.ratios.status shouldBe DataStatus.SOURCE_ERROR
    }
})
