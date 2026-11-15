package com.sixteenscreener.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class SymbolTest : FunSpec({

    context("accepts symbols matching the spec pattern") {
        withData(
            "AAPL",
            "A",
            "7203.T",
            "SAP.DE",
            "BRK.B",
            "BRK-B",
            "A".repeat(20),
            "1" + "-".repeat(19),
        ) { raw ->
            Symbol.of(raw).value shouldBe raw
        }
    }

    context("rejects symbols not matching the spec pattern") {
        withData(
            nameFn = { "'$it'" },
            "",
            "aapl",
            "Aapl",
            ".DE",
            "-A",
            "A".repeat(21),
            "SAP DE",
            "SAP_DE",
            "SAP/DE",
            " AAPL",
            "ÄPPL",
        ) { raw ->
            Symbol.parseOrNull(raw).shouldBeNull()
            shouldThrow<InvalidInputException> { Symbol.of(raw) }
        }
    }

    context("derives ticker and exchange code from the suffix") {
        withData(
            nameFn = { (raw, _, _) -> raw },
            Triple("AAPL", "AAPL", "US"),
            Triple("SAP.DE", "SAP", "DE"),
            Triple("SHEL.L", "SHEL", "L"),
            Triple("NESN.SW", "NESN", "SW"),
            Triple("7203.T", "7203", "T"),
            Triple("ASML.AS", "ASML", "AS"),
            Triple("BRK.B", "BRK.B", "US"),
            Triple("BRK-B", "BRK-B", "US"),
            Triple("FOO.XX", "FOO.XX", "US"),
            Triple("ABC.", "ABC.", "US"),
            Triple("A.B.DE", "A.B", "DE"),
            Triple("DE", "DE", "US"),
        ) { (raw, ticker, exchangeCode) ->
            val symbol = Symbol.of(raw)
            symbol.ticker shouldBe ticker
            symbol.exchangeCode shouldBe ExchangeCode(exchangeCode)
        }
    }
})
