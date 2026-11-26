package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.fakes.InMemoryRequestCounterRepository
import com.sixteenscreener.fakes.MutableClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class RateLimiterTest : FunSpec({
    val finnhub = SourceId.of("finnhub")
    val logoapi = SourceId.of("logoapi")
    val unlimited = SourceId.of("unlimited")
    val yahoo = SourceId.of("yahoo")
    val dailyCounter = RequestLimit(LimitInterval.DAY, null)
    val perMinute = RequestLimit(LimitInterval.MINUTE, 60)
    val perDay = RequestLimit(LimitInterval.DAY, 100)

    class Fixture {
        val clock = MutableClock(Instant.parse("2026-11-14T12:00:30Z"))
        val counters = InMemoryRequestCounterRepository()
        val limiter = RateLimiter(
            mapOf(
                finnhub to listOf(perMinute, perDay),
                logoapi to listOf(perDay),
                unlimited to emptyList(),
                yahoo to listOf(dailyCounter),
            ),
            counters,
            clock,
            totalCountedSources = setOf(unlimited, logoapi),
        )
    }

    test("grants every requested call within the limits") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 10) shouldBe 10
        fixture.limiter.usage(finnhub).map { it.used } shouldContainExactly listOf(10, 10)
    }

    test("limit exactly reached grants nothing more") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 60) shouldBe 60
        fixture.limiter.tryAcquire(finnhub) shouldBe 0
    }

    test("partial grant when fewer calls remain than requested") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 55)
        fixture.limiter.tryAcquire(finnhub, 10) shouldBe 5
    }

    test("the strictest of several intervals wins") {
        val fixture = Fixture()
        fixture.counters.setCount(finnhub, LimitInterval.DAY, fixture.clock.now(), 97)

        fixture.limiter.tryAcquire(finnhub, 20) shouldBe 3
        fixture.limiter.usage(finnhub).map { it.used } shouldContainExactly listOf(3, 100)
    }

    test("window rollover restores the budget of the rolled-over interval only") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 60)

        fixture.clock.advanceBy(Duration.ofSeconds(30))

        fixture.limiter.tryAcquire(finnhub, 50) shouldBe 40
        fixture.limiter.usage(finnhub).map { it.used } shouldContainExactly listOf(40, 100)
    }

    test("a source without limits gets every call") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(unlimited, 500) shouldBe 500
        fixture.limiter.usage(unlimited).shouldBeEmpty()
    }

    test("a source with a counter without a limit gets every call and counts it per day") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(yahoo, 500) shouldBe 500
        fixture.limiter.tryAcquire(yahoo, 2) shouldBe 2

        fixture.limiter.usage(yahoo).single().used shouldBe 502

        fixture.clock.advanceBy(Duration.ofDays(1))
        fixture.limiter.usage(yahoo).single().used shouldBe 0
    }

    test("saturation keeps a counter without a limit as it is") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(yahoo, 2)

        fixture.limiter.saturate(yahoo)

        fixture.limiter.usage(yahoo).single().used shouldBe 2
        fixture.limiter.tryAcquire(yahoo) shouldBe 1
    }

    test("requesting zero calls grants zero") {
        Fixture().limiter.tryAcquire(finnhub, 0) shouldBe 0
    }

    test("negative requests and unknown sources are rejected") {
        val fixture = Fixture()
        shouldThrow<IllegalArgumentException> { fixture.limiter.tryAcquire(finnhub, -1) }
        shouldThrow<IllegalArgumentException> { fixture.limiter.tryAcquire(SourceId.of("unknown")) }
    }

    test("saturation fills every current window") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 2)

        fixture.limiter.saturate(finnhub)

        fixture.limiter.usage(finnhub).map { it.used } shouldContainExactly listOf(60, 100)
        fixture.limiter.tryAcquire(finnhub) shouldBe 0
    }

    test("saturation leaves the next window unaffected") {
        val fixture = Fixture()
        fixture.limiter.saturate(logoapi)

        fixture.clock.advanceBy(Duration.ofDays(1))

        fixture.limiter.tryAcquire(logoapi, 3) shouldBe 3
    }

    test("saturation never lowers a count above the limit") {
        val fixture = Fixture()
        fixture.counters.setCount(logoapi, LimitInterval.DAY, fixture.clock.now(), 150)

        fixture.limiter.saturate(logoapi)

        fixture.limiter.usage(logoapi).single().used shouldBe 150
    }

    test("saturation of a source without limits does nothing") {
        val fixture = Fixture()
        fixture.limiter.saturate(unlimited)
        fixture.limiter.tryAcquire(unlimited, 5) shouldBe 5
    }

    test("usage reports zero and the window end for each limit") {
        val usage = Fixture().limiter.usage(finnhub)

        usage.map { it.used } shouldContainExactly listOf(0, 0)
        usage.map { it.resetsAt } shouldContainExactly listOf(
            Instant.parse("2026-11-14T12:01:00Z"),
            Instant.parse("2026-11-15T00:00:00Z"),
        )
    }

    test("granted calls of a source with a total counter are counted and never reset") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(unlimited, 3)
        fixture.clock.advanceBy(Duration.ofDays(400))
        fixture.limiter.tryAcquire(unlimited)

        fixture.limiter.totalCalls(listOf(unlimited)) shouldBe mapOf(unlimited to 4L)
    }

    test("only granted calls are counted in the total") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(logoapi, 120) shouldBe 100
        fixture.limiter.tryAcquire(logoapi) shouldBe 0
        fixture.limiter.tryAcquire(logoapi, 0) shouldBe 0

        fixture.limiter.totalCalls(listOf(logoapi)) shouldBe mapOf(logoapi to 100L)
    }

    test("sources without a total counter are neither counted nor reported") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 5)

        fixture.counters.totals(listOf(finnhub)) shouldBe mapOf(finnhub to 0L)
        fixture.limiter.totalCalls(listOf(finnhub, unlimited)) shouldBe mapOf(unlimited to 0L)
        fixture.limiter.totalCalls(listOf(finnhub)) shouldBe emptyMap()
    }

    test("a failing total counter doesn't block the call") {
        val fixture = Fixture()
        fixture.counters.failTotals = true

        fixture.limiter.tryAcquire(unlimited, 2) shouldBe 2
    }
})
