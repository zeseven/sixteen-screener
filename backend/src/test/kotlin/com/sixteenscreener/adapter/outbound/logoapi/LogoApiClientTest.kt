package com.sixteenscreener.adapter.outbound.logoapi

import com.sixteenscreener.Fixtures
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val svgHeaders = headersOf(HttpHeaders.ContentType, "image/svg+xml")

private class MockLogoApi(
    private val body: ByteArray = Fixtures.text("logoapi/aapl.svg").toByteArray(),
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val headers: Headers = svgHeaders,
    private val delay: Duration? = null,
    maxSizeBytes: Long = LogoApiClient.DEFAULT_MAX_SIZE_BYTES,
) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val client = LogoApiClient(
        id = SourceId.of("logoapi"),
        baseUrl = "https://logoapi.test/",
        call = SourceHttpCall(
            SourceHttpClient.create(
                MockEngine { request ->
                    requests += request
                    delay?.let { delay(it) }
                    respond(body, status, headers)
                },
            ),
            timeout = 200.milliseconds,
            clock = clock,
        ),
        maxSizeBytes = maxSizeBytes,
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    suspend fun logo(symbol: String) = client.logo(Symbol.of(symbol))
}

class LogoApiClientTest : FunSpec({

    test("downloads the SVG of a US ticker and asks for a 404 instead of a fallback image") {
        val api = MockLogoApi()

        val result = api.logo("AAPL").shouldBeInstanceOf<SourceResult.Success<ByteArray>>()

        String(result.data) shouldBe Fixtures.text("logoapi/aapl.svg")
        result.outcome shouldBe api.success
        api.requests.single().url.toString() shouldBe "https://logoapi.test/ticker/AAPL.svg?fallback=404"
    }

    context("ticker form") {
        withData(
            "AAPL" to "AAPL",
            "BRK.B" to "BRK.B",
            "SAP.DE" to "SAP.DE",
            "SHEL.L" to "SHEL.L",
        ) { (symbol, ticker) ->
            LogoApiClient.tickerOf(Symbol.of(symbol)) shouldBe ticker
        }
    }

    test("404 is no data") {
        val api = MockLogoApi(body = ByteArray(0), status = HttpStatusCode.NotFound)

        api.logo("ZZZZ") shouldBe SourceResult.NoData(SourceCallOutcome.httpError(404, api.clock.now()))
    }

    test("a fallback image flagged by the source is no data") {
        val api = MockLogoApi(headers = Headers.build { appendAll(svgHeaders); append(LogoApiClient.FALLBACK_HEADER, "tile") })

        api.logo("ZZZZ") shouldBe SourceResult.NoData(api.success)
    }

    test("a response that is not SVG is no data") {
        val api = MockLogoApi(body = Fixtures.text("logoapi/not-svg.html").toByteArray())

        api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
    }

    test("a response larger than the size cap is no data") {
        val svg = "<svg>" + " ".repeat(200) + "</svg>"
        val api = MockLogoApi(body = svg.toByteArray(), maxSizeBytes = 100)

        api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
    }

    test("a response exactly at the size cap is accepted") {
        val svg = "<svg></svg>"
        val api = MockLogoApi(body = svg.toByteArray(), maxSizeBytes = svg.length.toLong())

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.Success<ByteArray>>()
    }

    test("5xx is a source error") {
        val api = MockLogoApi(status = HttpStatusCode.BadGateway)

        api.logo("AAPL") shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(502, api.clock.now()))
    }

    test("429 is a limit reached by the source") {
        val api = MockLogoApi(status = HttpStatusCode.TooManyRequests)

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.LimitReached>().rejectedBySource shouldBe true
    }

    test("timeout") {
        val api = MockLogoApi(delay = 1000.milliseconds)

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.SourceError>().outcome.result shouldBe SourceCallResult.TIMEOUT
    }

    context("SVG content check") {
        withData(
            nameFn = { (content, _) -> "'${content.take(30)}'" },
            "<svg xmlns=\"http://www.w3.org/2000/svg\"/>" to true,
            "  \n<svg></svg>" to true,
            "﻿<svg></svg>" to true,
            "<?xml version=\"1.0\"?><svg></svg>" to true,
            "<?xml version=\"1.0\"?><html></html>" to false,
            "<html><svg></svg></html>" to false,
            "{\"error\": \"not found\"}" to false,
            "" to false,
        ) { (content, expected) ->
            SvgContent.isSvg(content.toByteArray()) shouldBe expected
        }
    }
})
