package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.Logo
import com.sixteenscreener.domain.model.LogoOrigin
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.FakeLogoProvider
import com.sixteenscreener.fakes.InMemoryLogoMissRepository
import com.sixteenscreener.fakes.InMemoryLogoStorage
import com.sixteenscreener.fakes.InMemoryRequestCounterRepository
import com.sixteenscreener.fakes.InMemorySourceCallOutcomeRepository
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

private val logoapi = SourceId.of("logoapi")
private val aapl = Symbol.of("AAPL")
private val defaultSvg = "<svg>default</svg>".toByteArray()
private val appleSvg = "<svg>apple</svg>".toByteArray()

private class LogoFixture(limit: Int = 1000, withProvider: Boolean = true) {
    val clock = MutableClock()
    val storage = InMemoryLogoStorage()
    val misses = InMemoryLogoMissRepository()
    val provider = FakeLogoProvider(logoapi)
    val counters = InMemoryRequestCounterRepository()
    val sourceCalls = InMemorySourceCallOutcomeRepository()
    private val limits = listOf(RequestLimit(LimitInterval.DAY, limit))
    private val limiter = RateLimiter(mapOf(logoapi to limits), counters, clock)
    val service = LogoService(
        storage = storage,
        provider = provider.takeIf { withProvider },
        misses = misses,
        guard = SourceCallGuard(limiter),
        outcomes = SourceCallOutcomes(sourceCalls),
        clock = clock,
        defaultLogo = defaultSvg,
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    suspend fun used(): Int = counters.usage(logoapi, limits, clock.now()).single().used

    suspend fun lastCall(): SourceCallOutcome? = sourceCalls.findAll(listOf(logoapi))[logoapi]
}

private infix fun Logo.shouldBeLogo(expected: Pair<ByteArray, LogoOrigin>) {
    String(svg) shouldBe String(expected.first)
    origin shouldBe expected.second
}

class LogoServiceTest : FunSpec({

    test("bucket hit is returned without asking the source") {
        val fixture = LogoFixture()
        fixture.storage.save(aapl, appleSvg)

        fixture.service.logo(aapl) shouldBeLogo (appleSvg to LogoOrigin.STORED)

        fixture.provider.calls.shouldBeEmpty()
        fixture.used() shouldBe 0
    }

    test("source hit is stored in the bucket, counted and recorded") {
        val fixture = LogoFixture()
        fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

        fixture.service.logo(aapl) shouldBeLogo (appleSvg to LogoOrigin.DOWNLOADED)

        String(fixture.storage.find(aapl)!!) shouldBe String(appleSvg)
        fixture.used() shouldBe 1
        fixture.lastCall() shouldBe fixture.success
        fixture.misses.lastMiss(aapl).shouldBeNull()
    }

    test("logo not found in the source: miss recorded, default logo returned") {
        val fixture = LogoFixture()
        val notFound = SourceCallOutcome.httpError(404, fixture.clock.now())
        fixture.provider.logos[aapl] = SourceResult.NoData(notFound)

        fixture.service.logo(aapl) shouldBeLogo (defaultSvg to LogoOrigin.DEFAULT_MISSING)

        fixture.misses.lastMiss(aapl) shouldBe fixture.clock.now()
        fixture.storage.contains(aapl) shouldBe false
        fixture.lastCall() shouldBe notFound
    }

    test("non-SVG response (no data from the provider) is recorded as a miss") {
        val fixture = LogoFixture()
        fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)

        fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_MISSING

        fixture.misses.lastMiss(aapl) shouldBe fixture.clock.now()
    }

    test("recorded miss younger than 14 days: default logo without a call") {
        val fixture = LogoFixture()
        fixture.misses.recordMiss(aapl, fixture.clock.now())
        fixture.clock.advanceBy(Duration.ofDays(14).minusSeconds(1))

        fixture.service.logo(aapl) shouldBeLogo (defaultSvg to LogoOrigin.DEFAULT_MISSING)

        fixture.provider.calls.shouldBeEmpty()
        fixture.used() shouldBe 0
    }

    test("expired miss: the source is asked again") {
        val fixture = LogoFixture()
        fixture.misses.recordMiss(aapl, fixture.clock.now())
        fixture.clock.advanceBy(Duration.ofDays(14))
        fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

        fixture.service.logo(aapl) shouldBeLogo (appleSvg to LogoOrigin.DOWNLOADED)

        fixture.provider.calls shouldContainExactly listOf(aapl)
    }

    test("limit reached: temporary default logo, no call, no miss") {
        val fixture = LogoFixture(limit = 0)

        val logo = fixture.service.logo(aapl)

        logo shouldBeLogo (defaultSvg to LogoOrigin.DEFAULT_UNAVAILABLE)
        logo.isTemporary shouldBe true
        fixture.provider.calls.shouldBeEmpty()
        fixture.misses.lastMiss(aapl).shouldBeNull()
        fixture.lastCall().shouldBeNull()
    }

    test("source answering 429: temporary default logo, counters saturated, outcome recorded") {
        val fixture = LogoFixture()
        val tooMany = SourceCallOutcome.httpError(429, fixture.clock.now())
        fixture.provider.logos[aapl] = SourceResult.LimitReached(tooMany)

        fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_UNAVAILABLE

        fixture.used() shouldBe 1000
        fixture.lastCall() shouldBe tooMany
        fixture.misses.lastMiss(aapl).shouldBeNull()
    }

    test("timeout: temporary default logo, outcome recorded, no miss") {
        val fixture = LogoFixture()
        val timeout = SourceCallOutcome.timeout(fixture.clock.now())
        fixture.provider.logos[aapl] = SourceResult.SourceError(timeout)

        fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_UNAVAILABLE

        fixture.lastCall() shouldBe timeout
        fixture.misses.lastMiss(aapl).shouldBeNull()
    }

    test("source not supporting the symbol: temporary default logo without a miss") {
        val fixture = LogoFixture()
        fixture.provider.logos[aapl] = SourceResult.NotSupported(SourceCallOutcome.httpError(403, fixture.clock.now()))

        fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_UNAVAILABLE

        fixture.misses.lastMiss(aapl).shouldBeNull()
    }

    test("storage write failure still returns the downloaded logo") {
        val fixture = LogoFixture()
        fixture.storage.failingWrites = true
        fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

        fixture.service.logo(aapl) shouldBeLogo (appleSvg to LogoOrigin.DOWNLOADED)
    }

    test("storage read failure falls back to the source") {
        val fixture = LogoFixture()
        fixture.storage.failingReads = true
        fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

        fixture.service.logo(aapl) shouldBeLogo (appleSvg to LogoOrigin.DOWNLOADED)
    }

    test("miss repository failures: the source is asked and the default logo returned") {
        val fixture = LogoFixture()
        fixture.misses.failing = true
        fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)

        fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_MISSING

        fixture.provider.calls shouldContainExactly listOf(aapl)
    }

    test("no logo source configured: temporary default logo") {
        val fixture = LogoFixture(withProvider = false)

        fixture.service.logo(aapl) shouldBeLogo (defaultSvg to LogoOrigin.DEFAULT_UNAVAILABLE)
    }
})
