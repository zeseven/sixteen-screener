package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.IndicatorDefaults
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.WatchlistUpdate
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.funSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

private val CREATED_AT = Instant.parse("2026-11-14T12:00:00Z")

fun profileCandidate(name: String, defaultSource: SourceId? = null) = Profile(
    id = ProfileId.random(),
    name = ProfileName.of(name),
    defaultSource = defaultSource,
    watchlist = emptyList(),
    createdAt = CREATED_AT,
    updatedAt = CREATED_AT,
)

fun profileRepositoryContract(newRepository: suspend () -> ProfileRepository) = funSpec {

    test("creates a profile for a new name") {
        val repository = newRepository()
        val profile = profileCandidate("Alice", defaultSource = SourceId.of("finnhub"))

        val creation = repository.getOrCreate(profile)

        creation.created shouldBe true
        creation.profile shouldBe profile
        repository.findById(profile.id) shouldBe profile
    }

    test("returns the existing profile for the same name in another case and with surrounding spaces") {
        val repository = newRepository()
        val original = repository.getOrCreate(profileCandidate("Alice")).profile

        val creation = repository.getOrCreate(profileCandidate("  aLICE "))

        creation.created shouldBe false
        creation.profile shouldBe original
    }

    test("names in different Unicode normal forms are the same name") {
        val repository = newRepository()
        val original = repository.getOrCreate(profileCandidate("Renée")).profile

        val creation = repository.getOrCreate(profileCandidate("RENÉE"))

        creation.created shouldBe false
        creation.profile.id shouldBe original.id
    }

    context("names that are unusual as document IDs") {
        withData(
            nameFn = { "'$it'" },
            "a/b",
            "../..",
            "__name__",
            ".",
            "日本語の名前",
            "emoji 😀",
            "x".repeat(ProfileName.MAX_LENGTH),
        ) { name ->
            val repository = newRepository()
            val profile = profileCandidate(name)

            repository.getOrCreate(profile).created shouldBe true
            repository.getOrCreate(profileCandidate(name)).profile shouldBe profile
            repository.findById(profile.id) shouldBe profile
        }
    }

    test("concurrent creation with the same name yields one profile") {
        val repository = newRepository()

        val creations = coroutineScope {
            (1..8).map { async { repository.getOrCreate(profileCandidate("Bob")) } }.awaitAll()
        }

        creations.count { it.created } shouldBe 1
        creations.map { it.profile.id }.toSet() shouldHaveSize 1
    }

    test("unknown profile ID is not found") {
        newRepository().findById(ProfileId.random()).shouldBeNull()
    }

    test("watchlist add is idempotent, keeps insertion order and the first stored entry, and sets updatedAt") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Carol")).profile.id
        val at = CREATED_AT.plusSeconds(60)

        repository.addToWatchlist(id, watchlistEntry("MSFT", sector = "Technology", name = "MICROSOFT CORP"), at)
        repository.addToWatchlist(id, watchlistEntry("SAP.DE"), at)
        val update = repository.addToWatchlist(id, watchlistEntry("MSFT", sector = "Other", name = "Other"), at.plusSeconds(1))

        val profile = update.shouldBeInstanceOf<WatchlistUpdate.Updated>().profile
        profile.watchlist shouldContainExactly listOf(
            watchlistEntry("MSFT", sector = "Technology", name = "MICROSOFT CORP"),
            watchlistEntry("SAP.DE"),
        )
        profile.updatedAt shouldBe at.plusSeconds(1)
        repository.findById(id) shouldBe profile
    }

    test("watchlist entries keep name, sector and tags") {
        val repository = newRepository()
        val profile = profileCandidate("Grace").copy(
            watchlist = listOf(
                watchlistEntry("AAPL", sector = "Technology", tags = listOf("dividend", "core"), name = "APPLE INC"),
                watchlistEntry("AD.AS", name = "Koninklijke Ahold Delhaize NV"),
            ),
        )
        repository.getOrCreate(profile)

        repository.findById(profile.id)?.watchlist shouldContainExactly profile.watchlist
    }

    test("watchlist add fails when full, except for a symbol already present") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Dave")).profile.id
        (1..Profile.MAX_WATCHLIST_SIZE).forEach { repository.addToWatchlist(id, watchlistEntry("S$it"), CREATED_AT) }

        repository.addToWatchlist(id, watchlistEntry("NEW"), CREATED_AT) shouldBe WatchlistUpdate.WatchlistFull
        repository.addToWatchlist(id, watchlistEntry("S1"), CREATED_AT).shouldBeInstanceOf<WatchlistUpdate.Updated>()
            .profile.watchlist shouldHaveSize Profile.MAX_WATCHLIST_SIZE
    }

    test("concurrent adds never exceed the watchlist limit") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Frank")).profile.id
        (1..Profile.MAX_WATCHLIST_SIZE - 2).forEach { repository.addToWatchlist(id, watchlistEntry("S$it"), CREATED_AT) }

        val updates = coroutineScope {
            (1..5).map { async { repository.addToWatchlist(id, watchlistEntry("N$it"), CREATED_AT) } }.awaitAll()
        }

        updates.count { it is WatchlistUpdate.Updated } shouldBe 2
        repository.findById(id)?.watchlist?.size shouldBe Profile.MAX_WATCHLIST_SIZE
    }

    test("watchlist remove is idempotent, keeps the order of the other entries and drops the tags of the removed one") {
        val repository = newRepository()
        val profile = profileCandidate("Eve").copy(
            watchlist = listOf(
                watchlistEntry("AAPL", tags = listOf("a")),
                watchlistEntry("MSFT", sector = "Technology", tags = listOf("m")),
                watchlistEntry("SAP.DE"),
                watchlistEntry("IBM"),
            ),
        )
        val id = repository.getOrCreate(profile).profile.id

        repository.removeFromWatchlist(id, Symbol.of("SAP.DE"), CREATED_AT)
        repository.removeFromWatchlist(id, Symbol.of("AAPL"), CREATED_AT)
        val update = repository.removeFromWatchlist(id, Symbol.of("AAPL"), CREATED_AT)

        update.shouldBeInstanceOf<WatchlistUpdate.Updated>().profile.watchlist shouldBe
            listOf(watchlistEntry("MSFT", sector = "Technology", tags = listOf("m")), watchlistEntry("IBM"))
        repository.addToWatchlist(id, watchlistEntry("AAPL"), CREATED_AT)
        repository.findById(id)?.watchlistEntry(Symbol.of("AAPL")) shouldBe watchlistEntry("AAPL")
    }

    test("company update changes only the name and the sector of the given entry and sets updatedAt") {
        val repository = newRepository()
        val profile = profileCandidate("Heidi").copy(
            watchlist = listOf(
                watchlistEntry("AAPL", sector = "Old", tags = listOf("t"), name = "AAPL"),
                watchlistEntry("MSFT", "Software", name = "Microsoft Corp"),
            ),
        )
        val id = repository.getOrCreate(profile).profile.id
        val at = CREATED_AT.plusSeconds(30)

        val update = repository.updateWatchlistCompany(id, Symbol.of("AAPL"), "Apple Inc", "Technology", at)

        val updated = update.shouldBeInstanceOf<WatchlistUpdate.Updated>().profile
        updated.watchlist shouldContainExactly listOf(
            watchlistEntry("AAPL", sector = "Technology", tags = listOf("t"), name = "Apple Inc"),
            watchlistEntry("MSFT", "Software", name = "Microsoft Corp"),
        )
        updated.updatedAt shouldBe at
        repository.findById(id) shouldBe updated
    }

    test("company update with only a changed name or only a changed sector is written") {
        val repository = newRepository()
        val profile = profileCandidate("Mallory").copy(watchlist = listOf(watchlistEntry("AAPL", sector = "Technology")))
        val id = repository.getOrCreate(profile).profile.id

        repository.updateWatchlistCompany(id, Symbol.of("AAPL"), "Apple Inc", "Technology", CREATED_AT.plusSeconds(1))
        repository.findById(id)?.watchlist shouldContainExactly listOf(watchlistEntry("AAPL", "Technology", name = "Apple Inc"))

        repository.updateWatchlistCompany(id, Symbol.of("AAPL"), "Apple Inc", "Hardware", CREATED_AT.plusSeconds(2))
        repository.findById(id)?.watchlist shouldContainExactly listOf(watchlistEntry("AAPL", "Hardware", name = "Apple Inc"))
    }

    test("company update can clear the sector") {
        val repository = newRepository()
        val profile = profileCandidate("Ivan").copy(watchlist = listOf(watchlistEntry("AAPL", sector = "Old")))
        val id = repository.getOrCreate(profile).profile.id

        repository.updateWatchlistCompany(id, Symbol.of("AAPL"), "AAPL", null, CREATED_AT)

        repository.findById(id)?.watchlist shouldContainExactly listOf(watchlistEntry("AAPL"))
    }

    test("company update of a symbol outside the watchlist or with equal name and sector leaves the profile unchanged") {
        val repository = newRepository()
        val profile = profileCandidate("Judy").copy(
            watchlist = listOf(watchlistEntry("AAPL", sector = "Technology", name = "Apple Inc")),
        )
        val id = repository.getOrCreate(profile).profile.id
        val later = CREATED_AT.plusSeconds(60)

        repository.updateWatchlistCompany(id, Symbol.of("MSFT"), "Microsoft Corp", "Software", later)
            .shouldBeInstanceOf<WatchlistUpdate.Updated>().profile shouldBe profile
        repository.updateWatchlistCompany(id, Symbol.of("AAPL"), "Apple Inc", "Technology", later)
            .shouldBeInstanceOf<WatchlistUpdate.Updated>().profile shouldBe profile
        repository.findById(id) shouldBe profile
    }

    test("watchlist changes of an unknown profile report ProfileNotFound") {
        val repository = newRepository()
        repository.addToWatchlist(ProfileId.random(), watchlistEntry("AAPL"), CREATED_AT) shouldBe WatchlistUpdate.ProfileNotFound
        repository.updateWatchlistCompany(ProfileId.random(), Symbol.of("AAPL"), "Apple Inc", "Technology", CREATED_AT) shouldBe
            WatchlistUpdate.ProfileNotFound
        repository.removeFromWatchlist(ProfileId.random(), Symbol.of("AAPL"), CREATED_AT) shouldBe WatchlistUpdate.ProfileNotFound
    }

    test("a new profile has no indicator settings") {
        val repository = newRepository()
        val profile = repository.getOrCreate(profileCandidate("Ivy")).profile

        repository.findById(profile.id)?.indicatorSettings.shouldBeEmpty()
    }

    test("changed indicator settings are stored in order with updatedAt") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Jack")).profile.id
        val at = CREATED_AT.plusSeconds(60)
        val settings = IndicatorDefaults.all.map { it.setting }.reversed()

        val changed = repository.changeIndicatorSettings(id, at) { stored ->
            stored.shouldBeEmpty()
            settings
        }

        changed?.indicatorSettings shouldBe settings
        changed?.updatedAt shouldBe at
        repository.findById(id) shouldBe changed
    }

    test("indicator settings keep all their values") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Kim")).profile.id
        val setting = IndicatorSetting(
            ratioCode = RatioCode.LT_DEBT_TO_EQUITY,
            enabled = true,
            thresholds = listOf(0.0, 0.25, 3.0),
            levels = listOf(IndicatorLevel.WARNING, IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL, IndicatorLevel.BAD),
            belowZeroLevel = IndicatorLevel.GOOD,
        )

        repository.changeIndicatorSettings(id, CREATED_AT) { listOf(setting) }

        repository.findById(id)?.indicatorSettings shouldContainExactly listOf(setting)
    }

    test("unchanged indicator settings are not written") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Liam")).profile.id
        val settings = IndicatorDefaults.all.map { it.setting }
        repository.changeIndicatorSettings(id, CREATED_AT.plusSeconds(60)) { settings }

        val unchanged = repository.changeIndicatorSettings(id, CREATED_AT.plusSeconds(120)) { stored -> stored }

        unchanged?.updatedAt shouldBe CREATED_AT.plusSeconds(60)
        repository.findById(id)?.updatedAt shouldBe CREATED_AT.plusSeconds(60)
    }

    test("indicator settings of an unknown profile are not changed") {
        newRepository().changeIndicatorSettings(ProfileId.random(), CREATED_AT) { it }.shouldBeNull()
    }

    test("watchlist changes keep the indicator settings") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Mia")).profile.id
        val settings = IndicatorDefaults.all.map { it.setting }
        repository.changeIndicatorSettings(id, CREATED_AT) { settings }

        repository.addToWatchlist(id, watchlistEntry("AAPL"), CREATED_AT.plusSeconds(1))
        repository.removeFromWatchlist(id, Symbol.of("AAPL"), CREATED_AT.plusSeconds(2))

        repository.findById(id)?.indicatorSettings shouldBe settings
    }

    test("concurrent completions of the indicator settings keep every added ratio") {
        val repository = newRepository()
        val id = repository.getOrCreate(profileCandidate("Noah")).profile.id
        val defaults = IndicatorDefaults.all.map { it.setting }

        coroutineScope {
            defaults.chunked(8).map { chunk ->
                async {
                    repository.changeIndicatorSettings(id, CREATED_AT) { stored ->
                        defaults.filter { setting -> setting in stored || setting in chunk }
                    }
                }
            }.awaitAll()
        }

        repository.findById(id)?.indicatorSettings shouldBe defaults
    }
}
