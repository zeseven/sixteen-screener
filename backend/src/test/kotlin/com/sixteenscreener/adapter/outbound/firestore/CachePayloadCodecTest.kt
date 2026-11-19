package com.sixteenscreener.adapter.outbound.firestore

import com.sixteenscreener.contract.sampleBalanceSheet
import com.sixteenscreener.contract.sampleProfile
import com.sixteenscreener.contract.sampleRatios
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.Dividend
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

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

    test("company profiles cached before the logo URL was stored decode without a logo URL") {
        val encoded = CachePayloadCodec.encode(DataType.COMPANY_PROFILE, sampleProfile) - "logoUrl"

        val profile = CachePayloadCodec.decode(DataType.COMPANY_PROFILE, encoded)

        profile.logoUrl shouldBe null
        profile shouldBe sampleProfile.copy(logoUrl = null)
    }

    test("the logo URL is stored in the profile payload") {
        CachePayloadCodec.encode(DataType.COMPANY_PROFILE, sampleProfile)["logoUrl"] shouldBe sampleProfile.logoUrl
    }

    test("dividends round-trip, including missing dates") {
        val dividends = Dividends(
            listOf(
                Dividend(
                    exDate = LocalDate.parse("2026-05-12"),
                    paymentDate = LocalDate.parse("2026-05-15"),
                    recordDate = LocalDate.parse("2026-05-12"),
                    declarationDate = LocalDate.parse("2026-05-01"),
                    amount = 0.26,
                    currency = "EUR",
                ),
                Dividend(LocalDate.parse("2025-11-10"), null, null, null, amount = 1.0, currency = "EUR"),
            ),
        )

        CachePayloadCodec.decode(DataType.DIVIDENDS, CachePayloadCodec.encode(DataType.DIVIDENDS, dividends)) shouldBe dividends
    }

    test("an empty dividend list round-trips") {
        val encoded = CachePayloadCodec.encode(DataType.DIVIDENDS, Dividends(emptyList()))

        CachePayloadCodec.decode(DataType.DIVIDENDS, encoded) shouldBe Dividends(emptyList())
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

    test("unknown ratio codes are skipped and missing codes decode to null values") {
        val payload = mapOf("items" to listOf(mapOf("code" to "UNKNOWN", "value" to 1.0), mapOf("code" to "PE", "value" to 2.0)))

        CachePayloadCodec.decode(DataType.RATIOS, payload) shouldBe Ratios.of(mapOf(RatioCode.PE to 2.0))
    }

    test("ratios cached with the first 12 codes decode to all codes in display order") {
        val legacyCodes = listOf(
            "PE", "PB", "PS", "EV_EBITDA", "ROE", "ROA", "NET_MARGIN", "DEBT_TO_EQUITY", "CURRENT_RATIO",
            "DIVIDEND_YIELD", "EPS", "BETA",
        )
        val payload = mapOf("items" to legacyCodes.mapIndexed { index, code -> mapOf("code" to code, "value" to index.toDouble()) })

        val ratios = CachePayloadCodec.decode(DataType.RATIOS, payload)

        ratios.items.map { it.code } shouldBe RatioCode.entries
        ratios.items.filter { it.value != null }.map { it.code.name }.toSet() shouldBe legacyCodes.toSet()
        ratios.valueOf(RatioCode.BETA) shouldBe 11.0
        ratios.valueOf(RatioCode.ROI) shouldBe null
    }

    test("empty payloads decode to empty data") {
        CachePayloadCodec.decode(DataType.RATIOS, emptyMap()) shouldBe Ratios.of(emptyMap())
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
