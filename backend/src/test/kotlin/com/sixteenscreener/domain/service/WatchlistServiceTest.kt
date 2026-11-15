package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.PriceTrend
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.port.inbound.WatchlistUseCase.Companion.MAX_REFRESH_SYMBOLS
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

private class WatchlistFixture(vararg tickers: String) {
    val data = CompanyDataFixture()
    val service = WatchlistService(data.profiles, data.catalog, data.directory, data.service, data.identities)

    init {
        data.profiles.save(data.profile.copy(watchlist = tickers.map(Symbol::of)))
    }

    suspend fun watchlist() = service.watchlist(data.profile.id)

    suspend fun refresh(vararg tickers: String) = service.refreshWatchlistRows(data.profile.id, tickers.map(Symbol::of))
}

private fun List<CompanySummary>.symbols() = map { it.identity.symbol.value }

class WatchlistServiceTest : FunSpec({

    test("empty watchlist returns no rows without reading the cache") {
        val f = WatchlistFixture()

        f.watchlist().shouldBeEmpty()
        f.data.cache.getAllCalls shouldBe 0
    }

    test("unknown profile is not found") {
        shouldThrow<ResourceNotFoundException> { WatchlistFixture().service.watchlist(ProfileId.random()) }
    }

    test("refreshed rows follow the watchlist order with identity, sector and quote") {
        val f = WatchlistFixture("SAP.DE", "AAPL")
        f.data.listed(listing("AAPL", name = "APPLE INC"), listing("SAP.DE", mic = "XETR", currency = "EUR", name = "SAP SE"))
        f.data.cachedQuote("AAPL", 190.0, age = Duration.ofMinutes(5), previousPrice = 180.0)
        f.data.cached(profileKey("AAPL"), sampleProfile(), age = Duration.ofDays(1))
        f.data.quoteFromSource("SAP.DE", 200.0, "EUR")
        f.data.profileFromSource("SAP.DE", sampleProfile(name = "SAP SE", sector = "Software"))

        val rows = f.refresh("AAPL", "SAP.DE")

        rows.map { it.identity } shouldContainExactly listOf(
            CompanyIdentity(Symbol.of("SAP.DE"), "XETRA", "SAP SE", Market.EU),
            CompanyIdentity(Symbol.of("AAPL"), "NASDAQ", "APPLE INC", Market.US),
        )
        rows.map { it.sector } shouldContainExactly listOf("Software", "Technology")
        rows[1].quote?.data?.trend shouldBe PriceTrend.UP
        rows[0].quote?.data?.quote?.price shouldBe 200.0
    }

    test("profile default source is used for the watchlist data") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("AAPL"))
        f.data.profiles.save(f.data.profile.copy(defaultSource = SEC_EDGAR, watchlist = listOf(Symbol.of("AAPL"))))

        val row = f.watchlist().single()

        row.quote?.source shouldBe SEC_EDGAR
        row.quote?.status shouldBe DataStatus.NOT_SUPPORTED
        f.data.source.calls.shouldBeEmpty()
    }

    test("symbol no longer in the directory is returned with best-effort data") {
        val f = WatchlistFixture("GONE.DE")
        val cached = f.data.cachedQuote("GONE.DE", 12.0, age = Duration.ofHours(3))
        f.data.cached(profileKey("GONE.DE"), sampleProfile(name = "Gone AG", sector = "Industrials"), Duration.ofDays(2))

        val row = f.watchlist().single()

        row.identity shouldBe CompanyIdentity(Symbol.of("GONE.DE"), "DE", "Gone AG", market = null)
        row.sector shouldBe "Industrials"
        row.quote.shouldNotBeNull().data shouldBe cached.data
        f.data.quoteCalls().shouldBeEmpty()
    }

    test("refreshed company without cached quote and without budget has no quote data") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("AAPL"))
        f.data.exhaustBudget()

        val row = f.refresh("AAPL").single()

        row.quote?.status shouldBe DataStatus.LIMIT_REACHED
        row.quote?.data.shouldBeNull()
        row.sector.shouldBeNull()
    }

    test("watchlist rows carry cached quotes as is without quote requests") {
        val f = WatchlistFixture("STALE", "MISSING")
        f.data.listed(listing("STALE"), listing("MISSING"))
        val stale = f.data.cachedQuote("STALE", 5.0, age = Duration.ofHours(6))
        f.data.quoteFromSource("STALE", 6.0)
        f.data.quoteFromSource("MISSING", 7.0)
        f.data.profileFromSource("MISSING", sampleProfile(sector = "Energy"))

        val rows = f.watchlist()

        rows.symbols() shouldContainExactly listOf("STALE", "MISSING")
        rows[0].quote?.status shouldBe DataStatus.CACHED
        rows[0].quote?.data shouldBe stale.data
        rows[1].quote.shouldBeNull()
        rows[1].sector shouldBe "Energy"
        f.data.quoteCalls().shouldBeEmpty()
    }

    test("refresh returns only requested watchlist symbols in watchlist order, ignoring duplicates") {
        val f = WatchlistFixture("A", "B", "C", "D")
        listOf("A", "B", "C", "D", "X").forEach { f.data.listed(listing(it)); f.data.quoteFromSource(it, 1.0) }

        val rows = f.refresh("D", "X", "B", "D", "B")

        rows.symbols() shouldContainExactly listOf("B", "D")
        f.data.quoteCalls() shouldContainExactlyInAnyOrder listOf("quote:B", "quote:D")
    }

    test("refresh of symbols outside the watchlist returns no rows and makes no calls") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("MSFT"))
        f.data.quoteFromSource("MSFT", 1.0)

        f.refresh("MSFT").shouldBeEmpty()
        f.data.source.calls.shouldBeEmpty()
        f.data.cache.getAllCalls shouldBe 0
    }

    test("refresh accepts up to $MAX_REFRESH_SYMBOLS symbols") {
        val f = WatchlistFixture("AAPL")

        f.refresh(*Array(MAX_REFRESH_SYMBOLS) { "AAPL" }).symbols() shouldContainExactly listOf("AAPL")
    }

    test("refresh rejects no symbols and more than $MAX_REFRESH_SYMBOLS symbols") {
        val f = WatchlistFixture("AAPL")

        shouldThrow<InvalidInputException> { f.refresh() }
        shouldThrow<InvalidInputException> { f.refresh(*Array(MAX_REFRESH_SYMBOLS + 1) { "C%03d".format(it) }) }
    }

    test("refresh for an unknown profile is not found") {
        shouldThrow<ResourceNotFoundException> {
            WatchlistFixture().service.refreshWatchlistRows(ProfileId.random(), listOf(Symbol.of("AAPL")))
        }
    }
})
