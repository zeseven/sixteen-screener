package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class KnownExchangeCodesTest : FunSpec({
    context("Yahoo suffix equals the symbol suffix where Yahoo uses the same one") {
        withData(
            "AS", "BR", "PA", "LS", "IR", "MI", "MC", "DE", "F", "BE", "DU", "HM", "HA", "MU", "SG", "VI", "SW", "L", "IL",
            "CO", "ST", "HE", "OL", "IC", "WA", "PR", "BD", "AT", "TL", "RG", "VS", "TO", "V", "T", "HK", "TA", "JO", "KS",
            "AX", "SS", "SZ", "NS", "BO", "SA", "MX", "SI", "TW", "NZ", "IS", "SR",
        ) { code ->
            KnownExchangeCodes.yahooSuffixOf(ExchangeCode(code)) shouldBe code
        }
    }

    test("US symbols have an empty Yahoo suffix") {
        KnownExchangeCodes.yahooSuffixOf(ExchangeCode.US) shouldBe ""
    }

    context("exchanges without a known Yahoo suffix have none") {
        withData("ME", "PM", "DB", "KW", "BH", "CS", "NL", "BC", "CR", "QA", "CA", "VN") { code ->
            KnownExchangeCodes.yahooSuffixOf(ExchangeCode(code)) shouldBe null
        }
    }

    test("an unknown exchange code has no Yahoo suffix") {
        KnownExchangeCodes.yahooSuffixOf(ExchangeCode("XX")) shouldBe null
    }

    test("further exchanges whose Yahoo suffix equals the symbol suffix") {
        listOf("KQ", "TWO", "BK", "JK", "KL", "NE", "CN", "BA", "SN").forEach { code ->
            KnownExchangeCodes.isKnown(code) shouldBe true
            KnownExchangeCodes.yahooSuffixOf(ExchangeCode(code)) shouldBe code
        }
    }
})
