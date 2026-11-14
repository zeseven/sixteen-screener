package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.PriceTrend
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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
}

class WatchlistServiceTest : FunSpec({

    test("empty watchlist returns no rows without reading the cache") {
        val f = WatchlistFixture()

        f.watchlist().shouldBeEmpty()
        f.data.cache.getAllCalls shouldBe 0
    }

    test("unknown profile is not found") {
        shouldThrow<ResourceNotFoundException> { WatchlistFixture().service.watchlist(ProfileId.random()) }
    }

    test("rows follow the watchlist order with identity, sector and quote") {
        val f = WatchlistFixture("SAP.DE", "AAPL")
        f.data.listed(listing("AAPL", name = "APPLE INC"), listing("SAP.DE", mic = "XETR", currency = "EUR", name = "SAP SE"))
        f.data.cachedQuote("AAPL", 190.0, age = Duration.ofMinutes(5), previousPrice = 180.0)
        f.data.cached(profileKey("AAPL"), sampleProfile(), age = Duration.ofDays(1))
        f.data.quoteFromSource("SAP.DE", 200.0, "EUR")
        f.data.profileFromSource("SAP.DE", sampleProfile(name = "SAP SE", sector = "Software"))

        val rows = f.watchlist()

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

    test("company without cached quote and without budget has no quote data") {
        val f = WatchlistFixture("AAPL")
        f.data.listed(listing("AAPL"))
        f.data.exhaustBudget()

        val row = f.watchlist().single()

        row.quote?.status shouldBe DataStatus.LIMIT_REACHED
        row.quote?.data.shouldBeNull()
        row.sector.shouldBeNull()
    }
})
