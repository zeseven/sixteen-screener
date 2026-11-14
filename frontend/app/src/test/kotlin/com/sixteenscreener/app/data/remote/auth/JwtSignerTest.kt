package com.sixteenscreener.app.data.remote.auth

import com.sixteenscreener.app.testing.FakeServiceAccount
import com.sixteenscreener.app.testing.MutableClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Signature
import java.time.Instant
import java.util.Base64

class JwtSignerTest {

    private val issuedAt = Instant.parse("2026-11-14T10:00:00Z")
    private val signer = JwtSigner(MutableClock(issuedAt))
    private val key = ServiceAccountKey.parse(FakeServiceAccount.keyJson())

    @Test
    fun `header declares RS256 JWT with key ID`() {
        val header = decodeSegment(signer.sign(key, BACKEND_URL), 0)

        assertEquals(
            JsonObject(
                mapOf(
                    "alg" to JsonPrimitive("RS256"),
                    "typ" to JsonPrimitive("JWT"),
                    "kid" to JsonPrimitive(FakeServiceAccount.PRIVATE_KEY_ID),
                ),
            ),
            header,
        )
    }

    @Test
    fun `header has no key ID when the key has none`() {
        val keyWithoutId = ServiceAccountKey.parse(FakeServiceAccount.keyJson(privateKeyId = null))

        val header = decodeSegment(signer.sign(keyWithoutId, BACKEND_URL), 0)

        assertFalse(header.containsKey("kid"))
    }

    @Test
    fun `claims carry issuer, subject, audience, target audience and one hour lifetime`() {
        val claims = decodeSegment(signer.sign(key, BACKEND_URL), 1)

        assertEquals(
            JsonObject(
                mapOf(
                    "iss" to JsonPrimitive(FakeServiceAccount.CLIENT_EMAIL),
                    "sub" to JsonPrimitive(FakeServiceAccount.CLIENT_EMAIL),
                    "aud" to JsonPrimitive(FakeServiceAccount.TOKEN_URI),
                    "target_audience" to JsonPrimitive(BACKEND_URL),
                    "iat" to JsonPrimitive(issuedAt.epochSecond),
                    "exp" to JsonPrimitive(issuedAt.epochSecond + 3600),
                ),
            ),
            claims,
        )
    }

    @Test
    fun `segments are Base64 URL encoded without padding`() {
        val jwt = signer.sign(key, BACKEND_URL)

        assertEquals(3, jwt.split('.').size)
        assertFalse(jwt.contains('=') || jwt.contains('+') || jwt.contains('/'))
    }

    @Test
    fun `signature verifies with the matching public key`() {
        val jwt = signer.sign(key, BACKEND_URL)

        assertTrue(verify(jwt, FakeServiceAccount.keyPair.public))
    }

    @Test
    fun `signature does not verify when the claims are altered`() {
        val (header, _, signature) = signer.sign(key, BACKEND_URL).split('.')
        val forgedClaims = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"intruder"}""".toByteArray())

        assertFalse(verify("$header.$forgedClaims.$signature", FakeServiceAccount.keyPair.public))
    }

    private fun verify(jwt: String, publicKey: java.security.PublicKey): Boolean {
        val signingInput = jwt.substringBeforeLast('.')
        val signature = Base64.getUrlDecoder().decode(jwt.substringAfterLast('.'))
        return Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            verify(signature)
        }
    }

    private fun decodeSegment(jwt: String, index: Int): JsonObject =
        Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(jwt.split('.')[index]))).jsonObject

    private companion object {
        const val BACKEND_URL = "https://sixteen-backend.example.run.app"
    }
}
