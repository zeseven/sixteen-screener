package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private class ProfileFixture {
    val data = CompanyDataFixture().apply { listed(listing("AAPL"), listing("MSFT"), listing("SAP.DE", mic = "XETR")) }
    val service = ProfileService(data.profiles, data.directory, data.catalog, data.clock)

    suspend fun watchlistOf(id: ProfileId) = data.profiles.findById(id)!!.watchlist
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

            f.service.addToWatchlist(f.data.profile.id, Symbol.of("MSFT"))
            f.service.addToWatchlist(f.data.profile.id, Symbol.of("SAP.DE"))

            f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(Symbol.of("MSFT"), Symbol.of("SAP.DE"))
        }

        test("duplicate add is idempotent") {
            val f = ProfileFixture()

            repeat(2) { f.service.addToWatchlist(f.data.profile.id, Symbol.of("AAPL")) }

            f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(Symbol.of("AAPL"))
        }

        test("duplicate add succeeds even when the symbol is no longer in the directory") {
            val f = ProfileFixture()
            f.data.profiles.save(f.data.profile.copy(watchlist = listOf(Symbol.of("GONE"))))

            f.service.addToWatchlist(f.data.profile.id, Symbol.of("GONE"))

            f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(Symbol.of("GONE"))
        }

        test("unknown symbol is not found and not added") {
            val f = ProfileFixture()

            shouldThrow<ResourceNotFoundException> { f.service.addToWatchlist(f.data.profile.id, Symbol.of("NOPE")) }
            f.watchlistOf(f.data.profile.id).shouldBeEmpty()
        }

        test("add to an unknown profile is not found") {
            val f = ProfileFixture()

            shouldThrow<ResourceNotFoundException> { f.service.addToWatchlist(ProfileId.random(), Symbol.of("AAPL")) }
        }

        test("add to a full watchlist is rejected") {
            val f = ProfileFixture()
            val full = (1..Profile.MAX_WATCHLIST_SIZE).map { Symbol.of("S$it") }
            f.data.profiles.save(f.data.profile.copy(watchlist = full))

            shouldThrow<InvalidInputException> { f.service.addToWatchlist(f.data.profile.id, Symbol.of("AAPL")) }
            f.watchlistOf(f.data.profile.id) shouldBe full
        }

        test("remove deletes the symbol") {
            val f = ProfileFixture()
            f.service.addToWatchlist(f.data.profile.id, Symbol.of("AAPL"))
            f.service.addToWatchlist(f.data.profile.id, Symbol.of("MSFT"))

            f.service.removeFromWatchlist(f.data.profile.id, Symbol.of("AAPL"))

            f.watchlistOf(f.data.profile.id) shouldContainExactly listOf(Symbol.of("MSFT"))
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
