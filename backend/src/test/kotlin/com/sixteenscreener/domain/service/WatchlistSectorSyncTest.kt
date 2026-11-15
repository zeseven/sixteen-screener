package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Duration

private val AAPL = Symbol.of("AAPL")

private class SectorFixture(defaultSource: SourceId? = null, vararg entries: WatchlistEntry) {
    val data = CompanyDataFixture(defaultSource = defaultSource).apply {
        listed(listing("AAPL"), listing("MSFT"))
        profiles.save(profile.copy(watchlist = entries.toList()))
    }

    suspend fun companyProfile(source: SourceId? = null, refresh: Boolean = false) =
        data.service.companyProfile(data.profile.id, AAPL, source, refresh)

    suspend fun watchlist() = data.profiles.findById(data.profile.id)!!.watchlist
}

class WatchlistSectorSyncTest : FunSpec({

    test("profile fetched from the default source updates the sector of the watchlist entry") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Old", tags = listOf("core")), watchlistEntry("MSFT", "Software"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(
            watchlistEntry("AAPL", sector = "Technology", tags = listOf("core")),
            watchlistEntry("MSFT", "Software"),
        )
        f.data.profiles.findById(f.data.profile.id)!!.updatedAt shouldBe f.data.now
    }

    test("refreshed profile of the default source updates the sector") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Old"))
        f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(1))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile(refresh = true)

        f.watchlist() shouldContainExactly listOf(watchlistEntry("AAPL", sector = "Technology"))
    }

    test("explicitly requested default source counts as the default source") {
        val f = SectorFixture(null, watchlistEntry("AAPL"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile(source = FINNHUB)

        f.watchlist() shouldContainExactly listOf(watchlistEntry("AAPL", sector = "Technology"))
    }

    test("profile fetched from a source other than the default source doesn't update the sector") {
        val f = SectorFixture(SEC_EDGAR, watchlistEntry("AAPL", sector = "Old"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile(source = FINNHUB)

        f.watchlist() shouldContainExactly listOf(watchlistEntry("AAPL", sector = "Old"))
        f.data.profiles.sectorUpdates shouldBe 0
    }

    test("profile of a company outside the watchlist doesn't change the watchlist") {
        val f = SectorFixture(null, watchlistEntry("MSFT", "Software"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(watchlistEntry("MSFT", "Software"))
        f.data.profiles.sectorUpdates shouldBe 0
    }

    test("unchanged sector is not written") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Technology"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))

        f.companyProfile()

        f.data.profiles.sectorUpdates shouldBe 0
        f.data.profiles.findById(f.data.profile.id)!!.updatedAt shouldBe f.data.profile.updatedAt
    }

    test("cached profile doesn't update the sector") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Old"))
        f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Technology"), age = Duration.ofMinutes(5))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(watchlistEntry("AAPL", sector = "Old"))
    }

    test("failed fetch doesn't update the sector, even with a cached profile") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Old"))
        f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Technology"), age = Duration.ofDays(1))
        f.data.source.profiles[AAPL] = SourceResult.SourceError(SourceCallOutcome.httpError(500, f.data.now))

        f.companyProfile(refresh = true)

        f.watchlist() shouldContainExactly listOf(watchlistEntry("AAPL", sector = "Old"))
        f.data.profiles.sectorUpdates shouldBe 0
    }

    test("fetch skipped because of the limit doesn't update the sector") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Old"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))
        f.data.exhaustBudget()

        f.companyProfile()

        f.data.profiles.sectorUpdates shouldBe 0
    }

    test("source without a sector clears the stored sector") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Old"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = null))

        f.companyProfile()

        f.watchlist() shouldContainExactly listOf(watchlistEntry("AAPL"))
    }

    test("failed sector update still returns the fetched profile") {
        val f = SectorFixture(null, watchlistEntry("AAPL", sector = "Old"))
        f.data.profileFromSource("AAPL", sampleProfile(sector = "Technology"))
        f.data.profiles.failSectorUpdates = true

        f.companyProfile().profile.data?.sector shouldBe "Technology"
        f.watchlist() shouldContainExactly listOf(watchlistEntry("AAPL", sector = "Old"))
    }
})
