package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.Firestore
import com.sixteenscreener.contract.CompanyCacheUnderTest
import com.sixteenscreener.contract.companyCacheContract
import com.sixteenscreener.contract.logoMissRepositoryContract
import com.sixteenscreener.contract.profileCandidate
import com.sixteenscreener.contract.profileRepositoryContract
import com.sixteenscreener.contract.requestCounterRepositoryContract
import com.sixteenscreener.contract.sampleProfile
import com.sixteenscreener.contract.sampleRatios
import com.sixteenscreener.contract.secCikRepositoryContract
import com.sixteenscreener.contract.sourceCallOutcomeRepositoryContract
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteHistoryEntry
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private suspend fun Firestore.document(collection: String, id: String): Map<String, Any?> =
    checkNotNull(collection(collection).document(id).get().await().data) { "Document $collection/$id does not exist" }

class FirestoreProfileRepositoryEmulatorTest : FunSpec({
    val emulator = FirestoreEmulator()
    afterSpec { emulator.close() }

    include(profileRepositoryContract { FirestoreProfileRepository(emulator.reset()) })

    test("stores the profile and its name document as defined by the data model") {
        val firestore = emulator.reset()
        val profile = profileCandidate("  Zoë ")

        FirestoreProfileRepository(firestore).getOrCreate(profile)

        val nameId = FirestoreProfileRepository.nameDocumentId(ProfileName.of("zoë"))
        firestore.document(FirestoreCollections.PROFILE_NAMES, nameId) shouldBe mapOf("profileId" to profile.id.toString())
        val document = firestore.document(FirestoreCollections.PROFILES, profile.id.toString())
        document["name"] shouldBe "Zoë"
        document["watchlist"] shouldBe emptyList<String>()
        document["defaultSource"].shouldBeNull()
        document.keys shouldBe setOf("name", "defaultSource", "watchlist", "createdAt", "updatedAt")
    }

    test("stores watchlist entries as maps of symbol, name, sector and tags") {
        val firestore = emulator.reset()
        val repository = FirestoreProfileRepository(firestore)
        val profile = repository.getOrCreate(profileCandidate("Ann")).profile

        repository.addToWatchlist(
            profile.id,
            watchlistEntry("SAP.DE", sector = "Software", tags = listOf("eu"), name = "SAP SE"),
            Instant.now(),
        )
        repository.addToWatchlist(profile.id, watchlistEntry("AAPL"), Instant.now())

        firestore.document(FirestoreCollections.PROFILES, profile.id.toString())["watchlist"] shouldBe listOf(
            mapOf("symbol" to "SAP.DE", "name" to "SAP SE", "sector" to "Software", "tags" to listOf("eu")),
            mapOf("symbol" to "AAPL", "name" to "AAPL", "sector" to null, "tags" to emptyList<String>()),
        )
    }

    test("watchlist entry stored without a name is read with the ticker as name") {
        val firestore = emulator.reset()
        val repository = FirestoreProfileRepository(firestore)
        val profile = repository.getOrCreate(profileCandidate("Cid")).profile
        firestore.collection(FirestoreCollections.PROFILES).document(profile.id.toString()).update(
            "watchlist",
            listOf(
                mapOf("symbol" to "SAP.DE", "sector" to "Software", "tags" to emptyList<String>()),
                mapOf("symbol" to "AAPL", "name" to 42L, "sector" to null, "tags" to emptyList<String>()),
            ),
        ).await()

        repository.findById(profile.id)?.watchlist shouldBe listOf(
            watchlistEntry("SAP.DE", sector = "Software", name = "SAP"),
            watchlistEntry("AAPL", name = "AAPL"),
        )
    }

    test("skips watchlist entries of an unexpected shape") {
        val firestore = emulator.reset()
        val repository = FirestoreProfileRepository(firestore)
        val profile = repository.getOrCreate(profileCandidate("Ben")).profile
        firestore.collection(FirestoreCollections.PROFILES).document(profile.id.toString()).update(
            "watchlist",
            listOf(
                "AAPL",
                mapOf("symbol" to "not a symbol"),
                mapOf("sector" to "Technology"),
                mapOf("symbol" to "MSFT", "sector" to 42L, "tags" to listOf("ok", 1L)),
                mapOf("symbol" to "IBM", "tags" to "not a list"),
            ),
        ).await()

        repository.findById(profile.id)?.watchlist shouldBe listOf(
            watchlistEntry("MSFT", tags = listOf("ok")),
            watchlistEntry("IBM"),
        )
    }
})

class FirestoreCompanyCacheEmulatorTest : FunSpec({
    val emulator = FirestoreEmulator()
    afterSpec { emulator.close() }

    include(
        companyCacheContract {
            val firestore = emulator.reset()
            object : CompanyCacheUnderTest {
                override val cache = FirestoreCompanyCache(firestore)

                override suspend fun history(source: SourceId, symbol: Symbol): List<QuoteHistoryEntry> =
                    firestore.collection(FirestoreCollections.QUOTE_HISTORY)
                        .whereEqualTo("source", source.value)
                        .whereEqualTo("symbol", symbol.value)
                        .get().await()
                        .documents
                        .map { document ->
                            QuoteHistoryEntry(
                                source = SourceId.of(document.requiredString("source")),
                                symbol = Symbol.of(document.requiredString("symbol")),
                                marketDate = LocalDate.parse(document.requiredString("marketDate")),
                                quote = Quote(checkNotNull(document.getDouble("price")), document.requiredString("currency")),
                                fetchedAt = document.requiredInstant("fetchedAt"),
                            )
                        }
                        .sortedBy { it.marketDate }
            }
        },
    )

    val finnhub = SourceId.of("finnhub")
    val apple = Symbol.of("AAPL")
    val fetchedAt = Instant.parse("2026-11-14T15:00:00Z")
    val expireAt = fetchedAt.plus(Duration.ofDays(14)).toTimestamp()

    test("cached documents expire 14 days after fetchedAt") {
        val firestore = emulator.reset()
        FirestoreCompanyCache(firestore).put(CacheKey(finnhub, apple, DataType.RATIOS), CacheEntry(sampleRatios, fetchedAt))

        val document = firestore.document(FirestoreCollections.SOURCE_DATA, "finnhub__AAPL__RATIOS")

        document["fetchedAt"] shouldBe fetchedAt.toTimestamp()
        document["expireAt"] shouldBe expireAt
        document.keys shouldBe setOf("payload", "fetchedAt", "expireAt")
    }

    test("company profile documents expire 90 days after fetchedAt") {
        val firestore = emulator.reset()
        FirestoreCompanyCache(firestore).put(
            CacheKey(finnhub, apple, DataType.COMPANY_PROFILE),
            CacheEntry(sampleProfile, fetchedAt),
        )

        val document = firestore.document(FirestoreCollections.SOURCE_DATA, "finnhub__AAPL__COMPANY_PROFILE")

        document["fetchedAt"] shouldBe fetchedAt.toTimestamp()
        document["expireAt"] shouldBe fetchedAt.plus(Duration.ofDays(90)).toTimestamp()
    }

    test("quote documents carry the market day and previous price; history documents expire too") {
        val firestore = emulator.reset()
        val cache = FirestoreCompanyCache(firestore)
        val newYork = ZoneId.of("America/New_York")
        cache.putQuote(finnhub, apple, Quote(100.0, "USD"), fetchedAt.minus(Duration.ofDays(1)), newYork)

        cache.putQuote(finnhub, apple, Quote(101.5, "USD"), fetchedAt, newYork)

        firestore.document(FirestoreCollections.SOURCE_DATA, "finnhub__AAPL__QUOTE") shouldBe mapOf(
            "payload" to mapOf("price" to 101.5, "currency" to "USD"),
            "fetchedAt" to fetchedAt.toTimestamp(),
            "expireAt" to expireAt,
            "marketDate" to "2026-11-14",
            "previousPrice" to 100.0,
            "previousPriceDate" to "2026-11-13",
        )
        firestore.document(FirestoreCollections.QUOTE_HISTORY, "finnhub__AAPL__2026-11-14") shouldBe mapOf(
            "source" to "finnhub",
            "symbol" to "AAPL",
            "marketDate" to "2026-11-14",
            "price" to 101.5,
            "currency" to "USD",
            "fetchedAt" to fetchedAt.toTimestamp(),
            "expireAt" to expireAt,
        )
    }

    test("an unreadable cache document is treated as absent") {
        val firestore = emulator.reset()
        firestore.collection(FirestoreCollections.SOURCE_DATA).document("finnhub__AAPL__RATIOS")
            .set(mapOf("payload" to "not a map")).await()

        FirestoreCompanyCache(firestore).get(CacheKey(finnhub, apple, DataType.RATIOS)).shouldBeNull()
    }
})

class FirestoreRequestCounterRepositoryEmulatorTest : FunSpec({
    val emulator = FirestoreEmulator()
    afterSpec { emulator.close() }

    include(requestCounterRepositoryContract { FirestoreRequestCounterRepository(emulator.reset()) })

    test("counter documents expire at the end of their window") {
        val firestore = emulator.reset()
        val now = Instant.parse("2026-11-14T12:00:30Z")

        FirestoreRequestCounterRepository(firestore)
            .tryAcquire(SourceId.of("finnhub"), listOf(RequestLimit(LimitInterval.MONTH, 1000)), 7, now)

        firestore.document(FirestoreCollections.REQUEST_COUNTERS, "finnhub__MONTH__2026-11-01T00:00:00Z") shouldBe mapOf(
            "count" to 7L,
            "windowStart" to Instant.parse("2026-11-01T00:00:00Z").toTimestamp(),
            "expireAt" to Instant.parse("2026-12-01T00:00:00Z").toTimestamp(),
        )
    }
})

class FirestoreSourceCallOutcomeRepositoryEmulatorTest : FunSpec({
    val emulator = FirestoreEmulator()
    afterSpec { emulator.close() }

    include(sourceCallOutcomeRepositoryContract { FirestoreSourceCallOutcomeRepository(emulator.reset()) })

    test("outcome documents as defined by the data model") {
        val firestore = emulator.reset()
        val at = Instant.parse("2026-11-14T12:00:00Z")

        FirestoreSourceCallOutcomeRepository(firestore).record(SourceId.of("finnhub"), SourceCallOutcome.httpError(429, at))

        firestore.document(FirestoreCollections.SOURCE_CALLS, "finnhub") shouldBe
            mapOf("result" to "HTTP_ERROR", "httpStatus" to 429L, "at" to at.toTimestamp())
    }
})

class FirestoreLogoMissRepositoryEmulatorTest : FunSpec({
    val emulator = FirestoreEmulator()
    afterSpec { emulator.close() }

    include(logoMissRepositoryContract { FirestoreLogoMissRepository(emulator.reset()) })

    test("logo misses expire 14 days after the check") {
        val firestore = emulator.reset()
        val checkedAt = Instant.parse("2026-11-14T12:00:00Z")

        FirestoreLogoMissRepository(firestore).recordMiss(Symbol.of("SAP.DE"), checkedAt)

        firestore.document(FirestoreCollections.LOGO_MISSES, "SAP.DE") shouldBe mapOf(
            "checkedAt" to checkedAt.toTimestamp(),
            "expireAt" to checkedAt.plus(Duration.ofDays(14)).toTimestamp(),
        )
    }
})

class FirestoreSecCikRepositoryEmulatorTest : FunSpec({
    val emulator = FirestoreEmulator()
    afterSpec { emulator.close() }

    include(secCikRepositoryContract { FirestoreSecCikRepository(emulator.reset()) })

    test("CIK documents expire 14 days after they are stored") {
        val firestore = emulator.reset()
        val storedAt = Instant.parse("2026-11-14T12:00:00Z")

        FirestoreSecCikRepository(firestore).save("BRK-B", 1067983, storedAt)

        firestore.document(FirestoreCollections.SEC_CIK, "BRK-B") shouldBe mapOf(
            "cik" to 1067983L,
            "expireAt" to storedAt.plus(Duration.ofDays(14)).toTimestamp(),
        )
    }
})
