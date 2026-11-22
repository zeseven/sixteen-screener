package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.Tag
import com.sixteenscreener.domain.model.WatchlistEntry
import com.sixteenscreener.fakes.InMemoryProfileRepository
import com.sixteenscreener.fakes.MutableClock
import com.sixteenscreener.watchlistEntry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Duration

private val AAPL = Symbol.of("AAPL")

private class TagFixture(vararg entries: WatchlistEntry) {
    val clock = MutableClock()
    val profiles = InMemoryProfileRepository()
    val profile = Profile(ProfileId.random(), ProfileName.of("Alice"), entries.toList(), clock.now(), clock.now())
        .also(profiles::save)
    val service = TagService(profiles, clock)

    suspend fun set(symbol: Symbol, vararg tags: Tag) = service.setCompanyTags(profile.id, symbol, tags.toList())

    suspend fun stored() = profiles.findById(profile.id)!!
}

class TagServiceTest : FunSpec({

    context("setCompanyTags") {
        test("replaces all tags of the company, returns them in request order and sets updatedAt") {
            val f = TagFixture(watchlistEntry("AAPL", tags = listOf(Tag("star", "old"))), watchlistEntry("MSFT", tags = listOf(Tag("gem", "m"))))
            f.clock.advanceBy(Duration.ofMinutes(1))

            val tags = f.set(AAPL, Tag("star", "core"), Tag("bank", "Dividend"))

            tags shouldContainExactly listOf(Tag("star", "core"), Tag("bank", "Dividend"))
            f.stored().watchlist shouldContainExactly listOf(
                watchlistEntry("AAPL", tags = listOf(Tag("star", "core"), Tag("bank", "Dividend"))),
                watchlistEntry("MSFT", tags = listOf(Tag("gem", "m"))),
            )
            f.stored().updatedAt shouldBe f.clock.now()
        }

        test("an empty list removes all tags") {
            val f = TagFixture(watchlistEntry("AAPL", tags = listOf(Tag("star", "old"))))

            f.set(AAPL).shouldBeEmpty()

            f.stored().watchlistEntry(AAPL)!!.tags.shouldBeEmpty()
        }

        test("accepts 20 tags") {
            val f = TagFixture(watchlistEntry("AAPL"))
            val tags = (1..Tag.MAX_TAGS_PER_COMPANY).map { Tag("star", "t$it") }

            f.set(AAPL, *tags.toTypedArray()) shouldContainExactly tags
        }

        test("rejects 21 tags and keeps the stored ones") {
            val f = TagFixture(watchlistEntry("AAPL", tags = listOf(Tag("star", "old"))))

            shouldThrow<InvalidInputException> {
                f.set(AAPL, *(1..Tag.MAX_TAGS_PER_COMPANY + 1).map { Tag("star", "t$it") }.toTypedArray())
            }
            f.stored().watchlistEntry(AAPL)!!.tags shouldContainExactly listOf(Tag("star", "old"))
        }

        test("rejects two tags that are the same tag ignoring case") {
            val f = TagFixture(watchlistEntry("AAPL"))

            shouldThrow<InvalidInputException> { f.set(AAPL, Tag("star", "Core"), Tag("star", "core")) }
            f.stored().watchlistEntry(AAPL)!!.tags.shouldBeEmpty()
        }

        test("company outside the watchlist is rejected with 400 semantics") {
            val f = TagFixture(watchlistEntry("MSFT"))

            shouldThrow<InvalidInputException> { f.set(AAPL, Tag("star", "core")) }
            f.stored() shouldBe f.profile
        }

        test("unknown profile is not found") {
            val f = TagFixture()

            shouldThrow<ResourceNotFoundException> { f.service.setCompanyTags(ProfileId.random(), AAPL, emptyList()) }
        }

        test("unchanged tags are not written") {
            val f = TagFixture(watchlistEntry("AAPL", tags = listOf(Tag("star", "core"))))
            f.clock.advanceBy(Duration.ofMinutes(1))

            f.set(AAPL, Tag("star", "core")) shouldContainExactly listOf(Tag("star", "core"))
            f.stored() shouldBe f.profile
        }

        test("tags are removed with the watchlist entry and absent when the company is added again") {
            val f = TagFixture(watchlistEntry("AAPL"))
            f.set(AAPL, Tag("star", "core"))

            f.profiles.removeFromWatchlist(f.profile.id, AAPL, f.clock.now())
            f.profiles.addToWatchlist(f.profile.id, watchlistEntry("AAPL"), f.clock.now())

            f.stored().watchlistEntry(AAPL)!!.tags.shouldBeEmpty()
            f.service.profileTags(f.profile.id).shouldBeEmpty()
        }
    }

    context("profileTags") {
        test("a profile without tags has none") {
            val f = TagFixture(watchlistEntry("AAPL"))

            f.service.profileTags(f.profile.id).shouldBeEmpty()
        }

        test("returns the distinct tags of all companies sorted by text ignoring case, then by icon") {
            val f = TagFixture(
                watchlistEntry("AAPL", tags = listOf(Tag("star", "core"), Tag("bank", "Dividend"))),
                watchlistEntry("MSFT", tags = listOf(Tag("star", "Core"), Tag("gem", "core"), Tag("bank", "dividend"))),
                watchlistEntry("IBM", tags = listOf(Tag("rocket", "Alpha"))),
                watchlistEntry("SAP.DE"),
            )

            f.service.profileTags(f.profile.id) shouldContainExactly listOf(
                Tag("rocket", "Alpha"),
                Tag("gem", "core"),
                Tag("star", "core"),
                Tag("bank", "Dividend"),
            )
        }

        test("of the same tags the first found in watchlist order is returned") {
            val f = TagFixture(
                watchlistEntry("MSFT", tags = listOf(Tag("star", "CORE"))),
                watchlistEntry("AAPL", tags = listOf(Tag("star", "core"))),
            )

            f.service.profileTags(f.profile.id) shouldContainExactly listOf(Tag("star", "CORE"))
        }

        test("unknown profile is not found") {
            shouldThrow<ResourceNotFoundException> { TagFixture().service.profileTags(ProfileId.random()) }
        }
    }
})
