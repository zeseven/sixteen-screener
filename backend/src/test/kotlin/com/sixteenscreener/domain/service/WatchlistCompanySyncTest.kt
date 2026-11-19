package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.RequestTrigger
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Duration

private val AAPL = Symbol.of("AAPL")

private fun apple(sector: String? = null, tags: List<String> = emptyList(), name: String = "Apple Inc") =
    watchlistEntry("AAPL", sector, tags, name)

private class CompanyFixture(defaultSource: SourceId? = null, vararg entries: WatchlistEntry) {
    val data = CompanyDataFixture(defaultSource = defaultSource).apply {
        listed(listing("AAPL"), listing("MSFT"))
        profiles.save(profile.copy(watchlist = entries.toList()))
    }

    suspend fun companyProfile(source: SourceId? = null, trigger: RequestTrigger = RequestTrigger.AUTO) =
        data.service.companyProfile(data.profile.id, AAPL, source, trigger)

    suspend fun watchlist() = data.profiles.findById(data.profile.id)!!.watchlist
}

class WatchlistCompanySyncTest : FunSpec({

    test("profile fetched from the default source updates the sector of the watchlist entry") {
        val f = CompanyFixture(null, apple(sector = "Old", tags = listOf("core")), watchlistEntry("MSFT", "Software"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(
            apple(sector = "Technology", tags = listOf("core")),
            watchlistEntry("MSFT", "Software"),
        )
        f.data.profiles.findById(f.data.profile.id)!!.updatedAt shouldBe f.data.now
    }

    test("profile fetched from the default source updates the name of the watchlist entry") {
        val f = CompanyFixture(null, apple(sector = "Technology", name = "AAPL"))
        f.data.profileFromSource("AAPL", sampleProfile(name = "Apple Inc", sector = "Technology"))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(apple(sector = "Technology", name = "Apple Inc"))
        f.data.profiles.companyUpdates shouldBe 1
    }

    test("changed name and sector are updated together in one write") {
        val f = CompanyFixture(null, apple(sector = "Old", name = "APPLE INC"))
        f.data.profileFromSource("AAPL", sampleProfile(name = "Apple Inc", sector = "Technology"))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(apple(sector = "Technology", name = "Apple Inc"))
        f.data.profiles.companyUpdates shouldBe 1
    }

    context("profile without a name keeps the stored name and still updates the sector") {
        withData(nameFn = { "name = '$it'" }, "", "   ") { blank ->
            val f = CompanyFixture(null, apple(sector = "Old", name = "Ahold Delhaize"))
            f.data.profileFromSource("AAPL", sampleProfile(name = blank, sector = "Technology"))

            f.companyProfile()

            f.watchlist() shouldContainExactly listOf(apple(sector = "Technology", name = "Ahold Delhaize"))
        }
    }

    test("profile with a null name and an equal sector is not written") {
        val f = CompanyFixture(null, apple(sector = "Technology", name = "Stored"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology").copy(name = null))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(apple(sector = "Technology", name = "Stored"))
        f.data.profiles.companyUpdates shouldBe 0
    }

    test("refreshed profile of the default source updates the sector") {
        val f = CompanyFixture(null, apple(sector = "Old"))
        f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(1))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile(trigger = RequestTrigger.MANUAL)

        f.watchlist() shouldContainExactly listOf(apple(sector = "Technology"))
    }

    test("explicitly requested default source counts as the default source") {
        val f = CompanyFixture(null, apple())
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile(source = FINNHUB)

        f.watchlist() shouldContainExactly listOf(apple(sector = "Technology"))
    }

    test("profile fetched from a source other than the default source doesn't update name or sector") {
        val f = CompanyFixture(SEC_EDGAR, apple(sector = "Old", name = "AAPL"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile(source = FINNHUB)

        f.watchlist() shouldContainExactly listOf(apple(sector = "Old", name = "AAPL"))
        f.data.profiles.companyUpdates shouldBe 0
    }

    test("profile of a company outside the watchlist doesn't change the watchlist") {
        val f = CompanyFixture(null, watchlistEntry("MSFT", "Software"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(watchlistEntry("MSFT", "Software"))
        f.data.profiles.companyUpdates shouldBe 0
    }

    test("unchanged name and sector are not written") {
        val f = CompanyFixture(null, apple(sector = "Technology"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile()

        f.data.profiles.companyUpdates shouldBe 0
        f.data.profiles.findById(f.data.profile.id)!!.updatedAt shouldBe f.data.profile.updatedAt
    }

    test("cached profile doesn't update name or sector") {
        val f = CompanyFixture(null, apple(sector = "Old", name = "AAPL"))
        f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Technology"), age = Duration.ofMinutes(5))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(apple(sector = "Old", name = "AAPL"))
    }

    test("failed fetch doesn't update name or sector, even with a cached profile") {
        val f = CompanyFixture(null, apple(sector = "Old", name = "AAPL"))
        f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Technology"), age = Duration.ofDays(1))
        f.data.source.profiles[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(500, f.data.now))

        f.companyProfile(trigger = RequestTrigger.MANUAL)

        f.watchlist() shouldContainExactly listOf(apple(sector = "Old", name = "AAPL"))
        f.data.profiles.companyUpdates shouldBe 0
    }

    test("fetch skipped because of the limit doesn't update name or sector") {
        val f = CompanyFixture(null, apple(sector = "Old", name = "AAPL"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))
        f.data.exhaustBudget()

        f.companyProfile()

        f.data.profiles.companyUpdates shouldBe 0
    }

    test("source without a sector clears the stored sector") {
        val f = CompanyFixture(null, apple(sector = "Old"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = null))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(apple())
    }

    test("failed update still returns the fetched profile") {
        val f = CompanyFixture(null, apple(sector = "Old"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))
        f.data.profiles.failCompanyUpdates = true

        f.companyProfile().profile.data?.sector shouldBe "Technology"
        f.watchlist() shouldContainExactly listOf(apple(sector = "Old"))
    }
})
