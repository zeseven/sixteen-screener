package com.sixteenscreener.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private val finnhub = SourceId.of("finnhub")
private val eodhd = SourceId.of("eodhd")

class SourceRoleTest : FunSpec({

    context("role of a company") {
        withData(
            nameFn = { "${it.first} is ${it.second}" },
            "AAPL" to SourceRole.US,
            "BRK.B" to SourceRole.US,
            "SAP.DE" to SourceRole.INTERNATIONAL,
            "SHEL.L" to SourceRole.INTERNATIONAL,
            "7203.T" to SourceRole.INTERNATIONAL,
            "SHOP.TO" to SourceRole.INTERNATIONAL,
        ) { (symbol, role) ->
            SourceRole.of(Symbol.of(symbol)) shouldBe role
        }
    }

    context("default sources") {
        val defaults = DefaultSources(search = finnhub, us = finnhub, international = eodhd)

        test("the default source of each role") {
            defaults.of(SourceRole.SEARCH) shouldBe finnhub
            defaults.of(SourceRole.US) shouldBe finnhub
            defaults.of(SourceRole.INTERNATIONAL) shouldBe eodhd
        }

        test("roles a source is the default of, in role order") {
            defaults.rolesOf(finnhub) shouldContainExactly listOf(SourceRole.SEARCH, SourceRole.US)
            defaults.rolesOf(eodhd) shouldContainExactly listOf(SourceRole.INTERNATIONAL)
            defaults.rolesOf(SourceId.of("sec-edgar")).shouldBeEmpty()
        }
    }
})
