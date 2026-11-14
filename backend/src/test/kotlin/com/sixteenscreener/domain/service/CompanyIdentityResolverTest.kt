package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyIdentity
import com.sixteenscreener.domain.model.Market
import com.sixteenscreener.domain.model.Symbol
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CompanyIdentityResolverTest : FunSpec({
    val resolver = CompanyIdentityResolver(MarketResolver())

    test("listing gives exchange name, market and name") {
        resolver.identify(Symbol.of("SAP.DE"), listing("SAP.DE", mic = "XETR", name = "SAP SE"), fallbackName = "SAP") shouldBe
            CompanyIdentity(Symbol.of("SAP.DE"), "XETRA", "SAP SE", Market.EU)
    }

    test("listing on an exchange outside the US and EU markets has no market") {
        resolver.identify(Symbol.of("7203.T"), listing("7203.T", mic = "XTKS")).market shouldBe null
    }

    test("listing without MIC falls back to the exchange code") {
        val identity = resolver.identify(Symbol.of("SHEL.L"), listing("SHEL.L", mic = null, name = "SHELL PLC"))

        identity.exchange shouldBe "L"
        identity.market shouldBe null
        identity.name shouldBe "SHELL PLC"
    }

    test("without listing the fallback name and the exchange code are used") {
        resolver.identify(Symbol.of("AAPL"), listing = null, fallbackName = "Apple Inc") shouldBe
            CompanyIdentity(Symbol.of("AAPL"), "US", "Apple Inc", market = null)
    }

    test("without listing and fallback name the ticker is the name") {
        resolver.identify(Symbol.of("SAP.DE"), listing = null).name shouldBe "SAP"
    }
})
