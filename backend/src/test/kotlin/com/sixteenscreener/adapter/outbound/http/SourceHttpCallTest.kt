package com.sixteenscreener.adapter.outbound.http

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.net.ConnectException
import java.net.ServerSocket
import java.nio.channels.UnresolvedAddressException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class PriceDto(val c: Double)

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class SourceHttpCallTest : FunSpec({
    val clock = MutableClock()
    val now = clock.now()

    fun callWith(handler: MockRequestHandler) =
        SourceHttpCall(SourceHttpClient.create(MockEngine(handler)), timeout = 200.milliseconds, clock = clock)

    suspend fun SourceHttpCall.price(errorStatus: ErrorStatusMapping = ErrorStatusMapping.DEFAULT): SourceResult<Double> =
        execute({ url("https://source.test/quote") }, errorStatus) { response -> response.body<PriceDto>().c.takeIf { it > 0 } }

    test("2XX response mapped to data is a success") {
        val result = callWith { respond("""{"c": 12.5, "unknown": 1}""", HttpStatusCode.OK, jsonHeaders) }.price()

        result shouldBe SourceResult.Success(12.5, SourceCallOutcome.success(now))
    }

    test("2XX response mapped to null is no data with a success outcome") {
        val result = callWith { respond("""{"c": 0}""", HttpStatusCode.OK, jsonHeaders) }.price()

        result shouldBe SourceResult.NoData(SourceCallOutcome.success(now))
    }

    context("responses outside 2XX are HTTP errors with the status code") {
        withData(
            nameFn = { "status $it" },
            HttpStatusCode.MovedPermanently,
            HttpStatusCode.NotModified,
            HttpStatusCode.BadRequest,
            HttpStatusCode.Forbidden,
            HttpStatusCode.NotFound,
            HttpStatusCode.InternalServerError,
            HttpStatusCode.ServiceUnavailable,
        ) { status ->
            val result = callWith {
                respond("", status, headersOf(HttpHeaders.Location, "https://elsewhere.test"))
            }.price()

            result shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(status.value, now))
        }
    }

    test("429 is limit reached rejected by the source") {
        val result = callWith { respond("", HttpStatusCode.TooManyRequests) }.price()

        val limitReached = result.shouldBeInstanceOf<SourceResult.LimitReached>()
        limitReached.outcome shouldBe SourceCallOutcome.httpError(429, now)
        limitReached.rejectedBySource shouldBe true
    }

    test("error status mapping can override single statuses") {
        val mapping = ErrorStatusMapping.overriding(
            403 to { outcome -> SourceResult.NotSupported(outcome) },
            404 to { outcome -> SourceResult.NoData(outcome) },
        )

        callWith { respond("", HttpStatusCode.Forbidden) }.price(mapping) shouldBe
            SourceResult.NotSupported(SourceCallOutcome.httpError(403, now))
        callWith { respond("", HttpStatusCode.NotFound) }.price(mapping) shouldBe
            SourceResult.NoData(SourceCallOutcome.httpError(404, now))
        callWith { respond("", HttpStatusCode.BadGateway) }.price(mapping) shouldBe
            SourceResult.SourceError(SourceCallOutcome.httpError(502, now))
    }

    test("request timeout is a timeout") {
        val result = callWith {
            delay(10.seconds)
            respond("""{"c": 1}""", HttpStatusCode.OK, jsonHeaders)
        }.price()

        result shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(now))
    }

    test("connection refused reported by the engine is a timeout") {
        val result = callWith { throw ConnectException("Connection refused") }.price()

        result shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(now))
    }

    test("unresolved host is a timeout") {
        val result = callWith { throw UnresolvedAddressException() }.price()

        result shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(now))
    }

    test("connection refused by a real local port is a timeout") {
        val closedPort = ServerSocket(0).use { it.localPort }
        val call = SourceHttpCall(SourceHttpClient.create(CIO.create()), timeout = 2.seconds, clock = clock)

        val result = call.execute({ url("http://127.0.0.1:$closedPort/quote") }) { it.body<PriceDto>().c }

        result shouldBe SourceResult.SourceError(SourceCallOutcome.timeout(now))
    }

    test("malformed JSON is an application error") {
        val result = callWith { respond("""{"c": """, HttpStatusCode.OK, jsonHeaders) }.price()

        result shouldBe SourceResult.SourceError(SourceCallOutcome.appError(now))
    }

    test("JSON of an unexpected shape is an application error") {
        val result = callWith { respond("""[1, 2]""", HttpStatusCode.OK, jsonHeaders) }.price()

        result.outcome?.result shouldBe SourceCallResult.APP_ERROR
    }

    test("exception while mapping the response is an application error") {
        val call = callWith { respond("""{"c": 1}""", HttpStatusCode.OK, jsonHeaders) }

        val result = call.execute<Double>({ url("https://source.test/quote") }) { error("mapping failed") }

        result shouldBe SourceResult.SourceError(SourceCallOutcome.appError(now))
    }

    test("unexpected exception in the engine is an application error") {
        val result = callWith { throw IllegalStateException("engine bug") }.price()

        result shouldBe SourceResult.SourceError(SourceCallOutcome.appError(now))
    }
})
