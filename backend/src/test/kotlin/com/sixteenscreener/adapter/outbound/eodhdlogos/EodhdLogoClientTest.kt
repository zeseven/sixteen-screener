package com.sixteenscreener.adapter.outbound.eodhdlogos

import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.adapter.outbound.logo.PngLogoDownload
import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val BASE_URL = "https://eodhd.com/img/logos"
private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val applePng = pngSignature + byteArrayOf(0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52)
private val pngHeaders = headersOf(HttpHeaders.ContentType, "image/png")

private class MockEodhdLogos(
    private val body: ByteArray = applePng,
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val headers: Headers = pngHeaders,
    private val delay: Duration? = null,
    private val streamed: Boolean = false,
    maxSizeBytes: Long = PngLogoDownload.DEFAULT_MAX_SIZE_BYTES,
) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val client = EodhdLogoClient(
        id = SourceId.of("eodhd-logos"),
        baseUrl = "$BASE_URL/",
        call = SourceHttpCall(
            SourceHttpClient.create(
                MockEngine { request ->
                    requests += request
                    delay?.let { delay(it) }
                    if (streamed) respond(ByteReadChannel(body), status, headers) else respond(body, status, headers)
                },
            ),
            timeout = 200.milliseconds,
            clock = clock,
        ),
        maxSizeBytes = maxSizeBytes,
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    val requestedUrl: String get() = requests.single().url.toString()

    suspend fun logo(symbol: String) = client.logo(Symbol.of(symbol))
}

class EodhdLogoClientTest : FunSpec({

    test("provides PNG logos") {
        MockEodhdLogos().client.format shouldBe ImageFormat.PNG
    }

    test("downloads the PNG with a GET without credentials") {
        val api = MockEodhdLogos()

        val result = api.logo("AAPL").shouldBeInstanceOf<SourceResult.Success<ByteArray>>()

        result.data.toList() shouldBe applePng.toList()
        result.outcome shouldBe api.success
        val request = api.requests.single()
        request.method shouldBe HttpMethod.Get
        request.headers[HttpHeaders.Authorization] shouldBe null
        request.url.parameters.isEmpty() shouldBe true
    }

    context("URL is built from the EODHD exchange code and the ticker with dots replaced by dashes") {
        withData(
            "AAPL" to "US/AAPL.png",
            "FISI" to "US/FISI.png",
            "BRK.B" to "US/BRK-B.png",
            "SAP.DE" to "XETRA/SAP.png",
            "ASML.AS" to "AS/ASML.png",
            "MC.PA" to "PA/MC.png",
            "NOVO-B.CO" to "CO/NOVO-B.png",
            "SHEL.L" to "LSE/SHEL.png",
            "PKO.WA" to "WAR/PKO.png",
        ) { (symbol, path) ->
            val api = MockEodhdLogos()

            api.logo(symbol)

            api.requestedUrl shouldBe "$BASE_URL/$path"
        }
    }

    context("a symbol of an exchange without an EODHD code is no data without a request") {
        withData("7203.T", "SHEL.IL", "0700.HK") { symbol ->
            val api = MockEodhdLogos()

            api.logo(symbol) shouldBe SourceResult.NoData(outcome = null)

            api.requests.shouldBeEmpty()
        }
    }

    test("404 with an HTML body is no data") {
        val api = MockEodhdLogos(
            body = "<html><body>Not Found</body></html>".toByteArray(),
            status = HttpStatusCode.NotFound,
            headers = headersOf(HttpHeaders.ContentType, "text/html"),
        )

        api.logo("ZZZZ") shouldBe SourceResult.NoData(SourceCallOutcome.httpError(404, api.clock.now()))
    }

    context("a response that is not PNG is no data") {
        withData(
            nameFn = { it.first },
            "HTML" to "<html></html>".toByteArray(),
            "SVG" to "<svg></svg>".toByteArray(),
            "empty" to ByteArray(0),
            "signature only" to pngSignature,
        ) { (_, body) ->
            val api = MockEodhdLogos(body = body)

            api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
        }
    }

    test("a PNG larger than 1 MB announced by its Content-Length is no data") {
        val api = MockEodhdLogos(body = applePng + ByteArray(1024 * 1024))

        api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
    }

    test("a streamed body larger than the size cap is no data") {
        val api = MockEodhdLogos(body = applePng + ByteArray(100), streamed = true, maxSizeBytes = 100)

        api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
    }

    test("5xx is a source error, not a miss") {
        val api = MockEodhdLogos(status = HttpStatusCode.ServiceUnavailable)

        api.logo("AAPL") shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(503, api.clock.now()))
    }

    test("429 is a limit reached by the source") {
        val api = MockEodhdLogos(status = HttpStatusCode.TooManyRequests)

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.LimitReached>().rejectedBySource shouldBe true
    }

    test("timeout is a source error, not a miss") {
        val api = MockEodhdLogos(delay = 1000.milliseconds)

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.SourceError>().outcome.result shouldBe SourceCallResult.TIMEOUT
    }
})
