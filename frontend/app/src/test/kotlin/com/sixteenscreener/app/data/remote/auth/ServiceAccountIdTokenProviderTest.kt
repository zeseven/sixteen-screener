package com.sixteenscreener.app.data.remote.auth

import com.sixteenscreener.app.testing.FakeServiceAccount
import com.sixteenscreener.app.testing.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ServiceAccountIdTokenProviderTest {

    private val server = MockWebServer()
    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(start)
    private val tokenExpiry = start.plus(Duration.ofHours(1))
    private val keyLoads = AtomicInteger()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun provider(keySource: ServiceAccountKeySource = keySourceFor(server.url("/token").toString())) =
        ServiceAccountIdTokenProvider(
            keySource = keySource,
            jwtSigner = JwtSigner(clock),
            httpClient = OkHttpClient(),
            backendUrl = BACKEND_URL,
            clock = clock,
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = Dispatchers.IO,
        )

    private fun keySourceFor(tokenUri: String) = ServiceAccountKeySource {
        keyLoads.incrementAndGet()
        ServiceAccountKey.parse(FakeServiceAccount.keyJson(tokenUri = tokenUri))
    }

    private fun enqueueToken(token: String) {
        server.enqueue(MockResponse.Builder().body("""{"id_token":"$token"}""").build())
    }

    @Test
    fun `exchanges a signed JWT at the token endpoint`() = runTest {
        val token = FakeServiceAccount.idToken(tokenExpiry.epochSecond)
        enqueueToken(token)

        val result = provider().idToken()

        assertEquals(token, result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/token", request.url.encodedPath)
        val form = request.body!!.utf8().split('&').associate {
            val (name, value) = it.split('=')
            name to URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form["grant_type"])
        val claims = String(Base64.getUrlDecoder().decode(form.getValue("assertion").split('.')[1]))
        assertTrue(claims, claims.contains("\"target_audience\":\"$BACKEND_URL\""))
    }

    @Test
    fun `token is reused before expiry`() = runTest {
        enqueueToken(FakeServiceAccount.idToken(tokenExpiry.epochSecond))
        val provider = provider()

        val first = provider.idToken()
        clock.advanceBy(Duration.ofMinutes(30))
        val second = provider.idToken()

        assertEquals(first, second)
        assertEquals(1, server.requestCount)
        assertEquals(1, keyLoads.get())
    }

    @Test
    fun `token is reused until just before the five minute refresh margin`() = runTest {
        enqueueToken(FakeServiceAccount.idToken(tokenExpiry.epochSecond))
        val provider = provider()
        provider.idToken()

        clock.now = tokenExpiry.minus(Duration.ofMinutes(5)).minusSeconds(1)
        provider.idToken()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `token is refreshed at the five minute refresh margin`() = runTest {
        val first = FakeServiceAccount.idToken(tokenExpiry.epochSecond, subject = "first")
        val second = FakeServiceAccount.idToken(tokenExpiry.plus(Duration.ofHours(1)).epochSecond, subject = "second")
        enqueueToken(first)
        enqueueToken(second)
        val provider = provider()
        provider.idToken()

        clock.now = tokenExpiry.minus(Duration.ofMinutes(5))
        val refreshed = provider.idToken()

        assertEquals(second, refreshed)
        assertEquals(2, server.requestCount)
        assertEquals(1, keyLoads.get())
    }

    @Test
    fun `already expired token from the endpoint is not cached`() = runTest {
        enqueueToken(FakeServiceAccount.idToken(start.epochSecond, subject = "stale"))
        enqueueToken(FakeServiceAccount.idToken(tokenExpiry.epochSecond, subject = "fresh"))
        val provider = provider()

        provider.idToken()
        provider.idToken()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `concurrent callers share one refresh`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"id_token":"${FakeServiceAccount.idToken(tokenExpiry.epochSecond)}"}""")
                .headersDelay(200, TimeUnit.MILLISECONDS)
                .build(),
        )
        val provider = provider()

        val tokens = (1..20).map { async(Dispatchers.Default) { provider.idToken() } }.awaitAll()

        assertEquals(1, tokens.distinct().size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalidate drops the cached token`() = runTest {
        val first = FakeServiceAccount.idToken(tokenExpiry.epochSecond, subject = "first")
        val second = FakeServiceAccount.idToken(tokenExpiry.epochSecond, subject = "second")
        enqueueToken(first)
        enqueueToken(second)
        val provider = provider()

        provider.invalidate(provider.idToken())

        assertEquals(second, provider.idToken())
    }

    @Test
    fun `invalidating an outdated token keeps the cached one`() = runTest {
        enqueueToken(FakeServiceAccount.idToken(tokenExpiry.epochSecond))
        val provider = provider()
        val cached = provider.idToken()

        provider.invalidate("outdated-token")

        assertEquals(cached, provider.idToken())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `token endpoint error fails with authentication exception and is retried on the next call`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_grant"}""").build())
        val token = FakeServiceAccount.idToken(tokenExpiry.epochSecond)
        enqueueToken(token)
        val provider = provider()

        val exception = runCatching { provider.idToken() }.exceptionOrNull()

        assertTrue(exception is AuthenticationException)
        assertTrue(exception!!.message!!.contains("400"))
        assertEquals(token, provider.idToken())
    }

    @Test
    fun `response without ID token fails with authentication exception`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"access_token":"abc"}""").build())

        assertAuthenticationFailure()
    }

    @Test
    fun `malformed token response fails with authentication exception`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>").build())

        assertAuthenticationFailure()
    }

    @Test
    fun `ID token without expiry fails with authentication exception`() = runTest {
        enqueueToken("header.${Base64.getUrlEncoder().withoutPadding().encodeToString("{}".toByteArray())}.signature")

        assertAuthenticationFailure()
    }

    @Test
    fun `ID token that is not a JWT fails with authentication exception`() = runTest {
        enqueueToken("not-a-jwt")

        assertAuthenticationFailure()
    }

    @Test
    fun `invalid key fails with authentication exception without calling the endpoint`() = runTest {
        val provider = provider(keySource = { throw InvalidServiceAccountKeyException("broken") })

        val exception = runCatching { provider.idToken() }.exceptionOrNull()

        assertTrue(exception is AuthenticationException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `unreachable token endpoint fails with IO exception that is not an authentication failure`() {
        val unreachable = server.url("/token").toString()
        server.close()
        val provider = provider(keySource = keySourceFor(unreachable))

        val exception = assertThrows(IOException::class.java) { runBlocking { provider.idToken() } }

        assertFalse(exception is AuthenticationException)
    }

    private suspend fun assertAuthenticationFailure() {
        val exception = runCatching { provider().idToken() }.exceptionOrNull()
        assertTrue("Unexpected $exception", exception is AuthenticationException)
    }

    private companion object {
        const val BACKEND_URL = "https://sixteen-backend.example.run.app"
    }
}
