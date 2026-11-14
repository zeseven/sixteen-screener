package com.sixteenscreener.app.data.remote.auth

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.Signature
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.inject.Inject

/**
 * Builds the RS256-signed JWT assertion exchanged for a Google-signed ID token.
 */
class JwtSigner @Inject constructor(private val clock: Clock) {

    fun sign(key: ServiceAccountKey, targetAudience: String): String {
        val issuedAt = clock.instant().epochSecond
        val header = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
            key.privateKeyId?.let { put("kid", it) }
        }
        val claims = buildJsonObject {
            put("iss", key.clientEmail)
            put("sub", key.clientEmail)
            put("aud", key.tokenUri)
            put("target_audience", targetAudience)
            put("iat", issuedAt)
            put("exp", issuedAt + ASSERTION_LIFETIME.seconds)
        }
        val signingInput = "${header.toString().base64Url()}.${claims.toString().base64Url()}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(key.privateKey)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        return "$signingInput.${signature.base64Url()}"
    }

    private fun String.base64Url() = toByteArray(Charsets.UTF_8).base64Url()

    private fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    companion object {
        val ASSERTION_LIFETIME: Duration = Duration.ofHours(1)
    }
}
