package com.sixteenscreener.app.data.remote

import com.sixteenscreener.app.data.remote.auth.AuthInterceptor
import com.sixteenscreener.app.data.remote.auth.IdTokenProvider
import com.sixteenscreener.app.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    private val tokenClient = NetworkModule.provideTokenHttpClient()
    private val authenticatedClient = NetworkModule.provideAuthenticatedHttpClient(tokenClient, AuthInterceptor(NoTokens))

    @Test
    fun `timeouts are sized for Cloud Run cold starts`() {
        listOf(tokenClient, authenticatedClient).forEach { client ->
            assertEquals(15_000, client.connectTimeoutMillis)
            assertEquals(30_000, client.readTimeoutMillis)
        }
    }

    @Test
    fun `only the authenticated client adds ID tokens`() {
        assertTrue(tokenClient.interceptors.none { it is AuthInterceptor })
        assertEquals(1, authenticatedClient.interceptors.count { it is AuthInterceptor })
    }

    @Test
    fun `JSON configuration ignores unknown keys`() {
        assertTrue(NetworkModule.provideJson().configuration.ignoreUnknownKeys)
    }

    private object NoTokens : IdTokenProvider {
        override suspend fun idToken(): String = error("not used")

        override fun invalidate(token: String) = Unit
    }
}
