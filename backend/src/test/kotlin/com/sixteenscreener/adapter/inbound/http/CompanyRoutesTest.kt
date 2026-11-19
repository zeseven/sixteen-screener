package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.TestPorts
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.IndicatorDefaults
import com.sixteenscreener.fakes.FakeBalanceSheetSource
import com.sixteenscreener.testConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

private val finnhub = SourceId.of("finnhub")
private val aapl = Symbol.of("AAPL")
private val tabs = listOf("price-ratios", "balance-sheet", "profile")

private fun fullConfig(): AppConfig = testConfig().let { config ->
    val finnhubSource = config.sources.single()
    config.copy(
        sources = listOf(
            finnhubSource.copy(capabilities = Capability.entries.toSet() - Capability.LOGO),
            finnhubSource.copy(
                id = SourceId.of("sec-edgar"),
                name = "SEC EDGAR",
                capabilities = setOf(Capability.BALANCE_SHEET),
                selectableAsDefault = false,
                limits = listOf(RequestLimit(LimitInterval.MINUTE, 300)),
            ),
        ),
    )
}

private fun AppConfig.manualOnlyFinnhub(): AppConfig =
    copy(sources = sources.map { if (it.id.value == "finnhub") it.copy(automaticRequests = false) else it })

private val ratios = Ratios(RatioCode.entries.map { Ratio(it, if (it == RatioCode.PE) 30.5 else null) })

private val profile = CompanyProfile(
    name = "Apple Inc",
    country = "US",
    currency = "USD",
    sector = "Technology",
    industry = null,
    ipoDate = LocalDate.parse("1980-12-12"),
    marketCapitalization = 3.4e12,
    sharesOutstanding = 1.5e10,
    employees = null,
    website = "https://www.apple.com",
    description = null,
)

private val balanceSheet = BalanceSheet(
    listOf(
        BalanceSheetPeriod(
            periodEnd = LocalDate.parse("2025-09-27"),
            fiscalYear = 2025,
            fiscalPeriod = FiscalPeriod.FY,
            filedAt = null,
            form = null,
            items = listOf(
                BalanceSheetItem("Assets", "Total assets", 3.6e11, "USD"),
                BalanceSheetItem("SharesOutstanding", "Shares outstanding", 1.5e10, null),
            ),
        ),
    ),
)

private fun TestPorts.listedApple() =
    symbolDirectory.add(ListedSymbol(aapl, "APPLE INC", "XNAS", "USD", "Common Stock"))

private fun TestPorts.success() = SourceCallOutcome.success(clock.now())

private fun TestPorts.sourceAnswersEverything() {
    marketData.quotes[aapl] = SourceResult.Success(Quote(228.5, "USD"), success())
    marketData.ratios[aapl] = SourceResult.Success(ratios, success())
    marketData.profiles[aapl] = SourceResult.Success(profile, success())
    balanceSheets.balanceSheets[aapl] = SourceResult.Success(balanceSheet, success())
}

private fun TestPorts.sourceFailsEverything() {
    val failure = SourceResult.SourceError(SourceCallOutcome.httpError(503, clock.now()))
    marketData.quotes[aapl] = failure
    marketData.ratios[aapl] = failure
    marketData.profiles[aapl] = failure
    balanceSheets.balanceSheets[aapl] = failure
}

private suspend fun TestPorts.cacheEverything(age: Duration) {
    val fetchedAt = clock.now() - age
    companyCache.seedQuote(
        CacheKey(finnhub, aapl, DataType.QUOTE),
        CacheEntry(QuoteRecord(Quote(200.0, "USD"), LocalDate.parse("2026-11-13"), null), fetchedAt),
    )
    companyCache.put(CacheKey(finnhub, aapl, DataType.RATIOS), CacheEntry(ratios, fetchedAt))
    companyCache.put(CacheKey(finnhub, aapl, DataType.COMPANY_PROFILE), CacheEntry(profile, fetchedAt))
    companyCache.put(CacheKey(finnhub, aapl, DataType.BALANCE_SHEET), CacheEntry(balanceSheet, fetchedAt))
}

private fun TestPorts.exhaustBudget() =
    requestCounters.setCount(finnhub, LimitInterval.MINUTE, clock.now(), Int.MAX_VALUE / 2)

private fun TestPorts.sourceCallCount() = marketData.calls.size + balanceSheets.calls.size

private fun meta(status: String, fetchedAt: String?, source: String = "finnhub") = JsonObject(
    mapOf(
        "source" to JsonPrimitive(source),
        "status" to JsonPrimitive(status),
        "fetchedAt" to (fetchedAt?.let(::JsonPrimitive) ?: JsonNull),
    ),
)

private fun peIndicator(level: String) = JsonObject(
    mapOf("ratioCode" to JsonPrimitive("PE"), "name" to JsonPrimitive("P/E"), "level" to JsonPrimitive(level)),
)

private fun JsonObject.meta() = getValue("meta").jsonObject

private fun JsonObject.status() = meta()["status"]

private fun JsonObject.isEmptyData(): Boolean =
    meta()["fetchedAt"] == JsonNull && when {
        containsKey("ratios") -> get("quote") == JsonNull && getValue("ratios").jsonArray.isEmpty()
        containsKey("periods") -> getValue("periods").jsonArray.isEmpty()
        else -> get("profile") == JsonNull
    }

class CompanyRoutesTest : FunSpec({

    context("price and ratios") {
        test("cache miss on the default source answers fresh data in the exact shape") {
            routeTest { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/price-ratios").json()

                body.keys shouldBe setOf("meta", "quote", "ratios", "indicators")
                body["indicators"] shouldBe JsonArray(listOf(peIndicator("WARNING")))
                body.meta() shouldBe meta("FRESH", "2026-11-14T12:00:00Z")
                body["quote"] shouldBe JsonObject(
                    mapOf(
                        "price" to JsonPrimitive(228.5),
                        "currency" to JsonPrimitive("USD"),
                        "fetchedAt" to JsonPrimitive("2026-11-14T12:00:00Z"),
                        "previousCachedPrice" to JsonNull,
                        "trend" to JsonPrimitive("UNCHANGED"),
                        "source" to JsonPrimitive("finnhub"),
                    ),
                )
                val ratioItems = body.getValue("ratios").jsonArray.map { it.jsonObject }
                ratioItems.map { it["code"] } shouldContainExactly RatioCode.entries.map { JsonPrimitive(it.name) }
                ratioItems.map { it["name"] } shouldContainExactly RatioCode.entries.map { JsonPrimitive(it.displayName) }
                ratioItems.map { it["unit"] } shouldContainExactly RatioCode.entries.map { JsonPrimitive(it.unit.name) }
                ratioItems.first() shouldBe JsonObject(
                    mapOf(
                        "code" to JsonPrimitive("PE"),
                        "name" to JsonPrimitive("P/E"),
                        "unit" to JsonPrimitive("RATIO"),
                        "value" to JsonPrimitive(30.5),
                    ),
                )
                ratioItems.first { it["code"] == JsonPrimitive("ROE") }["unit"] shouldBe JsonPrimitive("PERCENT")
                ratioItems.last()["unit"] shouldBe JsonPrimitive("CURRENCY")
                ratioItems.last()["value"] shouldBe JsonNull
                ports.marketData.calls shouldContainExactlyInAnyOrder listOf("quote:AAPL", "ratios:AAPL")
            }
        }

        test("indicators use the profile's settings and a P/E calculated from the price while the ratio stays null") {
            routeTest { ports ->
                val settings = IndicatorDefaults.all.map { it.setting.copy(enabled = it.ratioCode == RatioCode.PE) }
                val saved = ports.savedProfile().copy(indicatorSettings = settings).also(ports.profiles::save)
                ports.listedApple()
                ports.marketData.quotes[aapl] = SourceResult.Success(Quote(13.9, "USD"), ports.success())
                ports.marketData.ratios[aapl] = SourceResult.Success(
                    Ratios.of(mapOf(RatioCode.EPS to -2.252, RatioCode.ROE to 50.0)),
                    ports.success(),
                )

                val body = client.get("/profiles/${saved.id}/companies/AAPL/price-ratios").json()

                body["indicators"] shouldBe JsonArray(listOf(peIndicator("BAD")))
                body.getValue("ratios").jsonArray.first().jsonObject["value"] shouldBe JsonNull
            }
        }

        test("no ratios make no indicators and a profile without settings gets the defaults") {
            routeTest { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceFailsEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/price-ratios").json()

                body["indicators"] shouldBe JsonArray(emptyList())
                ports.profiles.findById(saved.id)?.indicatorSettings shouldBe IndicatorDefaults.all.map { it.setting }
            }
        }

        test("explicit source is used") {
            routeTest { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/price-ratios?source=finnhub").json()

                body.status() shouldBe JsonPrimitive("FRESH")
            }
        }

        test("source failure without cache answers empty data with SOURCE_ERROR") {
            routeTest { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceFailsEverything()

                val response = client.get("/profiles/${saved.id}/companies/AAPL/price-ratios")

                response.status shouldBe HttpStatusCode.OK
                val body = response.json()
                body.meta() shouldBe meta("SOURCE_ERROR", null)
                body["quote"] shouldBe JsonNull
                body["ratios"] shouldBe JsonArray(emptyList())
            }
        }
    }

    context("balance sheet") {
        test("cache miss on the default source answers fresh data in the exact shape") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/balance-sheet").json()

                body.keys shouldBe setOf("meta", "periods")
                body.meta() shouldBe meta("FRESH", "2026-11-14T12:00:00Z")
                body.getValue("periods").jsonArray.single() shouldBe JsonObject(
                    mapOf(
                        "periodEnd" to JsonPrimitive("2025-09-27"),
                        "fiscalYear" to JsonPrimitive(2025),
                        "fiscalPeriod" to JsonPrimitive("FY"),
                        "filedAt" to JsonNull,
                        "form" to JsonNull,
                        "items" to JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "concept" to JsonPrimitive("Assets"),
                                        "label" to JsonPrimitive("Total assets"),
                                        "value" to JsonPrimitive(3.6e11),
                                        "unit" to JsonPrimitive("USD"),
                                    ),
                                ),
                                JsonObject(
                                    mapOf(
                                        "concept" to JsonPrimitive("SharesOutstanding"),
                                        "label" to JsonPrimitive("Shares outstanding"),
                                        "value" to JsonPrimitive(1.5e10),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                ports.balanceSheets.calls shouldContainExactly listOf(aapl)
            }
        }

        test("filing date and form are returned when present") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                val period = balanceSheet.periods.single().copy(filedAt = LocalDate.parse("2025-10-31"), form = "10-K")
                ports.balanceSheets.balanceSheets[aapl] = SourceResult.Success(BalanceSheet(listOf(period)), ports.success())

                val body = client.get("/profiles/${saved.id}/companies/AAPL/balance-sheet?source=finnhub").json()

                val returned = body.getValue("periods").jsonArray.single().jsonObject
                returned["filedAt"] shouldBe JsonPrimitive("2025-10-31")
                returned["form"] shouldBe JsonPrimitive("10-K")
            }
        }

        test("explicit SEC EDGAR source is fetched, cached and counted against its own limit") {
            val secEdgar = FakeBalanceSheetSource(SourceId.of("sec-edgar"))
            routeTest(TestPorts(additionalBalanceSheets = listOf(secEdgar)), fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                val period = balanceSheet.periods.single().copy(filedAt = LocalDate.parse("2025-10-31"), form = "10-K")
                secEdgar.balanceSheets[aapl] = SourceResult.Success(BalanceSheet(listOf(period)), ports.success())

                val body = client.get("/profiles/${saved.id}/companies/AAPL/balance-sheet?source=sec-edgar").json()

                body.meta() shouldBe meta("FRESH", "2026-11-14T12:00:00Z", source = "sec-edgar")
                body.getValue("periods").jsonArray.single().jsonObject["form"] shouldBe JsonPrimitive("10-K")
                secEdgar.calls shouldContainExactly listOf(aapl)
                ports.sourceCallCount() shouldBe 0
                ports.companyCache.get(CacheKey(SourceId.of("sec-edgar"), aapl, DataType.BALANCE_SHEET))?.data shouldBe
                    BalanceSheet(listOf(period))
                ports.requestCounters.usage(SourceId.of("sec-edgar"), listOf(RequestLimit(LimitInterval.MINUTE, 300)), ports.clock.now())
                    .single().used shouldBe 1
                ports.sourceCalls.findAll(listOf(SourceId.of("sec-edgar"))) shouldBe
                    mapOf(SourceId.of("sec-edgar") to ports.success())
            }
        }

        test("SEC EDGAR NOT_SUPPORTED answer for a non-US symbol keeps the response empty") {
            val secEdgar = FakeBalanceSheetSource(SourceId.of("sec-edgar"))
            routeTest(TestPorts(additionalBalanceSheets = listOf(secEdgar)), fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                secEdgar.balanceSheets[aapl] = SourceResult.NotSupported(outcome = null)

                val body = client.get("/profiles/${saved.id}/companies/AAPL/balance-sheet?source=sec-edgar").json()

                body.meta() shouldBe meta("NOT_SUPPORTED", null, source = "sec-edgar")
                body["periods"] shouldBe JsonArray(emptyList())
                ports.sourceCalls.findAll(listOf(SourceId.of("sec-edgar"))) shouldBe emptyMap()
            }
        }

        test("SEC EDGAR without a balance sheet adapter answers NOT_SUPPORTED without a call") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/balance-sheet?source=sec-edgar").json()

                body.meta() shouldBe meta("NOT_SUPPORTED", null, source = "sec-edgar")
                body["periods"] shouldBe JsonArray(emptyList())
                ports.sourceCallCount() shouldBe 0
            }
        }
    }

    context("company profile") {
        test("cache miss on the default source answers fresh data in the exact shape") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/profile").json()

                body.keys shouldBe setOf("meta", "profile")
                body.meta() shouldBe meta("FRESH", "2026-11-14T12:00:00Z")
                body["profile"] shouldBe JsonObject(
                    mapOf(
                        "symbol" to JsonPrimitive("AAPL"),
                        "ticker" to JsonPrimitive("AAPL"),
                        "exchange" to JsonPrimitive("NASDAQ"),
                        "name" to JsonPrimitive("Apple Inc"),
                        "market" to JsonPrimitive("US"),
                        "type" to JsonPrimitive("Common Stock"),
                        "stock" to JsonPrimitive(true),
                        "homeExchange" to JsonPrimitive(true),
                        "country" to JsonPrimitive("US"),
                        "currency" to JsonPrimitive("USD"),
                        "sector" to JsonPrimitive("Technology"),
                        "industry" to JsonNull,
                        "ipoDate" to JsonPrimitive("1980-12-12"),
                        "marketCapitalization" to JsonPrimitive(3.4e12),
                        "sharesOutstanding" to JsonPrimitive(1.5e10),
                        "employees" to JsonNull,
                        "website" to JsonPrimitive("https://www.apple.com"),
                        "description" to JsonNull,
                    ),
                )
            }
        }

        test("a listing on an exchange outside the company's country is not its home exchange") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                val appleOnXetra = Symbol.of("APC.DE")
                ports.symbolDirectory.add(ListedSymbol(appleOnXetra, "APPLE INC", "XETR", "EUR", "Common Stock"))
                ports.marketData.profiles[appleOnXetra] = SourceResult.Success(profile, ports.success())

                val returned = client.get("/profiles/${saved.id}/companies/APC.DE/profile").json()["profile"]!!.jsonObject

                returned["country"] shouldBe JsonPrimitive("US")
                returned["stock"] shouldBe JsonPrimitive(true)
                returned["homeExchange"] shouldBe JsonPrimitive(false)
            }
        }

        test("a depositary receipt is not on its home exchange") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.symbolDirectory.add(ListedSymbol(aapl, "APPLE INC", "XNAS", "USD", "ADR"))
                ports.marketData.profiles[aapl] = SourceResult.Success(profile, ports.success())

                val returned = client.get("/profiles/${saved.id}/companies/AAPL/profile").json()["profile"]!!.jsonObject

                returned["type"] shouldBe JsonPrimitive("ADR")
                returned["stock"] shouldBe JsonPrimitive(false)
                returned["homeExchange"] shouldBe JsonPrimitive(false)
            }
        }

        test("company outside the US and EU markets has a null market") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                val toyota = Symbol.of("7203.T")
                ports.symbolDirectory.add(ListedSymbol(toyota, "TOYOTA MOTOR CORP", "XTKS", "JPY", "Common Stock"))
                ports.marketData.profiles[toyota] = SourceResult.Success(profile.copy(name = null), ports.success())

                val returned = client.get("/profiles/${saved.id}/companies/7203.T/profile").json()["profile"]!!.jsonObject

                returned["market"] shouldBe JsonNull
                returned["exchange"] shouldBe JsonPrimitive("TSE")
                returned["ticker"] shouldBe JsonPrimitive("7203")
                returned["name"] shouldBe JsonPrimitive("TOYOTA MOTOR CORP")
            }
        }

        test("source without data for the company answers NO_DATA with a null profile") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.marketData.profiles[aapl] = SourceResult.NoData(ports.success())

                val body = client.get("/profiles/${saved.id}/companies/AAPL/profile?source=finnhub").json()

                body shouldBe JsonObject(mapOf("meta" to meta("NO_DATA", null), "profile" to JsonNull))
            }
        }
    }

    context("every tab") {
        withData(nameFn = { "$it: unsupported by the source answers NOT_SUPPORTED with empty data" }, tabs) { tab ->
            val config = testConfig().let { config ->
                config.copy(sources = config.sources.map { it.copy(capabilities = setOf(Capability.SEARCH)) })
            }
            routeTest(config = config) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab").json()

                body.status() shouldBe JsonPrimitive("NOT_SUPPORTED")
                body.isEmptyData() shouldBe true
                ports.sourceCallCount() shouldBe 0
            }
        }

        withData(nameFn = { "$it: stale cache is answered as CACHED without a call" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.cacheEverything(age = Duration.ofDays(2))
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab").json()

                body.meta() shouldBe meta("CACHED", "2026-11-12T12:00:00Z")
                ports.sourceCallCount() shouldBe 0
            }
        }

        withData(nameFn = { "$it: trigger=MANUAL with budget requests the source" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.cacheEverything(age = Duration.ofMinutes(10))
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab?trigger=MANUAL").json()

                body.meta() shouldBe meta("FRESH", "2026-11-14T12:00:00Z")
                (ports.sourceCallCount() > 0) shouldBe true
            }
        }

        withData(nameFn = { "$it: trigger=MANUAL without budget answers the cache with LIMIT_REACHED" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.cacheEverything(age = Duration.ofMinutes(10))
                ports.sourceAnswersEverything()
                ports.exhaustBudget()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab?trigger=MANUAL").json()

                body.meta() shouldBe meta("LIMIT_REACHED", "2026-11-14T11:50:00Z")
                body.isEmptyData() shouldBe false
                ports.sourceCallCount() shouldBe 0
            }
        }

        withData(nameFn = { "$it: cache miss without budget answers empty data with LIMIT_REACHED" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()
                ports.exhaustBudget()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab").json()

                body.status() shouldBe JsonPrimitive("LIMIT_REACHED")
                body.isEmptyData() shouldBe true
            }
        }

        withData(nameFn = { "$it: a source with automatic requests off answers NOT_REQUESTED to AUTO without cache" }, tabs) { tab ->
            routeTest(config = fullConfig().manualOnlyFinnhub()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab").json()

                body.status() shouldBe JsonPrimitive("NOT_REQUESTED")
                body.isEmptyData() shouldBe true
                ports.sourceCallCount() shouldBe 0
            }
        }

        withData(nameFn = { "$it: a source with automatic requests off is called for trigger=MANUAL without cache" }, tabs) { tab ->
            routeTest(config = fullConfig().manualOnlyFinnhub()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceAnswersEverything()

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab?trigger=MANUAL").json()

                body.status() shouldBe JsonPrimitive("FRESH")
                (ports.sourceCallCount() > 0) shouldBe true
            }
        }

        withData(nameFn = { "$it: trigger=AUTO with a fresh cache makes no call" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.cacheEverything(age = Duration.ofMinutes(10))

                val body = client.get("/profiles/${saved.id}/companies/AAPL/$tab?trigger=AUTO").json()

                body.meta() shouldBe meta("FRESH", "2026-11-14T11:50:00Z")
                ports.sourceCallCount() shouldBe 0
            }
        }

        withData(nameFn = { "$it: source failure with cache answers the cache with SOURCE_ERROR" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.cacheEverything(age = Duration.ofDays(2))
                ports.sourceFailsEverything()

                val response = client.get("/profiles/${saved.id}/companies/AAPL/$tab?trigger=MANUAL")

                response.status shouldBe HttpStatusCode.OK
                val body = response.json()
                body.meta() shouldBe meta("SOURCE_ERROR", "2026-11-12T12:00:00Z")
                body.isEmptyData() shouldBe false
            }
        }

        withData(nameFn = { "$it: source failure without cache answers empty data with SOURCE_ERROR" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()
                ports.sourceFailsEverything()

                val response = client.get("/profiles/${saved.id}/companies/AAPL/$tab")

                response.status shouldBe HttpStatusCode.OK
                response.json().meta() shouldBe meta("SOURCE_ERROR", null)
                response.json().isEmptyData() shouldBe true
            }
        }

        withData(nameFn = { "$it: unknown source is not found" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()

                client.get("/profiles/${saved.id}/companies/AAPL/$tab?source=nope").shouldBeProblem(404)
                ports.sourceCallCount() shouldBe 0
            }
        }

        data class InvalidParameter(val tab: String, val query: String)

        withData(
            nameFn = { "${it.tab}?${it.query} is a bad request" },
            tabs.flatMap { tab ->
                listOf("source=Finnhub", "source=fin_hub", "source=", "trigger=auto", "trigger=Manual", "trigger=REFRESH", "trigger=true", "trigger=")
                    .map { InvalidParameter(tab, it) }
            },
        ) { (tab, query) ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.listedApple()

                client.get("/profiles/${saved.id}/companies/AAPL/$tab?$query").shouldBeProblem(400)
                ports.sourceCallCount() shouldBe 0
            }
        }

        withData(nameFn = { "$it: unknown symbol is not found" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()

                client.get("/profiles/${saved.id}/companies/NOPE/$tab").shouldBeProblem(404)
                ports.sourceCallCount() shouldBe 0
            }
        }

        withData(nameFn = { "$it: malformed symbol is a bad request" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()

                client.get("/profiles/${saved.id}/companies/aapl/$tab").shouldBeProblem(400)
            }
        }

        withData(nameFn = { "$it: malformed profile ID is a bad request" }, tabs) { tab ->
            routeTest(config = fullConfig()) {
                client.get("/profiles/not-a-uuid/companies/AAPL/$tab").shouldBeProblem(400)
            }
        }

        withData(nameFn = { "$it: unknown profile is not found" }, tabs) { tab ->
            routeTest(config = fullConfig()) { ports ->
                ports.listedApple()

                client.get("/profiles/${UUID.randomUUID()}/companies/AAPL/$tab").shouldBeProblem(404)
                ports.sourceCallCount() shouldBe 0
            }
        }
    }

    context("symbol of an exchange not covered by the directory source") {
        test("price and ratios answer NOT_SUPPORTED without a source request") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.symbolDirectory.notCovered(ExchangeCode("AS"))

                val response = client.get("/profiles/${saved.id}/companies/AD.AS/price-ratios")

                response.status shouldBe HttpStatusCode.OK
                response.json().status() shouldBe JsonPrimitive("NOT_SUPPORTED")
                response.json().isEmptyData() shouldBe true
                ports.sourceCallCount() shouldBe 0
            }
        }

        test("company profile answers NOT_SUPPORTED with the static exchange table identity") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.symbolDirectory.notCovered(ExchangeCode("AS"))

                val body = client.get("/profiles/${saved.id}/companies/AD.AS/profile").json()

                body.status() shouldBe JsonPrimitive("NOT_SUPPORTED")
                body["profile"] shouldBe JsonNull
                ports.sourceCallCount() shouldBe 0
            }
        }

        test("balance sheet of Finnhub answers NOT_SUPPORTED without a source request or budget reservation") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.symbolDirectory.notCovered(ExchangeCode("AS"))

                val response = client.get("/profiles/${saved.id}/companies/AD.AS/balance-sheet?source=finnhub")

                response.status shouldBe HttpStatusCode.OK
                response.json().status() shouldBe JsonPrimitive("NOT_SUPPORTED")
                response.json().isEmptyData() shouldBe true
                ports.sourceCallCount() shouldBe 0
                ports.requestCounters.usage(SourceId.of("finnhub"), listOf(RequestLimit(LimitInterval.MINUTE, 60)), ports.clock.now())
                    .single().used shouldBe 0
            }
        }

        test("balance sheet of SEC EDGAR answers instead of not found") {
            routeTest(config = fullConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.symbolDirectory.notCovered(ExchangeCode("AS"))

                client.get("/profiles/${saved.id}/companies/AD.AS/balance-sheet?source=sec-edgar").status shouldBe
                    HttpStatusCode.OK
            }
        }
    }
})
