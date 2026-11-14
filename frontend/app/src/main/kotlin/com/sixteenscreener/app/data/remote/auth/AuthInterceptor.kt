package com.sixteenscreener.app.data.remote.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.inject.Inject

/**
 * Adds the ID token as a bearer token. On `401` the token is invalidated and the request is retried once with a new token.
 */
class AuthInterceptor @Inject constructor(private val idTokenProvider: IdTokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentToken()
        val response = chain.proceed(chain.request().withBearer(token))
        if (response.code != HTTP_UNAUTHORIZED) return response

        response.close()
        idTokenProvider.invalidate(token)
        return chain.proceed(chain.request().withBearer(currentToken()))
    }

    private fun currentToken(): String = runBlocking { idTokenProvider.idToken() }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()
}
