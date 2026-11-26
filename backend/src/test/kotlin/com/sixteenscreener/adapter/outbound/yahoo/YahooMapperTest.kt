package com.sixteenscreener.adapter.outbound.yahoo

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Dividend
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.RatioUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

private inline fun <reified T> fixture(name: String): T = SourceHttpClient.json.decodeFromString(Fixtures.text("yahoo/$name"))

private const val TOLERANCE = 1e-9

class YahooMapperTest : FunSpec({

    context("ratios") {
        test("SAP.DE: fractions become percents, percents and multiples are kept, debt/equity becomes a ratio") {
            val ratios = YahooMapper.ratios(fixture("statistics-sap-de.json")).shouldNotBeNull()

            ratios.valueOf(RatioCode.PE) shouldBe 28.0
            ratios.valueOf(RatioCode.PB) shouldBe 4.8424473
            ratios.valueOf(RatioCode.PS) shouldBe 5.6697755
            ratios.valueOf(RatioCode.EV_EBITDA) shouldBe 17.823
            ratios.valueOf(RatioCode.EV_REVENUE) shouldBe 5.545
            ratios.valueOf(RatioCode.PEG) shouldBe 1.5908
            ratios.valueOf(RatioCode.ROE) shouldBe (18.178 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.ROA) shouldBe (9.906 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.GROSS_MARGIN) shouldBe (73.583 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.OPERATING_MARGIN) shouldBe (29.121 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.NET_MARGIN) shouldBe (20.413 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.REVENUE_GROWTH) shouldBe (13.2 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.EPS_GROWTH) shouldBe (13.4 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.CURRENT_RATIO) shouldBe 1.157
            ratios.valueOf(RatioCode.QUICK_RATIO) shouldBe 0.983
            ratios.valueOf(RatioCode.DEBT_TO_EQUITY) shouldBe (0.22789 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.DIVIDEND_YIELD) shouldBe 1.32
            ratios.valueOf(RatioCode.PAYOUT_RATIO) shouldBe (36.180001 plusOrMinus 1e-6)
            ratios.valueOf(RatioCode.BETA) shouldBe 0.702
            ratios.valueOf(RatioCode.EPS) shouldBe 6.91
            ratios.valueOf(RatioCode.BOOK_VALUE_PER_SHARE) shouldBe 39.955
        }

        test("ratios Yahoo doesn't provide are null and every code is present in display order") {
            val ratios = YahooMapper.ratios(fixture("statistics-aapl.json")).shouldNotBeNull()

            ratios.items.map { it.code } shouldContainExactly RatioCode.entries
            listOf(
                RatioCode.PCF,
                RatioCode.PFCF,
                RatioCode.ROI,
                RatioCode.PRETAX_MARGIN,
                RatioCode.REVENUE_GROWTH_5Y,
                RatioCode.EPS_GROWTH_5Y,
                RatioCode.DIVIDEND_GROWTH_5Y,
                RatioCode.LT_DEBT_TO_EQUITY,
                RatioCode.INTEREST_COVERAGE,
                RatioCode.ASSET_TURNOVER,
                RatioCode.INVENTORY_TURNOVER,
                RatioCode.RECEIVABLES_TURNOVER,
            ).forEach { ratios.valueOf(it).shouldBeNull() }
        }

        test("AAPL: return on equity above 100 percent and a dividend yield below 1 percent") {
            val ratios = YahooMapper.ratios(fixture("statistics-aapl.json")).shouldNotBeNull()

            ratios.valueOf(RatioCode.ROE) shouldBe (142.257 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.DIVIDEND_YIELD) shouldBe 0.3
            ratios.valueOf(RatioCode.DEBT_TO_EQUITY) shouldBe (0.84286 plusOrMinus TOLERANCE)
            ratios.valueOf(RatioCode.EPS) shouldBe 8.98
        }

        test("SHEL.L: per share amounts in pounds and a negative beta") {
            val ratios = YahooMapper.ratios(fixture("statistics-shel-l.json")).shouldNotBeNull()

            ratios.valueOf(RatioCode.EPS) shouldBe 4.3
            ratios.valueOf(RatioCode.BOOK_VALUE_PER_SHARE) shouldBe 25.85746
            ratios.valueOf(RatioCode.BETA) shouldBe -0.248
        }

        test("every mapped Yahoo key keeps the meaning of its ratio code") {
            YahooMapper.amountKeys.keys.all { it.unit == RatioUnit.CURRENCY } shouldBe true
            YahooMapper.ratioKeys.keys.none { it.unit == RatioUnit.CURRENCY } shouldBe true
        }

        test("per share amounts are dropped without a currency") {
            val ratios = YahooMapper.ratios(
                StatisticsDto(symbol = "X", currency = null, ratios = mapOf("trailingPE" to 12.0), amounts = mapOf("trailingEps" to 1.0)),
            ).shouldNotBeNull()

            ratios.valueOf(RatioCode.PE) shouldBe 12.0
            ratios.valueOf(RatioCode.EPS).shouldBeNull()
        }

        test("unknown keys are ignored and statistics without any known value are no data") {
            YahooMapper.ratios(StatisticsDto(symbol = "X", currency = "USD", ratios = mapOf("forwardPE" to 12.0))).shouldBeNull()
            YahooMapper.ratios(StatisticsDto(symbol = "X", currency = "USD")).shouldBeNull()
        }
    }

    context("dividends") {
        test("SHEL.L: the full history converted to pounds by the proxy, newest first") {
            val dividends = YahooMapper.dividends(fixture("dividends-shel-l.json")).items

            dividends.first() shouldBe Dividend(
                exDate = LocalDate.parse("2026-11-12"),
                paymentDate = null,
                recordDate = null,
                declarationDate = null,
                amount = 0.29798483000000003,
                currency = "GBP",
            )
            dividends.last().exDate shouldBe LocalDate.parse("2005-08-03")
            dividends.map { it.exDate } shouldBe dividends.map { it.exDate }.sortedDescending()
        }

        test("trailing yield of AAPL from the last 12 months of dividends and its quote") {
            val dividends = YahooMapper.dividends(fixture("dividends-aapl.json"))
            val quote = YahooMapper.quote(fixture("quote-aapl.json"), "USD").shouldNotBeNull()

            dividends.trailingYield(quote, LocalDate.parse("2026-11-26")) shouldBe
                ((0.26 + 0.27 + 0.27 + 0.27) / 355.58 * 100 plusOrMinus TOLERANCE)
        }

        test("entries without a date or a positive amount are dropped") {
            val dividends = YahooMapper.dividends(
                DividendsDto(
                    currency = "EUR",
                    dividends = listOf(
                        DividendDto("2026-05-06", 2.5),
                        DividendDto(null, 1.0),
                        DividendDto("not a date", 1.0),
                        DividendDto("2025-05-14", null),
                        DividendDto("2024-05-14", 0.0),
                    ),
                ),
            )

            dividends.items.map { it.exDate } shouldContainExactly listOf(LocalDate.parse("2026-05-06"))
        }

        test("dividends without a currency are empty") {
            YahooMapper.dividends(DividendsDto(currency = null, dividends = listOf(DividendDto("2026-05-06", 2.5)))).items
                .shouldBeEmpty()
        }
    }

    context("company profile") {
        test("SAP.DE") {
            val profile = YahooMapper.companyProfile(fixture("profile-sap-de.json")).shouldNotBeNull()

            profile.copy(description = null) shouldBe CompanyProfile(
                name = "SAP SE",
                country = "DE",
                currency = "EUR",
                sector = "Technology",
                industry = "Software - Application",
                ipoDate = null,
                marketCapitalization = null,
                sharesOutstanding = null,
                employees = 113076,
                website = "https://www.sap.com",
                description = null,
                securityType = "Common Stock",
            )
            profile.description.shouldNotBeNull()
        }

        test("SHEL.L and AAPL countries") {
            YahooMapper.companyProfile(fixture("profile-shel-l.json"))?.country shouldBe "GB"
            YahooMapper.companyProfile(fixture("profile-aapl.json"))?.country shouldBe "US"
        }

        test("a profile without any value is no data") {
            YahooMapper.companyProfile(ProfileDto(symbol = "X")).shouldBeNull()
        }

        context("security type from the Yahoo quote type") {
            withData(
                nameFn = { (quoteType, typeDisplay, expected) -> "$quoteType / $typeDisplay is $expected" },
                Triple("EQUITY", "Equity", "Common Stock"),
                Triple("ETF", "ETF", "ETF"),
                Triple("MUTUALFUND", "Fund", "Fund"),
                Triple("INDEX", "Index", "Index"),
                Triple("CRYPTOCURRENCY", null, null),
                Triple(null, "Equity", "Equity"),
                Triple(null, null, null),
            ) { (quoteType, typeDisplay, expected) ->
                YahooMapper.securityTypeOf(quoteType, typeDisplay) shouldBe expected
            }
        }

        context("country codes from English country names") {
            withData(
                nameFn = { (name, code) -> "$name is $code" },
                "Germany" to "DE",
                "United States" to "US",
                "United Kingdom" to "GB",
                "Netherlands" to "NL",
                "Switzerland" to "CH",
                "South Korea" to "KR",
                "Taiwan" to "TW",
                "Hong Kong" to "HK",
                "Czech Republic" to "CZ",
                "Turkey" to "TR",
                " japan " to "JP",
                "Atlantis" to null,
                "" to null,
            ) { (name, code) ->
                YahooMapper.countryCodeOf(name) shouldBe code
            }
        }
    }

    context("balance sheet") {
        test("SAP.DE: annual periods newest first with readable labels in the statement currency") {
            val balanceSheet = YahooMapper.balanceSheet(fixture("balance-sheet-sap-de.json")).shouldNotBeNull()

            val latest = balanceSheet.periods.first()
            latest.periodEnd shouldBe LocalDate.parse("2025-12-31")
            latest.fiscalYear shouldBe 2025
            latest.fiscalPeriod shouldBe FiscalPeriod.FY
            latest.filedAt.shouldBeNull()
            latest.form.shouldBeNull()
            latest.items.first() shouldBe BalanceSheetItem("treasurySharesNumber", "Treasury Shares Number", 60945184.0, "EUR")
            latest.items.single { it.concept == "stockholdersEquity" }.value shouldBe 44586000000.0
            balanceSheet.periods.map { it.periodEnd } shouldBe balanceSheet.periods.map { it.periodEnd }.sortedDescending()
        }

        test("years without a valid date or without items are left out; no year left is no data") {
            val balanceSheet = YahooMapper.balanceSheet(
                BalanceSheetDto(
                    currency = "EUR",
                    years = listOf(
                        BalanceSheetYearDto("2024-12-31", mapOf("totalAssets" to 1.0)),
                        BalanceSheetYearDto("2025-12-31", mapOf("totalAssets" to 2.0)),
                        BalanceSheetYearDto("2023-12-31", emptyMap()),
                        BalanceSheetYearDto(null, mapOf("totalAssets" to 3.0)),
                    ),
                ),
            ).shouldNotBeNull()

            balanceSheet.periods.map { it.fiscalYear } shouldContainExactly listOf(2025, 2024)
            YahooMapper.balanceSheet(BalanceSheetDto(currency = "EUR")).shouldBeNull()
        }

        context("labels") {
            withData(
                nameFn = { (key, label) -> "$key is $label" },
                "totalAssets" to "Total Assets",
                "netPPE" to "Net PPE",
                "stockholdersEquity" to "Stockholders Equity",
                "tradeandOtherPayablesNonCurrent" to "Tradeand Other Payables Non Current",
                "otherShortTermInvestments" to "Other Short Term Investments",
                "cashCashEquivalentsAndShortTermInvestments" to "Cash Cash Equivalents And Short Term Investments",
                "gainsLossesNotAffectingRetainedEarnings" to "Gains Losses Not Affecting Retained Earnings",
                "assetsHeldForSaleCurrent" to "Assets Held For Sale Current",
            ) { (key, label) ->
                YahooMapper.labelOf(key) shouldBe label
            }
        }

        test("SHEL.L balance sheets of five years") {
            YahooMapper.balanceSheet(fixture("balance-sheet-shel-l.json")).shouldNotBeNull().periods shouldHaveSize 5
        }
    }
})
