package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.LimitUnit
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import com.sixteenscreener.testConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val finnhub = SourceId.of("finnhub")
private val tiingo = SourceId.of("tiingo")
private val eodhd = SourceId.of("eodhd")
private val secEdgar = SourceId.of("sec-edgar")
private val logoapi = SourceId.of("logoapi")
private val finnhubLogos = SourceId.of("finnhub-logos")

private fun sourcesConfig(): AppConfig = testConfig().let { config ->
    val (finnhubSource, eodhdSource) = config.sources
    config.copy(
        sources = listOf(
            finnhubSource,
            eodhdSource,
            finnhubSource.copy(
                id = secEdgar,
                name = "SEC EDGAR",
                capabilities = setOf(Capability.BALANCE_SHEET),
                roles = emptySet(),
                limits = listOf(RequestLimit(LimitInterval.MINUTE, 300)),
            ),
            finnhubSource.copy(
                id = SourceId.of("fmp"),
                name = "FMP",
                enabled = false,
            ),
            finnhubSource.copy(
                id = tiingo,
                name = "Tiingo",
                capabilities = setOf(Capability.QUOTE, Capability.SEARCH),
                roles = setOf(SourceRole.US, SourceRole.INTERNATIONAL),
                automaticRequests = false,
                limits = listOf(
                    RequestLimit(LimitInterval.HOUR, 50),
                    RequestLimit(LimitInterval.MONTH, 500, LimitUnit.UNIQUE_SYMBOLS),
                ),
            ),
            finnhubSource.copy(
                id = logoapi,
                name = "logoapi.sh",
                capabilities = setOf(Capability.LOGO),
                roles = emptySet(),
                limits = emptyList(),
            ),
            finnhubSource.copy(
                id = finnhubLogos,
                name = "Finnhub logos",
                capabilities = setOf(Capability.LOGO),
                roles = emptySet(),
                limits = emptyList(),
                countTotalCalls = true,
            ),
        ),
    )
}

private fun JsonObject.items(): List<JsonObject> = getValue("items").jsonArray.map { it.jsonObject }

private fun JsonObject.item(id: SourceId): JsonObject = items().single { it["id"] == JsonPrimitive(id.value) }

private fun limit(interval: String, limit: Int, used: Int, resetsAt: String, unit: String = "REQUESTS") = JsonObject(
    mapOf(
        "interval" to JsonPrimitive(interval),
        "unit" to JsonPrimitive(unit),
        "limit" to JsonPrimitive(limit),
        "used" to JsonPrimitive(used),
        "resetsAt" to JsonPrimitive(resetsAt),
    ),
)

private fun JsonObject.usedOf(id: SourceId): JsonElement? =
    item(id).getValue("limits").jsonArray.single().jsonObject["used"]

class SourceRoutesTest : FunSpec({

    test("enabled sources in configuration order in the exact shape; counters absent count as unused") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/sources").json()

            body.keys shouldBe setOf("items")
            body.items().map { it.getValue("id").jsonPrimitive.content } shouldContainExactly
                listOf("finnhub", "eodhd", "sec-edgar", "tiingo", "logoapi", "finnhub-logos")
            body.item(finnhub) shouldBe JsonObject(
                mapOf(
                    "id" to JsonPrimitive("finnhub"),
                    "name" to JsonPrimitive("Finnhub"),
                    "capabilities" to JsonArray(listOf("SEARCH", "QUOTE", "RATIOS").map(::JsonPrimitive)),
                    "roles" to JsonArray(listOf(JsonPrimitive("SEARCH"), JsonPrimitive("US"))),
                    "defaultFor" to JsonArray(listOf(JsonPrimitive("SEARCH"), JsonPrimitive("US"))),
                    "isDefault" to JsonPrimitive(true),
                    "automaticRequests" to JsonPrimitive(true),
                    "limits" to JsonArray(listOf(limit("MINUTE", 60, 0, "2026-11-14T12:01:00Z"))),
                    "lastCall" to JsonNull,
                    "totalCalls" to JsonNull,
                ),
            )
        }
    }

    test("several limits with units and reset times; a source without limits has none") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/sources").json()

            body.item(tiingo)["limits"] shouldBe JsonArray(
                listOf(
                    limit("HOUR", 50, 0, "2026-11-14T13:00:00Z"),
                    limit("MONTH", 500, 0, "2026-12-01T00:00:00Z", unit = "UNIQUE_SYMBOLS"),
                ),
            )
            body.item(logoapi)["limits"] shouldBe JsonArray(emptyList())
            body.item(secEdgar)["roles"] shouldBe JsonArray(emptyList())
        }
    }

    test("a source with automatic requests off is marked, sources without the setting have them on") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/sources").json()

            body.item(tiingo)["automaticRequests"] shouldBe JsonPrimitive(false)
            body.item(secEdgar)["automaticRequests"] shouldBe JsonPrimitive(true)
            body.item(finnhubLogos)["automaticRequests"] shouldBe JsonPrimitive(true)
        }
    }

    test("only the default search source is marked as default") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/sources").json()

            body.items().filter { it["isDefault"] == JsonPrimitive(true) }.map { it["id"] } shouldContainExactly
                listOf(JsonPrimitive("finnhub"))
        }
    }

    test("each source lists the roles it is the default source of, in role order") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/sources").json()

            body.item(eodhd)["defaultFor"] shouldBe JsonArray(listOf(JsonPrimitive("INTERNATIONAL")))
            body.item(eodhd)["roles"] shouldBe JsonArray(listOf(JsonPrimitive("INTERNATIONAL")))
            body.item(eodhd)["isDefault"] shouldBe JsonPrimitive(false)
            body.item(eodhd)["automaticRequests"] shouldBe JsonPrimitive(false)
            body.item(tiingo)["roles"] shouldBe JsonArray(listOf(JsonPrimitive("US"), JsonPrimitive("INTERNATIONAL")))
            body.item(tiingo)["defaultFor"] shouldBe JsonArray(emptyList())
            body.item(secEdgar)["defaultFor"] shouldBe JsonArray(emptyList())
        }
    }

    test("the default search source is marked for a different search default") {
        val config = sourcesConfig().let { it.copy(defaultSources = it.defaultSources.copy(search = tiingo)) }
        routeTest(config = config.copy(sources = config.sources.map { source ->
            if (source.id == tiingo) source.copy(roles = SourceRole.entries.toSet()) else source
        })) { ports ->
            val saved = ports.savedProfile()

            val body = client.get("/profiles/${saved.id}/sources").json()

            body.item(tiingo)["isDefault"] shouldBe JsonPrimitive(true)
            body.item(tiingo)["defaultFor"] shouldBe JsonArray(listOf(JsonPrimitive("SEARCH")))
            body.item(finnhub)["isDefault"] shouldBe JsonPrimitive(false)
            body.item(finnhub)["defaultFor"] shouldBe JsonArray(listOf(JsonPrimitive("US")))
        }
    }

    test("used calls of the current window; a new window starts unused") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()
            ports.requestCounters.setCount(finnhub, LimitInterval.MINUTE, ports.clock.now(), 42)

            client.get("/profiles/${saved.id}/sources").json().usedOf(finnhub) shouldBe JsonPrimitive(42)

            ports.clock.advanceBy(Duration.ofMinutes(1))
            val next = client.get("/profiles/${saved.id}/sources").json().item(finnhub)
            next.getValue("limits") shouldBe JsonArray(listOf(limit("MINUTE", 60, 0, "2026-11-14T12:02:00Z")))
        }
    }

    context("last call outcome") {
        val at = Instant.parse("2026-11-14T11:59:30Z")
        withData(
            nameFn = { it.first },
            Triple("success", SourceCallOutcome.success(at), Pair<String, Int?>("SUCCESS", null)),
            Triple("HTTP error with code", SourceCallOutcome.httpError(429, at), Pair<String, Int?>("HTTP_ERROR", 429)),
            Triple("timeout", SourceCallOutcome.timeout(at), Pair<String, Int?>("TIMEOUT", null)),
            Triple("application error", SourceCallOutcome.appError(at), Pair<String, Int?>("APP_ERROR", null)),
        ) { (_, outcome, expected) ->
            routeTest(config = sourcesConfig()) { ports ->
                val saved = ports.savedProfile()
                ports.sourceCalls.record(finnhub, outcome)

                val body = client.get("/profiles/${saved.id}/sources").json()

                body.item(finnhub)["lastCall"] shouldBe JsonObject(
                    mapOf(
                        "result" to JsonPrimitive(expected.first),
                        "httpStatus" to (expected.second?.let { JsonPrimitive(it) } ?: JsonNull),
                        "at" to JsonPrimitive("2026-11-14T11:59:30Z"),
                    ),
                )
                body.item(secEdgar)["lastCall"] shouldBe JsonNull
            }
        }
    }

    test("unreadable counters degrade to unused limits") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()
            ports.requestCounters.setCount(finnhub, LimitInterval.MINUTE, ports.clock.now(), 42)
            ports.requestCounters.failUsage = true

            val response = client.get("/profiles/${saved.id}/sources")

            response.status shouldBe HttpStatusCode.OK
            response.json().item(finnhub)["limits"] shouldBe JsonArray(listOf(limit("MINUTE", 60, 0, "2026-11-14T12:01:00Z")))
        }
    }

    test("unreadable call outcomes degrade to never called") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()
            ports.sourceCalls.record(finnhub, SourceCallOutcome.success(ports.clock.now()))
            ports.sourceCalls.failReads = true

            val response = client.get("/profiles/${saved.id}/sources")

            response.status shouldBe HttpStatusCode.OK
            response.json().items().map { it["lastCall"] }.toSet() shouldBe setOf(JsonNull)
        }
    }

    test("total calls are reported for sources with a total counter only") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()
            ports.requestCounters.incrementTotal(finnhubLogos, 7)
            ports.requestCounters.incrementTotal(finnhub, 3)

            val body = client.get("/profiles/${saved.id}/sources").json()

            body.item(finnhubLogos)["totalCalls"] shouldBe JsonPrimitive(7)
            body.item(finnhub)["totalCalls"] shouldBe JsonNull
            body.item(logoapi)["totalCalls"] shouldBe JsonNull
        }
    }

    test("a source with a total counter that was never called reports zero calls") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()

            client.get("/profiles/${saved.id}/sources").json().item(finnhubLogos)["totalCalls"] shouldBe JsonPrimitive(0)
        }
    }

    test("unreadable total counters degrade to null") {
        routeTest(config = sourcesConfig()) { ports ->
            val saved = ports.savedProfile()
            ports.requestCounters.failTotals = true

            val response = client.get("/profiles/${saved.id}/sources")

            response.status shouldBe HttpStatusCode.OK
            response.json().item(finnhubLogos)["totalCalls"] shouldBe JsonNull
        }
    }

    test("malformed profile ID is rejected with 400") {
        routeTest(config = sourcesConfig()) {
            client.get("/profiles/not-a-uuid/sources").shouldBeProblem(400)
        }
    }

    test("unknown profile ID is answered with 404") {
        routeTest(config = sourcesConfig()) {
            client.get("/profiles/${UUID.randomUUID()}/sources").shouldBeProblem(404)
        }
    }
})
