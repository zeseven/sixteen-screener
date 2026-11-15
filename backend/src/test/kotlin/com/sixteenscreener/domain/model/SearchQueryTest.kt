package com.sixteenscreener.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class SearchQueryTest : FunSpec({

    context("accepted lengths after trimming") {
        withData(
            nameFn = { "'$it'" },
            "a",
            " F ",
            "ab",
            "  ab  ",
            "abc",
            "  abc  ",
            "a".repeat(50),
            " ${"a".repeat(50)}\t",
            "a b",
        ) { raw ->
            SearchQuery.of(raw).value shouldBe raw.trim()
        }
    }

    context("rejected lengths after trimming") {
        withData(
            nameFn = { "'$it'" },
            "",
            "   ",
            "\t\n",
            "a".repeat(51),
            " ${"a".repeat(51)} ",
        ) { raw ->
            shouldThrow<InvalidInputException> { SearchQuery.of(raw) }
        }
    }

    context("ticker-only matching") {
        withData(nameFn = { "'$it' matches tickers only" }, "F", "gm", " GM ") { raw ->
            SearchQuery.of(raw).matchesTickersOnly shouldBe true
        }

        withData(nameFn = { "'$it' matches tickers and names" }, "abc", "apple", " ab c ") { raw ->
            SearchQuery.of(raw).matchesTickersOnly shouldBe false
        }
    }

    test("normalized form is lower case") {
        SearchQuery.of(" Apple ").normalized shouldBe "apple"
        SearchQuery.of("APPLE").normalized shouldBe SearchQuery.of("apple").normalized
    }
})
