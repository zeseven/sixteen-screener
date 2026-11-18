package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.Symbol
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class CompanyIdentityResolverTest : FunSpec({
    val resolver = CompanyIdentityResolver(MarketResolver())

    test("listing gives exchange name, market and name") {
        resolver.identify(Symbol.of("SAP.DE"), listing("SAP.DE", mic = "XETR", name = "SAP SE"), fallbackName = "SAP") shouldBe
            CompanyIdentity(Symbol.of("SAP.DE"), "XETRA", "SAP SE", Market.EU)
    }

    test("listing MIC wins over the static exchange table of the exchange code") {
        resolver.identify(Symbol.of("AAPL"), listing("AAPL", mic = "XNAS")).exchange shouldBe "NASDAQ"
    }

    test("listing on an exchange outside the US and EU markets has no market") {
        resolver.identify(Symbol.of("7203.T"), listing("7203.T", mic = "XTKS")).market shouldBe null
    }

    test("listing with an unknown MIC keeps the MIC as exchange name and has no market") {
        val identity = resolver.identify(Symbol.of("SAP.DE"), listing("SAP.DE", mic = "ZZZZ", name = "SAP SE"))

        identity.exchange shouldBe "ZZZZ"
        identity.market shouldBe null
    }

    test("listing without MIC takes exchange and market from the static exchange table") {
        resolver.identify(Symbol.of("SHEL.L"), listing("SHEL.L", mic = null, name = "SHELL PLC")) shouldBe
            CompanyIdentity(Symbol.of("SHEL.L"), "LSE", "SHELL PLC", Market.EU)
    }

    data class Case(val symbol: String, val exchange: String, val market: Market?)

    context("without listing, exchange and market come from the static exchange table") {
        withData(
            nameFn = { "${it.symbol} -> ${it.exchange} (${it.market})" },
            Case("AAPL", "US", Market.US),
            Case("AD.AS", "AMS", Market.EU),
            Case("SAP.DE", "XETRA", Market.EU),
            Case("MC.PA", "Paris", Market.EU),
            Case("ENEL.MI", "Milan", Market.EU),
            Case("SHEL.L", "LSE", Market.EU),
            Case("NESN.SW", "SIX", Market.EU),
            Case("EQNR.OL", "Oslo", Market.EU),
            Case("7203.T", "TSE", null),
            Case("SHOP.TO", "TSX", null),
            Case("0700.HK", "HKEX", null),
        ) { case ->
            val identity = resolver.identify(Symbol.of(case.symbol), listing = null)
            identity.exchange shouldBe case.exchange
            identity.market shouldBe case.market
        }
    }

    test("without listing the fallback name is used") {
        resolver.identify(Symbol.of("AAPL"), listing = null, fallbackName = "Apple Inc") shouldBe
            CompanyIdentity(Symbol.of("AAPL"), "US", "Apple Inc", Market.US)
    }

    test("without listing and fallback name the ticker is the name") {
        resolver.identify(Symbol.of("SAP.DE"), listing = null).name shouldBe "SAP"
    }
})
