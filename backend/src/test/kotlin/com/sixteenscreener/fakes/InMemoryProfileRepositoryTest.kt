package com.sixteenscreener.fakes

import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.WatchlistUpdate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

private val CREATED_AT = Instant.parse("2026-11-14T12:00:00Z")

private fun candidate(name: String) = Profile(
    id = ProfileId.random(),
    name = ProfileName.of(name),
    defaultSource = null,
    watchlist = emptyList(),
    createdAt = CREATED_AT,
    updatedAt = CREATED_AT,
)

class InMemoryProfileRepositoryTest : FunSpec({

    test("creates a profile for a new name") {
        val repository = InMemoryProfileRepository()
        val profile = candidate("Alice")

        val creation = repository.getOrCreate(profile)

        creation.created shouldBe true
        creation.profile shouldBe profile
        repository.findById(profile.id) shouldBe profile
    }

    test("returns the existing profile for the same name in another case and with spaces") {
        val repository = InMemoryProfileRepository()
        val original = repository.getOrCreate(candidate("Alice")).profile

        val creation = repository.getOrCreate(candidate("  aLICE "))

        creation.created shouldBe false
        creation.profile shouldBe original
    }

    test("concurrent creation with the same name yields one profile") {
        val repository = InMemoryProfileRepository()
        val creations = coroutineScope {
            (1..20).map { async { repository.getOrCreate(candidate("Bob")) } }.awaitAll()
        }
        creations.count { it.created } shouldBe 1
        creations.map { it.profile.id }.toSet().size shouldBe 1
    }

    test("watchlist add is idempotent and keeps insertion order") {
        val repository = InMemoryProfileRepository()
        val id = repository.getOrCreate(candidate("Carol")).profile.id
        val at = CREATED_AT.plusSeconds(60)

        repository.addToWatchlist(id, Symbol.of("MSFT"), at)
        repository.addToWatchlist(id, Symbol.of("AAPL"), at)
        val update = repository.addToWatchlist(id, Symbol.of("MSFT"), at)

        val profile = update.shouldBeInstanceOf<WatchlistUpdate.Updated>().profile
        profile.watchlist shouldContainExactly listOf(Symbol.of("MSFT"), Symbol.of("AAPL"))
        profile.updatedAt shouldBe at
    }

    test("watchlist add fails when full, except for a symbol already present") {
        val repository = InMemoryProfileRepository()
        val symbols = (1..Profile.MAX_WATCHLIST_SIZE).map { Symbol.of("S$it") }
        val profile = candidate("Dave").copy(watchlist = symbols)
        repository.save(profile)

        repository.addToWatchlist(profile.id, Symbol.of("NEW"), CREATED_AT) shouldBe WatchlistUpdate.WatchlistFull
        repository.addToWatchlist(profile.id, Symbol.of("S1"), CREATED_AT).shouldBeInstanceOf<WatchlistUpdate.Updated>()
    }

    test("watchlist remove is idempotent") {
        val repository = InMemoryProfileRepository()
        val id = repository.getOrCreate(candidate("Eve")).profile.id
        repository.addToWatchlist(id, Symbol.of("AAPL"), CREATED_AT)

        repository.removeFromWatchlist(id, Symbol.of("AAPL"), CREATED_AT)
        val update = repository.removeFromWatchlist(id, Symbol.of("AAPL"), CREATED_AT)

        update.shouldBeInstanceOf<WatchlistUpdate.Updated>().profile.watchlist shouldBe emptyList()
    }

    test("watchlist changes of an unknown profile report ProfileNotFound") {
        val repository = InMemoryProfileRepository()
        repository.addToWatchlist(ProfileId.random(), Symbol.of("AAPL"), CREATED_AT) shouldBe WatchlistUpdate.ProfileNotFound
        repository.removeFromWatchlist(ProfileId.random(), Symbol.of("AAPL"), CREATED_AT) shouldBe WatchlistUpdate.ProfileNotFound
    }
})
