package com.sixteenscreener.adapter.outbound.logo

import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceResult
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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val LOGO_URL = "https://logos.test/AAPL.png"
private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val applePng = pngSignature + byteArrayOf(0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52)
private val pngHeaders = headersOf(HttpHeaders.ContentType, "image/png")

private class MockPngLogos(
    private val body: ByteArray = applePng,
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val headers: Headers = pngHeaders,
    private val delay: Duration? = null,
    private val streamed: Boolean = false,
    maxSizeBytes: Long = PngLogoDownload.DEFAULT_MAX_SIZE_BYTES,
) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val download = PngLogoDownload(
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

    suspend fun download() = download.download(LOGO_URL)
}

class PngLogoDownloadTest : FunSpec({

    test("downloads the PNG with a single GET of the given URL") {
        val api = MockPngLogos()

        val result = api.download().shouldBeInstanceOf<SourceResult.Success<ByteArray>>()

        result.data.toList() shouldBe applePng.toList()
        result.outcome shouldBe api.success
        api.requests.single().method shouldBe HttpMethod.Get
        api.requests.single().url.toString() shouldBe LOGO_URL
    }

    test("404 with an HTML body is no data") {
        val api = MockPngLogos(
            body = "<html>Not found</html>".toByteArray(),
            status = HttpStatusCode.NotFound,
            headers = headersOf(HttpHeaders.ContentType, "text/html"),
        )

        api.download() shouldBe SourceResult.NoData(SourceCallOutcome.httpError(404, api.clock.now()))
    }

    context("a response that is not PNG is no data") {
        withData(
            nameFn = { it.first },
            "HTML" to "<html></html>".toByteArray(),
            "SVG" to "<svg></svg>".toByteArray(),
            "empty" to ByteArray(0),
            "signature only" to pngSignature,
            "JPEG" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0),
        ) { (_, body) ->
            val api = MockPngLogos(body = body)

            api.download() shouldBe SourceResult.NoData(api.success)
        }
    }

    test("a PNG larger than the size cap announced by its Content-Length is no data") {
        val api = MockPngLogos(body = applePng + ByteArray(1024 * 1024))

        api.download() shouldBe SourceResult.NoData(api.success)
    }

    test("a streamed body larger than the size cap without a Content-Length is no data") {
        val api = MockPngLogos(body = applePng + ByteArray(100), streamed = true, maxSizeBytes = 100)

        api.download() shouldBe SourceResult.NoData(api.success)
    }

    test("a response exactly at the size cap is accepted") {
        val api = MockPngLogos(maxSizeBytes = applePng.size.toLong())

        api.download().shouldBeInstanceOf<SourceResult.Success<ByteArray>>()
    }

    test("the default size cap is 1 MB") {
        PngLogoDownload.DEFAULT_MAX_SIZE_BYTES shouldBe 1_048_576L
    }

    test("5xx is a source error, not a miss") {
        val api = MockPngLogos(status = HttpStatusCode.BadGateway)

        api.download() shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(502, api.clock.now()))
    }

    test("429 is a limit reached by the source") {
        val api = MockPngLogos(status = HttpStatusCode.TooManyRequests)

        api.download().shouldBeInstanceOf<SourceResult.LimitReached>().rejectedBySource shouldBe true
    }

    test("timeout is a source error, not a miss") {
        val api = MockPngLogos(delay = 1000.milliseconds)

        api.download().shouldBeInstanceOf<SourceResult.SourceError>().outcome.result shouldBe SourceCallResult.TIMEOUT
    }

    context("PNG content check") {
        withData(
            nameFn = { it.first },
            "PNG" to (applePng to true),
            "signature only" to (pngSignature to false),
            "wrong first byte" to ((byteArrayOf(0x88.toByte()) + applePng.copyOfRange(1, applePng.size)) to false),
            "truncated signature" to (pngSignature.copyOf(7) to false),
            "empty" to (ByteArray(0) to false),
        ) { (_, case) ->
            PngContent.isPng(case.first) shouldBe case.second
        }
    }
})
