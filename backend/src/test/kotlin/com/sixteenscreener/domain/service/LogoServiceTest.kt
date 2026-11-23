package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ImageFormat
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
import com.sixteenscreener.port.outbound.LogoProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

private val logoapi = SourceId.of("logoapi")
private val eodhdLogos = SourceId.of("eodhd-logos")
private val finnhubLogos = SourceId.of("finnhub-logos")
private val aapl = Symbol.of("AAPL")
private val defaultSvg = "<svg>default</svg>".toByteArray()
private val appleSvg = "<svg>apple</svg>".toByteArray()
private val applePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01)
private val eodhdApplePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x02)

private class LogoFixture(
    limit: Int = 1000,
    withProvider: Boolean = true,
    withPngProvider: Boolean = true,
    withEodhdProvider: Boolean = false,
) {
    val clock = MutableClock()
    val storage = InMemoryLogoStorage(ImageFormat.SVG)
    val eodhdStorage = InMemoryLogoStorage(ImageFormat.PNG)
    val pngStorage = InMemoryLogoStorage(ImageFormat.PNG)
    val misses = InMemoryLogoMissRepository()
    val provider = FakeLogoProvider(logoapi, ImageFormat.SVG)
    val eodhdProvider = FakeLogoProvider(eodhdLogos, ImageFormat.PNG)
    val pngProvider = FakeLogoProvider(finnhubLogos, ImageFormat.PNG)
    val counters = InMemoryRequestCounterRepository()
    val sourceCalls = InMemorySourceCallOutcomeRepository()
    private val limits = listOf(RequestLimit(LimitInterval.DAY, limit))
    private val limiter = RateLimiter(
        limitsBySource = mapOf(logoapi to limits, eodhdLogos to emptyList(), finnhubLogos to emptyList()),
        counters = counters,
        clock = clock,
        totalCountedSources = setOf(eodhdLogos, finnhubLogos),
    )
    val service = LogoService(
        tiers = listOf(
            LogoTier(storage, provider.takeIf { withProvider }),
            LogoTier(eodhdStorage, eodhdProvider.takeIf { withEodhdProvider }),
            LogoTier(pngStorage, pngProvider.takeIf { withPngProvider }),
        ),
        misses = misses,
        guard = SourceCallGuard(limiter),
        outcomes = SourceCallOutcomes(sourceCalls),
        clock = clock,
        defaultLogo = defaultSvg,
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    suspend fun used(): Int = counters.usage(logoapi, limits, clock.now()).single().used

    suspend fun totalPngCalls(): Long = counters.totals(listOf(finnhubLogos)).getValue(finnhubLogos)

    suspend fun totalEodhdCalls(): Long = counters.totals(listOf(eodhdLogos)).getValue(eodhdLogos)

    suspend fun lastCall(source: SourceId = logoapi): SourceCallOutcome? = sourceCalls.findAll(listOf(source))[source]

    fun noLogoAnywhere() {
        provider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, clock.now()))
        pngProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, clock.now()))
    }
}

private infix fun Logo.shouldBeLogo(expected: Triple<ByteArray, ImageFormat, LogoOrigin>) {
    bytes.toList() shouldBe expected.first.toList()
    format shouldBe expected.second
    origin shouldBe expected.third
}

private fun svg(bytes: ByteArray, origin: LogoOrigin) = Triple(bytes, ImageFormat.SVG, origin)

private fun png(bytes: ByteArray, origin: LogoOrigin) = Triple(bytes, ImageFormat.PNG, origin)

class LogoServiceTest : FunSpec({

    context("step 1: stored SVG") {
        test("bucket hit is returned without asking any source") {
            val fixture = LogoFixture()
            fixture.storage.save(aapl, appleSvg)
            fixture.pngStorage.save(aapl, applePng)

            fixture.service.logo(aapl) shouldBeLogo svg(appleSvg, LogoOrigin.STORED)

            fixture.provider.calls.shouldBeEmpty()
            fixture.pngProvider.calls.shouldBeEmpty()
            fixture.used() shouldBe 0
        }

        test("storage read failure moves on to the stored PNG") {
            val fixture = LogoFixture()
            fixture.storage.failingReads = true
            fixture.pngStorage.save(aapl, applePng)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.STORED)
        }
    }

    context("step 2: stored PNG") {
        test("stored PNG is returned as PNG without asking any source") {
            val fixture = LogoFixture()
            fixture.pngStorage.save(aapl, applePng)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.STORED)

            fixture.provider.calls.shouldBeEmpty()
            fixture.pngProvider.calls.shouldBeEmpty()
        }

        test("PNG storage read failure moves on to logoapi.sh") {
            val fixture = LogoFixture()
            fixture.pngStorage.failingReads = true
            fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo svg(appleSvg, LogoOrigin.DOWNLOADED)
        }

        test("PNG logos are read even when the Finnhub logo source is disabled") {
            val fixture = LogoFixture(withPngProvider = false)
            fixture.pngStorage.save(aapl, applePng)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.STORED)
        }
    }

    context("step 3: logoapi.sh") {
        test("source hit is stored in the SVG bucket folder, counted and recorded; Finnhub isn't asked") {
            val fixture = LogoFixture()
            fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo svg(appleSvg, LogoOrigin.DOWNLOADED)

            fixture.storage.find(aapl)!!.toList() shouldBe appleSvg.toList()
            fixture.pngStorage.contains(aapl) shouldBe false
            fixture.used() shouldBe 1
            fixture.lastCall() shouldBe fixture.success
            fixture.misses.lastMiss(logoapi, aapl).shouldBeNull()
            fixture.pngProvider.calls.shouldBeEmpty()
        }

        test("logo not found: logoapi.sh miss recorded and Finnhub asked") {
            val fixture = LogoFixture()
            val notFound = SourceCallOutcome.httpError(404, fixture.clock.now())
            fixture.provider.logos[aapl] = SourceResult.NoData(notFound)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.misses.lastMiss(logoapi, aapl) shouldBe fixture.clock.now()
            fixture.misses.lastMiss(finnhubLogos, aapl).shouldBeNull()
            fixture.storage.contains(aapl) shouldBe false
            fixture.lastCall() shouldBe notFound
        }

        test("recorded miss younger than 14 days: logoapi.sh skipped without a call") {
            val fixture = LogoFixture()
            fixture.misses.recordMiss(logoapi, aapl, fixture.clock.now())
            fixture.clock.advanceBy(Duration.ofDays(14).minusSeconds(1))
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.provider.calls.shouldBeEmpty()
            fixture.used() shouldBe 0
        }

        test("expired miss: logoapi.sh is asked again") {
            val fixture = LogoFixture()
            fixture.misses.recordMiss(logoapi, aapl, fixture.clock.now())
            fixture.clock.advanceBy(Duration.ofDays(14))
            fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo svg(appleSvg, LogoOrigin.DOWNLOADED)

            fixture.provider.calls shouldContainExactly listOf(aapl)
        }

        test("a miss recorded for Finnhub logos doesn't skip logoapi.sh") {
            val fixture = LogoFixture()
            fixture.misses.recordMiss(finnhubLogos, aapl, fixture.clock.now())
            fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo svg(appleSvg, LogoOrigin.DOWNLOADED)
        }

        test("limit reached: no call, no miss, Finnhub asked") {
            val fixture = LogoFixture(limit = 0)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.provider.calls.shouldBeEmpty()
            fixture.misses.lastMiss(logoapi, aapl).shouldBeNull()
            fixture.lastCall().shouldBeNull()
        }

        test("source answering 429: counters saturated, outcome recorded, no miss") {
            val fixture = LogoFixture()
            val tooMany = SourceCallOutcome.httpError(429, fixture.clock.now())
            fixture.provider.logos[aapl] = SourceResult.LimitReached(tooMany)
            fixture.pngProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, fixture.clock.now()))

            fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_UNAVAILABLE

            fixture.used() shouldBe 1000
            fixture.lastCall() shouldBe tooMany
            fixture.misses.lastMiss(logoapi, aapl).shouldBeNull()
        }

        test("timeout: outcome recorded, no miss, Finnhub asked") {
            val fixture = LogoFixture()
            val timeout = SourceCallOutcome.timeout(fixture.clock.now())
            fixture.provider.logos[aapl] = SourceResult.SourceError(timeout)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.lastCall() shouldBe timeout
            fixture.misses.lastMiss(logoapi, aapl).shouldBeNull()
        }

        test("source not supporting the symbol counts as a failure, not as a miss") {
            val fixture = LogoFixture()
            fixture.provider.logos[aapl] = SourceResult.NotSupported(SourceCallOutcome.httpError(403, fixture.clock.now()))
            fixture.pngProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, fixture.clock.now()))

            fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_UNAVAILABLE

            fixture.misses.lastMiss(logoapi, aapl).shouldBeNull()
        }

        test("a throwing source moves on to Finnhub") {
            val fixture = LogoFixture()
            val throwing = object : LogoProvider by fixture.provider {
                override suspend fun logo(symbol: Symbol): SourceResult<ByteArray> = error("boom")
            }
            val service = LogoService(
                tiers = listOf(LogoTier(fixture.storage, throwing), LogoTier(fixture.pngStorage, fixture.pngProvider)),
                misses = fixture.misses,
                guard = SourceCallGuard(RateLimiter(mapOf(logoapi to emptyList(), finnhubLogos to emptyList()), fixture.counters, fixture.clock)),
                outcomes = SourceCallOutcomes(fixture.sourceCalls),
                clock = fixture.clock,
                defaultLogo = defaultSvg,
            )
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)
        }

        test("storage write failure still returns the downloaded logo") {
            val fixture = LogoFixture()
            fixture.storage.failingWrites = true
            fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo svg(appleSvg, LogoOrigin.DOWNLOADED)
        }
    }

    context("step 4: Finnhub PNG") {
        test("source hit is stored in the PNG bucket folder, returned as PNG, recorded and counted in the total") {
            val fixture = LogoFixture()
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.pngStorage.find(aapl)!!.toList() shouldBe applePng.toList()
            fixture.storage.contains(aapl) shouldBe false
            fixture.lastCall(finnhubLogos) shouldBe fixture.success
            fixture.totalPngCalls() shouldBe 1L
            fixture.used() shouldBe 1
        }

        test("the next request returns the stored PNG without asking any source") {
            val fixture = LogoFixture()
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)
            fixture.service.logo(aapl)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.STORED)

            fixture.provider.calls shouldContainExactly listOf(aapl)
            fixture.pngProvider.calls shouldContainExactly listOf(aapl)
            fixture.totalPngCalls() shouldBe 1L
        }

        test("logo not found: Finnhub logos miss recorded") {
            val fixture = LogoFixture()
            fixture.noLogoAnywhere()

            fixture.service.logo(aapl)

            fixture.misses.lastMiss(finnhubLogos, aapl) shouldBe fixture.clock.now()
            fixture.pngStorage.contains(aapl) shouldBe false
        }

        test("recent Finnhub logos miss: no call and not counted") {
            val fixture = LogoFixture()
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.misses.recordMiss(finnhubLogos, aapl, fixture.clock.now())

            fixture.service.logo(aapl) shouldBeLogo svg(defaultSvg, LogoOrigin.DEFAULT_MISSING)

            fixture.pngProvider.calls.shouldBeEmpty()
            fixture.totalPngCalls() shouldBe 0L
        }

        test("PNG storage write failure still returns the downloaded logo") {
            val fixture = LogoFixture()
            fixture.pngStorage.failingWrites = true
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)
        }
    }

    context("EODHD PNG between logoapi.sh and Finnhub") {
        test("a stored EODHD PNG is returned before any download") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.eodhdStorage.save(aapl, eodhdApplePng)
            fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(eodhdApplePng, LogoOrigin.STORED)

            fixture.provider.calls.shouldBeEmpty()
            fixture.eodhdProvider.calls.shouldBeEmpty()
            fixture.pngProvider.calls.shouldBeEmpty()
        }

        test("a stored EODHD PNG is returned before a stored Finnhub PNG") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.eodhdStorage.save(aapl, eodhdApplePng)
            fixture.pngStorage.save(aapl, applePng)

            fixture.service.logo(aapl) shouldBeLogo png(eodhdApplePng, LogoOrigin.STORED)
        }

        test("a stored Finnhub PNG is returned before an EODHD download") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.pngStorage.save(aapl, applePng)
            fixture.eodhdProvider.logos[aapl] = SourceResult.Success(eodhdApplePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.STORED)

            fixture.eodhdProvider.calls.shouldBeEmpty()
        }

        test("a logoapi.sh download is returned without asking EODHD") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.provider.logos[aapl] = SourceResult.Success(appleSvg, fixture.success)
            fixture.eodhdProvider.logos[aapl] = SourceResult.Success(eodhdApplePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo svg(appleSvg, LogoOrigin.DOWNLOADED)

            fixture.eodhdProvider.calls.shouldBeEmpty()
            fixture.totalEodhdCalls() shouldBe 0L
        }

        test("an EODHD download is stored in its own folder, counted and returned without asking Finnhub") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.eodhdProvider.logos[aapl] = SourceResult.Success(eodhdApplePng, fixture.success)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(eodhdApplePng, LogoOrigin.DOWNLOADED)

            fixture.eodhdStorage.find(aapl)!!.toList() shouldBe eodhdApplePng.toList()
            fixture.pngStorage.contains(aapl) shouldBe false
            fixture.pngProvider.calls.shouldBeEmpty()
            fixture.lastCall(eodhdLogos) shouldBe fixture.success
            fixture.totalEodhdCalls() shouldBe 1L
        }

        test("an EODHD miss is recorded and falls through to Finnhub") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.eodhdProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, fixture.clock.now()))
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.misses.lastMiss(eodhdLogos, aapl) shouldBe fixture.clock.now()
            fixture.eodhdStorage.contains(aapl) shouldBe false
        }

        test("an EODHD miss without a request (no EODHD exchange) is recorded and falls through to Finnhub") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.eodhdProvider.logos[aapl] = SourceResult.NoData(outcome = null)
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.misses.lastMiss(eodhdLogos, aapl) shouldBe fixture.clock.now()
            fixture.lastCall(eodhdLogos).shouldBeNull()
        }

        test("a recent EODHD miss skips EODHD without a call") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.misses.recordMiss(eodhdLogos, aapl, fixture.clock.now())
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.eodhdProvider.calls.shouldBeEmpty()
            fixture.totalEodhdCalls() shouldBe 0L
        }

        test("a failing EODHD call falls through to Finnhub without a miss") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.eodhdProvider.logos[aapl] = SourceResult.SourceError(SourceCallOutcome.timeout(fixture.clock.now()))
            fixture.pngProvider.logos[aapl] = SourceResult.Success(applePng, fixture.success)

            fixture.service.logo(aapl) shouldBeLogo png(applePng, LogoOrigin.DOWNLOADED)

            fixture.misses.lastMiss(eodhdLogos, aapl).shouldBeNull()
        }

        test("misses from all three sources: default logo as missing") {
            val fixture = LogoFixture(withEodhdProvider = true)
            fixture.noLogoAnywhere()
            fixture.eodhdProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, fixture.clock.now()))

            fixture.service.logo(aapl) shouldBeLogo svg(defaultSvg, LogoOrigin.DEFAULT_MISSING)

            fixture.provider.calls shouldContainExactly listOf(aapl)
            fixture.eodhdProvider.calls shouldContainExactly listOf(aapl)
            fixture.pngProvider.calls shouldContainExactly listOf(aapl)
        }
    }

    context("step 5: default logo") {
        test("all sources report misses: default SVG that is not temporary") {
            val fixture = LogoFixture()
            fixture.noLogoAnywhere()

            val logo = fixture.service.logo(aapl)

            logo shouldBeLogo svg(defaultSvg, LogoOrigin.DEFAULT_MISSING)
            logo.isMissing shouldBe true
            logo.isTemporary shouldBe false
        }

        test("all sources skipped by recent misses: default logo as missing") {
            val fixture = LogoFixture()
            fixture.misses.recordMiss(logoapi, aapl, fixture.clock.now())
            fixture.misses.recordMiss(finnhubLogos, aapl, fixture.clock.now())

            fixture.service.logo(aapl) shouldBeLogo svg(defaultSvg, LogoOrigin.DEFAULT_MISSING)

            fixture.provider.calls.shouldBeEmpty()
            fixture.pngProvider.calls.shouldBeEmpty()
        }

        test("a miss and a failing Finnhub call: temporary default logo") {
            val fixture = LogoFixture()
            fixture.provider.logos[aapl] = SourceResult.NoData(fixture.success)
            fixture.pngProvider.logos[aapl] = SourceResult.SourceError(SourceCallOutcome.timeout(fixture.clock.now()))

            val logo = fixture.service.logo(aapl)

            logo shouldBeLogo svg(defaultSvg, LogoOrigin.DEFAULT_UNAVAILABLE)
            logo.isTemporary shouldBe true
            fixture.misses.lastMiss(finnhubLogos, aapl).shouldBeNull()
        }

        test("a failing stored logo read with misses from every source: temporary default logo") {
            val fixture = LogoFixture()
            fixture.storage.failingReads = true
            fixture.noLogoAnywhere()

            fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_UNAVAILABLE
        }

        test("miss repository failures: the sources are asked and the default logo returned as missing") {
            val fixture = LogoFixture()
            fixture.misses.failing = true
            fixture.noLogoAnywhere()

            fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_MISSING

            fixture.provider.calls shouldContainExactly listOf(aapl)
            fixture.pngProvider.calls shouldContainExactly listOf(aapl)
        }

        test("only the Finnhub logo source enabled and missing: default logo as missing") {
            val fixture = LogoFixture(withProvider = false)
            fixture.pngProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, fixture.clock.now()))

            fixture.service.logo(aapl).origin shouldBe LogoOrigin.DEFAULT_MISSING
        }

        test("no logo source configured: temporary default logo") {
            val fixture = LogoFixture(withProvider = false, withPngProvider = false)

            fixture.service.logo(aapl) shouldBeLogo svg(defaultSvg, LogoOrigin.DEFAULT_UNAVAILABLE)
        }
    }

    test("a source must provide the format its storage keeps") {
        shouldThrow<IllegalArgumentException> {
            LogoTier(InMemoryLogoStorage(ImageFormat.SVG), FakeLogoProvider(finnhubLogos, ImageFormat.PNG))
        }
    }
})
