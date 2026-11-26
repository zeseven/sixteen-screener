package com.sixteenscreener.adapter.outbound.yahoo

import com.sixteenscreener.adapter.outbound.http.BearerTokenProvider
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.fakes.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

private class WarmUpFixture(
    baseUrl: String = "https://yahoo-proxy.test/",
    bearerToken: BearerTokenProvider? = null,
    handler: MockRequestHandler = { respond("""{"status":"ok"}""", HttpStatusCode.OK) },
) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    val job = SupervisorJob()
    val warmUp = YahooProxyWarmUp(
        client = SourceHttpClient.create(
            MockEngine { request ->
                requests += request
                handler(request)
            },
        ),
        baseUrl = baseUrl,
        bearerToken = bearerToken,
        clock = clock,
        scope = CoroutineScope(job),
    )

    suspend fun settle() = withTimeout(5.seconds) { job.children.toList().joinAll() }
}

class YahooProxyWarmUpTest : FunSpec({
    test("sends a health request with the ID token and returns at once") {
        val fixture = WarmUpFixture(bearerToken = { "id-token" })

        fixture.warmUp.warmUp()
        fixture.settle()

        val request = fixture.requests.single()
        request.method shouldBe HttpMethod.Get
        request.url.toString() shouldBe "https://yahoo-proxy.test/health"
        request.headers[HttpHeaders.Authorization] shouldBe "Bearer id-token"
    }

    test("a proxy run locally gets no token") {
        val fixture = WarmUpFixture(baseUrl = "http://127.0.0.1:8099")

        fixture.warmUp.warmUp()
        fixture.settle()

        fixture.requests.single().headers[HttpHeaders.Authorization] shouldBe null
    }

    test("at most one health request per minute") {
        val fixture = WarmUpFixture()

        fixture.warmUp.warmUp()
        fixture.warmUp.warmUp()
        fixture.clock.advanceBy(Duration.ofSeconds(59))
        fixture.warmUp.warmUp()
        fixture.settle()
        fixture.requests shouldHaveSize 1

        fixture.clock.advanceBy(Duration.ofSeconds(1))
        fixture.warmUp.warmUp()
        fixture.settle()
        fixture.requests shouldHaveSize 2
    }

    test("a failed health request is only logged and the next one waits for the interval") {
        val fixture = WarmUpFixture(handler = { throw IOException("connection refused") })

        fixture.warmUp.warmUp()
        fixture.settle()
        fixture.warmUp.warmUp()
        fixture.settle()

        fixture.requests shouldHaveSize 1
        fixture.job.isActive shouldBe true
    }

    test("a failed token request sends nothing and doesn't fail the caller") {
        val fixture = WarmUpFixture(bearerToken = { error("metadata server answered 500") })

        fixture.warmUp.warmUp()
        fixture.settle()

        fixture.requests.shouldBeEmpty()
        fixture.job.isActive shouldBe true
    }

    test("an error status of the proxy is ignored") {
        val fixture = WarmUpFixture(handler = { respond("", HttpStatusCode.ServiceUnavailable) })

        fixture.warmUp.warmUp()
        fixture.settle()

        fixture.requests shouldHaveSize 1
    }

    test("closing cancels a running health request") {
        val fixture = WarmUpFixture(
            handler = {
                delay(30.seconds)
                respond("", HttpStatusCode.OK)
            },
        )
        fixture.warmUp.warmUp()

        fixture.warmUp.close()
        fixture.settle()

        fixture.job.isCancelled shouldBe true
    }
})
