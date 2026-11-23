package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.bundledDefaultLogo
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.config.SourceConfig
import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.testConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.time.Duration.Companion.seconds

private val logoapi = SourceId.of("logoapi")
private val aapl = Symbol.of("AAPL")
private const val APPLE_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0\"/></svg>"

private val eodhdLogos = SourceId.of("eodhd-logos")
private val finnhubLogos = SourceId.of("finnhub-logos")
private val APPLE_PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x7F)
private val EODHD_APPLE_PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x3E)

private fun logoConfig(
    limit: Int = 1000,
    withEodhdLogos: Boolean = false,
    withFinnhubLogos: Boolean = false,
): AppConfig = testConfig().let { config ->
    val logoApiSource = SourceConfig(
        id = logoapi,
        name = "logoapi.sh",
        enabled = true,
        baseUrl = "https://logoapi.test",
        capabilities = setOf(Capability.LOGO),
        limits = listOf(RequestLimit(LimitInterval.DAY, limit)),
        timeout = 5.seconds,
    )
    val finnhubLogosSource = logoApiSource.copy(
        id = finnhubLogos,
        name = "Finnhub logos",
        baseUrl = "https://static2.finnhub.test",
        limits = emptyList(),
        countTotalCalls = true,
    )
    val eodhdLogosSource = finnhubLogosSource.copy(id = eodhdLogos, name = "EODHD logos", baseUrl = "https://eodhd.test/img/logos")
    config.copy(
        sources = config.sources + listOfNotNull(
            logoApiSource,
            eodhdLogosSource.takeIf { withEodhdLogos },
            finnhubLogosSource.takeIf { withFinnhubLogos },
        ),
    )
}

private suspend fun HttpResponse.shouldBePng(body: ByteArray, cacheControl: String) {
    status shouldBe HttpStatusCode.OK
    contentType()?.withoutParameters() shouldBe ContentType.Image.PNG
    headers[HttpHeaders.CacheControl] shouldBe cacheControl
    readRawBytes().toList() shouldBe body.toList()
}

private suspend fun HttpResponse.shouldBeSvg(body: String, cacheControl: String) {
    status shouldBe HttpStatusCode.OK
    contentType()?.withoutParameters() shouldBe ContentType("image", "svg+xml")
    headers[HttpHeaders.CacheControl] shouldBe cacheControl
    bodyAsText() shouldBe body
}

class LogoRoutesTest : FunSpec({
    val defaultLogo = String(bundledDefaultLogo())

    test("the bundled default logo is SVG") {
        defaultLogo shouldStartWith "<svg"
    }

    test("stored logo with a long-lived Cache-Control") {
        routeTest(config = logoConfig()) { ports ->
            ports.logoStorage.save(aapl, APPLE_SVG.toByteArray())

            client.get("/logos/AAPL").shouldBeSvg(APPLE_SVG, "public, max-age=2592000")

            ports.logoProvider.calls.shouldBeEmpty()
        }
    }

    test("logo downloaded from the source is stored and returned") {
        routeTest(config = logoConfig()) { ports ->
            ports.logoProvider.logos[aapl] = SourceResult.Success(APPLE_SVG.toByteArray(), SourceCallOutcome.success(ports.clock.now()))

            client.get("/logos/AAPL").shouldBeSvg(APPLE_SVG, "public, max-age=2592000")

            ports.logoStorage.contains(aapl) shouldBe true
        }
    }

    test("logo missing in the source: default logo cached for one day") {
        routeTest(config = logoConfig()) { ports ->
            ports.logoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))

            client.get("/logos/AAPL").shouldBeSvg(defaultLogo, "public, max-age=86400")

            ports.logoMisses.lastMiss(logoapi, aapl) shouldBe ports.clock.now()
        }
    }

    test("logo missing in both sources: default logo cached for one day, a miss recorded per source") {
        routeTest(config = logoConfig(withFinnhubLogos = true)) { ports ->
            ports.logoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))
            ports.finnhubLogoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))

            client.get("/logos/AAPL").shouldBeSvg(defaultLogo, "public, max-age=86400")

            ports.logoMisses.lastMiss(logoapi, aapl) shouldBe ports.clock.now()
            ports.logoMisses.lastMiss(finnhubLogos, aapl) shouldBe ports.clock.now()
        }
    }

    test("stored PNG logo with the PNG content type and a long-lived Cache-Control") {
        routeTest(config = logoConfig(withFinnhubLogos = true)) { ports ->
            ports.finnhubLogoStorage.save(aapl, APPLE_PNG)

            client.get("/logos/AAPL").shouldBePng(APPLE_PNG, "public, max-age=2592000")

            ports.logoProvider.calls.shouldBeEmpty()
            ports.finnhubLogoProvider.calls.shouldBeEmpty()
        }
    }

    test("PNG logo downloaded from Finnhub when logoapi.sh has none") {
        routeTest(config = logoConfig(withFinnhubLogos = true)) { ports ->
            ports.logoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))
            ports.finnhubLogoProvider.logos[aapl] = SourceResult.Success(APPLE_PNG, SourceCallOutcome.success(ports.clock.now()))

            client.get("/logos/AAPL").shouldBePng(APPLE_PNG, "public, max-age=2592000")

            ports.finnhubLogoStorage.contains(aapl) shouldBe true
            ports.requestCounters.totals(listOf(finnhubLogos)) shouldBe mapOf(finnhubLogos to 1L)
        }
    }

    test("logoapi.sh missing and Finnhub failing: default logo with a short Cache-Control") {
        routeTest(config = logoConfig(withFinnhubLogos = true)) { ports ->
            ports.logoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))
            ports.finnhubLogoProvider.logos[aapl] = SourceResult.SourceError(SourceCallOutcome.httpError(500, ports.clock.now()))

            client.get("/logos/AAPL").shouldBeSvg(defaultLogo, "public, max-age=3600")
        }
    }

    context("EODHD logos between logoapi.sh and Finnhub logos") {
        val allLogoSources = logoConfig(withEodhdLogos = true, withFinnhubLogos = true)

        test("a stored EODHD PNG is returned before any download from logoapi.sh") {
            routeTest(config = allLogoSources) { ports ->
                ports.eodhdLogoStorage.save(aapl, EODHD_APPLE_PNG)
                ports.logoProvider.logos[aapl] = SourceResult.Success(APPLE_SVG.toByteArray(), SourceCallOutcome.success(ports.clock.now()))

                client.get("/logos/AAPL").shouldBePng(EODHD_APPLE_PNG, "public, max-age=2592000")

                ports.logoProvider.calls.shouldBeEmpty()
                ports.eodhdLogoProvider.calls.shouldBeEmpty()
            }
        }

        test("a stored EODHD PNG is returned before a stored Finnhub PNG") {
            routeTest(config = allLogoSources) { ports ->
                ports.eodhdLogoStorage.save(aapl, EODHD_APPLE_PNG)
                ports.finnhubLogoStorage.save(aapl, APPLE_PNG)

                client.get("/logos/AAPL").shouldBePng(EODHD_APPLE_PNG, "public, max-age=2592000")
            }
        }

        test("a logoapi.sh download is returned without asking EODHD") {
            routeTest(config = allLogoSources) { ports ->
                ports.logoProvider.logos[aapl] = SourceResult.Success(APPLE_SVG.toByteArray(), SourceCallOutcome.success(ports.clock.now()))
                ports.eodhdLogoProvider.logos[aapl] = SourceResult.Success(EODHD_APPLE_PNG, SourceCallOutcome.success(ports.clock.now()))

                client.get("/logos/AAPL").shouldBeSvg(APPLE_SVG, "public, max-age=2592000")

                ports.eodhdLogoProvider.calls.shouldBeEmpty()
                ports.finnhubLogoProvider.calls.shouldBeEmpty()
            }
        }

        test("an EODHD download is stored and returned without asking Finnhub") {
            routeTest(config = allLogoSources) { ports ->
                ports.logoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))
                ports.eodhdLogoProvider.logos[aapl] = SourceResult.Success(EODHD_APPLE_PNG, SourceCallOutcome.success(ports.clock.now()))
                ports.finnhubLogoProvider.logos[aapl] = SourceResult.Success(APPLE_PNG, SourceCallOutcome.success(ports.clock.now()))

                client.get("/logos/AAPL").shouldBePng(EODHD_APPLE_PNG, "public, max-age=2592000")

                ports.eodhdLogoStorage.contains(aapl) shouldBe true
                ports.finnhubLogoStorage.contains(aapl) shouldBe false
                ports.finnhubLogoProvider.calls.shouldBeEmpty()
                ports.requestCounters.totals(listOf(eodhdLogos)) shouldBe mapOf(eodhdLogos to 1L)
            }
        }

        test("an EODHD miss is recorded and Finnhub is asked") {
            routeTest(config = allLogoSources) { ports ->
                ports.logoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))
                ports.eodhdLogoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))
                ports.finnhubLogoProvider.logos[aapl] = SourceResult.Success(APPLE_PNG, SourceCallOutcome.success(ports.clock.now()))

                client.get("/logos/AAPL").shouldBePng(APPLE_PNG, "public, max-age=2592000")

                ports.logoMisses.lastMiss(eodhdLogos, aapl) shouldBe ports.clock.now()
                ports.finnhubLogoStorage.contains(aapl) shouldBe true
                ports.eodhdLogoStorage.contains(aapl) shouldBe false
            }
        }

        test("EODHD logos disabled: Finnhub is asked after logoapi.sh") {
            routeTest(config = logoConfig(withFinnhubLogos = true)) { ports ->
                ports.logoProvider.logos[aapl] = SourceResult.NoData(SourceCallOutcome.httpError(404, ports.clock.now()))
                ports.finnhubLogoProvider.logos[aapl] = SourceResult.Success(APPLE_PNG, SourceCallOutcome.success(ports.clock.now()))

                client.get("/logos/AAPL").shouldBePng(APPLE_PNG, "public, max-age=2592000")

                ports.eodhdLogoProvider.calls.shouldBeEmpty()
            }
        }
    }

    test("limit reached: default logo with a short Cache-Control so the app asks again") {
        routeTest(config = logoConfig(limit = 0)) { ports ->
            client.get("/logos/AAPL").shouldBeSvg(defaultLogo, "public, max-age=3600")

            ports.logoProvider.calls.shouldBeEmpty()
        }
    }

    test("source failure: default logo with a short Cache-Control") {
        routeTest(config = logoConfig()) { ports ->
            ports.logoProvider.logos[aapl] = SourceResult.SourceError(SourceCallOutcome.timeout(ports.clock.now()))

            client.get("/logos/AAPL").shouldBeSvg(defaultLogo, "public, max-age=3600")
        }
    }

    test("logo source disabled: default logo with a short Cache-Control") {
        routeTest { ports ->
            client.get("/logos/AAPL").shouldBeSvg(defaultLogo, "public, max-age=3600")

            ports.logoProvider.calls.shouldBeEmpty()
        }
    }

    test("symbols with an exchange suffix and share classes") {
        routeTest(config = logoConfig()) { ports ->
            ports.logoStorage.save(Symbol.of("SAP.DE"), APPLE_SVG.toByteArray())

            client.get("/logos/SAP.DE").shouldBeSvg(APPLE_SVG, "public, max-age=2592000")
        }
    }

    context("invalid symbols are rejected with 400") {
        withData(nameFn = { "'$it'" }, "aapl", "A".repeat(21), "AAPL%20X", ".AAPL") { symbol ->
            routeTest(config = logoConfig()) { ports ->
                client.get("/logos/$symbol").shouldBeProblem(400)

                ports.logoProvider.calls.shouldBeEmpty()
            }
        }
    }
})
