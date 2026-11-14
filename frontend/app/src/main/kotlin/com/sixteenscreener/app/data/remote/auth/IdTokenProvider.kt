package com.sixteenscreener.app.data.remote.auth

import com.sixteenscreener.app.di.BackendUrl
import com.sixteenscreener.app.di.IoDispatcher
import com.sixteenscreener.app.di.TokenHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies Google-signed ID tokens for calls to the backend.
 */
interface IdTokenProvider {
    /**
     * Returns a cached token that is valid for at least the refresh margin, or mints a new one.
     *
     * @throws AuthenticationException when no token can be obtained (invalid key, token endpoint rejection or malformed response)
     * @throws IOException when the token endpoint cannot be reached
     */
    suspend fun idToken(): String

    /**
     * Drops [token] from the cache if it is still the cached one, so the next [idToken] call mints a new token.
     */
    fun invalidate(token: String)
}

/**
 * Failure to obtain an ID token that is not a connectivity problem.
 */
class AuthenticationException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Singleton
class ServiceAccountIdTokenProvider @Inject constructor(
    private val keySource: ServiceAccountKeySource,
    private val jwtSigner: JwtSigner,
    @param:TokenHttpClient private val httpClient: OkHttpClient,
    @param:BackendUrl private val backendUrl: String,
    private val clock: Clock,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : IdTokenProvider {

    private val mutex = Mutex()
    private val cachedToken = AtomicReference<IdToken?>(null)
    private var key: ServiceAccountKey? = null

    override suspend fun idToken(): String =
        validCachedToken() ?: mutex.withLock {
            validCachedToken() ?: mintToken().also(cachedToken::set).value
        }

    override fun invalidate(token: String) {
        cachedToken.updateAndGet { cached -> cached?.takeUnless { it.value == token } }
    }

    private fun validCachedToken(): String? =
        cachedToken.get()?.takeIf { clock.instant() < it.expiresAt - REFRESH_MARGIN }?.value

    private suspend fun mintToken(): IdToken = withContext(ioDispatcher) {
        val key = loadKey()
        val assertion = jwtSigner.sign(key, backendUrl)
        val request = Request.Builder()
            .url(key.tokenUri)
            .post(
                FormBody.Builder()
                    .add("grant_type", JWT_BEARER_GRANT_TYPE)
                    .add("assertion", assertion)
                    .build(),
            )
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw AuthenticationException("Token endpoint returned HTTP ${response.code}: $body")
            }
            parseIdToken(body)
        }
    }

    private fun loadKey(): ServiceAccountKey = key ?: try {
        keySource.load().also { key = it }
    } catch (exception: InvalidServiceAccountKeyException) {
        throw AuthenticationException("Service account key is invalid", exception)
    }

    private fun parseIdToken(body: String): IdToken = try {
        val token = json.decodeFromString<TokenResponse>(body).idToken
            ?: throw AuthenticationException("Token endpoint response has no id_token")
        IdToken(value = token, expiresAt = Instant.ofEpochSecond(expiryOf(token)))
    } catch (exception: SerializationException) {
        throw AuthenticationException("Token endpoint response is malformed", exception)
    } catch (exception: IllegalArgumentException) {
        throw AuthenticationException("ID token is malformed", exception)
    }

    private fun expiryOf(token: String): Long {
        val payload = token.split('.').getOrNull(1) ?: throw IllegalArgumentException("ID token has no payload")
        val claims = json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
        return claims.jsonObject["exp"]?.jsonPrimitive?.long ?: throw IllegalArgumentException("ID token has no exp claim")
    }

    private data class IdToken(val value: String, val expiresAt: Instant)

    @Serializable
    private data class TokenResponse(@SerialName("id_token") val idToken: String? = null)

    companion object {
        val REFRESH_MARGIN: Duration = Duration.ofMinutes(5)
        private const val JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    }
}
