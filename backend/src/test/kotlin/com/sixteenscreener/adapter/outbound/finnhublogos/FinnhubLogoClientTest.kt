package com.sixteenscreener.adapter.outbound.finnhublogos

import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.adapter.outbound.logo.PngLogoDownload
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.fakes.InMemoryCompanyCache
import com.sixteenscreener.fakes.MutableClock
import com.sixteenscreener.port.outbound.CompanyCache
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

private const val BASE_URL = "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo"
private val finnhub = SourceId.of("finnhub")
private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val applePng = pngSignature + byteArrayOf(0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52)
private val pngHeaders = headersOf(HttpHeaders.ContentType, "image/png")

private fun profileWithLogo(logoUrl: String?) =
    CompanyProfile("Apple Inc", "US", "USD", "Technology", null, null, null, null, null, null, null, logoUrl)

private class MockFinnhubLogos(
    private val body: ByteArray = applePng,
    private val status: HttpStatusCode = HttpStatusCode.OK,
    private val headers: Headers = pngHeaders,
    private val delay: Duration? = null,
    maxSizeBytes: Long = PngLogoDownload.DEFAULT_MAX_SIZE_BYTES,
    cache: CompanyCache? = null,
) {
    val clock = MutableClock()
    val profiles = InMemoryCompanyCache()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val client = FinnhubLogoClient(
        id = SourceId.of("finnhub-logos"),
        baseUrl = "$BASE_URL/",
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
        profiles = cache ?: profiles,
        profileSource = finnhub,
        maxSizeBytes = maxSizeBytes,
    )

    val success: SourceCallOutcome get() = SourceCallOutcome.success(clock.now())

    val requestedUrl: String get() = requests.single().url.toString()

    suspend fun cacheProfile(symbol: String, logoUrl: String?, source: SourceId = finnhub) {
        profiles.put(CacheKey(source, Symbol.of(symbol), DataType.COMPANY_PROFILE), CacheEntry(profileWithLogo(logoUrl), clock.now()))
    }

    suspend fun logo(symbol: String) = client.logo(Symbol.of(symbol))
}

class FinnhubLogoClientTest : FunSpec({

    test("provides PNG logos") {
        MockFinnhubLogos().client.format shouldBe ImageFormat.PNG
    }

    test("downloads the PNG from the logo URL of the cached Finnhub profile") {
        val api = MockFinnhubLogos()
        api.cacheProfile("AAPL", "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/AAPL-custom.png")

        val result = api.logo("AAPL").shouldBeInstanceOf<SourceResult.Success<ByteArray>>()

        result.data.toList() shouldBe applePng.toList()
        result.outcome shouldBe api.success
        api.requestedUrl shouldBe "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/AAPL-custom.png"
    }

    context("URL built from the pattern when the cached profile has no usable logo URL") {
        withData(
            nameFn = { (_, logoUrl) -> "profile logo '$logoUrl'" },
            "AAPL" to null,
            "AAPL" to "",
            "AAPL" to "https://evil.example.com/AAPL.png",
            "AAPL" to "http://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/AAPL.png",
            "AAPL" to "https://static2.finnhub.io.evil.example.com/AAPL.png",
            "AAPL" to "not a url",
            "AAPL" to "/relative/AAPL.png",
        ) { (symbol, logoUrl) ->
            val api = MockFinnhubLogos()
            api.cacheProfile(symbol, logoUrl)

            api.logo(symbol)

            api.requestedUrl shouldBe "$BASE_URL/AAPL.png"
        }
    }

    context("pattern uses the bare ticker for US symbols and the full symbol otherwise") {
        withData(
            "AAPL" to "AAPL.png",
            "BRK.B" to "BRK.B.png",
            "SAP.DE" to "SAP.DE.png",
            "RY.TO" to "RY.TO.png",
            "ASML.AS" to "ASML.AS.png",
        ) { (symbol, file) ->
            val api = MockFinnhubLogos()

            api.logo(symbol)

            api.requestedUrl shouldBe "$BASE_URL/$file"
        }
    }

    test("a profile cached for another source is not used") {
        val api = MockFinnhubLogos()
        api.cacheProfile("AAPL", "https://static2.finnhub.io/other/AAPL.png", source = SourceId.of("fmp"))

        api.logo("AAPL")

        api.requestedUrl shouldBe "$BASE_URL/AAPL.png"
    }

    test("an unreadable profile cache falls back to the pattern") {
        val failing = object : CompanyCache by InMemoryCompanyCache() {
            override suspend fun <T : Any> get(key: CacheKey<T>): CacheEntry<T>? = error("cache unavailable")
        }
        val api = MockFinnhubLogos(cache = failing)

        api.logo("SAP.DE").shouldBeInstanceOf<SourceResult.Success<ByteArray>>()

        api.requestedUrl shouldBe "$BASE_URL/SAP.DE.png"
    }

    test("404 with a JSON body is no data") {
        val api = MockFinnhubLogos(
            body = """{"error":"Not found"}""".toByteArray(),
            status = HttpStatusCode.NotFound,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

        api.logo("ZZZZ") shouldBe SourceResult.NoData(SourceCallOutcome.httpError(404, api.clock.now()))
    }

    context("a response that is not PNG is no data") {
        withData(
            nameFn = { it.first },
            "JSON" to """{"error":"Not found"}""".toByteArray(),
            "SVG" to "<svg></svg>".toByteArray(),
            "empty" to ByteArray(0),
            "signature only" to pngSignature,
            "truncated signature" to pngSignature.copyOf(7),
            "JPEG" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0),
        ) { (_, body) ->
            val api = MockFinnhubLogos(body = body)

            api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
        }
    }

    test("a PNG larger than 1 MB announced by its Content-Length is no data") {
        val api = MockFinnhubLogos(body = applePng + ByteArray(1024 * 1024))

        api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
    }

    test("a body larger than the size cap is no data") {
        val api = MockFinnhubLogos(body = applePng + ByteArray(100), maxSizeBytes = 100)

        api.logo("AAPL") shouldBe SourceResult.NoData(api.success)
    }

    test("a response exactly at the size cap is accepted") {
        val api = MockFinnhubLogos(maxSizeBytes = applePng.size.toLong())

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.Success<ByteArray>>()
    }

    test("5xx is a source error, not a miss") {
        val api = MockFinnhubLogos(status = HttpStatusCode.BadGateway)

        api.logo("AAPL") shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(502, api.clock.now()))
    }

    test("403 is a source error, not a miss") {
        val api = MockFinnhubLogos(status = HttpStatusCode.Forbidden)

        api.logo("AAPL") shouldBe SourceResult.SourceError(SourceCallOutcome.httpError(403, api.clock.now()))
    }

    test("429 is a limit reached by the source") {
        val api = MockFinnhubLogos(status = HttpStatusCode.TooManyRequests)

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.LimitReached>().rejectedBySource shouldBe true
    }

    test("timeout is a source error, not a miss") {
        val api = MockFinnhubLogos(delay = 1000.milliseconds)

        api.logo("AAPL").shouldBeInstanceOf<SourceResult.SourceError>().outcome.result shouldBe SourceCallResult.TIMEOUT
    }
})
