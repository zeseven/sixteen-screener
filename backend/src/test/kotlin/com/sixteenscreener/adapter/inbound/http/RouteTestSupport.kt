package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.TestPorts
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.module
import com.sixteenscreener.testConfig
import com.sixteenscreener.testServices
import com.sixteenscreener.watchlistEntries
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

fun routeTest(
    ports: TestPorts = TestPorts(),
    config: AppConfig = testConfig(),
    block: suspend ApplicationTestBuilder.(TestPorts) -> Unit,
) = testApplication {
    application { module(testServices(ports, config)) }
    block(ports)
}

fun profileCapableConfig(): AppConfig = testConfig().let { config ->
    config.copy(sources = config.sources.map { it.copy(capabilities = it.capabilities + Capability.COMPANY_PROFILE) })
}

fun TestPorts.savedProfile(name: String = "Alice", watchlist: List<String> = emptyList()): Profile =
    Profile(
        id = ProfileId.random(),
        name = ProfileName.of(name),
        watchlist = watchlistEntries(watchlist),
        createdAt = clock.now(),
        updatedAt = clock.now(),
    ).also(profiles::save)

suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

suspend fun HttpResponse.shouldBeProblem(status: Int) {
    this.status.value shouldBe status
    contentType()?.withoutParameters() shouldBe ContentType("application", "problem+json")
    json()["status"].toString() shouldBe status.toString()
}
