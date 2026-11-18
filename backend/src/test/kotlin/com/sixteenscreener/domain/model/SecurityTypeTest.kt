package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class SecurityTypeTest : FunSpec({

    context("common stocks, REITs and unknown types are stocks") {
        withData(
            nameFn = { "'$it'" },
            "Common Stock",
            "common stock",
            " COMMON STOCK ",
            "REIT",
            "reit",
            "",
            "   ",
        ) { type ->
            SecurityType.isStock(type) shouldBe true
        }
    }

    test("a missing type is a stock") {
        SecurityType.isStock(null) shouldBe true
    }

    context("other security types are not stocks") {
        withData(
            nameFn = { "'$it'" },
            "ADR",
            "GDR",
            "Preference",
            "NY Reg Shrs",
            "ETP",
            "Common Stock ADR",
        ) { type ->
            SecurityType.isStock(type) shouldBe false
        }
    }

    test("identity derives the stock flag from its type") {
        CompanyIdentity(Symbol.of("TM"), "NYSE", "Toyota", Market.US, "ADR").stock shouldBe false
        CompanyIdentity(Symbol.of("AAPL"), "NASDAQ", "Apple", Market.US, null).stock shouldBe true
    }
})
