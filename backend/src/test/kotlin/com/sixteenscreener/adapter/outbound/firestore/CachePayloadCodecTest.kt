package com.sixteenscreener.adapter.outbound.firestore

import com.sixteenscreener.contract.sampleBalanceSheet
import com.sixteenscreener.contract.sampleProfile
import com.sixteenscreener.contract.sampleRatios
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CachePayloadCodecTest : FunSpec({

    test("ratios round-trip, including missing values") {
        val encoded = CachePayloadCodec.encode(DataType.RATIOS, sampleRatios)
        CachePayloadCodec.decode(DataType.RATIOS, encoded) shouldBe sampleRatios
    }

    test("balance sheets round-trip, including optional fields") {
        val encoded = CachePayloadCodec.encode(DataType.BALANCE_SHEET, sampleBalanceSheet)
        CachePayloadCodec.decode(DataType.BALANCE_SHEET, encoded) shouldBe sampleBalanceSheet
    }

    test("company profiles round-trip") {
        val encoded = CachePayloadCodec.encode(DataType.COMPANY_PROFILE, sampleProfile)
        CachePayloadCodec.decode(DataType.COMPANY_PROFILE, encoded) shouldBe sampleProfile
    }

    test("quotes round-trip") {
        CachePayloadCodec.decodeQuote(CachePayloadCodec.encodeQuote(Quote(189.98, "USD"))) shouldBe Quote(189.98, "USD")
    }

    test("payloads contain only Firestore value types") {
        val encoded = CachePayloadCodec.encode(DataType.BALANCE_SHEET, sampleBalanceSheet)
        val period = encoded.maps("periods").first()

        period["fiscalYear"] shouldBe 2025L
        period["periodEnd"] shouldBe "2025-09-27"
        period.maps("items").first()["value"] shouldBe 3.6498E11
    }

    test("numbers stored as integers are read as doubles and vice versa") {
        val payload = mapOf(
            "employees" to 100.0,
            "marketCapitalization" to 5L,
        )

        val profile = CachePayloadCodec.decode(DataType.COMPANY_PROFILE, payload)

        profile.employees shouldBe 100
        profile.marketCapitalization shouldBe 5.0
        CachePayloadCodec.decodeQuote(mapOf("price" to 10L, "currency" to "EUR")) shouldBe Quote(10.0, "EUR")
    }

    test("unknown ratio codes are skipped") {
        val payload = mapOf("items" to listOf(mapOf("code" to "UNKNOWN", "value" to 1.0), mapOf("code" to "PE", "value" to 2.0)))

        CachePayloadCodec.decode(DataType.RATIOS, payload) shouldBe Ratios(listOf(Ratio(RatioCode.PE, 2.0)))
    }

    test("empty payloads decode to empty data") {
        CachePayloadCodec.decode(DataType.RATIOS, emptyMap()) shouldBe Ratios(emptyList())
        CachePayloadCodec.decode(DataType.BALANCE_SHEET, emptyMap()) shouldBe BalanceSheet(emptyList())
        CachePayloadCodec.decode(DataType.COMPANY_PROFILE, emptyMap()) shouldBe
            CompanyProfile(null, null, null, null, null, null, null, null, null, null, null)
    }

    test("missing required fields fail decoding") {
        shouldThrow<IllegalStateException> { CachePayloadCodec.decodeQuote(mapOf("currency" to "USD")) }
    }

    test("quotes are rejected by the generic codec") {
        shouldThrow<IllegalArgumentException> { CachePayloadCodec.decode(DataType.QUOTE, emptyMap()) }
    }
})
