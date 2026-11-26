package com.sixteenscreener.adapter.outbound.gcp

import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.fakes.MutableClock
import com.sixteenscreener.proxyIdToken
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

private const val AUDIENCE = "https://sixteen-screener-yahoo-abc-ew.a.run.app"

private fun jwt(expiresAt: Instant, subject: String = "backend"): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
    val payload = encoder.encodeToString("""{"aud":"$AUDIENCE","sub":"$subject","exp":${expiresAt.epochSecond}}""".toByteArray())
    return "$header.$payload.signature"
}

private class MetadataServer(private val status: HttpStatusCode = HttpStatusCode.OK) {
    val clock = MutableClock()
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()
    private var issued = 0

    val provider = MetadataServerIdTokenProvider(
        client = SourceHttpClient.create(
            MockEngine { request ->
                requests += request
                issued += 1
                respond(jwt(clock.now().plus(Duration.ofHours(1)), subject = "token-$issued"), status)
            },
        ),
        audience = AUDIENCE,
        clock = clock,
    )
}

class MetadataServerIdTokenProviderTest : FunSpec({
    test("requests an ID token for the audience from the metadata server") {
        val server = MetadataServer()

        val token = server.provider.token()

        MetadataServerIdTokenProvider.expiryOf(token) shouldBe server.clock.now().plus(Duration.ofHours(1))
        val request = server.requests.single()
        request.method shouldBe HttpMethod.Get
        request.url.host shouldBe "metadata.google.internal"
        request.url.encodedPath shouldBe "/computeMetadata/v1/instance/service-accounts/default/identity"
        request.url.parameters["audience"] shouldBe AUDIENCE
        request.headers["Metadata-Flavor"] shouldBe "Google"
    }

    test("reuses the token until 5 minutes before its expiry") {
        val server = MetadataServer()
        val first = server.provider.token()

        server.clock.advanceBy(Duration.ofMinutes(54))
        server.provider.token() shouldBe first
        server.requests.size shouldBe 1

        server.clock.advanceBy(Duration.ofMinutes(1))
        val second = server.provider.token()
        (second == first) shouldBe false
        server.requests.size shouldBe 2
    }

    test("concurrent callers share one token request") {
        val server = MetadataServer()

        val tokens = coroutineScope { List(5) { async { server.provider.token() } }.awaitAll() }

        tokens.toSet().size shouldBe 1
        server.requests.size shouldBe 1
    }

    test("an answer outside 2XX fails and is not cached") {
        val server = MetadataServer(HttpStatusCode.NotFound)

        shouldThrow<IllegalStateException> { server.provider.token() }
        shouldThrow<IllegalStateException> { server.provider.token() }
        server.requests.size shouldBe 2
    }

    test("the expiry is read from the payload of the JWT") {
        MetadataServerIdTokenProvider.expiryOf(jwt(Instant.parse("2026-11-26T15:00:00Z"))) shouldBe
            Instant.parse("2026-11-26T15:00:00Z")
    }

    test("a token that is not a JWT or has no expiry is rejected") {
        shouldThrow<IllegalArgumentException> { MetadataServerIdTokenProvider.expiryOf("not-a-jwt") }
        val noExpiry = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"aud":"x"}""".toByteArray())
        shouldThrow<IllegalArgumentException> { MetadataServerIdTokenProvider.expiryOf("h.$noExpiry.s") }
    }

    test("an https proxy gets an ID token for its URL as the audience, an http proxy gets none") {
        val client = SourceHttpClient.create(MockEngine { respond("", HttpStatusCode.OK) })

        proxyIdToken("$AUDIENCE/", client, MutableClock()).shouldBeInstanceOf<MetadataServerIdTokenProvider>()
        proxyIdToken("http://127.0.0.1:8080", client, MutableClock()).shouldBeNull()
    }
})
