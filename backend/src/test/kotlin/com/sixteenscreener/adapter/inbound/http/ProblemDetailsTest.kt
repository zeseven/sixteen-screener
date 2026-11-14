package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.module
import com.sixteenscreener.testServices
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

private fun problemTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application {
        module(testServices())
        routing {
            get("/test/invalid-symbol") { Symbol.of(call.parameters["symbol"] ?: "lower") }
            get("/test/invalid-input") { throw InvalidInputException("Name must not be blank") }
            get("/test/bad-request") { throw BadRequestException("Malformed body") }
            get("/test/not-found") { throw ResourceNotFoundException("Profile 42 not found") }
            get("/test/crash") { error("secret internal state") }
        }
    }
    block()
}

private suspend fun HttpResponse.problem(): Problem = Json.decodeFromString(Problem.serializer(), bodyAsText())

class ProblemDetailsTest : FunSpec({

    test("domain validation error maps to 400 problem details") {
        problemTest {
            val response = client.get("/test/invalid-input")

            response.status shouldBe HttpStatusCode.BadRequest
            response.contentType()?.withoutParameters() shouldBe ContentType("application", "problem+json")
            response.problem() shouldBe Problem(title = "Bad Request", status = 400, detail = "Name must not be blank")
        }
    }

    test("invalid symbol maps to 400") {
        problemTest {
            val response = client.get("/test/invalid-symbol")

            response.status shouldBe HttpStatusCode.BadRequest
            response.problem().detail shouldBe "Invalid symbol 'lower'"
        }
    }

    test("Ktor bad request maps to 400") {
        problemTest {
            client.get("/test/bad-request").problem().status shouldBe 400
        }
    }

    test("missing resource maps to 404 problem details") {
        problemTest {
            val response = client.get("/test/not-found")

            response.status shouldBe HttpStatusCode.NotFound
            response.contentType()?.withoutParameters() shouldBe ContentType("application", "problem+json")
            response.problem() shouldBe Problem(title = "Not Found", status = 404, detail = "Profile 42 not found")
        }
    }

    test("unknown route maps to 404 problem details") {
        problemTest {
            val response = client.get("/no/such/route")

            response.status shouldBe HttpStatusCode.NotFound
            response.contentType()?.withoutParameters() shouldBe ContentType("application", "problem+json")
            response.problem().title shouldBe "Not Found"
        }
    }

    test("unexpected exception maps to 500 without internals") {
        problemTest {
            val response = client.get("/test/crash")

            response.status shouldBe HttpStatusCode.InternalServerError
            response.contentType()?.withoutParameters() shouldBe ContentType("application", "problem+json")
            val body = response.bodyAsText()
            body shouldNotContain "secret internal state"
            body shouldNotContain "IllegalStateException"
            body shouldBe """{"title":"Internal Server Error","status":500}"""
            response.problem() shouldBe Problem(title = "Internal Server Error", status = 500, detail = null)
        }
    }

    test("wrong method on an existing route answers with problem details") {
        problemTest {
            val response = client.post("/test/not-found")

            response.contentType()?.withoutParameters() shouldBe ContentType("application", "problem+json")
            response.problem().status shouldBe response.status.value
        }
    }
})
