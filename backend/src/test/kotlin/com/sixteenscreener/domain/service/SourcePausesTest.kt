package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.fakes.InMemorySourceCallOutcomeRepository
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

private val yahoo = SourceId.of("yahoo")
private val finnhub = SourceId.of("finnhub")
private val rateLimitedAt = Instant.parse("2026-11-27T10:00:00Z")
private val pauseEnd = rateLimitedAt.plus(Duration.ofMinutes(15))

private class PausesFixture(now: Instant = rateLimitedAt.plusSeconds(60)) {
    val clock = MutableClock(now)
    val repository = InMemorySourceCallOutcomeRepository()
    val pauses = SourcePauses(mapOf(yahoo to Duration.ofMinutes(15)), SourceCallOutcomes(repository), clock)

    suspend fun lastCall(source: SourceId, outcome: SourceCallOutcome) = repository.record(source, outcome)
}

class SourcePausesTest : FunSpec({
    val rateLimited = SourceCallOutcome.httpError(429, rateLimitedAt)

    test("a 429 younger than the pause pauses the source until the outcome time plus the pause") {
        val f = PausesFixture()

        f.pauses.pausedUntil(yahoo, rateLimited) shouldBe pauseEnd
    }

    test("the source is still paused just before the end of the pause") {
        val f = PausesFixture(now = pauseEnd.minusMillis(1))

        f.pauses.pausedUntil(yahoo, rateLimited) shouldBe pauseEnd
    }

    context("the source is no longer paused at or after the end of the pause") {
        withData(nameFn = { "now = $it" }, pauseEnd, pauseEnd.plusMillis(1), pauseEnd.plus(Duration.ofDays(1))) { now ->
            PausesFixture(now).pauses.pausedUntil(yahoo, rateLimited).shouldBeNull()
        }
    }

    context("other outcomes don't pause the source") {
        withData(
            nameFn = { "${it.result} ${it.httpStatus ?: ""}".trim() },
            SourceCallOutcome.httpError(500, rateLimitedAt),
            SourceCallOutcome.httpError(403, rateLimitedAt),
            SourceCallOutcome.httpError(404, rateLimitedAt),
            SourceCallOutcome.httpError(503, rateLimitedAt),
            SourceCallOutcome.success(rateLimitedAt),
            SourceCallOutcome.timeout(rateLimitedAt),
            SourceCallOutcome.appError(rateLimitedAt),
        ) { outcome ->
            PausesFixture().pauses.pausedUntil(yahoo, outcome).shouldBeNull()
        }
    }

    test("a source never called is not paused") {
        PausesFixture().pauses.pausedUntil(yahoo, lastCall = null).shouldBeNull()
    }

    test("a source without a pause is not paused after a 429") {
        val f = PausesFixture()

        f.pauses.hasPause(finnhub) shouldBe false
        f.pauses.pausedUntil(finnhub, rateLimited).shouldBeNull()
    }

    test("isPaused reads the stored last call of a source with a pause once") {
        val f = PausesFixture()
        f.lastCall(yahoo, rateLimited)

        f.pauses.isPaused(yahoo) shouldBe true
        f.repository.reads shouldBe 1
    }

    test("isPaused is false once the stored 429 is older than the pause") {
        val f = PausesFixture(now = pauseEnd)
        f.lastCall(yahoo, rateLimited)

        f.pauses.isPaused(yahoo) shouldBe false
    }

    test("isPaused is false when a newer outcome replaced the 429") {
        val f = PausesFixture()
        f.lastCall(yahoo, rateLimited)
        f.lastCall(yahoo, SourceCallOutcome.success(rateLimitedAt.plusSeconds(30)))

        f.pauses.isPaused(yahoo) shouldBe false
    }

    test("isPaused of a source without a pause reads nothing") {
        val f = PausesFixture()
        f.lastCall(finnhub, rateLimited)

        f.pauses.isPaused(finnhub) shouldBe false
        f.repository.reads shouldBe 0
    }

    test("a failed read of the last call doesn't pause the source") {
        val f = PausesFixture()
        f.lastCall(yahoo, rateLimited)
        f.repository.failReads = true

        f.pauses.isPaused(yahoo) shouldBe false
    }
})
