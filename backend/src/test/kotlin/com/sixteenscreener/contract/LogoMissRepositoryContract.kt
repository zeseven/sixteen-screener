package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoMissRepository
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

fun logoMissRepositoryContract(newRepository: suspend () -> LogoMissRepository) = funSpec {
    val at = Instant.parse("2026-11-14T12:00:00Z")

    test("no miss recorded") {
        newRepository().lastMiss(Symbol.of("AAPL")).shouldBeNull()
    }

    test("the latest recorded miss is returned per symbol") {
        val repository = newRepository()
        repository.recordMiss(Symbol.of("SAP.DE"), at)
        repository.recordMiss(Symbol.of("SAP.DE"), at.plusSeconds(60))

        repository.lastMiss(Symbol.of("SAP.DE")) shouldBe at.plusSeconds(60)
        repository.lastMiss(Symbol.of("SAP")).shouldBeNull()
    }
}
