package com.sixteenscreener.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ValueTypesTest : FunSpec({

    context("SourceId") {
        withData("finnhub", "sec-edgar", "logoapi", "a", "a".repeat(30)) { raw ->
            SourceId.of(raw).value shouldBe raw
        }
        withData(nameFn = { "rejects '$it'" }, "", "Finnhub", "sec_edgar", "a".repeat(31), "fmp ") { raw ->
            SourceId.parseOrNull(raw).shouldBeNull()
        }
    }

    context("ProfileId") {
        test("parses a canonical UUID") {
            val raw = "3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10"
            ProfileId.of(raw).toString() shouldBe raw
        }
        withData(nameFn = { "rejects '$it'" }, "", "not-a-uuid", "3f2b8c1e-6d4a-4f7e-9b1a", "1-1-1-1-1") { raw ->
            ProfileId.parseOrNull(raw).shouldBeNull()
            shouldThrow<InvalidInputException> { ProfileId.of(raw) }
        }
        test("random IDs differ") {
            ProfileId.random() shouldNotBe ProfileId.random()
        }
    }

    context("ProfileName") {
        test("trims surrounding whitespace") {
            ProfileName.of("  Alice \t").value shouldBe "Alice"
        }
        test("keeps any characters") {
            ProfileName.of("a/b ?#%").value shouldBe "a/b ?#%"
        }
        test("accepts 40 characters after trimming") {
            ProfileName.of("  " + "x".repeat(40) + "  ").value.length shouldBe 40
        }
        test("rejects 41 characters after trimming") {
            shouldThrow<InvalidInputException> { ProfileName.of("x".repeat(41)) }
        }
        test("counts code points, not UTF-16 units") {
            ProfileName.of("😀".repeat(40)).shouldNotBeNull()
        }
        withData(nameFn = { "rejects blank '${it.replace("\n", "\\n")}'" }, "", "   ", "\t\n") { raw ->
            shouldThrow<InvalidInputException> { ProfileName.of(raw) }
        }
        test("normalizes case and Unicode composition") {
            val composed = ProfileName.of("JöRG")
            val decomposed = ProfileName.of("jörg")
            composed.normalized shouldBe decomposed.normalized
        }
    }
})
