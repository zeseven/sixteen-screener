package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.ExchangeDirectory
import com.sixteenscreener.port.outbound.ExchangeDirectoryStore
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

fun exchangeDirectoryStoreContract(newStore: suspend () -> ExchangeDirectoryStore) = funSpec {
    val finnhub = SourceId.of("finnhub")
    val other = SourceId.of("other")
    val de = ExchangeCode("DE")
    val at = Instant.parse("2026-11-14T12:00:00Z")
    val sap = ListedSymbol(Symbol.of("SAP.DE"), "SAP SE", "XETR", "EUR", "Common Stock")
    val siemens = ListedSymbol(Symbol.of("SIE.DE"), "SIEMENS AG-REG", null, "EUR", null)

    test("no directory stored") {
        newStore().load(finnhub, de).shouldBeNull()
    }

    test("stored directory is loaded unchanged") {
        val store = newStore()
        val directory = ExchangeDirectory(finnhub, de, listOf(sap, siemens), at)

        store.save(directory)

        store.load(finnhub, de) shouldBe directory
    }

    test("saving replaces the directory of the same source and exchange only") {
        val store = newStore()
        val otherSource = ExchangeDirectory(other, de, listOf(sap), at)
        store.save(ExchangeDirectory(finnhub, de, listOf(sap), at))
        store.save(otherSource)
        val replacement = ExchangeDirectory(finnhub, de, listOf(siemens), at.plusSeconds(86_400))

        store.save(replacement)

        store.load(finnhub, de) shouldBe replacement
        store.load(other, de) shouldBe otherSource
        store.load(finnhub, ExchangeCode.US).shouldBeNull()
    }

    test("empty directory is stored") {
        val store = newStore()
        val directory = ExchangeDirectory(finnhub, ExchangeCode("ME"), emptyList(), at)

        store.save(directory)

        store.load(finnhub, ExchangeCode("ME")) shouldBe directory
    }
}
