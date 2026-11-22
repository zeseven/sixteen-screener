package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.TestPorts
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.Tag
import com.sixteenscreener.domain.model.WatchlistEntry
import com.sixteenscreener.watchlistEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val UNKNOWN_PROFILE = "3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10"

private val aapl = Symbol.of("AAPL")

private fun tagJson(icon: String, text: String) =
    JsonObject(mapOf("icon" to JsonPrimitive(icon), "text" to JsonPrimitive(text)))

private fun tagListJson(vararg tags: JsonObject) = JsonObject(mapOf("tags" to JsonArray(tags.toList())))

private fun tagListBody(vararg tags: Pair<String, String>) =
    tagListJson(*tags.map { (icon, text) -> tagJson(icon, text) }.toTypedArray()).toString()

private suspend fun ApplicationTestBuilder.putTags(profileId: String, symbol: String, body: String) =
    client.put("/profiles/$profileId/companies/$symbol/tags") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

private fun TestPorts.profileWith(vararg entries: WatchlistEntry) =
    savedProfile().copy(watchlist = entries.toList()).also(profiles::save)

class TagRoutesTest : FunSpec({

    context("PUT /profiles/{profileId}/companies/{symbol}/tags") {
        test("replaces the tags of a watchlist company and answers the trimmed tags") {
            routeTest { ports ->
                val profile = ports.profileWith(watchlistEntry("AAPL", tags = listOf(Tag("star", "old"))))

                val response = putTags(profile.id.toString(), "AAPL", tagListBody("chess-king" to "  Dividend king ", "star" to "core"))

                response.status shouldBe HttpStatusCode.OK
                response.json() shouldBe tagListJson(tagJson("chess-king", "Dividend king"), tagJson("star", "core"))
                ports.profiles.findById(profile.id)!!.watchlistEntry(aapl)!!.tags shouldContainExactly
                    listOf(Tag("chess-king", "Dividend king"), Tag("star", "core"))
            }
        }

        test("an empty list removes all tags") {
            routeTest { ports ->
                val profile = ports.profileWith(watchlistEntry("AAPL", tags = listOf(Tag("star", "old"))))

                val response = putTags(profile.id.toString(), "AAPL", """{"tags":[]}""")

                response.status shouldBe HttpStatusCode.OK
                response.json() shouldBe tagListJson()
                ports.profiles.findById(profile.id)!!.watchlistEntry(aapl)!!.tags.shouldBeEmpty()
            }
        }

        test("company outside the watchlist answers 400") {
            routeTest { ports ->
                val profile = ports.profileWith(watchlistEntry("MSFT"))

                putTags(profile.id.toString(), "AAPL", tagListBody("star" to "core")).shouldBeProblem(400)
            }
        }

        context("invalid tags answer 400 and keep the stored tags") {
            withData(
                mapOf(
                    "blank text" to tagListBody("star" to "   "),
                    "text of 101 characters" to tagListBody("star" to "x".repeat(101)),
                    "icon with upper case" to tagListBody("Star" to "core"),
                    "icon of 41 characters" to tagListBody("a".repeat(41) to "core"),
                    "21 tags" to tagListBody(*(1..21).map { "star" to "t$it" }.toTypedArray()),
                    "same tag ignoring case" to tagListBody("star" to "Core", "star" to " cORE"),
                    "missing text" to """{"tags":[{"icon":"star"}]}""",
                    "missing tags" to "{}",
                    "malformed JSON" to "{",
                ),
            ) { body ->
                routeTest { ports ->
                    val profile = ports.profileWith(watchlistEntry("AAPL", tags = listOf(Tag("star", "old"))))

                    putTags(profile.id.toString(), "AAPL", body).shouldBeProblem(400)
                    ports.profiles.findById(profile.id)!!.watchlistEntry(aapl)!!.tags shouldContainExactly listOf(Tag("star", "old"))
                }
            }
        }

        test("text of exactly 100 characters and 20 tags are accepted") {
            routeTest { ports ->
                val profile = ports.profileWith(watchlistEntry("AAPL"))
                val tags = listOf("star" to "x".repeat(100)) + (2..20).map { "star" to "t$it" }

                val response = putTags(profile.id.toString(), "AAPL", tagListBody(*tags.toTypedArray()))

                response.status shouldBe HttpStatusCode.OK
                ports.profiles.findById(profile.id)!!.watchlistEntry(aapl)!!.tags.size shouldBe 20
            }
        }

        test("unknown profile answers 404") {
            routeTest {
                putTags(UNKNOWN_PROFILE, "AAPL", tagListBody("star" to "core")).shouldBeProblem(404)
            }
        }

        test("invalid symbol answers 400") {
            routeTest { ports ->
                val profile = ports.savedProfile()

                putTags(profile.id.toString(), "aapl", tagListBody("star" to "core")).shouldBeProblem(400)
            }
        }

        test("makes no source requests") {
            routeTest { ports ->
                val profile = ports.profileWith(watchlistEntry("AAPL"))

                putTags(profile.id.toString(), "AAPL", tagListBody("star" to "core")).status shouldBe HttpStatusCode.OK
                ports.marketData.calls.shouldBeEmpty()
                ports.internationalMarketData.calls.shouldBeEmpty()
            }
        }
    }

    context("GET /profiles/{profileId}/tags") {
        test("answers the distinct tags of the profile sorted by text ignoring case, then by icon") {
            routeTest { ports ->
                val profile = ports.profileWith(
                    watchlistEntry("AAPL", tags = listOf(Tag("star", "core"), Tag("bank", "Dividend"))),
                    watchlistEntry("MSFT", tags = listOf(Tag("star", "CORE"), Tag("gem", "core"))),
                )

                val response = client.get("/profiles/${profile.id}/tags")

                response.status shouldBe HttpStatusCode.OK
                response.json() shouldBe tagListJson(tagJson("gem", "core"), tagJson("star", "core"), tagJson("bank", "Dividend"))
                ports.marketData.calls.shouldBeEmpty()
            }
        }

        test("a profile without tags answers an empty list") {
            routeTest { ports ->
                val profile = ports.savedProfile(watchlist = listOf("AAPL"))

                client.get("/profiles/${profile.id}/tags").json() shouldBe tagListJson()
            }
        }

        test("unknown profile answers 404") {
            routeTest {
                client.get("/profiles/$UNKNOWN_PROFILE/tags").shouldBeProblem(404)
            }
        }
    }
})
