package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private const val UNKNOWN_PROFILE = "3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10"

private suspend fun io.ktor.server.testing.ApplicationTestBuilder.createProfile(body: String) =
    client.post("/profiles") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

class ProfileRoutesTest : FunSpec({

    context("POST /profiles") {
        test("new name answers 201 with the created profile") {
            routeTest { ports ->
                val response = createProfile("""{"name":"  Alice "}""")

                response.status shouldBe HttpStatusCode.Created
                val body = response.json()
                body.keys shouldBe setOf("id", "name", "defaultSource")
                body["name"] shouldBe JsonPrimitive("Alice")
                body["defaultSource"] shouldBe JsonPrimitive("finnhub")
                val id = UUID.fromString(body["id"]!!.jsonPrimitive.content)
                ports.profiles.findById(com.sixteenscreener.domain.model.ProfileId(id)) shouldNotBe null
            }
        }

        test("existing name in another case answers 200 with the existing profile") {
            routeTest {
                val created = createProfile("""{"name":"Alice"}""").json()

                val response = createProfile("""{"name":"ALICE "}""")

                response.status shouldBe HttpStatusCode.OK
                response.json() shouldBe created
            }
        }

        context("invalid bodies answer 400 problem details") {
            withData(
                mapOf(
                    "blank name" to """{"name":"   "}""",
                    "name of 41 characters" to """{"name":"${"x".repeat(41)}"}""",
                    "missing name" to """{}""",
                    "null name" to """{"name":null}""",
                    "number as name" to """{"name":5}""",
                    "malformed JSON" to """{"name":""",
                    "empty body" to "",
                ),
            ) { body ->
                routeTest { createProfile(body).shouldBeProblem(400) }
            }
        }

        test("name of 40 characters is accepted") {
            routeTest { createProfile("""{"name":"${"x".repeat(40)}"}""").status shouldBe HttpStatusCode.Created }
        }

        test("unsupported content type answers 415 problem details") {
            routeTest {
                client.post("/profiles") {
                    contentType(ContentType.Text.Plain)
                    setBody("Alice")
                }.shouldBeProblem(415)
            }
        }
    }

    context("GET /profiles/{profileId}") {
        test("stored profile is returned") {
            routeTest { ports ->
                val profile = ports.savedProfile("Bob")

                val response = client.get("/profiles/${profile.id}")

                response.status shouldBe HttpStatusCode.OK
                response.json()["id"] shouldBe JsonPrimitive(profile.id.toString())
                response.json()["name"] shouldBe JsonPrimitive("Bob")
                response.json()["defaultSource"] shouldBe JsonPrimitive("finnhub")
            }
        }
    }

    context("profile-scoped routes") {
        data class Route(val method: HttpMethod, val path: String)

        val routes = listOf(
            Route(HttpMethod.Get, "/profiles/{id}"),
            Route(HttpMethod.Get, "/profiles/{id}/watchlist"),
            Route(HttpMethod.Put, "/profiles/{id}/watchlist/AAPL"),
            Route(HttpMethod.Delete, "/profiles/{id}/watchlist/AAPL"),
        )

        context("malformed profile ID answers 400") {
            withData(
                nameFn = { (route, id) -> "${route.method.value} ${route.path} with '$id'" },
                routes.flatMap { route -> listOf("not-a-uuid", "12345", "3f2b8c1e6d4a4f7e9b1a2c5d8e7f9a10").map { route to it } },
            ) { (route, id) ->
                routeTest { client.request(route.path.replace("{id}", id)) { method = route.method }.shouldBeProblem(400) }
            }
        }

        context("unknown profile ID answers 404") {
            withData(nameFn = { "${it.method.value} ${it.path}" }, routes) { route ->
                routeTest { ports ->
                    ports.symbolDirectory.add(ListedSymbol(Symbol.of("AAPL"), "APPLE INC", "XNAS", "USD", null))
                    client.request(route.path.replace("{id}", UNKNOWN_PROFILE)) { method = route.method }
                        .shouldBeProblem(404)
                }
            }
        }
    }

    context("watchlist changes") {
        test("PUT adds a known symbol with its sector and answers the stored WatchlistItem") {
            routeTest(config = profileCapableConfig()) { ports ->
                val profile = ports.savedProfile()
                ports.symbolDirectory.add(ListedSymbol(Symbol.of("AAPL"), "APPLE INC", "XNAS", "USD", null))
                ports.marketData.profiles[Symbol.of("AAPL")] = SourceResult.Success(
                    CompanyProfile("Apple Inc", "US", "USD", "Technology", null, null, null, null, null, null, null),
                    SourceCallOutcome.success(ports.clock.now()),
                )

                val response = client.put("/profiles/${profile.id}/watchlist/AAPL")

                response.status shouldBe HttpStatusCode.OK
                response.json() shouldBe JsonObject(
                    mapOf(
                        "symbol" to JsonPrimitive("AAPL"),
                        "ticker" to JsonPrimitive("AAPL"),
                        "exchange" to JsonPrimitive("NASDAQ"),
                        "name" to JsonPrimitive("APPLE INC"),
                        "market" to JsonPrimitive("US"),
                        "logoUrl" to JsonPrimitive("/logos/AAPL"),
                        "sector" to JsonPrimitive("Technology"),
                        "tags" to JsonArray(emptyList()),
                    ),
                )
                ports.profiles.findById(profile.id)!!.watchlist shouldContainExactly
                    listOf(watchlistEntry("AAPL", sector = "Technology"))
            }
        }

        test("PUT is idempotent and makes no source request for a symbol already in the watchlist") {
            routeTest(config = profileCapableConfig()) { ports ->
                val profile = ports.savedProfile()
                ports.symbolDirectory.add(ListedSymbol(Symbol.of("AAPL"), "APPLE INC", "XNAS", "USD", null))
                ports.marketData.profiles[Symbol.of("AAPL")] = SourceResult.Success(
                    CompanyProfile("Apple Inc", "US", "USD", "Technology", null, null, null, null, null, null, null),
                    SourceCallOutcome.success(ports.clock.now()),
                )

                val first = client.put("/profiles/${profile.id}/watchlist/AAPL").json()
                val second = client.put("/profiles/${profile.id}/watchlist/AAPL")

                second.status shouldBe HttpStatusCode.OK
                second.json() shouldBe first
                ports.marketData.calls shouldContainExactly listOf("profile:AAPL")
                ports.profiles.findById(profile.id)!!.watchlistSymbols shouldContainExactly listOf(Symbol.of("AAPL"))
            }
        }

        test("PUT still answers 200 with a null sector when the source fails") {
            routeTest(config = profileCapableConfig()) { ports ->
                val profile = ports.savedProfile()
                ports.symbolDirectory.add(ListedSymbol(Symbol.of("AAPL"), "APPLE INC", "XNAS", "USD", null))
                ports.marketData.profiles[Symbol.of("AAPL")] =
                    SourceResult.SourceError(SourceCallOutcome.httpError(500, ports.clock.now()))

                val response = client.put("/profiles/${profile.id}/watchlist/AAPL")

                response.status shouldBe HttpStatusCode.OK
                response.json()["sector"] shouldBe JsonNull
                ports.profiles.findById(profile.id)!!.watchlist shouldContainExactly listOf(watchlistEntry("AAPL"))
            }
        }

        test("PUT of an unknown symbol answers 404") {
            routeTest { ports ->
                val profile = ports.savedProfile()

                client.put("/profiles/${profile.id}/watchlist/NOPE").shouldBeProblem(404)
            }
        }

        test("PUT and DELETE with an invalid symbol answer 400") {
            routeTest { ports ->
                val profile = ports.savedProfile()

                client.put("/profiles/${profile.id}/watchlist/aapl").shouldBeProblem(400)
                client.delete("/profiles/${profile.id}/watchlist/${"A".repeat(21)}").shouldBeProblem(400)
            }
        }

        test("PUT to a full watchlist answers 400") {
            routeTest { ports ->
                val profile = ports.savedProfile(watchlist = (1..Profile.MAX_WATCHLIST_SIZE).map { "S$it" })
                ports.symbolDirectory.add(ListedSymbol(Symbol.of("AAPL"), "APPLE INC", "XNAS", "USD", null))

                client.put("/profiles/${profile.id}/watchlist/AAPL").shouldBeProblem(400)
            }
        }

        test("DELETE removes the symbol and is idempotent for a missing symbol") {
            routeTest { ports ->
                val profile = ports.savedProfile(watchlist = listOf("AAPL", "MSFT"))

                client.delete("/profiles/${profile.id}/watchlist/AAPL").status shouldBe HttpStatusCode.NoContent
                client.delete("/profiles/${profile.id}/watchlist/NOPE").status shouldBe HttpStatusCode.NoContent

                ports.profiles.findById(profile.id)!!.watchlistSymbols shouldContainExactly listOf(Symbol.of("MSFT"))
            }
        }

        test("DELETE of the last symbol leaves an empty watchlist") {
            routeTest { ports ->
                val profile = ports.savedProfile(watchlist = listOf("AAPL"))

                client.delete("/profiles/${profile.id}/watchlist/AAPL")

                ports.profiles.findById(profile.id)!!.watchlist.shouldBeEmpty()
            }
        }
    }

    test("source IDs are rendered as configured") {
        routeTest { ports ->
            val profile = ports.savedProfile().copy(defaultSource = SourceId.of("finnhub")).also(ports.profiles::save)

            client.get("/profiles/${profile.id}").json()["defaultSource"] shouldBe JsonPrimitive("finnhub")
        }
    }
})
