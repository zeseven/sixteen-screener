package com.sixteenscreener.adapter.inbound.http

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun JsonObject.items(): List<JsonObject> = getValue("items").jsonArray.map { it.jsonObject }

private fun JsonObject.itemWithSuffix(suffix: String): JsonObject =
    items().single { it["suffix"] == JsonPrimitive(suffix) }

private fun exchange(
    suffix: String,
    shortName: String,
    fullName: String,
    mic: String,
    currency: String,
    timeZone: String,
    market: String?,
) = JsonObject(
    mapOf(
        "suffix" to JsonPrimitive(suffix),
        "shortName" to JsonPrimitive(shortName),
        "fullName" to JsonPrimitive(fullName),
        "mic" to JsonPrimitive(mic),
        "currency" to JsonPrimitive(currency),
        "timeZone" to JsonPrimitive(timeZone),
        "market" to (market?.let(::JsonPrimitive) ?: JsonNull),
    ),
)

class ExchangeRoutesTest : FunSpec({

    test("the exchange table in the exact shape; a null market is present") {
        routeTest {
            val response = client.get("/exchanges")

            response.status shouldBe HttpStatusCode.OK
            val body = response.json()
            body.keys shouldBe setOf("items")
            body.itemWithSuffix("V") shouldBe
                exchange("V", "TSXV", "TSX Venture Exchange", "XTSX", "CAD", "America/Toronto", null)
            body.itemWithSuffix("AS") shouldBe
                exchange("AS", "AMS", "Euronext Amsterdam", "XAMS", "EUR", "Europe/Amsterdam", "EU")
        }
    }

    test("US exchanges have an empty suffix and come first") {
        routeTest {
            val items = client.get("/exchanges").json().items()

            items.first()["suffix"] shouldBe JsonPrimitive("")
            items.single { it["mic"] == JsonPrimitive("XNYS") } shouldBe
                exchange("", "NYSE", "New York Stock Exchange", "XNYS", "USD", "America/New_York", "US")
        }
    }

    test("suffixes of the same exchange give separate rows") {
        routeTest {
            val items = client.get("/exchanges").json().items()

            items.filter { it["mic"] == JsonPrimitive("XLON") }.map { it.getValue("suffix").jsonPrimitive.content } shouldBe
                listOf("IL", "L")
        }
    }

    test("needs no profile") {
        routeTest {
            client.get("/profiles/00000000-0000-0000-0000-000000000000/exchanges").status shouldBe HttpStatusCode.NotFound
            client.get("/exchanges").status shouldBe HttpStatusCode.OK
        }
    }
})
