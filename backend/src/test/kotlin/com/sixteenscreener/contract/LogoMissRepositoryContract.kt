package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoMissRepository
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

fun logoMissRepositoryContract(newRepository: suspend () -> LogoMissRepository) = funSpec {
    val at = Instant.parse("2026-11-14T12:00:00Z")
    val logoapi = SourceId.of("logoapi")
    val finnhubLogos = SourceId.of("finnhub-logos")

    test("no miss recorded") {
        newRepository().lastMiss(logoapi, Symbol.of("AAPL")).shouldBeNull()
    }

    test("the latest recorded miss is returned per symbol") {
        val repository = newRepository()
        repository.recordMiss(logoapi, Symbol.of("SAP.DE"), at)
        repository.recordMiss(logoapi, Symbol.of("SAP.DE"), at.plusSeconds(60))

        repository.lastMiss(logoapi, Symbol.of("SAP.DE")) shouldBe at.plusSeconds(60)
        repository.lastMiss(logoapi, Symbol.of("SAP")).shouldBeNull()
    }

    test("misses are kept apart per logo source") {
        val repository = newRepository()
        repository.recordMiss(logoapi, Symbol.of("AAPL"), at)
        repository.recordMiss(finnhubLogos, Symbol.of("MSFT"), at.plusSeconds(60))

        repository.lastMiss(logoapi, Symbol.of("AAPL")) shouldBe at
        repository.lastMiss(finnhubLogos, Symbol.of("AAPL")).shouldBeNull()
        repository.lastMiss(finnhubLogos, Symbol.of("MSFT")) shouldBe at.plusSeconds(60)
        repository.lastMiss(logoapi, Symbol.of("MSFT")).shouldBeNull()
    }
}
