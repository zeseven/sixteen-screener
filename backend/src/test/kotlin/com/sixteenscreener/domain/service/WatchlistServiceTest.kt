package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.PriceTrend
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistItem
import com.sixteenscreener.domain.model.WatchlistMarketData
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.port.inbound.WatchlistUseCase.Companion.MAX_MARKET_DATA_SYMBOLS
import com.sixteenscreener.watchlistEntries
import com.sixteenscreener.watchlistEntry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

private class WatchlistFixture(vararg tickers: String) {
    val data = CompanyDataFixture()
    val service = WatchlistService(data.profiles, data.catalog, data.directory, data.service, data.identities)

    init {
        data.profiles.save(data.profile.copy(watchlist = watchlistEntries(tickers.toList())))
    }

    suspend fun watchlist() = service.watchlist(data.profile.id)

    suspend fun marketData(vararg tickers: String) = service.marketData(data.profile.id, tickers.map(Symbol::of))
}

private fun List<WatchlistItem>.symbols() = map { it.identity.symbol.value }

@JvmName("marketDataSymbols")
private fun List<WatchlistMarketData>.symbols() = map { it.symbol.value }

class WatchlistServiceTest : FunSpec({

    test("empty watchlist returns no rows without reading the directory or the cache") {
        val f = WatchlistFixture()

        f.watchlist().shouldBeEmpty()
        f.data.cache.getAllCalls shouldBe 0
    }

    test("unknown profile is not found") {
        shouldThrow<ResourceNotFoundException> { WatchlistFixture().service.watchlist(ProfileId.random()) }
    }

    test("watchlist items carry the directory exchange and market and the stored name, sector and tags in watchlist order") {
        val f = WatchlistFixture()
        f.data.profiles.save(
            f.data.profile.copy(
                watchlist = listOf(
                    watchlistEntry("SAP.DE", "Software", listOf("eu", "core"), name = "SAP SE"),
                    watchlistEntry("AAPL", name = "Apple Inc"),
                ),
            ),
        )
        f.data.listed(listing("AAPL", name = "APPLE INC"), listing("SAP.DE", mic = "XETR", currency = "EUR", name = "SAP SE"))

        f.watchlist() shouldContainExactly listOf(
            WatchlistItem(CompanyIdentity(Symbol.of("SAP.DE"), "XETRA", "SAP SE", Market.EU), "Software", listOf("eu", "core")),
            WatchlistItem(CompanyIdentity(Symbol.of("AAPL"), "NASDAQ", "Apple Inc", Market.US), null, emptyList()),
        )
    }

    test("watchlist item of a not covered exchange has the stored name and the static exchange table identity") {
        val f = WatchlistFixture()
        f.data.directory.notCovered(ExchangeCode("AS"))
        f.data.profiles.save(
            f.data.profile.copy(watchlist = listOf(watchlistEntry("AD.AS", name = "Koninklijke Ahold Delhaize NV"))),
        )

        f.watchlist() shouldContainExactly listOf(
            WatchlistItem(
                CompanyIdentity(Symbol.of("AD.AS"), "Euronext Amsterdam", "Koninklijke Ahold Delhaize NV", Market.EU),
                null,
                emptyList(),
            ),
        )
        f.data.source.calls.shouldBeEmpty()
    }

    test("watchlist reads no cached company data and makes no source requests") {
        val f = WatchlistFixture("STALE", "MISSING")
        f.data.listed(listing("STALE"), listing("MISSING"))
        f.data.cachedQuote("STALE", 5.0, age = Duration.ofHours(6))
        f.data.cached(profileKey("STALE"), sampleProfile(sector = "Cached"), age = Duration.ofDays(1))
        f.data.quoteFromSource("STALE", 6.0)
        f.data.quoteFromSource("MISSING", 7.0)
        f.data.profileFromSource("MISSING", sampleProfile(sector = "Energy"))

        val items = f.watchlist()

        items.symbols() shouldContainExactly listOf("STALE", "MISSING")
        items.map { it.sector } shouldContainExactly listOf(null, null)
        f.data.cache.getCalls shouldBe 0
        f.data.cache.getAllCalls shouldBe 0
        f.data.source.calls.shouldBeEmpty()
        f.data.countingCounters.tryAcquireCalls shouldBe 0
    }

    test("symbol no longer in the directory is returned with the ticker as name and the static exchange table, ignoring a cached profile") {
        val f = WatchlistFixture()
        f.data.profiles.save(f.data.profile.copy(watchlist = listOf(watchlistEntry("GONE.DE", "Industrials"))))
        f.data.cached(profileKey("GONE.DE"), sampleProfile(name = "Gone AG"), Duration.ofDays(2))

        val item = f.watchlist().single()

        item.identity shouldBe CompanyIdentity(Symbol.of("GONE.DE"), "XETRA", "GONE", Market.EU)
        item.sector shouldBe "Industrials"
    }

    test("market data follows the watchlist order with the status of each quote") {
        val f = WatchlistFixture("SAP.DE", "AAPL")
        f.data.listed(listing("AAPL"), listing("SAP.DE", mic = "XETR", currency = "EUR"))
        f.data.cachedQuote("AAPL", 190.0, age = Duration.ofMinutes(5), previousPrice = 180.0)
        f.data.quoteFromSource("SAP.DE", 200.0, "EUR")

        val rows = f.marketData("AAPL", "SAP.DE")

        rows.symbols() shouldContainExactly listOf("SAP.DE", "AAPL")
        rows.map { it.quote.status } shouldContainExactly listOf(DataStatus.FRESH, DataStatus.FRESH)
        rows[0].quote.data?.quote?.price shouldBe 200.0
        rows[1].quote.data?.trend shouldBe PriceTrend.UP
        f.data.quoteCalls() shouldContainExactly listOf("quote:SAP.DE")
    }

    test("market data requests no company profiles") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("AAPL"))
        f.data.quoteFromSource("AAPL", 1.0)
        f.data.profileFromSource("AAPL")

        f.marketData("AAPL")

        f.data.profileCalls().shouldBeEmpty()
    }

    test("profile default source is used for the market data") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("AAPL"))
        f.data.profiles.save(f.data.profile.copy(defaultSource = SEC_EDGAR, watchlist = watchlistEntries("AAPL")))

        val quote = f.marketData("AAPL").single().quote

        quote.source shouldBe SEC_EDGAR
        quote.status shouldBe DataStatus.NOT_SUPPORTED
        f.data.source.calls.shouldBeEmpty()
    }

    test("company without cached quote and without budget has LIMIT_REACHED without quote data") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("AAPL"))
        f.data.exhaustBudget()

        val quote = f.marketData("AAPL").single().quote

        quote.status shouldBe DataStatus.LIMIT_REACHED
        quote.data.shouldBeNull()
    }

    test("symbol no longer in the directory keeps its cached quote without a request") {
        val f = WatchlistFixture("GONE.DE")
        val cached = f.data.cachedQuote("GONE.DE", 12.0, age = Duration.ofHours(3))

        val quote = f.marketData("GONE.DE").single().quote

        quote.status shouldBe DataStatus.SOURCE_ERROR
        quote.data shouldBe cached.data
        f.data.quoteCalls().shouldBeEmpty()
    }

    test("market data covers only requested watchlist symbols in watchlist order, ignoring duplicates") {
        val f = WatchlistFixture("A", "B", "C", "D")
        listOf("A", "B", "C", "D", "X").forEach { f.data.listed(listing(it)); f.data.quoteFromSource(it, 1.0) }

        val rows = f.marketData("D", "X", "B", "D", "B")

        rows.symbols() shouldContainExactly listOf("B", "D")
        f.data.quoteCalls() shouldContainExactlyInAnyOrder listOf("quote:B", "quote:D")
    }

    test("market data of symbols outside the watchlist returns no rows and makes no calls") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("MSFT"))
        f.data.quoteFromSource("MSFT", 1.0)

        f.marketData("MSFT").shouldBeEmpty()
        f.data.source.calls.shouldBeEmpty()
        f.data.cache.getAllCalls shouldBe 0
    }

    test("market data accepts up to $MAX_MARKET_DATA_SYMBOLS symbols") {
        val f = WatchlistFixture("AAPL")

        f.marketData(*Array(MAX_MARKET_DATA_SYMBOLS) { "AAPL" }).symbols() shouldContainExactly listOf("AAPL")
    }

    test("market data rejects no symbols and more than $MAX_MARKET_DATA_SYMBOLS symbols") {
        val f = WatchlistFixture("AAPL")

        shouldThrow<InvalidInputException> { f.marketData() }
        shouldThrow<InvalidInputException> { f.marketData(*Array(MAX_MARKET_DATA_SYMBOLS + 1) { "C%03d".format(it) }) }
    }

    test("market data for an unknown profile is not found") {
        shouldThrow<ResourceNotFoundException> {
            WatchlistFixture().service.marketData(ProfileId.random(), listOf(Symbol.of("AAPL")))
        }
    }
})
