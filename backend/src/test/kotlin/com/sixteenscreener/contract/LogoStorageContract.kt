package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoStorage
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

fun logoStorageContract(newStorage: suspend () -> LogoStorage) = funSpec {
    val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><rect width="1" height="1"/></svg>""".toByteArray()

    test("no logo stored") {
        newStorage().find(Symbol.of("AAPL")).shouldBeNull()
    }

    test("stored logo is found by its symbol only") {
        val storage = newStorage()

        storage.save(Symbol.of("SAP.DE"), svg)

        storage.find(Symbol.of("SAP.DE")) shouldBe svg
        storage.find(Symbol.of("SAP")).shouldBeNull()
    }

    test("saving replaces the stored logo") {
        val storage = newStorage()
        val replacement = "<svg/>".toByteArray()
        storage.save(Symbol.of("BRK.B"), svg)

        storage.save(Symbol.of("BRK.B"), replacement)

        storage.find(Symbol.of("BRK.B")) shouldBe replacement
    }
}
