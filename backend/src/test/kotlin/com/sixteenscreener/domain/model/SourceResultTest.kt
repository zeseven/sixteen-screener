package com.sixteenscreener.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.time.Instant

class SourceResultTest : FunSpec({
    val at = Instant.parse("2026-11-14T12:00:00Z")

    context("data status of each result") {
        withData(
            nameFn = { (result, status) -> "${result::class.simpleName} -> $status" },
            SourceResult.Success(1, SourceCallOutcome.success(at)) to DataStatus.FRESH,
            SourceResult.NoData(SourceCallOutcome.success(at)) to DataStatus.NO_DATA,
            SourceResult.NotSupported(null) to DataStatus.NOT_SUPPORTED,
            SourceResult.LimitReached(null) to DataStatus.LIMIT_REACHED,
            SourceResult.SourceError(SourceCallOutcome.timeout(at)) to DataStatus.SOURCE_ERROR,
        ) { (result, status) ->
            result.dataStatus shouldBe status
        }
    }

    test("limit reached distinguishes an upstream 429 from our own counters") {
        SourceResult.LimitReached(SourceCallOutcome.httpError(429, at)).rejectedBySource shouldBe true
        SourceResult.LimitReached(null).rejectedBySource shouldBe false
        SourceCallOutcome.httpError(429, at).isRateLimited shouldBe true
        SourceCallOutcome.httpError(500, at).isRateLimited shouldBe false
    }

    test("map transforms success data and keeps the outcome") {
        val outcome = SourceCallOutcome.success(at)
        SourceResult.Success(2, outcome).map { it * 10 } shouldBe SourceResult.Success(20, outcome)
    }

    test("map keeps failures unchanged") {
        val failure: SourceResult<Int> = SourceResult.SourceError(SourceCallOutcome.appError(at))
        failure.map { it * 10 } shouldBeSameInstanceAs failure
    }

    test("httpStatus is required for HTTP_ERROR only") {
        shouldThrow<IllegalArgumentException> { SourceCallOutcome(SourceCallResult.HTTP_ERROR, null, at) }
        shouldThrow<IllegalArgumentException> { SourceCallOutcome(SourceCallResult.SUCCESS, 200, at) }
    }
})
