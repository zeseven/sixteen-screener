package com.sixteenscreener.app.data.remote.auth

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private val server = MockWebServer()
    private val tokenProvider = SequentialTokenProvider()
    private val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenProvider)).build()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun get(): Int = client.newCall(Request.Builder().url(server.url("/profiles/1")).build()).execute().use { it.code }

    @Test
    fun `adds the ID token as bearer token`() {
        server.enqueue(MockResponse.Builder().code(200).build())

        assertEquals(200, get())

        assertEquals("Bearer token-1", server.takeRequest().headers["Authorization"])
        assertEquals(emptyList<String>(), tokenProvider.invalidated)
    }

    @Test
    fun `401 invalidates the token and retries once with a new token`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(200).build())

        assertEquals(200, get())

        assertEquals("Bearer token-1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer token-2", server.takeRequest().headers["Authorization"])
        assertEquals(listOf("token-1"), tokenProvider.invalidated)
    }

    @Test
    fun `second 401 is returned without another retry`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(200).build())

        assertEquals(401, get())

        assertEquals(2, server.requestCount)
        assertEquals(listOf("token-1"), tokenProvider.invalidated)
    }

    @Test
    fun `other errors are not retried`() {
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(MockResponse.Builder().code(200).build())

        assertEquals(403, get())

        assertEquals(1, server.requestCount)
        assertEquals(emptyList<String>(), tokenProvider.invalidated)
    }

    @Test
    fun `retry keeps method and body of the original request`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(201).build())
        val request = Request.Builder()
            .url(server.url("/profiles"))
            .post("""{"name":"Alice"}""".toRequestBody())
            .build()

        client.newCall(request).execute().close()

        server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("POST", retried.method)
        assertEquals("""{"name":"Alice"}""", retried.body!!.utf8())
    }

    private class SequentialTokenProvider : IdTokenProvider {
        private var issued = 0
        private var current: String? = null
        val invalidated = mutableListOf<String>()

        override suspend fun idToken(): String = current ?: "token-${++issued}".also { current = it }

        override fun invalidate(token: String) {
            invalidated += token
            if (current == token) current = null
        }
    }
}
