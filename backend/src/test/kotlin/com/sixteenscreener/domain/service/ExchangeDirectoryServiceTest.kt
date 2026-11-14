package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.FakeMarketDataSource
import com.sixteenscreener.fakes.InMemoryExchangeDirectoryStore
import com.sixteenscreener.fakes.InMemoryRequestCounterRepository
import com.sixteenscreener.fakes.InMemorySourceCallOutcomeRepository
import com.sixteenscreener.fakes.MutableClock
import com.sixteenscreener.port.outbound.ExchangeDirectory
import com.sixteenscreener.port.outbound.MarketDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import java.time.Duration

private val us = ExchangeCode.US
private val de = ExchangeCode("DE")
private val apple = ListedSymbol(Symbol.of("AAPL"), "APPLE INC", "XNAS", "USD", "Common Stock")
private val berkshire = ListedSymbol(Symbol.of("BRK.B"), "BERKSHIRE HATHAWAY INC-CL B", "XNYS", "USD", "Common Stock")
private val sap = ListedSymbol(Symbol.of("SAP.DE"), "SAP SE", "XETR", "EUR", "Common Stock")
private val siemens = ListedSymbol(Symbol.of("SIE.DE"), "SIEMENS AG-REG", "XETR", "EUR", "Common Stock")

private class GatedSource(private val delegate: FakeMarketDataSource) : MarketDataSource by delegate {
    val gate = CompletableDeferred<Unit>()

    override suspend fun exchangeSymbols(exchangeCode: ExchangeCode): SourceResult<List<ListedSymbol>> {
        gate.await()
        return delegate.exchangeSymbols(exchangeCode)
    }
}

private class Fixture(limit: Int = 60) {
    val clock = MutableClock()
    val source = FakeMarketDataSource()
    val store = InMemoryExchangeDirectoryStore()
    val outcomeRepository = InMemorySourceCallOutcomeRepository()
    val limiter = RateLimiter(
        mapOf(source.id to listOf(RequestLimit(LimitInterval.MINUTE, limit))),
        InMemoryRequestCounterRepository(),
        clock,
    )

    fun directory(source: MarketDataSource = this.source) = ExchangeDirectoryService(
        source = source,
        store = store,
        guard = SourceCallGuard(limiter),
        outcomes = SourceCallOutcomes(outcomeRepository),
        clock = clock,
    )

    fun listed(exchangeCode: ExchangeCode, vararg listings: ListedSymbol) {
        source.exchanges[exchangeCode] = SourceResult.Success(listings.toList(), SourceCallOutcome.success(clock.now()))
    }

    suspend fun stored(exchangeCode: ExchangeCode, age: Duration, vararg listings: ListedSymbol) {
        store.save(ExchangeDirectory(source.id, exchangeCode, listings.toList(), clock.now() - age))
    }

    val exchangeCalls: List<String> get() = source.calls.filter { it.startsWith("exchange:") }
}

class ExchangeDirectoryServiceTest : FunSpec({

    test("symbol without suffix is found in the US directory") {
        val fixture = Fixture()
        fixture.listed(us, apple, berkshire)
        val directory = fixture.directory()

        directory.find(Symbol.of("AAPL")) shouldBe apple
        directory.find(Symbol.of("BRK.B")) shouldBe berkshire
        fixture.exchangeCalls shouldBe listOf("exchange:US")
    }

    test("symbol with suffix is found in the directory of its exchange") {
        val fixture = Fixture()
        fixture.listed(de, sap)

        fixture.directory().find(Symbol.of("SAP.DE")) shouldBe sap
        fixture.exchangeCalls shouldBe listOf("exchange:DE")
    }

    test("symbol with an unsupported suffix is looked up in the US directory") {
        val fixture = Fixture()
        fixture.listed(us, apple)

        fixture.directory().find(Symbol.of("SAP.XX")).shouldBeNull()
        fixture.exchangeCalls shouldBe listOf("exchange:US")
    }

    test("unknown symbol of a loaded exchange is not found without further calls") {
        val fixture = Fixture()
        fixture.listed(us, apple)
        val directory = fixture.directory()
        directory.find(Symbol.of("AAPL"))

        directory.find(Symbol.of("ZZZZ")).shouldBeNull()
        fixture.exchangeCalls shouldBe listOf("exchange:US")
        fixture.store.loads shouldBe 1
    }

    test("directory missing in the store is downloaded once, saved and the outcome recorded") {
        val fixture = Fixture()
        fixture.listed(de, sap, siemens)
        val directory = fixture.directory()

        directory.find(Symbol.of("SAP.DE")) shouldBe sap
        directory.find(Symbol.of("SIE.DE")) shouldBe siemens

        fixture.exchangeCalls shouldBe listOf("exchange:DE")
        fixture.store.stored(fixture.source.id, de) shouldBe ExchangeDirectory(fixture.source.id, de, listOf(sap, siemens), fixture.clock.now())
        fixture.outcomeRepository.writes shouldBe listOf(fixture.source.id to SourceCallOutcome.success(fixture.clock.now()))
        fixture.limiter.usage(fixture.source.id).single().used shouldBe 1
    }

    test("directory younger than one day in the store is used without a download") {
        val fixture = Fixture()
        fixture.stored(de, Duration.ofHours(23), sap)

        fixture.directory().find(Symbol.of("SAP.DE")) shouldBe sap
        fixture.exchangeCalls.shouldBeEmpty()
    }

    test("directory older than one day in the store is downloaded again") {
        val fixture = Fixture()
        fixture.stored(de, Duration.ofDays(1), sap)
        fixture.listed(de, sap, siemens)

        fixture.directory().find(Symbol.of("SIE.DE")) shouldBe siemens
        fixture.exchangeCalls shouldBe listOf("exchange:DE")
        fixture.store.stored(fixture.source.id, de)?.downloadedAt shouldBe fixture.clock.now()
    }

    test("directory in memory is refreshed after one day") {
        val fixture = Fixture()
        fixture.listed(de, sap)
        val directory = fixture.directory()
        directory.find(Symbol.of("SAP.DE"))

        fixture.clock.advanceBy(Duration.ofHours(23))
        fixture.listed(de, sap, siemens)
        directory.find(Symbol.of("SIE.DE")).shouldBeNull()

        fixture.clock.advanceBy(Duration.ofHours(1))
        directory.find(Symbol.of("SIE.DE")) shouldBe siemens
        fixture.exchangeCalls shouldBe listOf("exchange:DE", "exchange:DE")
    }

    test("a directory refreshed by another instance is taken from the store instead of downloading") {
        val fixture = Fixture()
        fixture.listed(de, sap)
        val directory = fixture.directory()
        directory.find(Symbol.of("SAP.DE"))

        fixture.clock.advanceBy(Duration.ofDays(1))
        fixture.stored(de, Duration.ofMinutes(10), sap, siemens)

        directory.find(Symbol.of("SIE.DE")) shouldBe siemens
        fixture.exchangeCalls shouldBe listOf("exchange:DE")
    }

    test("own limit reached while refreshing keeps the stale directory and retries after a few minutes") {
        val fixture = Fixture(limit = 0)
        fixture.stored(de, Duration.ofDays(3), sap)
        fixture.listed(de, sap, siemens)
        val directory = fixture.directory()

        directory.find(Symbol.of("SAP.DE")) shouldBe sap
        directory.find(Symbol.of("SIE.DE")).shouldBeNull()
        fixture.exchangeCalls.shouldBeEmpty()
        fixture.outcomeRepository.writes.shouldBeEmpty()
        fixture.store.loads shouldBe 1

        fixture.clock.advanceBy(Duration.ofMinutes(5))
        directory.find(Symbol.of("SAP.DE")) shouldBe sap
        fixture.store.loads shouldBe 2
    }

    test("upstream 429 while refreshing keeps the stale directory, saturates the counters and records the outcome") {
        val fixture = Fixture()
        fixture.stored(de, Duration.ofDays(2), sap)
        val rejected = SourceCallOutcome.httpError(429, fixture.clock.now())
        fixture.source.exchanges[de] = SourceResult.LimitReached(rejected)

        fixture.directory().find(Symbol.of("SAP.DE")) shouldBe sap

        fixture.limiter.tryAcquire(fixture.source.id) shouldBe 0
        fixture.outcomeRepository.writes shouldBe listOf(fixture.source.id to rejected)
        fixture.store.stored(fixture.source.id, de)?.downloadedAt shouldBe fixture.clock.now() - Duration.ofDays(2)
    }

    test("failed download without a stored directory is cached as empty and retried after a few minutes") {
        val fixture = Fixture()
        fixture.source.exchanges[de] = SourceResult.SourceError(SourceCallOutcome.timeout(fixture.clock.now()))
        val directory = fixture.directory()

        directory.find(Symbol.of("SAP.DE")).shouldBeNull()
        fixture.clock.advanceBy(Duration.ofMinutes(4))
        directory.find(Symbol.of("SAP.DE")).shouldBeNull()
        fixture.exchangeCalls shouldBe listOf("exchange:DE")
        fixture.store.loads shouldBe 1

        fixture.listed(de, sap)
        fixture.clock.advanceBy(Duration.ofMinutes(1))
        directory.find(Symbol.of("SAP.DE")) shouldBe sap
        fixture.exchangeCalls shouldBe listOf("exchange:DE", "exchange:DE")
    }

    test("exchange without symbols at the source is cached as unknown until the next day") {
        val fixture = Fixture()
        val moscow = ExchangeCode("ME")
        fixture.source.exchanges[moscow] = SourceResult.NoData(SourceCallOutcome.success(fixture.clock.now()))
        val directory = fixture.directory()

        directory.find(Symbol.of("SBER.ME")).shouldBeNull()
        fixture.clock.advanceBy(Duration.ofHours(23))
        directory.find(Symbol.of("SBER.ME")).shouldBeNull()
        directory.find(Symbol.of("GAZP.ME")).shouldBeNull()
        fixture.exchangeCalls shouldBe listOf("exchange:ME")
        fixture.store.loads shouldBe 1
        fixture.store.saves shouldBe 0

        fixture.clock.advanceBy(Duration.ofHours(1))
        directory.find(Symbol.of("SBER.ME")).shouldBeNull()
        fixture.exchangeCalls shouldBe listOf("exchange:ME", "exchange:ME")
    }

    test("exchange not covered by the source plan is cached as unknown until the next day") {
        val fixture = Fixture()
        fixture.source.exchanges[de] = SourceResult.NotSupported(SourceCallOutcome.httpError(403, fixture.clock.now()))
        val directory = fixture.directory()

        directory.find(Symbol.of("SAP.DE")).shouldBeNull()
        fixture.clock.advanceBy(Duration.ofHours(12))
        directory.find(Symbol.of("SAP.DE")).shouldBeNull()

        fixture.exchangeCalls shouldBe listOf("exchange:DE")
        fixture.outcomeRepository.writes shouldBe listOf(fixture.source.id to SourceCallOutcome.httpError(403, fixture.clock.now() - Duration.ofHours(12)))
    }

    test("store read failure falls back to a download") {
        val fixture = Fixture()
        fixture.store.failLoads = true
        fixture.listed(de, sap)

        fixture.directory().find(Symbol.of("SAP.DE")) shouldBe sap
    }

    test("store write failure still serves the downloaded directory") {
        val fixture = Fixture()
        fixture.store.failSaves = true
        fixture.listed(de, sap)
        val directory = fixture.directory()

        directory.find(Symbol.of("SAP.DE")) shouldBe sap
        directory.find(Symbol.of("SAP.DE")) shouldBe sap
        fixture.exchangeCalls shouldBe listOf("exchange:DE")
    }

    test("concurrent first use of an exchange downloads once") {
        val fixture = Fixture()
        fixture.listed(de, sap, siemens)
        val gated = GatedSource(fixture.source)
        val directory = fixture.directory(gated)

        val results = coroutineScope {
            val lookups = (1..20).map { index ->
                async { directory.find(if (index % 2 == 0) sap.symbol else siemens.symbol) }
            }
            repeat(5) { yield() }
            gated.gate.complete(Unit)
            lookups.awaitAll()
        }

        results.toSet() shouldBe setOf(sap, siemens)
        fixture.exchangeCalls shouldBe listOf("exchange:DE")
        fixture.store.loads shouldBe 1
        fixture.limiter.usage(fixture.source.id).single().used shouldBe 1
    }

    test("findAll loads each exchange once and omits unknown symbols") {
        val fixture = Fixture()
        fixture.listed(us, apple)
        fixture.listed(de, sap, siemens)

        val found = fixture.directory().findAll(
            listOf(Symbol.of("AAPL"), Symbol.of("SAP.DE"), Symbol.of("SIE.DE"), Symbol.of("ZZZZ"), Symbol.of("AAPL")),
        )

        found shouldBe mapOf(apple.symbol to apple, sap.symbol to sap, siemens.symbol to siemens)
        fixture.exchangeCalls.sorted() shouldContainExactly listOf("exchange:DE", "exchange:US")
    }

    test("findAll of no symbols makes no calls") {
        val fixture = Fixture()

        fixture.directory().findAll(emptyList()).shouldBeEmpty()
        fixture.store.loads shouldBe 0
    }
})
