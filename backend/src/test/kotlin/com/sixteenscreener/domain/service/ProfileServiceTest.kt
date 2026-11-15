package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistItem
import com.sixteenscreener.domain.service.CompanyDataFixture.Companion.profileKey
import com.sixteenscreener.watchlistEntries
import com.sixteenscreener.watchlistEntry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration

private class ProfileFixture {
    val data = CompanyDataFixture().apply {
        listed(listing("AAPL", name = "APPLE INC"), listing("MSFT"), listing("SAP.DE", mic = "XETR", name = "SAP SE"))
    }
    val service = ProfileService(data.profiles, data.directory, data.catalog, data.service, data.identities, data.clock)

    suspend fun watchlistOf(id: ProfileId) = data.profiles.findById(id)!!.watchlist

    suspend fun add(ticker: String) = service.addToWatchlist(data.profile.id, Symbol.of(ticker))
}

class ProfileServiceTest : FunSpec({

    context("get or create") {
        test("new name creates a profile with the configured default source") {
            val f = ProfileFixture()

            val registration = f.service.getOrCreate("  Carol ")

            registration.created shouldBe true
            registration.profile.name.value shouldBe "Carol"
            registration.profile.defaultSource shouldBe FINNHUB
            registration.profile.watchlist.shouldBeEmpty()
            f.data.profiles.findById(registration.profile.id)?.createdAt shouldBe f.data.now
        }

        test("existing name in another case returns the existing profile") {
            val f = ProfileFixture()
            val first = f.service.getOrCreate("Carol")

            val second = f.service.getOrCreate(" CAROL")

            second.created shouldBe false
            second.profile shouldBe first.profile
        }

        test("different names create different profiles") {
            val f = ProfileFixture()

            f.service.getOrCreate("Carol").profile.id shouldNotBe f.service.getOrCreate("Dave").profile.id
        }

        test("blank name is rejected") {
            val f = ProfileFixture()

            shouldThrow<InvalidInputException> { f.service.getOrCreate("   ") }
        }

        test("name of 40 characters is accepted and 41 rejected") {
            val f = ProfileFixture()

            f.service.getOrCreate("a".repeat(40)).created shouldBe true
            shouldThrow<InvalidInputException> { f.service.getOrCreate("b".repeat(41)) }
        }
    }

    context("get by ID") {
        test("existing profile is returned with its default source") {
            val f = ProfileFixture()

            f.service.profile(f.data.profile.id).name shouldBe f.data.profile.name
        }

        test("profile default source is returned when set") {
            val f = ProfileFixture()
            f.data.profiles.save(f.data.profile.copy(defaultSource = SEC_EDGAR))

            f.service.profile(f.data.profile.id).defaultSource shouldBe SEC_EDGAR
        }

        test("unknown profile is not found") {
            shouldThrow<ResourceNotFoundException> { ProfileFixture().service.profile(ProfileId.random()) }
        }
    }

    context("watchlist changes") {
        test("add appends known symbols in order") {
            val f = ProfileFixture()

            f.add("MSFT")
            f.add("SAP.DE")

            f.watchlistOf(f.data.profile.id).map { it.symbol } shouldContainExactly
                listOf(Symbol.of("MSFT"), Symbol.of("SAP.DE"))
        }

        test("add returns the stored item with directory identity, sector and empty tags") {
            val f = ProfileFixture()
            f.data.profileFromSource("SAP.DE", sampleProfile(name = "SAP from source", sector = "Software"))

            val item = f.add("SAP.DE")

            item shouldBe WatchlistItem(
                identity = CompanyIdentity(Symbol.of("SAP.DE"), "XETRA", "SAP SE", Market.EU),
                sector = "Software",
                tags = emptyList(),
            )
            f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(watchlistEntry("SAP.DE", sector = "Software"))
        }

        test("add takes the sector of a cached profile that is not expired, without a source request") {
            val f = ProfileFixture()
            f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Cached"), age = Duration.ofDays(60))
            f.data.profileFromSource("AAPL", sampleProfile(sector = "Fetched"))

            f.add("AAPL").sector shouldBe "Cached"
            f.data.source.calls.shouldBeEmpty()
        }

        test("add requests a missing company profile and caches it") {
            val f = ProfileFixture()
            f.data.profileFromSource("AAPL", sampleProfile(sector = "Fetched"))

            f.add("AAPL").sector shouldBe "Fetched"
            f.data.profileCalls() shouldContainExactly listOf("profile:AAPL")
            f.data.cache.get(profileKey("AAPL"))?.data?.sector shouldBe "Fetched"
        }

        test("add requests an expired company profile again") {
            val f = ProfileFixture()
            f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(400))
            f.data.profileFromSource("AAPL", sampleProfile(sector = "New"))

            f.add("AAPL").sector shouldBe "New"
            f.data.profileCalls() shouldContainExactly listOf("profile:AAPL")
        }

        context("add takes the sector of an expired cached profile when the profile request fails") {
            data class Case(val name: String, val arrange: suspend CompanyDataFixture.() -> Unit)

            withData(
                nameFn = { it.name },
                Case("source error") {
                    source.profiles[Symbol.of("AAPL")] = SourceResult.SourceError(SourceCallOutcome.httpError(500, now))
                },
                Case("limit reached by the source") {
                    source.profiles[Symbol.of("AAPL")] = SourceResult.LimitReached(SourceCallOutcome.httpError(429, now))
                },
                Case("limit reached by the budget") { profileFromSource("AAPL"); exhaustBudget() },
            ) { case ->
                val f = ProfileFixture()
                f.data.cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(400))
                case.arrange(f.data)

                val item = f.add("AAPL")

                item.sector shouldBe "Old"
                f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(watchlistEntry("AAPL", sector = "Old"))
            }
        }

        context("add stores a null sector when no company profile is known") {
            data class Case(val name: String, val arrange: suspend CompanyDataFixture.() -> Unit)

            withData(
                nameFn = { it.name },
                Case("source error") {
                    source.profiles[Symbol.of("AAPL")] = SourceResult.SourceError(SourceCallOutcome.httpError(500, now))
                },
                Case("limit reached by the source") {
                    source.profiles[Symbol.of("AAPL")] = SourceResult.LimitReached(SourceCallOutcome.httpError(429, now))
                },
                Case("limit reached by the budget") { profileFromSource("AAPL"); exhaustBudget() },
                Case("no data") { source.profiles[Symbol.of("AAPL")] = SourceResult.NoData(success()) },
                Case("no data with an expired cached profile") {
                    source.profiles[Symbol.of("AAPL")] = SourceResult.NoData(success())
                    cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(400))
                },
                Case("not supported") {
                    source.profiles[Symbol.of("AAPL")] = SourceResult.NotSupported(SourceCallOutcome.httpError(403, now))
                },
                Case("not supported with an expired cached profile") {
                    source.profiles[Symbol.of("AAPL")] = SourceResult.NotSupported(SourceCallOutcome.httpError(403, now))
                    cached(profileKey("AAPL"), sampleProfile(sector = "Old"), age = Duration.ofDays(400))
                },
            ) { case ->
                val f = ProfileFixture()
                case.arrange(f.data)

                val item = f.add("AAPL")

                item.sector.shouldBeNull()
                item.identity.name shouldBe "APPLE INC"
                f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(watchlistEntry("AAPL"))
            }
        }

        test("add stores a null sector when the default source doesn't provide company profiles") {
            val f = ProfileFixture()
            f.data.profiles.save(f.data.profile.copy(defaultSource = SEC_EDGAR))

            f.add("AAPL").sector.shouldBeNull()
            f.data.source.calls.shouldBeEmpty()
        }

        test("add of a symbol already in the watchlist returns it unchanged without source requests") {
            val f = ProfileFixture()
            val entry = watchlistEntry("AAPL", sector = "Stored", tags = listOf("core"))
            f.data.profiles.save(f.data.profile.copy(watchlist = listOf(entry)))
            f.data.profileFromSource("AAPL", sampleProfile(sector = "Fetched"))

            val item = f.add("AAPL")

            item shouldBe WatchlistItem(CompanyIdentity(Symbol.of("AAPL"), "NASDAQ", "APPLE INC", Market.US), "Stored", listOf("core"))
            f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(entry)
            f.data.source.calls.shouldBeEmpty()
            f.data.cache.getAllCalls shouldBe 0
        }

        test("duplicate add succeeds even when the symbol is no longer in the directory") {
            val f = ProfileFixture()
            f.data.profiles.save(f.data.profile.copy(watchlist = watchlistEntries("GONE")))

            val item = f.add("GONE")

            item.identity shouldBe CompanyIdentity(Symbol.of("GONE"), "US", "GONE", Market.US)
            f.watchlistOf(f.data.profile.id) shouldContainExactly watchlistEntries("GONE")
        }

        test("unknown symbol is not found, not added and causes no source request") {
            val f = ProfileFixture()
            f.data.profileFromSource("NOPE")

            shouldThrow<ResourceNotFoundException> { f.add("NOPE") }
            f.watchlistOf(f.data.profile.id).shouldBeEmpty()
            f.data.source.calls.shouldBeEmpty()
        }

        test("add to an unknown profile is not found") {
            val f = ProfileFixture()

            shouldThrow<ResourceNotFoundException> { f.service.addToWatchlist(ProfileId.random(), Symbol.of("AAPL")) }
        }

        test("add to a full watchlist is rejected without a source request") {
            val f = ProfileFixture()
            val full = watchlistEntries((1..Profile.MAX_WATCHLIST_SIZE).map { "S$it" })
            f.data.profiles.save(f.data.profile.copy(watchlist = full))
            f.data.profileFromSource("AAPL")

            shouldThrow<InvalidInputException> { f.add("AAPL") }
            f.watchlistOf(f.data.profile.id) shouldBe full
            f.data.source.calls.shouldBeEmpty()
        }

        test("remove deletes the entry with its tags and keeps the order of the others") {
            val f = ProfileFixture()
            val entries = listOf(watchlistEntry("A"), watchlistEntry("AAPL", "Technology", listOf("core")), watchlistEntry("B"))
            f.data.profiles.save(f.data.profile.copy(watchlist = entries))

            f.service.removeFromWatchlist(f.data.profile.id, Symbol.of("AAPL"))

            f.watchlistOf(f.data.profile.id) shouldContainExactly watchlistEntries("A", "B")
        }

        test("remove of a missing symbol is idempotent") {
            val f = ProfileFixture()

            f.service.removeFromWatchlist(f.data.profile.id, Symbol.of("NOPE"))

            f.watchlistOf(f.data.profile.id).shouldBeEmpty()
        }

        test("remove from an unknown profile is not found") {
            val f = ProfileFixture()

            shouldThrow<ResourceNotFoundException> {
                f.service.removeFromWatchlist(ProfileId.random(), Symbol.of("AAPL"))
            }
        }
    }
})
