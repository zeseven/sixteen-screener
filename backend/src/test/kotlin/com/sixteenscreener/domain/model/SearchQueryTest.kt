package com.sixteenscreener.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class SearchQueryTest : FunSpec({

    context("accepted lengths after trimming") {
        withData(
            nameFn = { "'$it'" },
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
            "ab",
            "  ab  ",
            "a".repeat(51),
        ) { raw ->
            shouldThrow<InvalidInputException> { SearchQuery.of(raw) }
        }
    }

    test("normalized form is lower case") {
        SearchQuery.of(" Apple ").normalized shouldBe "apple"
        SearchQuery.of("APPLE").normalized shouldBe SearchQuery.of("apple").normalized
    }
})
