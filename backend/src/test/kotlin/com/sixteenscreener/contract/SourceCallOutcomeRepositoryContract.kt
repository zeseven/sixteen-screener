package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.SourceCallOutcomeRepository
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import java.time.Instant

fun sourceCallOutcomeRepositoryContract(newRepository: suspend () -> SourceCallOutcomeRepository) = funSpec {
    val finnhub = SourceId.of("finnhub")
    val secEdgar = SourceId.of("sec-edgar")
    val logoapi = SourceId.of("logoapi")
    val at = Instant.parse("2026-11-14T12:00:00Z")

    test("sources never called are absent") {
        newRepository().findAll(listOf(finnhub, secEdgar)).shouldBeEmpty()
    }

    test("every kind of outcome round-trips") {
        val repository = newRepository()
        val outcomes = mapOf(
            finnhub to SourceCallOutcome.success(at),
            secEdgar to SourceCallOutcome.httpError(403, at),
            logoapi to SourceCallOutcome.timeout(at),
            SourceId.of("tiingo") to SourceCallOutcome.appError(at),
        )
        outcomes.forEach { (source, outcome) -> repository.record(source, outcome) }

        repository.findAll(outcomes.keys) shouldContainExactly outcomes
    }

    test("the last recorded outcome replaces the previous one") {
        val repository = newRepository()
        repository.record(finnhub, SourceCallOutcome.httpError(500, at))

        repository.record(finnhub, SourceCallOutcome.success(at.plusSeconds(1)))

        repository.findAll(listOf(finnhub, secEdgar)) shouldContainExactly mapOf(finnhub to SourceCallOutcome.success(at.plusSeconds(1)))
    }

    test("findAll of no sources is empty") {
        val repository = newRepository()
        repository.record(finnhub, SourceCallOutcome.success(at))
        repository.findAll(emptyList()).shouldBeEmpty()
    }
}
