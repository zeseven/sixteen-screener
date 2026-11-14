package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.fakes.InMemorySourceCallOutcomeRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

class SourceCallRecorderTest : FunSpec({
    val finnhub = SourceId.of("finnhub")
    val secEdgar = SourceId.of("sec-edgar")
    val t0 = Instant.parse("2026-11-14T12:00:00Z")

    test("one write per source with the latest outcome of the request") {
        val repository = InMemorySourceCallOutcomeRepository()

        SourceCallOutcomes(repository).recording { recorder ->
            recorder.record(finnhub, SourceCallOutcome.success(t0))
            recorder.record(finnhub, SourceCallOutcome.httpError(500, t0.plusSeconds(2)))
            recorder.record(finnhub, SourceCallOutcome.timeout(t0.plusSeconds(1)))
            recorder.record(secEdgar, SourceCallOutcome.success(t0))
        }

        repository.writes shouldContainExactlyInAnyOrder listOf(
            finnhub to SourceCallOutcome.httpError(500, t0.plusSeconds(2)),
            secEdgar to SourceCallOutcome.success(t0),
        )
    }

    test("of outcomes at the same instant the last recorded wins") {
        val repository = InMemorySourceCallOutcomeRepository()

        SourceCallOutcomes(repository).recording { recorder ->
            recorder.record(finnhub, SourceCallOutcome.success(t0))
            recorder.record(finnhub, SourceCallOutcome.appError(t0))
        }

        repository.writes shouldBe listOf(finnhub to SourceCallOutcome.appError(t0))
    }

    test("concurrent calls of one request still give one write") {
        val repository = InMemorySourceCallOutcomeRepository()

        SourceCallOutcomes(repository).recording { recorder ->
            coroutineScope {
                (1..50).map { i ->
                    async { recorder.record(finnhub, SourceCallOutcome.success(t0.plusSeconds(i.toLong()))) }
                }.awaitAll()
            }
        }

        repository.writes shouldBe listOf(finnhub to SourceCallOutcome.success(t0.plusSeconds(50)))
    }

    test("results without a call are not recorded") {
        val repository = InMemorySourceCallOutcomeRepository()

        SourceCallOutcomes(repository).recording { recorder ->
            recorder.record(finnhub, SourceResult.LimitReached(outcome = null))
            recorder.record(finnhub, SourceResult.NotSupported(outcome = null))
        }

        repository.writes.shouldBeEmpty()
    }

    test("results of calls record their outcome") {
        val repository = InMemorySourceCallOutcomeRepository()

        SourceCallOutcomes(repository).recording { recorder ->
            recorder.record(finnhub, SourceResult.LimitReached(SourceCallOutcome.httpError(429, t0)))
        }

        repository.writes shouldBe listOf(finnhub to SourceCallOutcome.httpError(429, t0))
    }

    test("a failed outcome write is swallowed and the result of the request returned") {
        val repository = InMemorySourceCallOutcomeRepository().apply { failWrites = true }

        val result = SourceCallOutcomes(repository).recording { recorder ->
            recorder.record(finnhub, SourceCallOutcome.success(t0))
            recorder.record(secEdgar, SourceCallOutcome.success(t0))
            "response"
        }

        result shouldBe "response"
    }

    test("outcomes are written even when the request fails") {
        val repository = InMemorySourceCallOutcomeRepository()

        shouldThrow<IllegalStateException> {
            SourceCallOutcomes(repository).recording { recorder ->
                recorder.record(finnhub, SourceCallOutcome.timeout(t0))
                error("request failed")
            }
        }

        repository.writes shouldBe listOf(finnhub to SourceCallOutcome.timeout(t0))
    }

    test("last calls are read from the repository") {
        val repository = InMemorySourceCallOutcomeRepository()
        val outcomes = SourceCallOutcomes(repository)
        outcomes.recording { it.record(finnhub, SourceCallOutcome.success(t0)) }

        outcomes.lastCalls(listOf(finnhub, secEdgar)) shouldContainExactly mapOf(finnhub to SourceCallOutcome.success(t0))
    }
})
