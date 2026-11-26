package com.sixteenscreener.adapter.outbound.gcp

import com.sixteenscreener.adapter.outbound.http.BearerTokenProvider
import com.sixteenscreener.port.outbound.Clock
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/**
 * Google-signed ID token of the Cloud Run service account for [audience], taken from the metadata server without a Google
 * auth library (ADR-088). A token is reused until [REFRESH_BEFORE_EXPIRY] before the expiry in its payload.
 */
class MetadataServerIdTokenProvider(
    private val client: HttpClient,
    private val audience: String,
    private val clock: Clock,
    private val identityUrl: String = DEFAULT_IDENTITY_URL,
) : BearerTokenProvider {
    private data class IdToken(val value: String, val expiresAt: Instant)

    private val mutex = Mutex()
    private var cached: IdToken? = null

    override suspend fun token(): String = mutex.withLock {
        cached?.takeIf { clock.now() < it.expiresAt - REFRESH_BEFORE_EXPIRY }?.value
            ?: fetch().also { cached = it }.value
    }

    private suspend fun fetch(): IdToken {
        val response = client.get(identityUrl) {
            parameter(AUDIENCE, audience)
            header(METADATA_FLAVOR, GOOGLE)
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds }
        }
        check(response.status.isSuccess()) { "Metadata server answered ${response.status.value} to the ID token request" }
        val token = response.bodyAsText().trim()
        return IdToken(token, expiryOf(token))
    }

    companion object {
        const val DEFAULT_IDENTITY_URL =
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity"

        private const val AUDIENCE = "audience"
        private const val METADATA_FLAVOR = "Metadata-Flavor"
        private const val GOOGLE = "Google"
        private const val EXPIRY_CLAIM = "exp"
        private const val JWT_PARTS = 3

        private val REQUEST_TIMEOUT = 5.seconds
        private val REFRESH_BEFORE_EXPIRY: Duration = Duration.ofMinutes(5)

        /** Expiry of the JWT [token], read from the `exp` claim of its payload without verifying the signature. */
        fun expiryOf(token: String): Instant {
            val parts = token.split('.')
            require(parts.size == JWT_PARTS) { "ID token is not a JWT" }
            val payload = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]))).jsonObject
            val expiry = requireNotNull(payload[EXPIRY_CLAIM]?.jsonPrimitive?.longOrNull) { "ID token has no expiry" }
            return Instant.ofEpochSecond(expiry)
        }
    }
}
