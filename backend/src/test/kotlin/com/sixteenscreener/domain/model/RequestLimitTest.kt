package com.sixteenscreener.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RequestLimitTest : FunSpec({
    test("remaining calls of a limit never go below zero") {
        val limit = RequestLimit(LimitInterval.DAY, 20)

        limit.isUnlimited shouldBe false
        limit.remaining(0) shouldBe 20
        limit.remaining(15) shouldBe 5
        limit.remaining(25) shouldBe 0
    }

    test("a counter without a limit has unlimited remaining calls") {
        val counter = RequestLimit(LimitInterval.DAY, null)

        counter.isUnlimited shouldBe true
        counter.remaining(1_000_000) shouldBe Long.MAX_VALUE
    }

    test("a negative limit is rejected") {
        shouldThrow<IllegalArgumentException> { RequestLimit(LimitInterval.DAY, -1) }
    }
})
