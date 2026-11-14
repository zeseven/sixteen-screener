package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.fakes.InMemoryRequestCounterRepository
import com.sixteenscreener.fakes.InMemorySourceCallOutcomeRepository
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class SourceCallGuardTest : FunSpec({
    val finnhub = SourceId.of("finnhub")
    val perMinute = RequestLimit(LimitInterval.MINUTE, 3)

    class Fixture {
        val clock = MutableClock()
        val outcomesRepository = InMemorySourceCallOutcomeRepository()
        val outcomes = SourceCallOutcomes(outcomesRepository)
        val limiter = RateLimiter(mapOf(finnhub to listOf(perMinute)), InMemoryRequestCounterRepository(), clock)
        val guard = SourceCallGuard(limiter)
    }

    test("a granted call is made, counted and its outcome recorded") {
        val fixture = Fixture()
        val success = SourceResult.Success(42, SourceCallOutcome.success(fixture.clock.now()))

        val result = fixture.outcomes.recording { recorder -> fixture.guard.single(finnhub, recorder) { success } }

        result shouldBe success
        fixture.limiter.usage(finnhub).single().used shouldBe 1
        fixture.outcomesRepository.writes shouldBe listOf(finnhub to success.outcome)
    }

    test("no call is made when the limit is reached and nothing is recorded") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 3)
        var called = false

        val result = fixture.outcomes.recording { recorder ->
            fixture.guard.single(finnhub, recorder) {
                called = true
                SourceResult.NoData(SourceCallOutcome.success(fixture.clock.now()))
            }
        }

        result shouldBe SourceResult.LimitReached(outcome = null)
        called shouldBe false
        fixture.outcomesRepository.writes.shouldBeEmpty()
    }

    test("an upstream 429 saturates the counters of the source") {
        val fixture = Fixture()
        val rejected = SourceResult.LimitReached(SourceCallOutcome.httpError(429, fixture.clock.now()))

        fixture.outcomes.recording { recorder -> fixture.guard.single(finnhub, recorder) { rejected } }

        fixture.limiter.usage(finnhub).single().used shouldBe 3
        fixture.outcomesRepository.writes shouldBe listOf(finnhub to rejected.outcome)
    }

    test("a completed call of a bulk reservation is recorded without saturating on other errors") {
        val fixture = Fixture()
        fixture.limiter.tryAcquire(finnhub, 1)
        val failed = SourceResult.SourceError(SourceCallOutcome.httpError(503, fixture.clock.now()))

        fixture.outcomes.recording { recorder -> fixture.guard.completed(finnhub, recorder, failed) }

        fixture.limiter.usage(finnhub).single().used shouldBe 1
        fixture.outcomesRepository.writes shouldBe listOf(finnhub to failed.outcome)
    }
})
