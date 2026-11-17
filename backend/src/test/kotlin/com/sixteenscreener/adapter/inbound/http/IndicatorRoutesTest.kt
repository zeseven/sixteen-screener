package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.contract.tunedDefaults
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.service.IndicatorDefaults
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val UNKNOWN_PROFILE = "3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10"

private const val CUSTOM_PE =
    """{"ratioCode":"PE","enabled":false,"thresholds":[10,20,30],"levels":["GOOD","GOOD","WARNING","BAD"],"belowZeroLevel":"WARNING"}"""

private suspend fun ApplicationTestBuilder.putSettings(profileId: Any, body: String): HttpResponse =
    client.put("/profiles/$profileId/indicator-settings") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

private fun JsonObject.settings(): List<JsonObject> = getValue("settings").jsonArray.map { it.jsonObject }

private fun numbers(vararg values: Double) = JsonArray(values.map { JsonPrimitive(it) })

private fun strings(vararg values: String) = JsonArray(values.map { JsonPrimitive(it) })

class IndicatorRoutesTest : FunSpec({

    context("GET /profiles/{profileId}/indicator-settings") {
        test("answers all indicator ratios in settings order with their metadata and scale") {
            routeTest { ports ->
                val saved = ports.savedProfile()

                val response = client.get("/profiles/${saved.id}/indicator-settings")

                response.status shouldBe HttpStatusCode.OK
                val body = response.json()
                body.keys shouldBe setOf("settings")
                val settings = body.settings()
                settings.map { it["ratioCode"] } shouldContainExactly RatioCode.indicators.map { JsonPrimitive(it.name) }
                settings.first() shouldBe JsonObject(
                    mapOf(
                        "ratioCode" to JsonPrimitive("PE"),
                        "enabled" to JsonPrimitive(true),
                        "thresholds" to numbers(15.0, 25.0, 40.0),
                        "levels" to strings("GOOD", "NEUTRAL", "WARNING", "BAD"),
                        "belowZeroLevel" to JsonPrimitive("BAD"),
                        "name" to JsonPrimitive("P/E"),
                        "shortName" to JsonPrimitive("P/E"),
                        "unit" to JsonPrimitive("RATIO"),
                        "category" to JsonPrimitive("VALUATION"),
                        "scaleMax" to JsonPrimitive(60.0),
                        "step" to JsonPrimitive(1.0),
                    ),
                )
                val roe = settings.first { it["ratioCode"] == JsonPrimitive("ROE") }
                roe["name"] shouldBe JsonPrimitive("ROE")
                roe["unit"] shouldBe JsonPrimitive("PERCENT")
                roe["category"] shouldBe JsonPrimitive("PROFITABILITY")
                settings.first { it["ratioCode"] == JsonPrimitive("EV_EBITDA") }["shortName"] shouldBe JsonPrimitive("EV/EB")
                ports.profiles.findById(saved.id)?.indicatorSettings shouldBe IndicatorDefaults.all.map { it.setting }
            }
        }

        test("scale comes from the stored defaults") {
            routeTest { ports ->
                ports.indicatorDefaults.stored = tunedDefaults
                val saved = ports.savedProfile()

                val settings = client.get("/profiles/${saved.id}/indicator-settings").json().settings()

                settings.first()["scaleMax"] shouldBe JsonPrimitive(120.0)
                settings.first()["enabled"] shouldBe JsonPrimitive(false)
            }
        }

        test("unknown profile answers 404") {
            routeTest {
                client.get("/profiles/$UNKNOWN_PROFILE/indicator-settings").shouldBeProblem(404)
            }
        }

        test("invalid profile ID answers 400") {
            routeTest {
                client.get("/profiles/not-a-uuid/indicator-settings").shouldBeProblem(400)
            }
        }
    }

    context("PUT /profiles/{profileId}/indicator-settings") {
        test("replaces the given ratios, keeps the others and answers all stored settings") {
            routeTest { ports ->
                val saved = ports.savedProfile()

                val response = putSettings(saved.id, """{"settings":[$CUSTOM_PE]}""")

                response.status shouldBe HttpStatusCode.OK
                val settings = response.json().settings()
                settings.map { it["ratioCode"] } shouldContainExactly RatioCode.indicators.map { JsonPrimitive(it.name) }
                settings.first()["enabled"] shouldBe JsonPrimitive(false)
                settings.first()["thresholds"] shouldBe numbers(10.0, 20.0, 30.0)
                settings.first()["levels"] shouldBe strings("GOOD", "GOOD", "WARNING", "BAD")
                settings.first()["belowZeroLevel"] shouldBe JsonPrimitive("WARNING")
                settings.first()["scaleMax"] shouldBe JsonPrimitive(60.0)
                settings[8]["enabled"] shouldBe JsonPrimitive(true)
                val stored = ports.profiles.findById(saved.id)?.indicatorSettings.orEmpty()
                stored.first().levels shouldContainExactly
                    listOf(IndicatorLevel.GOOD, IndicatorLevel.GOOD, IndicatorLevel.WARNING, IndicatorLevel.BAD)
                stored.drop(1) shouldContainExactly IndicatorDefaults.all.drop(1).map { it.setting }
                client.get("/profiles/${saved.id}/indicator-settings").json().settings() shouldBe settings
            }
        }

        test("an empty update answers the stored settings") {
            routeTest { ports ->
                val saved = ports.savedProfile()

                val response = putSettings(saved.id, """{"settings":[]}""")

                response.status shouldBe HttpStatusCode.OK
                response.json().settings().size shouldBe 31
            }
        }

        context("invalid updates answer 400 problem details and change nothing") {
            withData(
                mapOf(
                    "unknown ratio code" to CUSTOM_PE.replace("\"PE\"", "\"XYZ\""),
                    "per share ratio" to CUSTOM_PE.replace("\"PE\"", "\"EPS\""),
                    "repeated ratio" to "$CUSTOM_PE,$CUSTOM_PE",
                    "2 thresholds" to CUSTOM_PE.replace("[10,20,30]", "[10,20]"),
                    "4 thresholds" to CUSTOM_PE.replace("[10,20,30]", "[10,20,30,40]"),
                    "3 levels" to CUSTOM_PE.replace("\"GOOD\",\"GOOD\",", "\"GOOD\","),
                    "unknown level" to CUSTOM_PE.replace("\"WARNING\",\"BAD\"", "\"UGLY\",\"BAD\""),
                    "unknown below zero level" to CUSTOM_PE.replace("\"belowZeroLevel\":\"WARNING\"", "\"belowZeroLevel\":\"x\""),
                    "unordered thresholds" to CUSTOM_PE.replace("[10,20,30]", "[20,10,30]"),
                    "threshold above the scale maximum" to CUSTOM_PE.replace("[10,20,30]", "[10,20,61]"),
                    "negative threshold" to CUSTOM_PE.replace("[10,20,30]", "[-1,20,30]"),
                    "missing levels" to CUSTOM_PE.replace(",\"levels\":[\"GOOD\",\"GOOD\",\"WARNING\",\"BAD\"]", ""),
                    "threshold null" to CUSTOM_PE.replace("[10,20,30]", "[null,20,30]"),
                ),
            ) { setting ->
                routeTest { ports ->
                    val saved = ports.savedProfile()

                    putSettings(saved.id, """{"settings":[$setting]}""").shouldBeProblem(400)

                    ports.profiles.findById(saved.id)?.indicatorSettings shouldBe emptyList()
                }
            }
        }

        test("body without settings answers 400") {
            routeTest { ports ->
                putSettings(ports.savedProfile().id, "{}").shouldBeProblem(400)
            }
        }

        test("unknown profile answers 404") {
            routeTest {
                putSettings(UNKNOWN_PROFILE, """{"settings":[$CUSTOM_PE]}""").shouldBeProblem(404)
            }
        }
    }
})
